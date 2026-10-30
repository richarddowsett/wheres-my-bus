#!/usr/bin/env bash
set -euo pipefail

PROJECT_ID="wheres-my-bus-513220"
REGION="europe-west1"
TF_STATE_BUCKET="${PROJECT_ID}-tfstate"
IMAGE_URI="${REGION}-docker.pkg.dev/${PROJECT_ID}/wheres-my-bus/backend:latest"

echo "==> 1. Configuring gcloud for project ${PROJECT_ID} (${REGION})..."
gcloud config set project "${PROJECT_ID}"
gcloud config set compute/region "${REGION}"
gcloud config set run/region "${REGION}"

echo "==> 2. Enabling required Bootstrap APIs (Storage, Cloud Build, Artifact Registry)..."
gcloud services enable \
  storage.googleapis.com \
  cloudbuild.googleapis.com \
  artifactregistry.googleapis.com \
  run.googleapis.com \
  firestore.googleapis.com \
  cloudscheduler.googleapis.com \
  secretmanager.googleapis.com \
  --project="${PROJECT_ID}"

echo "==> 3. Ensuring Terraform remote state bucket gs://${TF_STATE_BUCKET} exists..."
if ! gcloud storage buckets describe "gs://${TF_STATE_BUCKET}" --project="${PROJECT_ID}" >/dev/null 2>&1; then
  gcloud storage buckets create "gs://${TF_STATE_BUCKET}" \
    --project="${PROJECT_ID}" \
    --location="${REGION}" \
    --uniform-bucket-level-access \
    --public-access-prevention
  gcloud storage buckets update "gs://${TF_STATE_BUCKET}" --versioning
fi

echo "==> 4. Creating Artifact Registry repository via Terraform..."
cd terraform
if [ ! -f terraform.tfvars ]; then
  cp terraform.tfvars.example terraform.tfvars
  echo "Created terraform/terraform.tfvars from example. Make sure to set bods_api_key and family_pin!"
fi
terraform init
terraform apply -target=google_artifact_registry_repository.repo -auto-approve
cd ..

echo "==> 5. Building and pushing combined Webapp + Kotlin Backend image via Cloud Build..."
gcloud builds submit . \
  --project="${PROJECT_ID}" \
  --region="${REGION}" \
  --tag="${IMAGE_URI}"

echo "==> 6. Applying full Terraform infrastructure (Cloud Run, Firestore, Cloud Scheduler, Secrets)..."
cd terraform
terraform apply -auto-approve

SERVICE_URL=$(terraform output -raw cloud_run_service_url)
echo ""
echo "✅ Deployment Complete!"
echo "🌐 Live Web App & API URL: ${SERVICE_URL}"
