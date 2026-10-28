# Voxie

Voxie schedules one-way AI voice calls from an Android app. Personal users call individual contacts. Business users also save groups, review CSV or Google Sheets imports, and schedule one outbound job per eligible recipient.

Supabase handles email accounts; Flask owns plan checks, test credits, contacts, campaign scheduling, and authenticated APIs. DeepSeek prepares a message and time, ElevenLabs creates hosted audio, APScheduler triggers Exotel's outbound flow, and Exotel callbacks update call status. Calls play a prepared message and end; they are not conversational.

## Local development

1. Copy `backend/.env.example` to `backend/.env` and fill in the existing Supabase, DeepSeek, ElevenLabs, Exotel, and Razorpay **test** credentials. Set `PUBLIC_BASE_URL` to a reachable HTTPS backend before generating call audio.
2. From `backend`, run `.\.venv\Scripts\python.exe run.py`. Startup creates the new tables and adds the required call columns. The equivalent PostgreSQL migration is in `backend/migrations/003_plans_groups_campaigns.sql`.
3. From `android`, run `.\gradlew.bat :app:assembleDebug`, then install/run from Android Studio. Configure the backend Wi-Fi address in app connection settings for a physical phone.

For pricing, credit rules, Business setup, Google Sheets OAuth, and test limitations, see [Voxie plans and Business MVP](docs/voxie-plans-business.md). API routes are in [docs/api.md](docs/api.md), and Razorpay checkout setup is in [docs/razorpay-test.md](docs/razorpay-test.md).
