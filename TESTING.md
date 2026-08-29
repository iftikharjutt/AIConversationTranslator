# Testing & Verification Guide

## Automated Unit Tests
Run the automated test suite with:
```bash
./build_apk.sh test
```

Tested components:
- `LanguageTest`: Language model definitions, ISO codes, and Rohingya capability safety checks.
- `ContextGenerationTest`: Rolling conversation context generation formatting.
- `ApiModelsTest`: Kotlinx serialization and deserialization for API requests and responses.
- `AudioSegmentationTest`: PCM to 16kHz WAV conversion and binary RIFF header validation.

## Backend Verification
Run the backend test suite with:
```bash
cd backend && npm test
```
