const TranslationProvider = require('./TranslationProvider');

class AiTranslationProvider extends TranslationProvider {
    constructor(apiKey, model = 'gpt-4o-mini') {
        super();
        this.apiKey = apiKey;
        this.model = model;
    }

    async translate(text, sourceLanguage, targetLanguage, context) {
        if (!this.apiKey) {
            throw new Error("TRANSLATION_API_KEY is not configured on the backend.");
        }

        if (sourceLanguage === 'rhg' || targetLanguage === 'rhg') {
            throw new Error("TRANSLATION_NOT_SUPPORTED: Rohingya translation requires verified language support.");
        }

        const systemPrompt = `You are a professional, highly nuanced AI conversation translator.
Translate the spoken dialogue from ${sourceLanguage} to ${targetLanguage}.
Guidelines:
- Produce natural conversational translation.
- Preserve precise meaning, tone, names, locations, numbers, and key terms.
- Do not translate word-for-word if it creates awkward phrasing.
- If there are minor speech-recognition errors, use the surrounding context to correct them intelligently.
- Do NOT add explanations, notes, or commentary.
- Return ONLY the final translated sentence.`;

        const messages = [
            { role: 'system', content: systemPrompt }
        ];

        if (context && context.trim().length > 0) {
            messages.push({
                role: 'user',
                content: `Recent conversation context:\n"""\n${context}\n"""`
            });
            messages.push({
                role: 'assistant',
                content: `Understood. I will use this recent context to translate subsequent conversation accurately.`
            });
        }

        messages.push({
            role: 'user',
            content: `Translate this spoken segment to ${targetLanguage}:\n${text}`
        });

        const response = await fetch('https://api.openai.com/v1/chat/completions', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${this.apiKey}`
            },
            body: JSON.stringify({
                model: this.model,
                messages: messages,
                temperature: 0.3
            })
        });

        if (!response.ok) {
            const err = await response.text();
            throw new Error(`AI Translation Error (${response.status}): ${err}`);
        }

        const data = await response.json();
        const translation = data.choices && data.choices[0] && data.choices[0].message
            ? data.choices[0].message.content.trim()
            : '';

        return { translation };
    }
}

module.exports = AiTranslationProvider;
