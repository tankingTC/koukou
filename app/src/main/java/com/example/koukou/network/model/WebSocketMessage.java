package com.example.koukou.network.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WebSocketMessage {
    public String type;
    public String from;
    public String to;
    public String content;
    public long timestamp;
    public String messageId;
    public String clientMessageId;
    public String conversationId;
    public String requestId;
    public String fromUserId;
    public String toUserId;
    public String chatType;
    public String msgType;
    public String status;
    public String errorCode;
    public String errorMessage;
    public long serverTimestamp;
    public long lastMessageTime;
    public List<WebSocketMessage> messages;
    public Map<String, Object> extra;
    public Map<String, Object> request = new LinkedHashMap<>();

    public String senderNickname;
    public String senderAvatar;
}
