package com.example.koukou.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "users")
public class UserEntity {
    @PrimaryKey
    @NonNull
    public String userId = "";
    
    public String account;
    public String password; // 增加密码字段以储存验证信息
    public String nickname;
    public String avatarUrl;
    public String signature;
}
