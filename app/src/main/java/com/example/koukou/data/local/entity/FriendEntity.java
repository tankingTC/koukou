package com.example.koukou.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(tableName = "friends", primaryKeys = {"ownerId", "friendId"})
public class FriendEntity {
    @NonNull
    public String ownerId = "";
    @NonNull
    public String friendId = "";
}