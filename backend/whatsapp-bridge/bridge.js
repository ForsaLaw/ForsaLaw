const express = require('express');
const crypto = require('crypto');
const { Client, LocalAuth } = require('whatsapp-web.js');
const qrcode = require('qrcode');

const app = express();
const port = 3099;

// Secret partage avec le backend Java. Obligatoire : le bridge refuse de demarrer sans (fail-fast).
const BRIDGE_TOKEN = process.env.WHATSAPP_BRIDGE_TOKEN;
if (!BRIDGE_TOKEN || BRIDGE_TOKEN.length < 16) {
    console.error('FATAL: WHATSAPP_BRIDGE_TOKEN manquant ou trop court (>= 16 caracteres requis). Bridge non demarre.');
    process.exit(1);
}

app.use(express.json());

// Authentification : toute requete doit presenter "Authorization: Bearer <WHATSAPP_BRIDGE_TOKEN>".
app.use((req, res, next) => {
    const header = req.headers['authorization'] || '';
    const expected = `Bearer ${BRIDGE_TOKEN}`;
    const provided = Buffer.from(header);
    const reference = Buffer.from(expected);
    // Comparaison a temps constant (evite les attaques temporelles) ; longueurs egales requises.
    if (provided.length !== reference.length || !crypto.timingSafeEqual(provided, reference)) {
        return res.status(401).json({ error: 'Non autorise.' });
    }
    next();
});

let qrCodeData = null;
let isConnected = false;

// Configurer le client WhatsApp avec stockage local de la session
const client = new Client({
    authStrategy: new LocalAuth({ dataPath: './wwebjs_auth' }),
    puppeteer: { 
        args: ['--no-sandbox', '--disable-setuid-sandbox'],
        headless: true
    }
});

client.on('qr', (qr) => {
    console.log('QR Code généré. Scan it to authenticate.');
    qrcode.toDataURL(qr, (err, url) => {
        if (!err) qrCodeData = url;
    });
});

client.on('ready', () => {
    console.log('✅ Client WhatsApp Web prêt !');
    isConnected = true;
    qrCodeData = null; // Nettoyer le QR Code
});

client.on('authenticated', () => {
    console.log('Authentifié avec succès.');
});

client.on('auth_failure', msg => {
    console.error('Échec de l\'authentification', msg);
    isConnected = false;
});

client.on('disconnected', (reason) => {
    console.log('Client déconnecté', reason);
    isConnected = false;
    client.initialize(); // Tenter de relancer
});

// Initialiser le client (se lance silencieusement en fond)
client.initialize();

// --- Endpoints HTTP pour le backend Java ---

app.get('/qr', (req, res) => {
    if (isConnected) {
        return res.status(200).json({ status: 'already_connected' });
    }
    if (!qrCodeData) {
        return res.status(503).json({ error: 'QR Code non disponible. Essayez dans quelques secondes.' });
    }
    res.send(qrCodeData); // Renvoie en base64 pour affichage côté frontend
});

app.get('/status', (req, res) => {
    res.json({ connected: isConnected });
});

app.post('/send', async (req, res) => {
    const { to, message } = req.body;
    
    if (!isConnected) {
        return res.status(503).json({ error: 'WhatsApp non connecté.' });
    }
    
    if (!to || !message) {
        return res.status(400).json({ error: 'champs to et message requis.' });
    }

    try {
        // Formater le numéro : supprimer les +, espaces, et ajouter @c.us
        let chatId = to.replace(/[^0-9]/g, '');
        // Si c'est un numéro tunisien commençant par 0, on l'enlève et on met 216
        if(chatId.length === 8) {
            chatId = '216' + chatId;
        }
        chatId = chatId + '@c.us';

        // Envoyer le message
        await client.sendMessage(chatId, message);
        console.log(`Message envoyé à ${chatId}`);
        res.json({ success: true, message: 'Message envoyé.' });
    } catch (err) {
        console.error('Erreur d\'envoi', err);
        res.status(500).json({ error: 'Erreur d\'envoi: ' + err.message });
    }
});

app.listen(port, () => {
    console.log(`WhatsApp Bridge écoute sur http://localhost:${port}`);
});
