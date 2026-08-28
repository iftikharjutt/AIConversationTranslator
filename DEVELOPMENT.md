# AI Conversation Translator — Development Guide

## Environment Overview
- **Platform:** Linux / Termux (aarch64)
- **Java Version:** OpenJDK 21
- **Android SDK:** API 34 (`/data/data/com.termux/files/home/android-sdk`)
- **Build Tools:** 34.0.0 with native AAPT2 (`/data/data/com.termux/files/usr/bin/aapt2`)
- **Node.js:** Node.js v20+ / npm

## Architecture Summary
- **Package:** `com.example.aitranslator`
- **Pattern:** MVVM + Clean Architecture + Repository Pattern
- **Core Principle:** Independent continuous recording and asynchronous translation queue.
- **Android Stack:**
  - Kotlin 2.0.20
  - Jetpack Compose + Material 3
  - AndroidX WorkManager (ProcessSegmentWorker)
  - AndroidX Room (AppDatabase, ConversationDao, SegmentDao)
  - AndroidX DataStore Preferences
  - Dagger Hilt for Dependency Injection
  - Retrofit 2 + OkHttp 3 + Kotlinx Serialization
  - AudioRecord (16kHz, 16-bit PCM, Mono)
  - Android TextToSpeech
- **Backend Stack:**
  - Node.js / Express or Ktor
  - Endpoints: `POST /v1/transcribe`, `POST /v1/translate`
  - Modular provider interfaces (`SpeechToTextProvider`, `TranslationProvider`)
  - Zero API keys embedded inside Android client

## Build Commands
- Run tests: `./gradlew test`
- Build Debug APK: `./gradlew assembleDebug`
- Build script with Termux AAPT2 override: `./build_apk.sh`
