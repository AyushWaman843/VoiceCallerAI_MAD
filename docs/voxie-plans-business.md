# Voxie plans and Business MVP

## Run

1. Set the existing Supabase, DeepSeek, ElevenLabs, Exotel, `PUBLIC_BASE_URL`, and Razorpay **test** credentials in `backend/.env`. Keep Razorpay's secret and every provider key on the backend.
2. Start the backend with `cd backend` then `.\.venv\Scripts\python.exe run.py`. The app initializes new tables and adds the three `call_jobs` columns. For a managed deployment, the equivalent SQL is in `backend/migrations/003_plans_groups_campaigns.sql`.
3. Build Android with `cd android` then `.\gradlew.bat :app:assembleDebug`. The backend address remains configurable in app connection settings. Public media URLs and Exotel webhooks still require the same reachable public backend as before.
4. For Google Sheets imports, configure Google Cloud as described below. The Android app requests only `spreadsheets.readonly`; it reads the selected spreadsheet directly and sends the rows to the authenticated Voxie backend for review. Voxie login stays with Supabase, with no Google login option.

## Google Sheets authorization for a debug build

1. In the Google Cloud project you use for Voxie, enable **Google Sheets API** under **APIs & Services → Library**.
2. Under **Google Auth Platform → Clients**, create an **Android** OAuth client. Package name: `com.ayush.madv2`. Enter the SHA-1 of the certificate signing the installed APK. On this development computer, `cd android; .\gradlew.bat :app:signingReport` reports the debug SHA-1 as `17:BE:7D:E9:90:4A:6F:D7:A2:EA:F4:87:2D:42:2F:1C:F2:E5:2E:1F`. Recheck it on a different computer or for a release build.
3. Under **Google Auth Platform → Data Access**, add `https://www.googleapis.com/auth/spreadsheets.readonly`. Configure Branding and set Audience to **External** for a personal Google account. While the OAuth app is in **Testing**, add the Google account you select in the app under **Audience → Test users**. Owning the spreadsheet does not automatically make that account an OAuth test user.
4. Reinstall the latest debug APK and retry. If authorization still fails, report the numeric code now shown in the app. The CSV import remains available if you export the sheet as CSV.

## Onboarding and plans

The flow is welcome, Supabase signup/login, three-tier pricing, Razorpay test checkout, then the dashboard for the verified tier. The backend owns prices, credits, and entitlements. Optional `PLAN_CATALOG_JSON` overrides the three plans. It must contain exactly `starter`, `plus`, and `business`. Defaults: Personal Starter ₹49/10 credits, Personal Plus ₹199/50 credits, Business ₹699/200 credits. Purchases of another tier switch the account's active tier and add its test credits. Existing verified test purchases initialize an account plan once on first status lookup. There is no production balance.

Razorpay order creation, captured-payment lookup, and signature verification happen on the backend. `test_credit_purchases` owns the payment record. `account_plans` owns spendable test credits and the active tier; `credit_events` records purchases, reservations, and refunds. Repeated verification of an order returns the existing receipt without another credit allocation.

One credit is reserved for each scheduled recipient. Cancelling a pending job refunds it. Provider failure, no answer, busy, and opt-out before execution refund it. No answer and busy are shown as **Missed**; provider errors as **Failed**. A connected call consumes it. Completed Exotel callbacks are used as the available answered signal; the provider's callback does not prove the entire audio was heard. Costs shown for Business campaigns are estimates only and require `EXOTEL_ESTIMATED_COST_PAISE` to be set. They are not charged by this test-mode wallet.

Personal users may generate an audio preview on the confirmation card. The backend stores it for 30 minutes under their account and reuses that file when the exact message is scheduled, so it does not request TTS twice. New preview generation is capped at ten per account per hour. A public HTTPS `PUBLIC_BASE_URL` is required before generating audio; missing ElevenLabs credentials now fail clearly instead of creating a placeholder file.

## Business workflow

- `GET/POST /groups`, `PATCH/DELETE /groups/{id}`: saved group CRUD.
- `POST/PATCH/DELETE /groups/{id}/contacts[/{contact_id}]`: manual member CRUD. A new number needs a fresh consent confirmation. Opted-out contacts cannot enter a campaign.
- `POST /groups/imports/preview`: CSV multipart upload or JSON `rows` from a read-only Google Sheet. Deterministic headers and two-column lists are supported. Ambiguous columns return `needs_columns`; resubmit with column indices. The import is staged and does not save members.
- `POST /groups/imports/{id}/approve`: explicitly selected, corrected rows are validated again and then saved in one transaction. Consent must be confirmed separately before those contacts become campaign eligible.
- `POST /campaigns/preview`: a saved group ID or unambiguous group name in the prompt selects the account's group. DeepSeek extracts the message and time if needed. ElevenLabs creates one shared hosted audio preview for the group. This is a one-way message; no conference or interactive call is created.
- `PATCH /campaigns/{id}`: change recipients/time/message in a draft. Audio is regenerated only if the message changes.
- `POST /campaigns/{id}/schedule` with `{"confirmed":true}`: reserve one credit and create one ordinary call job per consent-approved recipient.
- `GET /campaigns`, `POST /campaigns/{id}/cancel`: status summary and cancellation of pending jobs.

All group, import, campaign, and call records are filtered by the authenticated Supabase user ID. Business endpoints check the verified active plan on the server. Group names in prompts are matched against that user's saved groups. A Business user can still schedule an individual saved contact through the existing Personal call path. Android profile Settings edit the Supabase display name, Business organization name, and scheduling timezone.

## Testing without provider charges

Run backend unit tests with `cd backend` and `.\.venv\Scripts\python.exe -m unittest discover -s tests -v`; Android build checks with `cd android` and `.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest`. The backend tests mock DeepSeek, TTS, Razorpay, and Exotel. A real end-to-end checkout needs configured Razorpay test keys. A real outbound call still needs a verified Exotel trial recipient, a reachable audio URL, a configured Exotel flow, and working ElevenLabs credits. A real Google Sheet import needs the Android OAuth setup above.
