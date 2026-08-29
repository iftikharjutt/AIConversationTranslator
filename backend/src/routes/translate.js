const express = require('express');
const ProviderFactory = require('../providers/ProviderFactory');

const router = express.Router();

router.post('/', async (req, res) => {
    try {
        const { text, sourceLanguage, targetLanguage, context } = req.body;

        if (!text || typeof text !== 'string') {
            return res.status(400).json({ error: 'Field "text" is required' });
        }
        if (!sourceLanguage || !targetLanguage) {
            return res.status(400).json({ error: 'Fields "sourceLanguage" and "targetLanguage" are required' });
        }

        const translationProvider = ProviderFactory.getTranslationProvider();
        const result = await translationProvider.translate(text, sourceLanguage, targetLanguage, context);

        res.json({
            translation: result.translation || ''
        });
    } catch (err) {
        console.error('Error in /v1/translate:', err.message);
        const status = err.message.includes('NOT_SUPPORTED') ? 422 : 500;
        res.status(status).json({ error: err.message });
    }
});

module.exports = router;
