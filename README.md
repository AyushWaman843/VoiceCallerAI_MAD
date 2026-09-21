# VoiceCaller AI

An Android app that lets you schedule automated AI-generated voice calls to your contacts using natural language.

## How It Works
1. User types: "Remind Rahul about the meeting tomorrow at 6pm"
2. DeepSeek LLM extracts: recipient, message, scheduled time
3. ElevenLabs generates voice audio from the rephrased message
4. MSG91 places the automated call at the scheduled time
5. Call status is sent back via webhook and shown in the app

## Project Structure
```
voicecaller-project/
├── backend/              # Flask backend (Python)
│   ├── app/
│   │   ├── __init__.py
│   │   ├── config.py
│   │   ├── database.py
│   │   ├── models/
│   │   ├── routes/
│   │   ├── services/
│   │   ├── tasks/
│   │   └── utils/
│   ├── .env              # Your API keys (never commit)
│   ├── .env.example      # Template
│   ├── requirements.txt
│   └── run.py
├── android/              # Kotlin Android app
├── .codex/               # Codex AI instructions
└── docs/                 # API references and notes
```

## Docs
- [Setup Guide](docs/setup.md)
- [API Reference](docs/api.md)
- [Tech Stack Decisions](docs/decisions.md)

## Dev Setup
See [docs/setup.md](docs/setup.md)
