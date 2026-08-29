# AI Conversation Translator

An intelligent, continuous conversational voice translation application for Android built with Kotlin, Jetpack Compose, Material 3, and an independent backend proxy architecture.

---

## Key Features

1. **Independent Continuous Recording & Processing Pipeline**:
   - Audio is recorded continuously using `AudioRecord` (16 kHz, Mono, 16-bit PCM).
   - Audio is divided into configurable segments (e.g. 3 minutes default, 10 seconds for debug/rapid testing).
   - Once a segment is completed, it is immediately converted to WAV and queued for asynchronous processing.
   - Recording immediately begins for the next segment without interruption or waiting on network/AI operations.
2. **Context-Aware AI Translation**:
   - Uses recent conversation context windows to preserve conversational flow, tone, speaker context, named entities, and resolve speech ambiguities.
3. **Robust Offline Support**:
   - Audio segments remain persisted locally during connection drops.
   - WorkManager automatically retries and resumes pending translations upon network restoration.
4. **Android Foreground Service**:
   - Runs with microphone foreground service permissions and wake lock so conversation recording continues even when the screen is locked or the app is in the background.
5. **Text-To-Speech (TTS)**:
   - On-demand audio playback for translated text and configurable auto-play.
6. **Privacy & Security**:
   - API keys and AI credentials never exist inside the APK.
   - Configurable automatic deletion of audio files post-processing.
   - Complete local history management.

---

## Languages Supported
- English (en)
- Urdu (ur)
- Malay (ms)
- Indonesian (id)
- Arabic (ar)
- Hindi (hi)
- Bengali (bn)
- Chinese (zh)
- Tamil (ta)
- Spanish, French, German, Japanese, Korean, Turkish, Vietnamese, Thai, Persian, Pashto
- *Rohingya (rhg)*: Explicitly tagged as experimental requiring capability verification.

---

## Quick Start

### 1. Start the Backend Proxy
```bash
cd backend
npm install
npm start
```
By default, the backend runs in mock mode on `http://0.0.0.0:3000`. To use OpenAI Whisper / GPT-4o, set `STT_PROVIDER=openai`, `TRANSLATION_PROVIDER=openai`, and `OPENAI_API_KEY=your_key` in `backend/.env`.

### 2. Build the Android Application
```bash
./build_apk.sh
```
The compiled APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.

### 3. Run Unit Tests
```bash
./build_apk.sh test
```
