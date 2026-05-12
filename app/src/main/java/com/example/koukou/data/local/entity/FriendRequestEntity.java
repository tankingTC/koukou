package com.example.koukou.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "friend_requests")
public class FriendRequestEntity {
    @PrimaryKey
    @NonNull
    public String requestId;
    public String fromUserId;
    public String fromNickname;
    public String fromAvatar;
    public String toUserId;
    public String message;
    public String status;
    public long createdAt;
    public long updatedAt;
}
