output "cloud_run_service_url" {
  description = "Public HTTPS URL of the deployed Cloud Run service (serves both API and React Native Web UI)"
  value       = google_cloud_run_v2_service.app.uri
}

output "artifact_registry_repository" {
  description = "Docker repository URI for pushing the backend container image"
  value       = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.repo.repository_id}"
}

output "cloud_run_service_account" {
  description = "Service account email used by the Cloud Run backend"
  value       = google_service_account.cloud_run_sa.email
}

output "bods_api_key_secret_name" {
  description = "GCP Secret Manager resource name for the BODS API key"
  value       = google_secret_manager_secret.bods_api_key.name
}
