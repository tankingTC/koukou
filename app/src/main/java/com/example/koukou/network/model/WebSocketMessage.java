package com.example.koukou.network.model;

public class WebSocketMessage {
    public String type;       // "login", "message", "ack", "presence", "heartbeat", "profile_update"
    public String from;       // user_id
    public String to;         // target_id
    public String content;    // 消息内容
    public long timestamp;    // 发送时间
    public String messageId;  // uuid
    public String chatType;   // "single" 或 "group"
    
    // Add profile sync info
    public String senderNickname;
    public String senderAvatar;
}
