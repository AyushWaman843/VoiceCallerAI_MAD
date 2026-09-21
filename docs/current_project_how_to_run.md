# Current Project - How To Run

This file documents the working local setup for the current project as of 2026-08-28.

## What is working now

- Flask backend
- APScheduler-based call scheduling
- DeepSeek message generation
- ElevenLabs MP3 generation
- Public MP3 hosting through the Flask `/media/<file>` route
- Exotel outbound call trigger
- Exotel Flow fetching the hosted audio URL

## Required `.env` values

Set these in `backend/.env`.

```env
DEEPSEEK_API_KEY=

ELEVENLABS_API_KEY=
ELEVENLABS_VOICE_ID=
ELEVENLABS_MODEL_ID=eleven_flash_v2_5

EXOTEL_API_KEY=
EXOTEL_API_TOKEN=
EXOTEL_ACCOUNT_SID=
EXOTEL_CALLER_ID=
EXOTEL_FLOW_ID=
EXOTEL_BASE_URL=api.exotel.com

DATABASE_URL=

FLASK_ENV=development
FLASK_PORT=5000
SECRET_KEY=make-this-a-long-random-string
PUBLIC_BASE_URL=https://your-public-cloudflare-url.trycloudflare.com

TIMEZONE=Asia/Kolkata
```

## Exotel dashboard setup

Use this as the Exotel status callback URL:

```text
https://your-public-cloudflare-url.trycloudflare.com/webhook/exotel
```

Inside the Exotel Flow `Greeting` applet, the audio URL endpoint must be:

```text
https://your-public-cloudflare-url.trycloudflare.com/exotel-audio-url
```

Do not point the Greeting applet to `/webhook/exotel`.

## Start the backend

Open PowerShell in the project root:

```powershell
cd C:\Users\palla\Downloads\voicecaller-project\backend
.\.venv\Scripts\python.exe run.py
```

The backend should start on:

```text
http://127.0.0.1:5000
```

## Start the public tunnel

If Cloudflare Tunnel is already installed, open a second PowerShell window:

```powershell
cloudflared tunnel --url http://127.0.0.1:5000
```

Copy the generated `https://...trycloudflare.com` URL.

Then update `PUBLIC_BASE_URL` in `backend/.env` to that exact URL if it changed.

## Full local run flow

1. Start the Flask backend.
2. Start the Cloudflare tunnel.
3. Make sure `PUBLIC_BASE_URL` matches the live Cloudflare URL.
4. Make sure the Exotel callback URL is `/webhook/exotel`.
5. Make sure the Exotel Flow `Greeting` applet points to `/exotel-audio-url`.
6. Open `http://127.0.0.1:5000` in the browser.
7. Fill the form and schedule the call at least 2 minutes ahead.

## What should happen

1. The form submits to `POST /schedule-call`.
2. DeepSeek rewrites the message.
3. ElevenLabs generates the MP3 immediately.
4. The backend stores the hosted MP3 URL in the database.
5. APScheduler waits until the scheduled IST time.
6. At run time, the backend asks Exotel to start the call.
7. Exotel enters the configured Flow.
8. The Flow fetches `/exotel-audio-url`.
9. The backend returns the hosted MP3 URL.
10. Exotel plays that MP3 to the recipient.
11. Exotel sends status updates to `/webhook/exotel`.

## Useful checks

Open these in the browser:

```text
http://127.0.0.1:5000/health
http://127.0.0.1:5000/calls?user_id=user_001
```

Watch the backend terminal for these lines during a successful test:

```text
POST /schedule-call 201
Exotel call created successfully ...
GET /exotel-audio-url 200
GET /media/<file>.mp3 200
POST /webhook/exotel 200
```

## Retry an old failed call without using new ElevenLabs tokens

Use this route to reschedule a failed or cancelled call with the already generated MP3:

```powershell
Invoke-RestMethod -Uri "http://127.0.0.1:5000/calls/<job_id>/retry" -Method POST
```

The backend will move that call to a little over 2 minutes ahead and reuse the existing audio file.
