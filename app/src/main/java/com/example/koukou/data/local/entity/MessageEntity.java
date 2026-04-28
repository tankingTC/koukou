package com.example.koukou.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "messages")
public class MessageEntity {
    @PrimaryKey
    @NonNull
    public String messageId = "";
    
    public String conversationId; // 关联的会话ID
    public String senderId;
    public String receiverId;
    public String content;
    public String msgType; // "text", "image", "video", "emoji"
    public String localPath; // URI string for local image/video
    public long timestamp;
    public String chatType; // "single" 或 "group"
    public String status;   // "sending", "sent", "read", "failed"
}
