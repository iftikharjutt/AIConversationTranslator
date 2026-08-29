const assert = require('assert');
const fs = require('fs');
const path = require('path');
const http = require('http');

// Start server in-process for testing
process.env.PORT = 3099;
process.env.STT_PROVIDER = 'mock';
process.env.TRANSLATION_PROVIDER = 'mock';

require('../src/index');

setTimeout(async () => {
    try {
        console.log('Testing Backend API Endpoints...');

        // 1. Test Health
        const healthRes = await fetch('http://127.0.0.1:3099/health');
        const healthData = await healthRes.json();
        assert.strictEqual(healthData.status, 'ok', 'Health status should be ok');
        console.log('✔ Health Check Passed');

        // 2. Test Translation
        const translateRes = await fetch('http://127.0.0.1:3099/v1/translate', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                text: 'Selamat petang, bagaimana keadaan anda hari ini?',
                sourceLanguage: 'ms',
                targetLanguage: 'ur',
                context: ''
            })
        });
        const translateData = await translateRes.json();
        assert.ok(translateData.translation, 'Translation should not be empty');
        console.log('✔ Translation API Passed:', translateData.translation);

        // 3. Test Transcribe
        const dummyWav = path.join(__dirname, 'test.wav');
        fs.writeFileSync(dummyWav, Buffer.from('RIFF....WAVEfmt ....data....'));
        
        const formData = new FormData();
        const blob = new Blob([fs.readFileSync(dummyWav)], { type: 'audio/wav' });
        formData.append('audio', blob, 'test.wav');
        formData.append('language', 'ms');

        const transcribeRes = await fetch('http://127.0.0.1:3099/v1/transcribe', {
            method: 'POST',
            body: formData
        });
        const transcribeData = await transcribeRes.json();
        assert.ok(transcribeData.text, 'Transcription should not be empty');
        console.log('✔ Transcribe API Passed:', transcribeData.text);

        if (fs.existsSync(dummyWav)) fs.unlinkSync(dummyWav);

        console.log('ALL BACKEND TESTS PASSED SUCCESSFULLY!');
        process.exit(0);
    } catch (err) {
        console.error('Backend Test Failed:', err);
        process.exit(1);
    }
}, 1000);
