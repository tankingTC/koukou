package com.example.koukou.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "messages", indices = {
        @Index("clientMessageId"),
        @Index("serverMessageId")
})
public class MessageEntity {
    @PrimaryKey
    @NonNull
    public String messageId = "";

    public String clientMessageId;
    public String serverMessageId;
    public String conversationId;
    public String senderId;
    public String receiverId;
    public String content;
    public String msgType;
    public String localPath;
    public long timestamp;
    public String chatType;
    public String status;
    public String lastErrorCode;
    public String lastErrorMessage;
    public boolean isRead;
    public int retryCount;
    public long serverTimestamp;
}
