require('dotenv').config();
const express = require('express');
const cors = require('cors');
const transcribeRoute = require('./routes/transcribe');
const translateRoute = require('./routes/translate');

const app = express();
const PORT = process.env.PORT || 3000;
const HOST = process.env.HOST || '0.0.0.0';

app.use(cors());
app.use(express.json({ limit: '5mb' }));
app.use(express.urlencoded({ extended: true }));

// Health check
app.get('/health', (req, res) => {
    res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

// API Routes
app.use('/v1/transcribe', transcribeRoute);
app.use('/v1/translate', translateRoute);

app.listen(PORT, HOST, () => {
    console.log(`AI Conversation Translator Backend running on http://${HOST}:${PORT}`);
});
