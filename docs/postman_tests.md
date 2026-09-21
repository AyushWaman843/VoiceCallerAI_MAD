# Postman Test Cases

Download Postman from https://www.postman.com/downloads/

## 1. Health Check
GET http://localhost:5000/health

Expected:
{"status": "ok"}

---

## 2. Schedule a Call (time in message)
POST http://localhost:5000/schedule-call
Content-Type: application/json

{
  "user_id": "test_user",
  "contact_name": "Rahul",
  "contact_number": "+91XXXXXXXXXX",
  "message": "remind him about the meeting tomorrow at 6pm",
  "user_name": "Priya"
}

Expected:
{
  "status": "scheduled",
  "job_id": "...",
  "call_time": "...",
  "message_preview": "Hi Rahul, this is an automated message on behalf of Priya...",
  "time_extracted": true
}

---

## 3. Schedule a Call (no time in message)
POST http://localhost:5000/schedule-call
Content-Type: application/json

{
  "user_id": "test_user",
  "contact_name": "Rahul",
  "contact_number": "+91XXXXXXXXXX",
  "message": "remind him about the standup",
  "user_name": "Priya"
}

Expected:
{
  "status": "needs_time",
  "message_preview": "...",
  "time_extracted": false
}

---

## 4. Schedule with manually chosen time (after needs_time response)
POST http://localhost:5000/schedule-call
Content-Type: application/json

{
  "user_id": "test_user",
  "contact_name": "Rahul",
  "contact_number": "+91XXXXXXXXXX",
  "message": "remind him about the standup",
  "user_name": "Priya",
  "chosen_time": "2026-08-29T09:00:00+05:30"
}

---

## 5. Get all calls
GET http://localhost:5000/calls?user_id=test_user

---

## 6. Cancel a call
DELETE http://localhost:5000/calls/JOB_ID_HERE

---

## 7. Simulate MSG91 webhook
POST http://localhost:5000/webhook/msg91
Content-Type: application/json

{
  "requestId": "msg91_test_001",
  "status": "connected",
  "duration": "25",
  "to": "+91XXXXXXXXXX"
}
