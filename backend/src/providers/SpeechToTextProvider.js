class SpeechToTextProvider {
    /**
     * @param {string} audioFilePath - Path to audio file (wav/pcm)
     * @param {string} [languageCode] - Optional ISO language code
     * @returns {Promise<{ text: string, detectedLanguage?: string }>}
     */
    async transcribe(audioFilePath, languageCode) {
        throw new Error("transcribe() not implemented");
    }
}

module.exports = SpeechToTextProvider;
