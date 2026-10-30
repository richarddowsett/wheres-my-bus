locals {
  required_apis = [
    "run.googleapis.com",
    "firestore.googleapis.com",
    "cloudscheduler.googleapis.com",
    "secretmanager.googleapis.com",
    "artifactregistry.googleapis.com",
    "iam.googleapis.com",
  ]
}

resource "google_project_service" "apis" {
  for_each           = toset(local.required_apis)
  project            = var.project_id
  service            = each.value
  disable_on_destroy = false
}

# ------------------------------------------------------------------------------
# Artifact Registry Repository for Container Images
# ------------------------------------------------------------------------------
resource "google_artifact_registry_repository" "repo" {
  location      = var.region
  repository_id = "wheres-my-bus"
  description   = "Container images for NIBS Route 21 Bus Disruption Monitor"
  format        = "DOCKER"

  depends_on = [google_project_service.apis]
}

# ------------------------------------------------------------------------------
# Cloud Firestore Database (Native Mode)
# ------------------------------------------------------------------------------
resource "google_firestore_database" "database" {
  project                     = var.project_id
  name                        = "(default)"
  location_id                 = var.firestore_location
  type                        = "FIRESTORE_NATIVE"
  concurrency_mode            = "OPTIMISTIC"
  app_engine_integration_mode = "DISABLED"

  depends_on = [google_project_service.apis]
}

# ------------------------------------------------------------------------------
# Secret Manager Secrets (BODS API Key container created by Terraform; value populated manually)
# ------------------------------------------------------------------------------
resource "google_secret_manager_secret" "bods_api_key" {
  project   = var.project_id
  secret_id = var.bods_api_key_secret_id

  replication {
    auto {}
  }

  depends_on = [google_project_service.apis]
}

resource "google_secret_manager_secret" "family_pin" {
  project   = var.project_id
  secret_id = "${var.service_name}-family-pin"

  replication {
    auto {}
  }

  depends_on = [google_project_service.apis]
}

resource "google_secret_manager_secret_version" "family_pin_version" {
  secret      = google_secret_manager_secret.family_pin.id
  secret_data = var.family_pin
}

# ------------------------------------------------------------------------------
# Service Accounts & IAM
# ------------------------------------------------------------------------------
resource "google_service_account" "cloud_run_sa" {
  account_id   = "${var.service_name}-run-sa"
  display_name = "Cloud Run runtime SA for NIBS Route 21 Monitor"
}

resource "google_project_iam_member" "firestore_user" {
  project = var.project_id
  role    = "roles/datastore.user"
  member  = "serviceAccount:${google_service_account.cloud_run_sa.email}"
}

resource "google_secret_manager_secret_iam_member" "bods_key_access" {
  project   = var.project_id
  secret_id = google_secret_manager_secret.bods_api_key.secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.cloud_run_sa.email}"
}

resource "google_secret_manager_secret_iam_member" "family_pin_access" {
  secret_id = google_secret_manager_secret.family_pin.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.cloud_run_sa.email}"
}

resource "google_service_account" "scheduler_sa" {
  account_id   = "${var.service_name}-sched-sa"
  display_name = "Cloud Scheduler SA for invoking Route 21 commute poller"
}

# ------------------------------------------------------------------------------
# Cloud Run v2 Service (Kotlin + Ktor Backend & Bundled React Native Web SPA)
# ------------------------------------------------------------------------------
resource "google_cloud_run_v2_service" "app" {
  name     = var.service_name
  location = var.region
  ingress  = "INGRESS_TRAFFIC_ALL"

  template {
    service_account = google_service_account.cloud_run_sa.email

    scaling {
      min_instance_count = 0
      max_instance_count = 2
    }

    containers {
      image = var.container_image

      ports {
        container_port = 8080
      }

      resources {
        limits = {
          cpu    = "1"
          memory = "512Mi"
        }
        cpu_idle = true
      }

      env {
        name  = "GCP_PROJECT_ID"
        value = var.project_id
      }

      env {
        name  = "BODS_NOC"
        value = "NIBS"
      }

      env {
        name  = "BODS_LINE_REF"
        value = "21"
      }

      env {
        name = "BODS_API_KEY"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.bods_api_key.secret_id
            version = "latest"
          }
        }
      }

      env {
        name  = "GCP_PROJECT_ID"
        value = var.project_id
      }

      env {
        name  = "STORAGE_MODE"
        value = "FIRESTORE"
      }

      env {
        name = "FAMILY_PIN"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.family_pin.secret_id
            version = "latest"
          }
        }
      }
    }
  }

  depends_on = [
    google_project_service.apis,
    google_secret_manager_secret.bods_api_key,
    google_secret_manager_secret_iam_member.bods_key_access,
    google_secret_manager_secret_version.family_pin_version,
    google_firestore_database.database,
  ]
}

# Allow public HTTP access to the web UI & API (protected at application layer by X-Family-Pin)
resource "google_cloud_run_v2_service_iam_member" "public_invoker" {
  project  = var.project_id
  location = var.region
  name     = google_cloud_run_v2_service.app.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}

# Allow Cloud Scheduler SA to invoke Cloud Run via OIDC
resource "google_cloud_run_v2_service_iam_member" "scheduler_invoker" {
  project  = var.project_id
  location = var.region
  name     = google_cloud_run_v2_service.app.name
  role     = "roles/run.invoker"
  member   = "serviceAccount:${google_service_account.scheduler_sa.email}"
}

# ------------------------------------------------------------------------------
# Cloud Scheduler Jobs (Morning Window, Afternoon Window, Daily Timetable Sync)
# ------------------------------------------------------------------------------
resource "google_cloud_scheduler_job" "morning_commute_poll" {
  name             = "${var.service_name}-morning-poll"
  description      = "Polls BODS SIRI-VM and SIRI-SX every minute during morning Brentwood -> Ongar school run"
  schedule         = var.morning_cron_schedule
  time_zone        = var.timezone
  attempt_deadline = "30s"
  region           = var.region

  http_target {
    http_method = "POST"
    uri         = "${google_cloud_run_v2_service.app.uri}/api/internal/poll?window=MORNING"

    oidc_token {
      service_account_email = google_service_account.scheduler_sa.email
      audience              = google_cloud_run_v2_service.app.uri
    }
  }

  depends_on = [google_project_service.apis]
}

resource "google_cloud_scheduler_job" "afternoon_commute_poll" {
  name             = "${var.service_name}-afternoon-poll"
  description      = "Polls BODS SIRI-VM and SIRI-SX every minute during afternoon Ongar -> Brentwood school return"
  schedule         = var.afternoon_cron_schedule
  time_zone        = var.timezone
  attempt_deadline = "30s"
  region           = var.region

  http_target {
    http_method = "POST"
    uri         = "${google_cloud_run_v2_service.app.uri}/api/internal/poll?window=AFTERNOON"

    oidc_token {
      service_account_email = google_service_account.scheduler_sa.email
      audience              = google_cloud_run_v2_service.app.uri
    }
  }

  depends_on = [google_project_service.apis]
}

resource "google_cloud_scheduler_job" "daily_timetable_sync" {
  name             = "${var.service_name}-timetable-sync"
  description      = "Syncs NIBS Route 21 TransXChange timetable daily from BODS"
  schedule         = var.timetable_sync_cron_schedule
  time_zone        = var.timezone
  attempt_deadline = "60s"
  region           = var.region

  http_target {
    http_method = "POST"
    uri         = "${google_cloud_run_v2_service.app.uri}/api/internal/sync-timetable"

    oidc_token {
      service_account_email = google_service_account.scheduler_sa.email
      audience              = google_cloud_run_v2_service.app.uri
    }
  }

  depends_on = [google_project_service.apis]
}

resource "google_cloud_scheduler_job" "daily_history_batch" {
  name             = "${var.service_name}-daily-history-batch"
  description      = "Runs daily batch to aggregate latest Route 21 delay and cancellation metrics"
  schedule         = var.daily_history_batch_cron_schedule
  time_zone        = var.timezone
  attempt_deadline = "60s"
  region           = var.region

  http_target {
    http_method = "POST"
    uri         = "${google_cloud_run_v2_service.app.uri}/api/internal/batch-daily-history"

    oidc_token {
      service_account_email = google_service_account.scheduler_sa.email
      audience              = google_cloud_run_v2_service.app.uri
    }
  }

  depends_on = [google_project_service.apis]
}
