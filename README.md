# Voxie

Voxie is an Android app for scheduling **one-way, AI-assisted voice calls**. Tell it who to call, what to say, and when; review the prepared message and audio; then Voxie schedules an Exotel outbound call. The recipient hears a pre-generated recording, not a live conversational agent.

Personal Starter and Personal Plus support individual calls. Business includes individual calls plus reusable contact groups, imports, templates, and per-recipient group campaigns. This repository contains the Android app, Flask API, database models, migrations, tests, and integration guides.

## Features

| Area | What it does |
| --- | --- |
| Onboarding | Welcome, Supabase email sign-up/sign-in, three-tier pricing, Razorpay **test-mode** checkout, then the plan's dashboard. Returning paid users bypass pricing. |
| Personal calling | Type or dictate a natural-language request, search/select a phone contact, star favorites, review/edit the message and schedule, preview audio, and track upcoming or past calls. Contacts and favorites are specific to the current phone, not a shared account address book. |
| Business calling | Keep individual calling, create and manage groups, call one saved member or a selected set, and schedule one separate outbound job per eligible recipient. |
| Contact imports | Enter contacts manually or stage a CSV/Google Sheet import. Common name/phone headers and ordinary two-column lists are detected; ambiguous columns require selection. Review, correct, exclude, and explicitly approve rows before saving. |
| Templates | Create, search, edit, delete, and attach named spoken-message templates to individual or group requests. |
| Scheduling | Choose a date in the calendar or give a time in the prompt. Campaigns can stagger recipients by an interval; confirmation shows each recipient's schedule, audio, and estimated credits. |
| Account | Edit display name, organization name (Business), timezone, theme, backend connection, and call-confirmation preference. The UI supports light/dark themes and haptic actions. |

Example requests:

```text
Call Dad tomorrow at 8 PM and wish him a happy birthday.
Call Aarav and Akshay from Appointments using the Reminder template tomorrow at 6 PM.
Call Batch B Parents tomorrow at 6 PM; remind them about Monday's exam, one call every 5 minutes.
```

Recipient selection is verified against the authenticated account's saved groups or the device-supplied phone contacts. The LLM helps interpret instructions and prepare speech; it does not authorize arbitrary recipients. Missing or ambiguous recipients and times require clarification. Group campaigns require eligible, consent-approved contacts and a final review.

## Architecture

```text
Android (Kotlin / Jetpack Compose)
  -> Flask API (Supabase bearer-token validation, plans, contacts, scheduling)
  -> DeepSeek (message and time extraction) + ElevenLabs (audio)
  -> SQLAlchemy database + APScheduler (one job per recipient)
  -> Exotel outbound flow -> public audio URL / status webhook
```

Flask owns account-scoped data, plan entitlements, test-credit accounting, and call jobs. Supabase Auth owns email identities and sessions; PostgreSQL (typically Supabase) stores application data. APScheduler reloads pending jobs on backend startup. Exotel callbacks and periodic reconciliation update call results. The backend must remain running at scheduled times; a temporary local tunnel is not production hosting.

## Requirements

- Python 3.11+ and Android Studio/Android SDK with a compatible JDK (see `android/gradle/libs.versions.toml` for build-tool versions).
- Android 8.0/API 26 or newer; microphone permission for voice input and contacts permission for phone-contact selection.
- A Supabase project and database connection. Live sandbox checkout, speech, and calling additionally need Razorpay test keys, DeepSeek, ElevenLabs, and Exotel credentials.
- A public HTTPS backend URL for Exotel callbacks and generated audio. For local phone testing, the phone must reach the computer on the same Wi-Fi.
- Optional Google Cloud project for read-only Google Sheets import. CSV import needs no Google credentials.

## Run Locally

From the repository root on Windows (PowerShell):

```powershell
cd backend
py -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
Copy-Item .env.example .env
# Edit .env with your own credentials and database URL.
.\.venv\Scripts\python.exe run.py
```

The backend serves `http://127.0.0.1:5000` and `GET /health`; it binds to `0.0.0.0:5000` for a phone on the same Wi-Fi. Use the project venv Python: a system Python without `faster-whisper` will start Flask but fail on voice transcription. The dev server is not for production. Startup creates/extends tables and reloads pending jobs; SQL migrations are in `backend/migrations/` for managed deployments.

In another terminal:

```powershell
cd android
.\gradlew.bat :app:assembleDebug
```

Install `android/app/build/outputs/apk/debug/app-debug.apk` via Android Studio or `adb install -r`. Enter `http://<your-computer-Wi-Fi-IP>:5000` in the app's Connection settings. `localhost` on a phone means the phone, not your computer. For external access, use HTTPS and set `PUBLIC_BASE_URL` to the public backend origin, without a path. Keep that URL stable while testing Exotel; restart Flask after changing `.env`.

The backend defaults to local SQLite only when `DATABASE_URL` is absent. Use PostgreSQL/Supabase for shared or durable data. Never commit `backend/.env`, database passwords, provider secrets, generated audio, virtual environments, or build outputs.

## Configuration

Copy `backend/.env.example` and set the values below. Provider credentials stay on the backend, never in the APK.

| Variable | Purpose |
| --- | --- |
| `DATABASE_URL` | SQLAlchemy PostgreSQL connection URI; required for the intended Supabase-backed setup. |
| `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY` | Supabase Auth project URL and publishable key. Configure the Email provider and email-confirmation policy in Supabase. |
| `DEEPSEEK_API_KEY` | Natural-language instruction/message processing. |
| `ELEVENLABS_API_KEY`, `ELEVENLABS_VOICE_ID` | Text-to-speech; `ELEVENLABS_MODEL_ID` is optional. |
| `EXOTEL_API_KEY`, `EXOTEL_API_TOKEN`, `EXOTEL_ACCOUNT_SID`, `EXOTEL_CALLER_ID`, `EXOTEL_FLOW_ID` | Exotel outbound API and saved voice flow. `EXOTEL_BASE_URL` defaults to `api.exotel.com`. |
| `PUBLIC_BASE_URL` | Public HTTPS origin Exotel can reach for audio and callbacks. It must resolve to the running backend. |
| `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` | Razorpay **test-mode** checkout and server-side payment verification. |
| `TRANSCRIPTION_PROVIDER` | `local` (default, faster-whisper) or `openai`. For `openai`, set `OPENAI_API_KEY`; local model/device settings are in `.env.example`. |
| `SECRET_KEY`, `TIMEZONE` | Flask secret and scheduler default timezone (`Asia/Kolkata` by default). Account scheduling uses its configured timezone. |
| `PLAN_CATALOG_JSON`, `EXOTEL_ESTIMATED_COST_PAISE` | Optional backend-owned plan prices/credits/entitlements and per-recipient provider-cost estimate. |

Default sandbox tiers are Starter (INR 49, 10 test credits), Plus (INR 199, 50), and Business (INR 699, 200). Configure them with `PLAN_CATALOG_JSON` rather than changing prices in Android. A verified purchase activates its tier and adds test credits once; repeated payment callbacks do not duplicate credits. One credit is reserved per scheduled recipient, refunded for cancellation before execution, failure, or a missed call, and consumed when connected. Test credits are not a production balance.

## External Setup

1. **Supabase:** Create the project, obtain the database URI, project URL, and publishable key. Enable Email sign-in. If email confirmation is enabled, users must confirm before signing in; disabling it is a Supabase dashboard setting, not an app switch. The backend validates protected requests' bearer tokens and scopes data by user ID.
2. **Razorpay:** Create test keys, put both in `backend/.env`, and complete checkout in the app. The backend checks the owned order, signature, amount/currency, and captured payment before allocating credits. Live keys are not supported by this MVP. See [Razorpay test setup](docs/razorpay-test.md).
3. **Exotel:** Set the API credentials, caller ID (ExoPhone), account SID, and saved outbound flow ID. Point its dynamic audio Greeting at `PUBLIC_BASE_URL/exotel-audio-url`; the API supplies `PUBLIC_BASE_URL/webhook/exotel` as its status callback. To avoid silence immediately after answer, place the included short ring Greeting before the dynamic Greeting as described in [Exotel start ring](docs/exotel-start-ring.md). A trial account may restrict calls to Exotel-verified recipient numbers. A healthy `/health` URL alone does not prove audio playback or provider delivery.
4. **Google Sheets (optional):** Enable Google Sheets API in Google Cloud. Create an **Android OAuth client** for package `com.ayush.madv2` and the signing certificate SHA-1 (`cd android; .\gradlew.bat signingReport`). Add `https://www.googleapis.com/auth/spreadsheets.readonly`; while the OAuth app is in Testing, add the importing Google account as a test user. The app reads a selected sheet once and sends rows to the backend for approval; there is no automatic sync or Google sign-in for Voxie. See [Business setup](docs/voxie-plans-business.md).

## Credits, Consent, and Data

Only Business plans can use server-enforced group and campaign endpoints. Imported rows are staged for review, not immediately added to groups. Duplicate/invalid numbers are flagged, and a spreadsheet entry alone does **not** establish permission for automated calls. Contacts must be explicitly consent-approved; opt-outs are excluded and eligibility is rechecked before execution. Repeated numbers are called once per campaign.

Key tables include `call_jobs`, `account_plans`, `test_credit_purchases`, `credit_events`, `contact_groups`, `group_contacts`, `contact_imports`, `message_templates`, `campaigns`, and `audio_previews`. Call status values include pending, calling, connected, missed, failed, and cancelled; campaign summaries aggregate their jobs. Exotel's answered/completed events do not prove a recipient heard the whole message. The default Flask process runs the scheduler in memory, so do not start multiple scheduler-enabled workers against the same database without changing the scheduling architecture.

## Tests

Backend tests mock external AI, payment, and telephony providers; they do not place real calls or charge a gateway:

```powershell
cd backend
.\.venv\Scripts\python.exe -m unittest discover -s tests -v
```

Android build and unit tests:

```powershell
cd android
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Manual end-to-end testing still requires real sandbox credentials, a reachable public backend, eligible Exotel recipients, and available ElevenLabs/Exotel credits. Verify checkout, contact consent, import approval, audio preview, scheduled call delivery, and callback status separately.

## Project Layout

| Path | Contents |
| --- | --- |
| `android/` | Kotlin/Jetpack Compose app, network layer, screens, and Android tests. |
| `backend/app/routes/` | Auth, payments, individual calls, groups, templates, campaigns, and Exotel webhook APIs. |
| `backend/app/services/` | LLM, speech, transcription, import, plan, and provider integrations. |
| `backend/app/models/`, `backend/migrations/` | SQLAlchemy models and SQL migrations. |
| `backend/tests/` | Backend tests with mocked providers. |
| `docs/` | Detailed [API reference](docs/api.md), [group prompts/templates](docs/group-prompts-templates.md), [Business setup](docs/voxie-plans-business.md), and integration notes. |

## Current Limitations

This is a sandbox MVP, not a production calling platform: purchases and credits are test-only; calls are one-way; Sheets imports are one-time; Flask's local scheduler and media storage require a continuously running backend; and provider credentials, trial verification, network reachability, and the Exotel flow must be configured before a live call works. CSV import and much of the UI can be tested without placing calls, but real speech generation, checkout, and telephony cannot be replaced with silent mocks in production use.

## Troubleshooting

- **Phone cannot connect:** Check `GET /health`, the computer's current Wi-Fi IP, port 5000, firewall, and that the phone and computer share a network. A Cloudflare/other tunnel also needs a reachable local backend.
- **Voice transcription fails:** Start `run.py` with `backend/.venv` after installing `requirements.txt`; the default local provider needs `faster-whisper` and its model download. The optional OpenAI provider needs its own API key.
- **Account service unavailable:** Confirm the Supabase URL/key, backend internet access, and Supabase project status. A backend `/health` response does not prove Supabase is reachable.
- **Call connects without audio:** Check the public `PUBLIC_BASE_URL`, generated audio URL, saved Exotel flow's dynamic Greeting, and callback configuration. The short ring Greeting is an optional audible cue, not a substitute for the message URL.
- **Google authorization cancelled:** Check the Android OAuth package/SHA-1, Sheets API and read-only scope, and the importing account's Testing allowlist entry. Exporting to CSV is an alternative.
