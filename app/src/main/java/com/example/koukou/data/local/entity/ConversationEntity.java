package com.example.koukou.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "conversations")
public class ConversationEntity {
    @PrimaryKey
    @NonNull
    public String conversationId = "";

    public String ownerId; // To isolate conversations per account
    public String targetId;
    public String targetName;
    public String targetAvatarUrl;
    public String lastMessage;
    public long lastMessageTime;
    public int unreadCount;
    public boolean isPinned;
}
