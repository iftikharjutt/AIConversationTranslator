const SpeechToTextProvider = require('./SpeechToTextProvider');

class MockSttProvider extends SpeechToTextProvider {
    async transcribe(audioFilePath, languageCode) {
        const lang = languageCode ? languageCode.toLowerCase() : 'en';
        
        // Return realistic mock transcripts per language
        const mockTranscripts = {
            'ms': 'Selamat petang, bagaimana keadaan anda hari ini?',
            'ur': 'آپ کا شکریہ، میں بالکل ٹھیک ہوں۔',
            'en': 'Hello, we are testing the conversational voice translation system.',
            'id': 'Halo, selamat datang di aplikasi penerjemah percakapan.',
            'ar': 'مرحبا بك في تطبيق الترجمة الفورية.',
            'hi': 'नमस्ते, बातचीत अनुवादक में आपका स्वागत है।',
            'bn': 'হ্যালো, কথোপকথন অনুবাদ অ্যাপ্লিকেশনে স্বাগতম।',
            'zh': '你好，欢迎使用对话翻译系统。',
            'ta': 'வணக்கம், உரையாடல் மொழிபெயர்ப்பாளர் உங்களை வரவேற்கிறது.'
        };

        if (lang === 'rhg') {
            throw new Error("STT_NOT_SUPPORTED: Rohingya language model is not supported by this STT provider.");
        }

        const text = mockTranscripts[lang] || `Recognized speech segment for language [${lang}].`;
        return { text, detectedLanguage: lang };
    }
}

module.exports = MockSttProvider;
