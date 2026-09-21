# ngrok Setup Guide

ngrok creates a public HTTPS URL that tunnels to your laptop.
Needed for: MSG91 webhooks + Android app connecting to your backend.

## One Time Setup
1. Go to https://ngrok.com → Sign up (free)
2. Download ngrok for Windows
3. Extract ngrok.exe anywhere (e.g. C:\ngrok\ngrok.exe)
4. Go to ngrok dashboard → copy your auth token
5. Run once in terminal:
   ngrok config add-authtoken YOUR_TOKEN_HERE

## Every Time You Dev
1. Start your Flask server first (python run.py)
2. Open a second terminal and run:
   ngrok http 5000
3. Copy the https://xxxx.ngrok-free.app URL
4. Use this URL in:
   - Android app as BASE_URL
   - MSG91 dashboard as webhook URL: https://xxxx.ngrok-free.app/webhook/msg91

## Important
- ngrok URL changes every time you restart it (free plan)
- So update BASE_URL in the Android app each dev session
- Or pay for a fixed domain ($8/month) — not needed for college project
