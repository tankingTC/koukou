const mysql = require('mysql2/promise');
const config = require('./config');

const pool = mysql.createPool(config.mysql);

async function query(sql, params = []) {
  const [rows] = await pool.execute(sql, params);
  return rows;
}

async function hasColumn(tableName, columnName) {
  const [rows] = await pool.query(`SHOW COLUMNS FROM \`${tableName}\` LIKE ?`, [columnName]);
  return rows.length > 0;
}

async function ensureSchema() {
  await query(
    `CREATE TABLE IF NOT EXISTS friend_requests (
      request_id     VARCHAR(96) PRIMARY KEY,
      from_user_id   VARCHAR(32) NOT NULL,
      from_nickname  VARCHAR(64),
      from_avatar    TEXT,
      to_user_id     VARCHAR(32) NOT NULL,
      message        TEXT,
      status         VARCHAR(16) NOT NULL,
      created_at     BIGINT NOT NULL,
      updated_at     BIGINT NOT NULL,
      INDEX idx_friend_req_to_status (to_user_id, status, created_at),
      INDEX idx_friend_req_from_to (from_user_id, to_user_id, status)
    ) ENGINE=InnoDB`
  );

  if (!(await hasColumn('friends', 'created_at'))) {
    await query('ALTER TABLE friends ADD COLUMN created_at BIGINT NOT NULL DEFAULT 0');
  }
}

module.exports = {
  pool,
  query,
  ensureSchema
};
