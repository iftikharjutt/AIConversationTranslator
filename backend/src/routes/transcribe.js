const express = require('express');
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const ProviderFactory = require('../providers/ProviderFactory');

const router = express.Router();
const uploadDir = path.join(__dirname, '../../uploads');
if (!fs.existsSync(uploadDir)) {
    fs.mkdirSync(uploadDir, { recursive: true });
}

const upload = multer({
    dest: uploadDir,
    limits: { fileSize: 25 * 1024 * 1024 } // 25MB max
});

router.post('/', upload.single('audio'), async (req, res) => {
    let uploadedFile = null;
    try {
        if (!req.file) {
            return res.status(400).json({ error: 'Audio file is required (field name: audio)' });
        }
        uploadedFile = req.file.path;
        const language = req.body.language || 'auto';

        const sttProvider = ProviderFactory.getSttProvider();
        const result = await sttProvider.transcribe(uploadedFile, language);

        res.json({
            text: result.text || '',
            detectedLanguage: result.detectedLanguage || language
        });
    } catch (err) {
        console.error('Error in /v1/transcribe:', err.message);
        const status = err.message.includes('NOT_SUPPORTED') ? 422 : 500;
        res.status(status).json({ error: err.message });
    } finally {
        if (uploadedFile && fs.existsSync(uploadedFile)) {
            try { fs.unlinkSync(uploadedFile); } catch (_) {}
        }
    }
});

module.exports = router;
