const http = require('http');
const express = require('express');
const WebSocket = require('ws');
const config = require('./config');
const { query, ensureSchema } = require('./db');
const auth = require('./auth');
const sessions = require('./sessions');
const messages = require('./messages');

const app = express();
app.use(express.json());

app.get('/health', (req, res) => {
  res.json({ ok: true, service: 'koukou-server', time: Date.now() });
});

app.get('/api/users/available-id', async (req, res, next) => {
  try {
    const koukouId = await generateAvailableUserId();
    res.json({ koukouId });
  } catch (err) {
    next(err);
  }
});

app.post('/api/register', async (req, res, next) => {
  try {
    const account = String(req.body.account || req.body.userId || '').trim();
    const password = String(req.body.password || '');
    const nickname = String(req.body.nickname || account || 'koukou_user').trim();
    if (!account || !password) {
      res.status(400).json({ error: 'account_and_password_required' });
      return;
    }
    if (!/^\d{10}$/.test(account)) {
      res.status(400).json({ error: 'invalid_account_format', message: 'account must be a 10-digit koukou id' });
      return;
    }
    const exists = await findUserByIdentifier(account);
    if (exists) {
      res.status(409).json({ error: 'user_exists', message: 'user already exists' });
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
      avatarUrl: 'ic_avatar_1',
      signature: '',
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
      nickname: user.nickname || user.account,
      avatarUrl: user.avatar_url || 'ic_avatar_1',
      signature: user.signature || '',
      token: auth.signToken(user)
    });
  } catch (err) {
    next(err);
  }
});

app.get('/api/users/:id', requireAuth, async (req, res, next) => {
  try {
    const identifier = String(req.params.id || '').trim();
    const user = await findUserByIdentifier(identifier);
    if (!user) {
      res.status(404).json({ error: 'user_not_found' });
      return;
    }
    res.json({ user: toUserDto(user) });
  } catch (err) {
    next(err);
  }
});

app.get('/api/friends', requireAuth, async (req, res, next) => {
  try {
    const rows = await query(
      `SELECT u.* FROM friends f
       INNER JOIN users u ON u.user_id = f.friend_id
       WHERE f.owner_id = ?
       ORDER BY u.nickname ASC, u.user_id ASC`,
      [req.userId]
    );
    res.json({ items: rows.map(toUserDto) });
  } catch (err) {
    next(err);
  }
});

app.get('/api/friends/requests', requireAuth, async (req, res, next) => {
  try {
    const rows = await query(
      `SELECT * FROM friend_requests
       WHERE to_user_id = ?
       ORDER BY created_at DESC
       LIMIT 200`,
      [req.userId]
    );
    const items = rows.map(toFriendRequestDto);
    res.json({
      items,
      pendingCount: items.filter(item => item.status === 'pending').length
    });
  } catch (err) {
    next(err);
  }
});

app.post('/api/friends/requests', requireAuth, async (req, res, next) => {
  try {
    const fromUser = await findUserByIdentifier(req.userId);
    const targetIdentifier = String(req.body.targetId || '').trim();
    const targetUser = await findUserByIdentifier(targetIdentifier);
    if (!targetUser) {
      res.status(404).json({ error: 'user_not_found' });
      return;
    }
    if (targetUser.user_id === req.userId) {
      res.status(400).json({ error: 'cannot_add_self' });
      return;
    }
    if (await areFriends(req.userId, targetUser.user_id)) {
      res.status(409).json({ error: 'already_friends' });
      return;
    }

    const pending = await query(
      `SELECT * FROM friend_requests
       WHERE from_user_id = ? AND to_user_id = ? AND status = 'pending'
       LIMIT 1`,
      [req.userId, targetUser.user_id]
    );
    if (pending.length > 0) {
      res.status(409).json({ error: 'request_pending' });
      return;
    }

    const reversePending = await query(
      `SELECT * FROM friend_requests
       WHERE from_user_id = ? AND to_user_id = ? AND status = 'pending'
       LIMIT 1`,
      [targetUser.user_id, req.userId]
    );
    if (reversePending.length > 0) {
      await acceptRequest(reversePending[0], req.userId);
      sessions.sendToUser(targetUser.user_id, {
        type: 'friend_request_status',
        requestId: reversePending[0].request_id,
        status: 'accepted',
        fromUserId: req.userId,
        fromNickname: fromUser ? fromUser.nickname : req.userId
      });
      res.json({ ok: true, autoAccepted: true });
      return;
    }

    const now = Date.now();
    const requestRow = {
      request_id: `${req.userId}_${targetUser.user_id}_${now}`,
      from_user_id: req.userId,
      from_nickname: fromUser ? (fromUser.nickname || fromUser.account || req.userId) : req.userId,
      from_avatar: fromUser ? (fromUser.avatar_url || 'ic_avatar_1') : 'ic_avatar_1',
      to_user_id: targetUser.user_id,
      message: String(req.body.message || '请求添加你为好友').trim() || '请求添加你为好友',
      status: 'pending',
      created_at: now,
      updated_at: now
    };

    await query(
      `INSERT INTO friend_requests
      (request_id, from_user_id, from_nickname, from_avatar, to_user_id, message, status, created_at, updated_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        requestRow.request_id,
        requestRow.from_user_id,
        requestRow.from_nickname,
        requestRow.from_avatar,
        requestRow.to_user_id,
        requestRow.message,
        requestRow.status,
        requestRow.created_at,
        requestRow.updated_at
      ]
    );

    sessions.sendToUser(targetUser.user_id, {
      type: 'friend_request',
      request: toFriendRequestDto(requestRow)
    });
    res.json({ request: toFriendRequestDto(requestRow) });
  } catch (err) {
    next(err);
  }
});

app.post('/api/friends/requests/:requestId/accept', requireAuth, async (req, res, next) => {
  try {
    const rows = await query('SELECT * FROM friend_requests WHERE request_id = ? LIMIT 1', [req.params.requestId]);
    if (rows.length === 0) {
      res.status(404).json({ error: 'request_not_found' });
      return;
    }
    const requestRow = rows[0];
    if (requestRow.to_user_id !== req.userId) {
      res.status(403).json({ error: 'request_not_found' });
      return;
    }
    if (requestRow.status !== 'pending') {
      res.status(409).json({ error: 'request_handled' });
      return;
    }

    await acceptRequest(requestRow, req.userId);
    sessions.sendToUser(requestRow.from_user_id, {
      type: 'friend_request_status',
      requestId: requestRow.request_id,
      status: 'accepted',
      fromUserId: req.userId
    });
    res.json({ ok: true });
  } catch (err) {
    next(err);
  }
});

app.post('/api/friends/requests/:requestId/reject', requireAuth, async (req, res, next) => {
  try {
    const rows = await query('SELECT * FROM friend_requests WHERE request_id = ? LIMIT 1', [req.params.requestId]);
    if (rows.length === 0) {
      res.status(404).json({ error: 'request_not_found' });
      return;
    }
    const requestRow = rows[0];
    if (requestRow.to_user_id !== req.userId) {
      res.status(403).json({ error: 'request_not_found' });
      return;
    }
    if (requestRow.status !== 'pending') {
      res.status(409).json({ error: 'request_handled' });
      return;
    }

    await query(
      'UPDATE friend_requests SET status = ?, updated_at = ? WHERE request_id = ?',
      ['rejected', Date.now(), requestRow.request_id]
    );
    sessions.sendToUser(requestRow.from_user_id, {
      type: 'friend_request_status',
      requestId: requestRow.request_id,
      status: 'rejected',
      fromUserId: req.userId
    });
    res.json({ ok: true });
  } catch (err) {
    next(err);
  }
});

app.delete('/api/friends/:friendId', requireAuth, async (req, res, next) => {
  try {
    const friendUser = await findUserByIdentifier(String(req.params.friendId || '').trim());
    if (!friendUser) {
      res.status(404).json({ error: 'user_not_found' });
      return;
    }
    if (!(await areFriends(req.userId, friendUser.user_id))) {
      res.status(404).json({ error: 'not_friends' });
      return;
    }

    await query(
      'DELETE FROM friends WHERE (owner_id = ? AND friend_id = ?) OR (owner_id = ? AND friend_id = ?)',
      [req.userId, friendUser.user_id, friendUser.user_id, req.userId]
    );
    res.json({ ok: true });
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

function getBearerToken(req) {
  const value = String(req.get('Authorization') || '').trim();
  if (!value.toLowerCase().startsWith('bearer ')) {
    return '';
  }
  return value.substring(7).trim();
}

function requireAuth(req, res, next) {
  const userId = auth.verifyToken(getBearerToken(req));
  if (!userId) {
    res.status(401).json({ error: 'unauthorized' });
    return;
  }
  req.userId = userId;
  next();
}

async function findUserByIdentifier(identifier) {
  const value = String(identifier || '').trim();
  if (!value) {
    return null;
  }
  const rows = await query('SELECT * FROM users WHERE user_id = ? OR account = ? LIMIT 1', [value, value]);
  return rows.length > 0 ? rows[0] : null;
}

async function generateAvailableUserId() {
  for (let i = 0; i < 32; i++) {
    const candidate = String(Math.floor(Math.random() * 9000000000) + 1000000000);
    const existing = await findUserByIdentifier(candidate);
    if (!existing) {
      return candidate;
    }
  }
  throw new Error('unable_to_generate_available_user_id');
}

async function areFriends(userA, userB) {
  const rows = await query(
    'SELECT 1 FROM friends WHERE owner_id = ? AND friend_id = ? LIMIT 1',
    [userA, userB]
  );
  return rows.length > 0;
}

async function acceptRequest(requestRow) {
  const now = Date.now();
  await query(
    'INSERT IGNORE INTO friends (owner_id, friend_id, created_at) VALUES (?, ?, ?), (?, ?, ?)',
    [requestRow.to_user_id, requestRow.from_user_id, now, requestRow.from_user_id, requestRow.to_user_id, now]
  );
  await query(
    'UPDATE friend_requests SET status = ?, updated_at = ? WHERE request_id = ?',
    ['accepted', now, requestRow.request_id]
  );
}

function toUserDto(row) {
  return {
    userId: row.user_id,
    account: row.account,
    nickname: row.nickname || row.account,
    avatarUrl: row.avatar_url || 'ic_avatar_1',
    signature: row.signature || ''
  };
}

function toFriendRequestDto(row) {
  return {
    requestId: row.request_id,
    fromUserId: row.from_user_id,
    fromNickname: row.from_nickname || row.from_user_id,
    fromAvatar: row.from_avatar || 'ic_avatar_1',
    toUserId: row.to_user_id,
    message: row.message || '',
    status: row.status || 'pending',
    createdAt: Number(row.created_at || 0),
    updatedAt: Number(row.updated_at || row.created_at || 0)
  };
}

async function bootstrap() {
  await ensureSchema();
  server.listen(config.port, config.host, () => {
    console.log(`koukou-server listening on http://${config.host}:${config.port}`);
  });
}

bootstrap().catch(err => {
  console.error('bootstrap_failed', err);
  process.exit(1);
});
