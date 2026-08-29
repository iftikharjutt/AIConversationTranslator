# Privacy & Data Retention Policy

## Zero Client-Side Secrets
No private API keys or provider tokens are bundled in the Android client. All interactions with third-party AI APIs route through the configurable backend proxy.

## Audio Retention Controls
Users have granular control over their audio recordings in **Settings**:
- **Save Audio Locally (ON/OFF):** When OFF, audio files are not stored persistently.
- **Auto-delete Audio (ON/OFF):** When ON, audio files are automatically purged from disk once the segment has been successfully transcribed and translated.
- **History Deletion:** Deleting a conversation immediately erases all associated database records and underlying audio segment files.
