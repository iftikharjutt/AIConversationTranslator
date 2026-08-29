const MockSttProvider = require('./MockSttProvider');
const MockTranslationProvider = require('./MockTranslationProvider');
const WhisperSttProvider = require('./WhisperSttProvider');
const AiTranslationProvider = require('./AiTranslationProvider');

class ProviderFactory {
    static getSttProvider() {
        const type = process.env.STT_PROVIDER || 'mock';
        if (type === 'openai' && process.env.OPENAI_API_KEY) {
            return new WhisperSttProvider(process.env.OPENAI_API_KEY, process.env.OPENAI_STT_MODEL);
        }
        return new MockSttProvider();
    }

    static getTranslationProvider() {
        const type = process.env.TRANSLATION_PROVIDER || 'mock';
        if (type === 'openai' && process.env.OPENAI_API_KEY) {
            return new AiTranslationProvider(process.env.OPENAI_API_KEY, process.env.OPENAI_TRANSLATE_MODEL);
        }
        return new MockTranslationProvider();
    }
}

module.exports = ProviderFactory;
