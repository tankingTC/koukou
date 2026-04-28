package com.example.koukou.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.koukou.data.local.entity.FriendEntity;
import com.example.koukou.data.local.entity.UserEntity;

import java.util.List;

@Dao
public interface FriendDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertFriend(FriendEntity... friends);

    @Query("SELECT u.* FROM users u INNER JOIN friends f ON u.userId = f.friendId WHERE f.ownerId = :ownerId")
    LiveData<List<UserEntity>> getFriendsByOwner(String ownerId);

    @Query("SELECT u.* FROM users u INNER JOIN friends f ON u.userId = f.friendId WHERE f.ownerId = :ownerId")
    List<UserEntity> getFriendsByOwnerSync(String ownerId);

    @Query("SELECT COUNT(*) FROM friends WHERE ownerId = :ownerId AND friendId = :friendId")
    int isFriend(String ownerId, String friendId);

    @Query("DELETE FROM friends WHERE (ownerId = :ownerId AND friendId = :friendId) OR (ownerId = :friendId AND friendId = :ownerId)")
    void deleteFriend(String ownerId, String friendId);

    @Query("DELETE FROM friends WHERE ownerId = :ownerId")
    void deleteAllByOwner(String ownerId);
}
