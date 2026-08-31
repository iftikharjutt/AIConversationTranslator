# AI Conversation Translator

An intelligent, continuous conversational voice translation application for Android built with Kotlin, Jetpack Compose, Material 3, and direct Google Gemini Cloud AI integration.

---

## Key Features

1. **Direct Google Gemini AI Integration**:
   - Directly connects to Google Gemini API (`v1beta/models/{model}:generateContent`) from your Android device.
   - High-fidelity multimodal audio processing + context-aware translation in a single unified step.
   - Configure your personal Gemini API key locally in Settings.
   - Live **"Check Account for Eligible Models"** tool to discover all Gemini models available on your Google account.
   - Instant **"Test Connection"** verification.

2. **Supported Gemini Models**:
   - `gemini-2.5-flash` (*Recommended & Default*) - High speed, state-of-the-art multimodal audio translation
   - `gemini-2.5-flash-lite` - Ultra low latency & high throughput
   - `gemini-2.5-pro` - Deep reasoning & high nuance accuracy
   - `gemini-2.0-flash` - Next-gen fast multimodal model
   - `gemini-2.0-flash-lite` - Lightweight next-gen model
   - `gemini-1.5-flash` / `gemini-1.5-pro` - Classic production models
   - Custom Model ID entry

3. **Sample-Accurate Gap-Free Continuous Recording**:
   - Audio is recorded continuously on an uninterrupted worker thread (`AudioRecord` 16 kHz, Mono, 16-bit PCM).
   - Audio is divided into sample-accurate segments (e.g. 3 minutes default, 10 seconds for debug/rapid testing).
   - Once a segment boundary is reached, the completed segment is flushed to a WAV file asynchronously while the microphone capture loop immediately continues without dropping samples.
   - Network calls and AI processing never block microphone capture.

4. **Context-Aware Conversational Translation**:
   - Uses a rolling multi-turn context window of recent segments to preserve conversational continuity, tone, speaker context, named entities, and resolve speech ambiguities.

5. **Robust Offline Support**:
   - Audio segments remain safely stored locally during network drops.
   - WorkManager automatically retries and resumes pending translations upon connectivity restoration.

6. **Android Foreground Service**:
   - Runs with microphone foreground service permissions and wake lock so conversation recording continues when the screen is locked or the app is in the background.

7. **Text-To-Speech (TTS)**:
   - On-demand audio playback for translated text and configurable auto-play.

8. **Privacy & Security**:
   - Gemini API keys are encrypted and stored locally in private DataStore on your device.
   - No hardcoded API keys in the repository.

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
- *Rohingya (rhg)*: Tagged as experimental requiring capability verification.

---

## Quick Start

### 1. Build the Android Application
```bash
./build_apk.sh
```
The compiled APK will be output to:
- `dist/AIConversationTranslator.apk`
- `/sdcard/Download/AIConversationTranslator.apk`

### 2. Configure Gemini in the App
1. Launch **AI Conversation Translator**.
2. Tap the **Key** icon in the top bar or go to **Settings**.
3. Paste your Google Gemini API Key and tap **Save Key**.
4. Tap **Test Connection** or **Check Account for Eligible Models**.
5. Tap **Start Recording** on the Home screen to begin continuous translation!
