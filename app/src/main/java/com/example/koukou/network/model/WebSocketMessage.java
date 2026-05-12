package com.example.koukou.network.model;

import java.util.List;
import java.util.Map;

public class WebSocketMessage {
    public String type;       // "chat_message", "message_ack", "sync_request", "sync_response", "heartbeat_ping", "heartbeat_pong"
    public String from;       // user_id
    public String to;         // target_id
    public String content;
    public long timestamp;
    public String messageId;
    public String clientMessageId;
    public String conversationId;
    public String fromUserId;
    public String toUserId;
    public String chatType;   // "single" 或 "group"
    public String msgType;    // "text", "image", "video", "emoji", "file"
    public String status;
    public long serverTimestamp;
    public long lastMessageTime;
    public List<WebSocketMessage> messages;
    public Map<String, Object> extra;

    public String senderNickname;
    public String senderAvatar;
}
