const http = require('http');
const express = require('express');
const WebSocket = require('ws');
const config = require('./config');
const { query } = require('./db');
const auth = require('./auth');
const sessions = require('./sessions');
const messages = require('./messages');

const app = express();
app.use(express.json());

app.get('/health', (req, res) => {
  res.json({ ok: true, service: 'koukou-server', time: Date.now() });
});

app.post('/api/register', async (req, res, next) => {
  try {
    const account = String(req.body.account || req.body.userId || '').trim();
    const password = String(req.body.password || '');
    const nickname = String(req.body.nickname || account);
    if (!account || !password) {
      res.status(400).json({ error: 'account_and_password_required' });
      return;
    }
    const now = Date.now();
    const passwordHash = await auth.hashPassword(password);
    await query(
      `INSERT INTO users (user_id, account, password_hash, nickname, avatar_url, signature, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
      [account, account, passwordHash, nickname, 'ic_avatar_1', '', now, now]
    );
    res.json({
      userId: account,
      account,
      nickname,
      token: auth.signToken({ user_id: account, account })
    });
  } catch (err) {
    next(err);
  }
});

app.post('/api/login', async (req, res, next) => {
  try {
    const account = String(req.body.account || '').trim();
    const password = String(req.body.password || '');
    const rows = await query('SELECT * FROM users WHERE account = ? OR user_id = ? LIMIT 1', [account, account]);
    if (rows.length === 0 || !(await auth.verifyPassword(password, rows[0].password_hash))) {
      res.status(401).json({ error: 'invalid_credentials' });
      return;
    }
    const user = rows[0];
    res.json({
      userId: user.user_id,
      account: user.account,
      nickname: user.nickname,
      avatarUrl: user.avatar_url,
      signature: user.signature,
      token: auth.signToken(user)
    });
  } catch (err) {
    next(err);
  }
});

app.use((err, req, res, next) => {
  console.error(err);
  res.status(500).json({ error: 'server_error', message: err.message });
});

const server = http.createServer(app);
const wss = new WebSocket.Server({ server, path: '/ws' });

wss.on('connection', (ws, req) => {
  const url = new URL(req.url, 'http://127.0.0.1');
  const userId = auth.verifyToken(url.searchParams.get('token'));
  if (!userId) {
    ws.close(1008, 'Unauthorized');
    return;
  }

  ws.userId = userId;
  sessions.add(userId, ws);

  ws.on('message', async raw => {
    try {
      const message = JSON.parse(raw.toString());
      if (message.type === 'heartbeat_ping') {
        ws.send(JSON.stringify({ type: 'heartbeat_pong', timestamp: Date.now() }));
        return;
      }
      if (message.type === 'sync_request') {
        await messages.handleSyncRequest(ws, message);
        return;
      }
      if (message.type === 'chat_message' || message.type === 'message') {
        await messages.handleChatMessage(ws, message);
        return;
      }
      ws.send(JSON.stringify({ type: 'error', errorCode: 'unknown_type', errorMessage: 'Unknown message type' }));
    } catch (err) {
      console.error(err);
      ws.send(JSON.stringify({ type: 'error', errorCode: 'server_error', errorMessage: err.message }));
    }
  });

  ws.on('close', () => sessions.remove(userId, ws));
  ws.on('error', () => sessions.remove(userId, ws));
});

server.listen(config.port, '127.0.0.1', () => {
  console.log(`koukou-server listening on http://127.0.0.1:${config.port}`);
});
