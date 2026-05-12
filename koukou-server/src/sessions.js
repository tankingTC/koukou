const sessions = new Map();

function add(userId, ws) {
  if (!sessions.has(userId)) {
    sessions.set(userId, new Set());
  }
  sessions.get(userId).add(ws);
}

function remove(userId, ws) {
  const set = sessions.get(userId);
  if (!set) {
    return;
  }
  set.delete(ws);
  if (set.size === 0) {
    sessions.delete(userId);
  }
}

function sendToUser(userId, payload) {
  const set = sessions.get(userId);
  if (!set || set.size === 0) {
    return false;
  }
  const text = JSON.stringify(payload);
  for (const ws of set) {
    if (ws.readyState === 1) {
      ws.send(text);
    }
  }
  return true;
}

module.exports = {
  add,
  remove,
  sendToUser
};
