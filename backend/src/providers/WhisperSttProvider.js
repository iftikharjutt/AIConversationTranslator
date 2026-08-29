const SpeechToTextProvider = require('./SpeechToTextProvider');
const fs = require('fs');

class WhisperSttProvider extends SpeechToTextProvider {
    constructor(apiKey, model = 'whisper-1') {
        super();
        this.apiKey = apiKey;
        this.model = model;
    }

    async transcribe(audioFilePath, languageCode) {
        if (!this.apiKey) {
            throw new Error("OPENAI_API_KEY is not configured on the backend.");
        }
        
        if (languageCode === 'rhg') {
            throw new Error("STT_NOT_SUPPORTED: Rohingya is not supported by Whisper.");
        }

        const formData = new FormData();
        const fileBlob = new Blob([fs.readFileSync(audioFilePath)], { type: 'audio/wav' });
        formData.append('file', fileBlob, 'audio.wav');
        formData.append('model', this.model);
        if (languageCode && languageCode !== 'auto') {
            formData.append('language', languageCode);
        }

        const response = await fetch('https://api.openai.com/v1/audio/transcriptions', {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${this.apiKey}`
            },
            body: formData
        });

        if (!response.ok) {
            const err = await response.text();
            throw new Error(`OpenAI STT Error (${response.status}): ${err}`);
        }

        const data = await response.json();
        return { text: data.text || '' };
    }
}

module.exports = WhisperSttProvider;
