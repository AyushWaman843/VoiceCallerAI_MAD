# MSG91 Setup Guide

## Account Setup
1. Go to https://msg91.com → Sign up
2. Complete KYC (Indian ID required for full access)
3. Go to Voice section → buy a virtual number (cheapest available)
4. Go to API → copy your Auth Key

## DLT Registration
- Required for production in India (TRAI regulation)
- For college project / testing your own number: not strictly needed
- You can call your own number freely during development

## In Your .env
MSG91_AUTH_KEY=your_auth_key
MSG91_VIRTUAL_NUMBER=+91XXXXXXXXXX  (the number you bought)

## Webhook Setup (do after ngrok is running)
1. In MSG91 dashboard → Voice → Webhook URL
2. Paste: https://xxxx.ngrok-free.app/webhook/msg91
3. MSG91 will POST call status here after every call

## Test Call Flow
- During dev, use your own number as contact_number
- Check if you receive the automated call
- Check webhook logs in your Flask terminal
