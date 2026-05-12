require('dotenv').config();

module.exports = {
  port: Number(process.env.PORT || 8080),
  jwtSecret: process.env.JWT_SECRET || '',
  allowUserIdToken: String(process.env.DEV_AUTH_ALLOW_USER_ID || 'true') === 'true',
  mysql: {
    host: process.env.MYSQL_HOST || '127.0.0.1',
    port: Number(process.env.MYSQL_PORT || 3306),
    user: process.env.MYSQL_USER || 'koukou',
    password: process.env.MYSQL_PASSWORD || '',
    database: process.env.MYSQL_DB || 'koukou_im',
    waitForConnections: true,
    connectionLimit: 10,
    charset: 'utf8mb4'
  }
};
