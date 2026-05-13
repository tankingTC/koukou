const crypto = require('crypto');
const { query } = require('./db');
const sessions = require('./sessions');

function remoteConversationId(userA, userB) {
  return String(userA) <= String(userB)
    ? `single_${userA}_${userB}`
    : `single_${userB}_${userA}`;
}

async function handleChatMessage(ws, message) {
  const senderId = ws.userId;
  const receiverId = message.toUserId || message.to;
  if (!receiverId) {
    ws.send(JSON.stringify({
      type: 'error',
      errorCode: 'missing_receiver',
      errorMessage: 'Missing receiver',
      clientMessageId: message.clientMessageId || null
    }));
    return;
  }

  const [receiver] = await query('SELECT user_id FROM users WHERE user_id = ? OR account = ? LIMIT 1', [receiverId, receiverId]);
  if (!receiver) {
    ws.send(JSON.stringify({
      type: 'error',
      errorCode: 'receiver_not_found',
      errorMessage: 'Receiver not found',
      clientMessageId: message.clientMessageId || null
    }));
    return;
  }

  const friendship = await query(
    'SELECT 1 FROM friends WHERE owner_id = ? AND friend_id = ? LIMIT 1',
    [senderId, receiver.user_id]
  );
  if (friendship.length === 0) {
    ws.send(JSON.stringify({
      type: 'error',
      errorCode: 'not_friends',
      errorMessage: 'Users are not friends',
      clientMessageId: message.clientMessageId || null
    }));
    return;
  }

  const clientMessageId = message.clientMessageId || null;
  if (clientMessageId) {
    const existing = await query(
      'SELECT * FROM messages WHERE client_message_id = ? AND sender_id = ? LIMIT 1',
      [clientMessageId, senderId]
    );
    if (existing.length > 0) {
      sendAck(ws, existing[0], clientMessageId);
      return;
    }
  }

  const now = Date.now();
  const messageId = `server_${crypto.randomUUID()}`;
  const conversationId = message.conversationId || remoteConversationId(senderId, receiver.user_id);
  const chatType = message.chatType || 'single';
  const msgType = message.msgType || 'text';
  const content = message.content || '';
  const [sender] = await query('SELECT nickname, avatar_url FROM users WHERE user_id = ? LIMIT 1', [senderId]);

  await query(
    `INSERT INTO messages
      (message_id, client_message_id, conversation_id, sender_id, receiver_id, chat_type, msg_type, content, status, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    [messageId, clientMessageId, conversationId, senderId, receiver.user_id, chatType, msgType, content, 'sent', now]
  );

  const row = {
    message_id: messageId,
    client_message_id: clientMessageId,
    conversation_id: conversationId,
    sender_id: senderId,
    receiver_id: receiver.user_id,
    chat_type: chatType,
    msg_type: msgType,
    content,
    status: 'sent',
    created_at: now
  };

  sendAck(ws, row, clientMessageId);

  const pushPayload = toSocketMessage(
    row,
    (sender && sender.nickname) || message.senderNickname,
    (sender && sender.avatar_url) || message.senderAvatar
  );
  const deliveredOnline = sessions.sendToUser(receiver.user_id, pushPayload);
  if (!deliveredOnline) {
    await query(
      'INSERT INTO offline_messages (user_id, message_id, delivered, created_at) VALUES (?, ?, FALSE, ?)',
      [receiver.user_id, messageId, now]
    );
  }
}

async function handleSyncRequest(ws, message) {
  const since = Number(message.lastMessageTime || 0);
  const rows = await query(
    `SELECT m.*, u.nickname AS sender_nickname, u.avatar_url AS sender_avatar
     FROM messages m
     LEFT JOIN users u ON u.user_id = m.sender_id
     WHERE m.receiver_id = ? AND m.created_at > ?
     ORDER BY m.created_at ASC
     LIMIT 200`,
    [ws.userId, since]
  );
  ws.send(JSON.stringify({
    type: 'sync_response',
    messages: rows.map(row => toSocketMessage(row, row.sender_nickname, row.sender_avatar))
  }));
}

function sendAck(ws, row, clientMessageId) {
  ws.send(JSON.stringify({
    type: 'message_ack',
    clientMessageId,
    messageId: row.message_id,
    status: 'sent',
    serverTimestamp: row.created_at
  }));
}

function toSocketMessage(row, senderNickname, senderAvatar) {
  return {
    type: 'chat_message',
    clientMessageId: row.client_message_id,
    messageId: row.message_id,
    fromUserId: row.sender_id,
    toUserId: row.receiver_id,
    from: row.sender_id,
    to: row.receiver_id,
    conversationId: row.conversation_id,
    chatType: row.chat_type,
    msgType: row.msg_type,
    content: row.content,
    timestamp: row.created_at,
    serverTimestamp: row.created_at,
    senderNickname,
    senderAvatar
  };
}

module.exports = {
  handleChatMessage,
  handleSyncRequest
};
