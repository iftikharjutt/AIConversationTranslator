class TranslationProvider {
    /**
     * @param {string} text - Text to translate
     * @param {string} sourceLanguage - Source language code
     * @param {string} targetLanguage - Target language code
     * @param {string} [context] - Rolling conversation context
     * @returns {Promise<{ translation: string }>}
     */
    async translate(text, sourceLanguage, targetLanguage, context) {
        throw new Error("translate() not implemented");
    }
}

module.exports = TranslationProvider;
