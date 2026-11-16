# ReachHub — Architecture, Threat Model & Cloud Run Security Guide

## 1. System Architecture
- **Android Tablet Client (`ReachHub`)**:
  - Kotlin, Jetpack Compose, Material 3 (Cobalt-Blue workspace), Navigation Compose (`@Serializable` type-safe routes), Room Database (`reachhub_local.db`, v2), DataStore Preferences (`reachhub_operator_prefs`), WorkManager (`CampaignExecutionWorker`), and OkHttp (`CloudRunApiClient`).
  - Hardware-backed **Android Keystore ECDSA P-256** (`DeviceKeyManager`) generates a non-exportable signing key inside StrongBox/TEE. Every API request to Cloud Run carries `X-ReachHub-Key-Fingerprint`, `X-ReachHub-Timestamp`, `X-ReachHub-Nonce`, and `X-ReachHub-Signature`.
- **Google Cloud Run Backend (`/backend`)**:
  - Node.js 22 + TypeScript + Express container (`Dockerfile`) deployed to **Google Cloud Run** (HTTPS enforced).
  - Retrieves all Meta WhatsApp Cloud API secrets dynamically from **Google Cloud Secret Manager** (`META_ACCESS_TOKEN`, `META_APP_SECRET`, `META_WEBHOOK_VERIFY_TOKEN`, `META_WABA_ID`, `META_PHONE_MAP_JSON`, optional `REACHHUB_BOOTSTRAP_TOKEN`).
  - Uses **Cloud Firestore** (`FirestoreBackendStore`) for persistent device key records, nonce replay protection, SHA-256 flyer media cache, per-recipient idempotency records, and verified webhook status events.

## 2. Threat Model & Mitigations

| Threat | Mitigation Strategy | Implementation |
|---|---|---|
| Unauthorized Cloud Run access without login UI | Device-bound ECDSA P-256 signature verification (`SHA256withECDSA`), ±300s timestamp skew limit, single-use nonce replay guard (`409` on replay), and per-device sliding-window rate limiter. | `DeviceKeyManager.kt` + `backend/src/security/deviceAuth.ts` |
| Meta token / App Secret extraction from APK | Zero Meta tokens, App Secrets, or static shared API keys in Kotlin, `BuildConfig`, `.env`, or GitHub workflows. | GCP Secret Manager (`backend/src/config.ts`) |
| Accidental live WhatsApp dispatch | Defaults to **Simulation Mode**; Production Mode is locked behind a live **12-point Production Readiness Gate** + pre-dispatch audience audit + explicit confirmation checkbox. | `SettingsRepository` + `CreateCampaignDialog.kt` |
| Duplicate messages on retry or network drop | Deterministic `idempotencyKey` (`campaign_<id>_c<contactId>_<sha256>`) enforced by unique Room index and server-side idempotency lookup prior to Meta Graph calls. | `ReachHubDaos.kt` + `backend/src/routes/api.ts` |
| Spoofed WhatsApp delivery webhooks | Constant-time `crypto.timingSafeEqual` verification of `X-Hub-Signature-256` against raw HTTP body + event deduplication (`wamid:status:timestamp`) + monotonic precedence (`READ > DELIVERED > SENT > SUBMITTED > FAILED`). | `backend/src/security/webhookSignature.ts` + `store.ts` |
| CSV formula injection & oversize imports | SAF `OpenDocument` / `CreateDocument`, 15 MB & 25,000-row limits, strict E.164 normalization (`+91` default toggleable), and leading `'` neutralization for `=`, `+`, `-`, `@`, `\t`, `\r` cells. | `CsvParserAndSecurity.kt` |
| Malicious image flyers | Zero-permission Android Photo Picker (`PickVisualMedia`), 5 MB cap, JPEG/PNG magic-byte inspection, dimension decode check, SHA-256 deduplication. | `ImageMediaSecurity` + `MediaRepository` |
| PII exposure via Android backup | `android:allowBackup="false"`, XML database/DataStore exclusions, and phone masking in logs. | `AndroidManifest.xml` + `backup_rules.xml` |

## 3. Deploying Google Cloud Run & Secret Manager

1. **Create Secret Manager secrets** in your Google Cloud project:
   - `reachhub-meta-access-token`: Permanent System User token from Meta Business Manager
   - `reachhub-meta-app-secret`: Meta App Secret (used for `X-Hub-Signature-256` webhook HMAC)
   - `reachhub-meta-webhook-verify-token`: Random high-entropy string for `GET /webhooks/whatsapp`
   - `reachhub-meta-waba-id`: Your WhatsApp Business Account ID
   - `reachhub-meta-phone-map-json`: JSON map matching Settings `Cloud Run Profile Ref` keys to Meta Phone Number IDs, e.g.:
     ```json
     {"default": "109876543210987", "admissions": "109876543210988"}
     ```
   - `reachhub-bootstrap-registration-token`: One-time token you enter into ReachHub Settings -> **Register Tablet Key** when pairing your tablet.
2. **Grant the Cloud Run runtime Service Account**:
   - `roles/secretmanager.secretAccessor`
   - `roles/datastore.user` (for Firestore native mode)
3. **Deploy `/backend` to Cloud Run** (e.g., via Cloud Shell in tablet browser):
   ```bash
   gcloud run deploy reachhub-backend \
     --source ./backend \
     --region asia-south1 \
     --allow-unauthenticated \
     --set-env-vars "GCP_PROJECT_ID=YOUR_PROJECT_ID,META_GRAPH_VERSION=v21.0,CLOUD_TASKS_CONFIGURED=true"
   ```
   *(Security note: `--allow-unauthenticated` lets Meta webhooks and your ECDSA-signed tablet requests reach Cloud Run HTTPS without IAM login; every API route still requires ECDSA P-256 verification and every webhook requires Meta HMAC-SHA256 verification.)*
4. **Configure Meta WhatsApp Webhook**:
   - Callback URL: `https://<your-cloud-run-url>/webhooks/whatsapp`
   - Verify token: exact value stored in `reachhub-meta-webhook-verify-token`
   - Subscribe to `messages` field under WhatsApp Business Account webhooks.
5. **Pair Your Android Tablet in ReachHub Settings**:
   - Paste your `https://...run.app` URL -> **Save Endpoint**
   - Tap **Register Tablet Key** (enter your transient bootstrap token once)
   - Tap **Verify Readiness** -> Confirm GCP Billing budget alert toggle -> Once **12/12** checks pass, switch off Simulation Mode only when ready for live campaigns.
6. **Rotate Device or Meta Keys**:
   - Use **Rotate Key** in Settings to generate a fresh Android Keystore ECDSA P-256 key, then re-register with Cloud Run.
   - Add a new Secret Manager version in GCP for rotated Meta tokens and disable old versions cleanly.
