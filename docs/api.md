# Voxie API

Base URL for local Flask: `http://localhost:5000`. Android on a physical phone uses the computer's Wi-Fi address configured in app Settings. Exotel media and callbacks require the reachable HTTPS `PUBLIC_BASE_URL`.

Except `GET /health`, `GET /payments/plans`, and account signup/login/refresh, endpoints require `Authorization: Bearer <Supabase access token>`. Flask derives the account ID from that token.

## Account and plans

| Endpoint | Purpose |
| --- | --- |
| `POST /auth/signup`, `POST /auth/login`, `POST /auth/refresh` | Supabase email account and sessions |
| `GET /auth/me`, `PATCH /auth/profile`, `POST /auth/logout` | Account profile and session |
| `GET /payments/plans` | Three configured tiers and entitlements |
| `GET /payments/status` | Active tier, edition, test credit balance, purchases |
| `POST /payments/orders` | Create Razorpay test order, e.g. `{"pack":"business"}` |
| `POST /payments/verify` | Verify `order_id`, `payment_id`, `signature`; activate tier once |

`PATCH /auth/profile` accepts `full_name`, `timezone`, and Business only `organization_name`.

## Individual calls

`POST /schedule-call` accepts a natural-language request. The server resolves recipients against provided contacts and checks the verified plan and credit balance before TTS.

```json
{"raw_prompt":"Call Ayush tomorrow at 3 PM and remind him about the meeting","contacts":[{"name":"Ayush","number":"+919876543210"}],"confirmed":false}
```

It returns `needs_info` with `missing_fields`, or `needs_confirmation` with `message_preview` and `agent_result`. Resubmit with `confirmed: true` and that `agent_result` after review. Optional `chosen_time`, `confirmed_number`, `confirmed_contact_name`, and `confirmed_message` supply corrections. `scheduled` returns `job_id`, `call_time`, `audio_url`, and the spoken message. All schedules require at least two minutes' lead time.

`POST /calls/preview-audio` accepts `{"message":"exact spoken message"}` and returns `preview_id` and `audio_url`. Include `audio_preview_id` in the final `/schedule-call` request to reuse that file. IDs expire after 30 minutes and are tied to the account and exact message. Scheduling without a preview still generates the audio once.

`GET /calls` lists jobs; `DELETE /calls/{job_id}` cancels and refunds a pending job; `POST /calls/{job_id}/retry` reuses its audio. `POST /transcribe-audio` runs faster-whisper on Flask.

## Business groups and campaigns

| Endpoint | Purpose |
| --- | --- |
| `GET/POST /groups`, `PATCH/DELETE /groups/{id}` | Account-owned group CRUD |
| `POST /groups/{id}/contacts` | Add manual contact; `consent_confirmed` controls eligibility |
| `PATCH/DELETE /groups/{id}/contacts/{contact_id}` | Edit, opt out, or remove contact |
| `POST /groups/imports/preview` | Stage CSV multipart `file` or JSON sheet `rows` for review |
| `POST /groups/imports/{import_id}/approve` | Save selected, corrected rows and group |
| `POST /campaigns/preview` | Group, message, recipient, time and shared audio preview |
| `PATCH /campaigns/{id}` | Edit draft message, recipients or schedule |
| `POST /campaigns/{id}/schedule` | Requires `{"confirmed":true}`; creates one call job per recipient |
| `GET /campaigns`, `POST /campaigns/{id}/cancel` | List results or cancel pending jobs |

For `/campaigns/preview`, send `group_id` or a natural-language `raw_prompt` naming one owned group. Supply `message` and `scheduled_time` when entering details manually. Only consent-approved recipients are eligible; duplicate numbers are called once per campaign. The preview shows the complete recipient list, estimated credits and optional provider cost. Scheduling requires a separate confirmation.

`GET /media/{filename}` serves hosted MP3 files. `/exotel-audio-url` is the Exotel Greeting flow URL endpoint; `/webhook/exotel` receives telephony statuses. See [plans and Business setup](voxie-plans-business.md) and [Razorpay test setup](razorpay-test.md).
