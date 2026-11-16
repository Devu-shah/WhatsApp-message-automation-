# ReachHub — WhatsApp Campaign Manager (Android Tablet)

**ReachHub** is a single-operator, tablet-first native Android application built with **Kotlin, Jetpack Compose, Material 3, Room Database, DataStore, Navigation Compose, WorkManager, and OkHttp**, paired with a **Google Cloud Run** TypeScript backend for the official **Meta WhatsApp Business Platform Cloud API**.

## Features Implemented (Phases 1–8 Complete)
- **Single-Operator Tablet Workspace**: Cobalt-Blue Material 3 adaptive shell (`NavigationRail` on tablets `>= 600dp`, drawer + bottom bar on phones) with zero login/multi-user screens.
- **Settings & User-Defined Sender Lines**:
  - Configurable default country calling code (`+91` default) with an on/off auto-apply switch when CSV files already contain international numbers.
  - Multiple user-defined **WhatsApp Business API sender numbers** (`SenderProfile`) mapped to Cloud Run Phone Number ID profiles.
  - Hardware-backed **Android Keystore ECDSA P-256** key signing, one-tap **Register Tablet Key**, **Verify Readiness**, and **12-Point Production Readiness Gate**.
- **Contacts & Hardened CSV Import**:
  - SAF file picker, 15 MB & 25,000-row safety caps, E.164 normalization, deduplication, dynamic custom fields, suppression/opt-out guardrails, and tablet split-pane directory with import status badges (`CSV_IMPORTED`, `CSV_UPDATED`, `MANUAL`).
- **Dynamic Templates & Verified Flyer Media**:
  - `{Placeholder}` builder mapped deterministically to Meta `{{1}}`, `{{2}}` positional parameters + live contact preview.
  - Zero-permission Photo Picker (`PickVisualMedia`) with 5 MB cap, JPEG/PNG magic-byte validation, SHA-256 deduplication, and automatic Meta media upload caching.
- **Campaign Manager, Analytics, Logs & History**:
  - 3-step Campaign Wizard, Pre-Dispatch Audience Audit (Valid / Invalid / Duplicate / Opted-Out), explicit dispatch confirmation gate, Pause / Resume / Cancel controls, and idempotency keys.
  - Real-time Canvas delivery funnel analytics computed strictly from append-only status events (`QUEUED -> SUBMITTED -> SENT -> DELIVERED -> READ / FAILED / SKIPPED`), exponential-backoff retry classification (`130429` retryable vs `131026` permanent), safe draft cloning, and formula-injection-safe CSV exports.
- **Google Cloud Run Backend (`/backend`)**:
  - Express + TypeScript service loading Meta tokens/App Secrets exclusively from **Google Cloud Secret Manager**, verifying ECDSA P-256 tablet signatures + nonces + Meta `X-Hub-Signature-256` webhooks, and persisting state in **Cloud Firestore**.

## Building Debug & Signed Release APKs on GitHub Actions (No Laptop Required)
1. Push or upload this repository to GitHub (`main` branch).
2. Open **Actions** in your Android tablet browser:
   - Run **ReachHub Android Debug CI** -> download `reachhub-debug-apk` (`app-debug.apk`).
   - Run **ReachHub Backend & Security Checks** -> validates Cloud Run TypeScript compilation, security unit tests, and secret hygiene.
3. For a signed **Release APK**, configure these repository secrets under **Settings -> Secrets and variables -> Actions**:
   - `SIGNING_KEYSTORE_BASE64`
   - `SIGNING_STORE_PASSWORD`
   - `SIGNING_KEY_PASSWORD`
   Then run **ReachHub Signed Release APK**.

See `docs/ARCHITECTURE_AND_SECURITY.md` for step-by-step **Google Cloud Run**, **Secret Manager**, and **Meta Webhook** setup.
