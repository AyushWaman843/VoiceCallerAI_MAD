# Codex Instructions — VoiceCaller AI

## What This Project Is
An Android app where users type a message to schedule an automated AI voice call to a contact.
The backend processes the message with an LLM, schedules the call, generates audio via TTS,
and places the call via MSG91 at the right time.

## Tech Stack
- **Android:** Kotlin, MVVM architecture, Retrofit for API calls
- **Backend:** Python, Flask, APScheduler
- **Database:** Supabase (hosted PostgreSQL via SQLAlchemy)
- **LLM:** DeepSeek API (message rephrasing + time extraction)
- **Text-to-Speech:** ElevenLabs API
- **Telephony:** MSG91 Voice API
- **Tunneling (dev):** ngrok

## Project Structure
```
voicecaller-project/
├── backend/         # Flask Python backend
├── android/         # Kotlin Android app
├── .codex/          # Codex AI instructions
└── docs/            # Reference docs
```

## Coding Rules — ALWAYS FOLLOW THESE
- Never hardcode API keys — always use .env via python-dotenv
- Every DB model must have created_at and updated_at timestamps
- All Flask routes must return proper JSON with HTTP status codes
- Use async-friendly patterns where possible
- Add a comment on every non-obvious line of code
- Show the full file when editing — never partial edits
- Pydantic or dataclasses for request/response validation
- If something has a simpler alternative for a student project, mention it

## Current Dev Phase
**Phase 1: Flask backend** — build and test fully before touching Android

## Minimum Call Scheduling Rules
- No call can be scheduled within 2 minutes of current time
- LLM must extract time from natural language (e.g. "tomorrow 6pm", "next saturday", "after 2 hours")
- If no time found in message, return a flag so the app can prompt the user to pick a time
- All times stored in IST (UTC+5:30)

## API Endpoints
- POST /schedule-call — schedule a new call
- GET /calls — get all calls (upcoming + past)
- DELETE /calls/<job_id> — cancel a scheduled call
- POST /webhook/msg91 — receive call status from MSG91

## CallJob Table Fields
- id (UUID, primary key)
- user_id (string)
- contact_name (string)
- contact_number (string)
- original_message (text)
- rephrased_message (text)
- scheduled_time (datetime, IST)
- status (enum: pending / calling / connected / failed / cancelled)
- msg91_request_id (string, nullable)
- created_at / updated_at (datetime)
