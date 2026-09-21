# ElevenLabs Setup Guide

## Account Setup
1. Go to https://elevenlabs.io → Sign up
2. Free tier: 10,000 characters/month — enough for testing
3. Go to Profile → API Keys → copy your key

## Choosing a Voice
1. Go to Voices in the dashboard
2. Pick any voice you like (e.g. "Rachel" or "Adam")
3. Click on the voice → copy the Voice ID from the URL or settings

## In Your .env
ELEVENLABS_API_KEY=your_api_key
ELEVENLABS_VOICE_ID=voice_id_here

## How It's Used in the App
- The rephrased message text is sent to ElevenLabs
- ElevenLabs returns an audio file (MP3)
- That audio file URL is passed to MSG91
- MSG91 plays it when the call connects
