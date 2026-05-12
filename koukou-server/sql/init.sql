CREATE DATABASE IF NOT EXISTS koukou_im
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE koukou_im;

CREATE TABLE IF NOT EXISTS users (
    user_id        VARCHAR(32)  PRIMARY KEY,
    account        VARCHAR(32)  UNIQUE NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    nickname       VARCHAR(64),
    avatar_url     TEXT,
    signature      TEXT,
    created_at     BIGINT       NOT NULL,
    updated_at     BIGINT       NOT NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS friends (
    owner_id   VARCHAR(32) NOT NULL,
    friend_id  VARCHAR(32) NOT NULL,
    created_at BIGINT      NOT NULL,
    PRIMARY KEY (owner_id, friend_id),
    INDEX idx_friends_owner (owner_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS messages (
    message_id        VARCHAR(64)  PRIMARY KEY,
    client_message_id VARCHAR(64),
    conversation_id   VARCHAR(128) NOT NULL,
    sender_id         VARCHAR(32)  NOT NULL,
    receiver_id       VARCHAR(32)  NOT NULL,
    chat_type         VARCHAR(16)  NOT NULL,
    msg_type          VARCHAR(16)  NOT NULL,
    content           TEXT,
    status            VARCHAR(16)  NOT NULL,
    created_at        BIGINT       NOT NULL,
    INDEX idx_msg_conv_time (conversation_id, created_at),
    INDEX idx_msg_client (client_message_id, sender_id),
    INDEX idx_msg_receiver_time (receiver_id, created_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS offline_messages (
    id          BIGINT      PRIMARY KEY AUTO_INCREMENT,
    user_id     VARCHAR(32) NOT NULL,
    message_id  VARCHAR(64) NOT NULL,
    delivered   BOOLEAN     DEFAULT FALSE,
    created_at  BIGINT      NOT NULL,
    INDEX idx_offline_user (user_id, delivered)
) ENGINE=InnoDB;
