# Tech Stack Decisions

## Flask over FastAPI
Simpler to learn, less boilerplate. Fine for a college project.

## DeepSeek over OpenAI
Cheaper. OpenAI-API-compatible so swapping is trivial later.

## MSG91 over Twilio
India-focused, cheaper for Indian numbers, fewer DLT compliance issues.

## Supabase over local Postgres
No installation needed. Free hosted Postgres with a visual table editor.

## APScheduler over Celery
Celery needs Redis which adds setup complexity. APScheduler runs inside Flask. Much simpler.

## ngrok
Flask runs on localhost — not reachable by MSG91 webhooks or Android device. ngrok tunnels it publicly for free.

## Known Limitations
- Caller ID shows MSG91 number, not user's personal number
  → Mitigated: voice message opens with "This is an automated message on behalf of [name]"
- DLT registration required for production in India
- APScheduler jobs reload from Supabase on server restart
- Indian mobile numbers only (+91)
