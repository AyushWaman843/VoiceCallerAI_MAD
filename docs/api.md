# API Reference

## Base URL
- Local dev: http://localhost:5000
- With ngrok: https://xxxx.ngrok.io

---

## POST /schedule-call
Schedule a new automated voice call.

### Request Body
```json
{
  "user_id": "user_001",
  "contact_name": "Rahul",
  "contact_number": "+91XXXXXXXXXX",
  "message": "remind him about the meeting tomorrow at 6pm",
  "user_name": "Priya"
}
```

### Response — call scheduled
```json
{
  "status": "scheduled",
  "job_id": "uuid-here",
  "call_time": "2026-08-28T18:00:00+05:30",
  "message_preview": "Hi Rahul, this is an automated message on behalf of Priya. Just a reminder about your meeting tomorrow at 6 PM.",
  "time_extracted": true
}
```

### Response — no time found in message
```json
{
  "status": "needs_time",
  "message_preview": "Hi Rahul, this is an automated message on behalf of Priya...",
  "time_extracted": false
}
```
App shows a time picker and resubmits with chosen_time field added.

---

## GET /calls?user_id=user_001
Get all calls (upcoming + past).

---

## DELETE /calls/<job_id>
Cancel a pending call.

---

## POST /webhook/msg91
Called by MSG91 after a call is placed.

---

## GET /health
```json
{"status": "ok"}
```
