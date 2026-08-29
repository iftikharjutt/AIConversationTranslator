const TranslationProvider = require('./TranslationProvider');

class MockTranslationProvider extends TranslationProvider {
    async translate(text, sourceLanguage, targetLanguage, context) {
        const src = (sourceLanguage || 'en').toLowerCase();
        const tgt = (targetLanguage || 'en').toLowerCase();

        if (tgt === 'rhg' || src === 'rhg') {
            throw new Error("TRANSLATION_NOT_SUPPORTED: Rohingya capability has not been verified for this provider.");
        }

        // Realistic contextual dictionary for demo / test pairs
        const dictionary = {
            'Selamat petang, bagaimana keadaan anda hari ini?': {
                'ur': 'شب بخیر، آج آپ کا کیا حال ہے؟',
                'en': 'Good evening, how are you doing today?'
            },
            'آپ کا شکریہ، میں بالکل ٹھیک ہوں۔': {
                'ms': 'Terima kasih, saya baik-baik sahaja.',
                'en': 'Thank you, I am doing completely fine.'
            },
            'Hello, we are testing the conversational voice translation system.': {
                'ur': 'ہیلو، ہم بات چیت کے صوتی ترجمے کے نظام کی جانچ کر رہے ہیں۔',
                'ms': 'Halo, kami sedang menguji sistem terjemahan suara perbualan.'
            }
        };

        const direct = dictionary[text.trim()] && dictionary[text.trim()][tgt];
        if (direct) {
            return { translation: direct };
        }

        // Context-aware fallback translation
        return {
            translation: `[${tgt.toUpperCase()}] ${text}`
        };
    }
}

module.exports = MockTranslationProvider;
