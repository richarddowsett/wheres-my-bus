# Where's My Bus — NIBS Route 21 (Brentwood $\leftrightarrow$ Ongar) Monitor

A live school-commute disruption & ETA notification platform for **NIBS Buses Route 21 (`21` / `21ON`)** between **Brentwood** and **Ongar**, built with **React Native (Expo / React Native for Web)**, **Kotlin (Ktor)**, and **Terraform on Google Cloud** (Project: **`wheres-my-bus-513220`**, Region: **`europe-west1`**).

## Repository Structure

- [`webapp/`](file:///Users/richarddowsett/wheres-my-bus/webapp) — React Native & React Native Web frontend (TypeScript, ESLint, Jest $\ge 80\%$ coverage).
- [`backend/`](file:///Users/richarddowsett/wheres-my-bus/backend) — Kotlin + Ktor server integrating BODS `TransXChange` timetables, `SIRI-VM` live GPS tracking, `SIRI-SX` disruption feeds, and 7-day historical reliability analytics (ktlint, Detekt, Kover $\ge 80\%$ coverage).
- [`terraform/`](file:///Users/richarddowsett/wheres-my-bus/terraform) — Google Cloud Platform Infrastructure-as-Code (Cloud Run v2, Firestore, Cloud Scheduler, Secret Manager, Artifact Registry, GCS Remote State).
- [`.github/workflows/ci-cd.yml`](file:///Users/richarddowsett/wheres-my-bus/.github/workflows/ci-cd.yml) — Automated GitHub Actions CI/CD pipeline (runs all linters, unit/integration tests, $\ge 80\%$ coverage checks, Terraform validation, and deploys to Google Cloud on every push to `main`).
- [`Dockerfile`](file:///Users/richarddowsett/wheres-my-bus/Dockerfile) & [`deploy.sh`](file:///Users/richarddowsett/wheres-my-bus/deploy.sh) — Multi-stage build and 1-command Google Cloud deployment script.

## GitHub Actions Automated CI/CD Setup

The workflow in [`.github/workflows/ci-cd.yml`](file:///Users/richarddowsett/wheres-my-bus/.github/workflows/ci-cd.yml) runs three parallel quality gates (`webapp-quality`, `backend-quality`, `terraform-validate`) on every push and pull request, and automatically builds and deploys to **`wheres-my-bus-513220`** (`europe-west1`) when all checks pass on `main`.

### 1. Create the GitHub Actions Deployer Service Account in `gcloud`

```bash
export PROJECT_ID="wheres-my-bus-513220"

gcloud iam service-accounts create github-actions-deployer \
  --project="${PROJECT_ID}" \
  --display-name="GitHub Actions CI/CD Deployer"

for ROLE in \
  roles/run.admin \
  roles/datastore.owner \
  roles/cloudscheduler.admin \
  roles/secretmanager.admin \
  roles/artifactregistry.admin \
  roles/storage.admin \
  roles/iam.serviceAccountAdmin \
  roles/iam.serviceAccountUser \
  roles/resourcemanager.projectIamAdmin \
  roles/serviceusage.serviceUsageAdmin; do
  gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
    --member="serviceAccount:github-actions-deployer@${PROJECT_ID}.iam.gserviceaccount.com" \
    --role="${ROLE}"
done

# Export JSON key for GitHub Secrets (or configure Workload Identity Federation)
gcloud iam service-accounts keys create /tmp/gcp-sa-key.json \
  --iam-account="github-actions-deployer@${PROJECT_ID}.iam.gserviceaccount.com"
```

### 2. Add Repository Secrets in GitHub (`Settings -> Secrets and variables -> Actions`)

| Secret Name | Value |
| :--- | :--- |
| `BODS_API_KEY` | Your UK DfT Bus Open Data Service API Key |
| `FAMILY_PIN` | Your shared family PIN (e.g. `2121`) |
| `GCP_SA_KEY` | Contents of `/tmp/gcp-sa-key.json` *(or set `GCP_WIF_PROVIDER` + `GCP_WIF_SERVICE_ACCOUNT` for keyless OIDC)* |

## Deploying Manually to Google Cloud (`wheres-my-bus-513220`)

1. Configure your secrets in `terraform/terraform.tfvars`:
   ```bash
   cp terraform/terraform.tfvars.example terraform/terraform.tfvars
   # Edit bods_api_key and family_pin in terraform/terraform.tfvars
   ```
2. Authenticate `gcloud` and run [`./deploy.sh`](file:///Users/richarddowsett/wheres-my-bus/deploy.sh):
   ```bash
   gcloud auth login
   gcloud auth application-default login
   ./deploy.sh
   ```

## Running Locally

### 1. Start the Kotlin + Ktor Backend (Port 8080)

```bash
cd backend
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export BODS_API_KEY="your-bods-api-key" # optional: uses bundled NIBS Route 21 timetable if omitted
export FAMILY_PIN="2121"
./gradlew run
```

### 2. Start the React Native / Web App

In a second terminal:

```bash
cd webapp
export EXPO_PUBLIC_API_URL="http://localhost:8080"
npm run web
```
