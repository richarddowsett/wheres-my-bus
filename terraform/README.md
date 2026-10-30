# Terraform Infrastructure — NIBS Route 21 Bus Disruption Monitor

Provisions the Google Cloud Platform infrastructure in project **`wheres-my-bus-513220`** (default region **`europe-west1`**):
- **Remote State (`gcs` backend)**: Stores Terraform state in `gs://wheres-my-bus-513220-tfstate/wheres-my-bus/state`.
- **Cloud Run v2 (`wheres-my-bus-route21`)**: Scale-to-zero container hosting the Kotlin/Ktor backend and compiled React Native Web bundle.
- **Cloud Firestore (Native Mode)**: Stores device push subscriptions, active watch sessions, cached `TransXChange` timetables, and alert deduplication state.
- **Cloud Scheduler**:
  - `wheres-my-bus-route21-morning-poll` (`* 7-8 * * 1-5` `Europe/London`)
  - `wheres-my-bus-route21-afternoon-poll` (`* 15-16 * * 1-5` `Europe/London`)
  - `wheres-my-bus-route21-timetable-sync` (`0 4 * * *` `Europe/London`)
  - `wheres-my-bus-route21-daily-history-batch` (`0 19 * * 1-5` `Europe/London`)
- **Secret Manager**: Stores `BODS_API_KEY` and `FAMILY_PIN`.
- **Artifact Registry**: Docker repository `europe-west1-docker.pkg.dev/wheres-my-bus-513220/wheres-my-bus`.

## 1. Bootstrap Remote State Bucket in `gcloud`

```bash
gcloud auth login
gcloud auth application-default login
gcloud config set project wheres-my-bus-513220
gcloud config set compute/region europe-west1
gcloud config set run/region europe-west1

gcloud services enable storage.googleapis.com --project=wheres-my-bus-513220

gcloud storage buckets create gs://wheres-my-bus-513220-tfstate \
  --project=wheres-my-bus-513220 \
  --location=europe-west1 \
  --uniform-bucket-level-access \
  --public-access-prevention

gcloud storage buckets update gs://wheres-my-bus-513220-tfstate --versioning
```

## 2. Initialize & Apply Terraform

```bash
cd terraform
cp terraform.tfvars.example terraform.tfvars

terraform init
terraform fmt -check
terraform validate
terraform plan
terraform apply
```
