package com.example.koukou.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.koukou.data.local.entity.ConversationEntity;

import java.util.List;

@Dao
public interface ConversationDao {

    @Query("SELECT * FROM conversations WHERE ownerId = :ownerId ORDER BY lastMessageTime DESC")
    LiveData<List<ConversationEntity>> getAllConversations(String ownerId);     

    @Query("SELECT * FROM conversations WHERE conversationId = :id AND ownerId = :ownerId LIMIT 1")
    ConversationEntity getConversationById(String id, String ownerId);

    @Query("SELECT * FROM conversations WHERE conversationId = :id LIMIT 1")
    ConversationEntity getConversationSync(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ConversationEntity conversation);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(ConversationEntity conversation);

    @Update
    void update(ConversationEntity conversation);

    @Delete
    void delete(ConversationEntity conversation);

    @Query("DELETE FROM conversations WHERE conversationId = :convId")
    void deleteConversation(String convId);

    @Query("UPDATE conversations SET unreadCount = 0 WHERE conversationId = :convId")
    void clearUnreadCount(String convId);

    @Query("SELECT SUM(unreadCount) FROM conversations WHERE ownerId = :ownerId")
    LiveData<Integer> getTotalUnreadCount(String ownerId);

    @Query("DELETE FROM conversations WHERE ownerId = :ownerId")
    void deleteAllByOwner(String ownerId);

}
