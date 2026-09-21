# Build Progress

## Phase 1 — Backend Foundation
- [ ] Flask app setup (run.py, app/__init__.py, config.py)
- [ ] Supabase connection (database.py)
- [ ] CallJob DB model
- [ ] /health endpoint
- [ ] /schedule-call endpoint (basic, no LLM yet)
- [ ] Test with Postman

## Phase 2 — LLM Integration
- [ ] DeepSeek service (llm_service.py)
- [ ] Time extraction from natural language
- [ ] Message rephrasing
- [ ] Handle "needs_time" case
- [ ] Test with Postman

## Phase 3 — Scheduling
- [ ] APScheduler setup inside Flask
- [ ] Schedule job at extracted time
- [ ] Reload jobs from DB on server restart
- [ ] Cancel job endpoint
- [ ] GET /calls endpoint

## Phase 4 — Calling
- [ ] ElevenLabs TTS service
- [ ] MSG91 service
- [ ] Full call trigger chain
- [ ] MSG91 webhook handler
- [ ] Test full call end to end

## Phase 5 — Android App
- [ ] Project setup in Android Studio
- [ ] Retrofit API client
- [ ] Home screen (text input + contact picker)
- [ ] Confirmation screen (message preview + time)
- [ ] Calls history screen
- [ ] Connect to backend via ngrok

## Phase 6 — Polish
- [ ] Error handling both sides
- [ ] Loading states in app
- [ ] Edge cases
- [ ] Final end to end test
