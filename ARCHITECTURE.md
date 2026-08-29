# Architecture Documentation

## Overview
```
UI (Compose + Material 3)
   │
   ▼
ViewModel (StateFlow & Coroutines)
   │
   ▼
TranslationRepository (Domain & Data Layer)
 ┌─┴───────────────────────────────┐
 │                                 │
 ▼                                 ▼
Room Database               WorkManager / Audio Pipeline
(Conversations & Segments)  (ProcessSegmentWorker & AudioRecorder)
                                   │
                                   ▼
                            Backend API Service
                           (Node.js / Express Proxy)
                                ┌──┴──┐
                                ▼     ▼
                             STT    Contextual AI
```

## Recording & Processing Concurrency
- **AudioRecorder** executes on a dedicated IO coroutine.
- Once target segment length is reached:
  1. Temporary PCM buffer is finalized and converted to RIFF/WAV.
  2. Next segment recording starts synchronously on the microphone stream without dropped samples.
  3. The completed segment is inserted into Room with status `RECORDED`.
  4. WorkManager triggers `ProcessSegmentWorker` to upload, transcribe, translate with rolling context, and update status to `COMPLETED`.
