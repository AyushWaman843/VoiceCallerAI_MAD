# Setup Guide

## Prerequisites
- Python 3.11+
- VS Code with Codex extension
- Android Studio
- ngrok account (free) — ngrok.com

## API Keys You Need
| Service | Get it from | Used for |
|---------|------------|---------|
| DeepSeek | platform.deepseek.com/api-keys | LLM rephrasing + time extraction |
| ElevenLabs | elevenlabs.io/app/settings | Text-to-speech |
| MSG91 | msg91.com → API | Voice calls |
| Supabase | supabase.com → Settings → Database | Database connection string |

## Backend Setup (do this first)

### 1. Create virtual environment
```bash
cd backend
python -m venv venv

# Windows
venv\Scripts\activate
```

### 2. Install dependencies
```bash
pip install -r requirements.txt
```

### 3. Set up environment variables
```bash
cp .env.example .env
# Open .env and fill in all your API keys
```

### 4. Run database migrations
```bash
python migrate.py
```

### 5. Start the server
```bash
python run.py
# Server runs at http://localhost:5000
```

### 6. Expose with ngrok
```bash
ngrok http 5000
# Copy the https://xxxx.ngrok.io URL
# Use this as BASE_URL in Android app
# Use this + /webhook/msg91 in MSG91 dashboard
```

## Testing
Open http://localhost:5000/health — should return {"status": "ok"}
