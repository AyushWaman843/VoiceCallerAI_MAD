# Audible cue after answering

Upload `assets/voxie-start-ring.wav` directly into a static Exotel Greeting
applet at the start of the outbound flow configured by `EXOTEL_FLOW_ID`.

Flow order:

1. Call Start -> Greeting with the uploaded ring audio.
2. That Greeting's Next -> the existing dynamic Greeting using
   `https://<current-public-host>/exotel-audio-url`.
3. Dynamic Greeting's Next -> Hangup.
4. Save the flow.

Keep the current dynamic Greeting and its URL. The ring must be uploaded into
Exotel itself, not added to the dynamically downloaded message: prepending it
to the message cannot cover the time spent fetching that message.

The provided file is a 1.5-second double ring, 8000 Hz mono 16-bit PCM WAV.
Regenerate it with `python backend/tools/create_start_ring.py` from the repo root.

This gives an audible cue when Exotel starts the first applet. It cannot remove
carrier/flow connection delay before that applet starts, or guarantee continuous
sound while Exotel subsequently fetches dynamic audio. Verify both on an answered
test call after saving the flow; no live call is placed by the generator.

Reference: https://support.exotel.com/support/solutions/articles/48285-greeting-using-dynamic-text-or-audio-from-url
