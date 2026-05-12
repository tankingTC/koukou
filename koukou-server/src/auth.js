const jwt = require('jsonwebtoken');
const bcrypt = require('bcrypt');
const config = require('./config');

function signToken(user) {
  if (!config.jwtSecret) {
    return user.user_id;
  }
  return jwt.sign(
    { sub: user.user_id, account: user.account },
    config.jwtSecret,
    { expiresIn: '30d' }
  );
}

function verifyToken(token) {
  if (!token) {
    return null;
  }
  if (config.jwtSecret) {
    try {
      const payload = jwt.verify(token, config.jwtSecret);
      return payload.sub;
    } catch (err) {
      if (!config.allowUserIdToken) {
        return null;
      }
    }
  }
  return config.allowUserIdToken ? token : null;
}

async function hashPassword(password) {
  return bcrypt.hash(password, 10);
}

async function verifyPassword(password, hash) {
  return bcrypt.compare(password, hash);
}

module.exports = {
  signToken,
  verifyToken,
  hashPassword,
  verifyPassword
};
