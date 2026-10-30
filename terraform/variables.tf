variable "project_id" {
  description = "The Google Cloud Project ID where resources will be deployed"
  type        = string
  default     = "wheres-my-bus-513220"
}

variable "region" {
  description = "Primary GCP region for Cloud Run, Cloud Scheduler, and Artifact Registry"
  type        = string
  default     = "europe-west1"
}

variable "firestore_location" {
  description = "Location for the Cloud Firestore database"
  type        = string
  default     = "eur3" # Europe multi-region (or europe-west2)
}

variable "service_name" {
  description = "Name of the Cloud Run service"
  type        = string
  default     = "wheres-my-bus-route21"
}

variable "container_image" {
  description = "Full container image URI in Artifact Registry for the Kotlin/Ktor service"
  type        = string
  default     = "europe-west1-docker.pkg.dev/wheres-my-bus-513220/wheres-my-bus/backend:latest"
}

variable "bods_api_key_secret_id" {
  description = "Secret ID in GCP Secret Manager for the UK DfT BODS API key (populated manually outside Terraform)"
  type        = string
  default     = "bods-api-key"
}

variable "family_pin" {
  description = "Shared family passphrase/PIN required for X-Family-Pin header"
  type        = string
  sensitive   = true
  default     = "replace-with-family-pin"
}

variable "morning_cron_schedule" {
  description = "Cron schedule for polling NIBS Route 21 during the morning school commute (Mon-Fri)"
  type        = string
  default     = "* 7-8 * * 1-5"
}

variable "afternoon_cron_schedule" {
  description = "Cron schedule for polling NIBS Route 21 during the afternoon school return (Mon-Fri)"
  type        = string
  default     = "* 15-16 * * 1-5"
}

variable "timetable_sync_cron_schedule" {
  description = "Cron schedule for daily TransXChange timetable sync"
  type        = string
  default     = "0 4 * * *"
}

variable "daily_history_batch_cron_schedule" {
  description = "Cron schedule for daily end-of-day Route 21 punctuality & cancellation batch aggregation"
  type        = string
  default     = "0 19 * * 1-5"
}

variable "timezone" {
  description = "Timezone for Cloud Scheduler commute windows"
  type        = string
  default     = "Europe/London"
}
