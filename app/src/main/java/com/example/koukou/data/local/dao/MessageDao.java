package com.example.koukou.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.koukou.data.local.entity.MessageEntity;

import java.util.List;

@Dao
public interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMessage(MessageEntity message);

    @Query("SELECT * FROM messages WHERE conversationId = :convId ORDER BY timestamp ASC")
    LiveData<List<MessageEntity>> getMessagesByConversation(String convId);

    @Query("SELECT * FROM messages WHERE conversationId = :convId ORDER BY timestamp DESC LIMIT 1")
    MessageEntity getLatestMessageByConversation(String convId);

    @Query("UPDATE messages SET status = :status WHERE messageId = :msgId")
    void updateMessageStatus(String msgId, String status);

    @Query("DELETE FROM messages WHERE senderId = :userId OR receiverId = :userId")
    void deleteAllByUser(String userId);
}
