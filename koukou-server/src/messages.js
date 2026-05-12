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
    ws.send(JSON.stringify({ type: 'error', errorCode: 'missing_receiver', errorMessage: 'Missing receiver' }));
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
  const conversationId = message.conversationId || remoteConversationId(senderId, receiverId);
  const chatType = message.chatType || 'single';
  const msgType = message.msgType || 'text';
  const content = message.content || '';

  await query(
    `INSERT INTO messages
      (message_id, client_message_id, conversation_id, sender_id, receiver_id, chat_type, msg_type, content, status, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    [messageId, clientMessageId, conversationId, senderId, receiverId, chatType, msgType, content, 'sent', now]
  );

  const row = {
    message_id: messageId,
    client_message_id: clientMessageId,
    conversation_id: conversationId,
    sender_id: senderId,
    receiver_id: receiverId,
    chat_type: chatType,
    msg_type: msgType,
    content,
    status: 'sent',
    created_at: now
  };

  sendAck(ws, row, clientMessageId);

  const pushPayload = toSocketMessage(row, message.senderNickname, message.senderAvatar);
  const deliveredOnline = sessions.sendToUser(receiverId, pushPayload);
  if (!deliveredOnline) {
    await query(
      'INSERT INTO offline_messages (user_id, message_id, delivered, created_at) VALUES (?, ?, FALSE, ?)',
      [receiverId, messageId, now]
    );
  }
}

async function handleSyncRequest(ws, message) {
  const since = Number(message.lastMessageTime || 0);
  const rows = await query(
    `SELECT m.* FROM messages m
     WHERE m.receiver_id = ? AND m.created_at > ?
     ORDER BY m.created_at ASC
     LIMIT 200`,
    [ws.userId, since]
  );
  ws.send(JSON.stringify({
    type: 'sync_response',
    messages: rows.map(row => toSocketMessage(row))
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
