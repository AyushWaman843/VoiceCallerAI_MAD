# Test Contacts (for backend testing without the app)

Use these sample payloads to test different scenarios:

## Immediate-ish (3 mins from now)
"message": "tell him I'll be 10 minutes late, call in 3 minutes"

## Relative time
"message": "remind her about the call after 30 minutes"
"message": "call him in 2 hours"
"message": "remind her in 45 minutes about the report"

## Specific time today
"message": "call at 8pm tonight"
"message": "remind him at 6:30 this evening"

## Tomorrow
"message": "remind her tomorrow at 9am about the client meeting"
"message": "call him tomorrow morning at 10"

## Day of week
"message": "remind him this Friday at 3pm"
"message": "call her next Monday at 11am"

## No time (should return needs_time)
"message": "remind him about the project submission"
"message": "tell her the meeting is confirmed"
