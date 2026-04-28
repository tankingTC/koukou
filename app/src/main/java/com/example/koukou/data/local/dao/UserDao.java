package com.example.koukou.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.koukou.data.local.entity.UserEntity;

@Dao
public interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertUser(UserEntity user);

    @Query("SELECT * FROM users WHERE userId = :id")
    LiveData<UserEntity> getUserLiveData(String id);

    @Query("SELECT * FROM users WHERE userId = :id")
    UserEntity getUser(String id);

    @Query("SELECT * FROM users WHERE account = :account LIMIT 1")
    UserEntity getUserByAccount(String account);
}
