# Backend API Specification

## 1. Health Check
- **Endpoint:** `GET /health`
- **Response:**
  ```json
  { "status": "ok", "timestamp": "2026-08-28T22:00:00.000Z" }
  ```

## 2. Transcribe Audio
- **Endpoint:** `POST /v1/transcribe`
- **Content-Type:** `multipart/form-data`
- **Parameters:**
  - `audio` (file, required): WAV/PCM audio stream
  - `language` (string, optional): ISO language code (e.g. `ms`, `ur`, `en`)
- **Response:**
  ```json
  {
    "text": "Selamat petang, bagaimana keadaan anda hari ini?",
    "detectedLanguage": "ms"
  }
  ```

## 3. Contextual AI Translation
- **Endpoint:** `POST /v1/translate`
- **Content-Type:** `application/json`
- **Body:**
  ```json
  {
    "text": "Selamat petang, bagaimana keadaan anda hari ini?",
    "sourceLanguage": "ms",
    "targetLanguage": "ur",
    "context": "Recent conversation dialogue history..."
  }
  ```
- **Response:**
  ```json
  {
    "translation": "شب بخیر، آج آپ کا کیا حال ہے؟"
  }
  ```
