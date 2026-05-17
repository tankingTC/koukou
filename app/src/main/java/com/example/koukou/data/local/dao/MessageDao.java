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

    @Query("SELECT * FROM messages WHERE messageId = :msgId LIMIT 1")
    MessageEntity getMessageById(String msgId);

    @Query("SELECT * FROM messages WHERE clientMessageId = :clientMessageId LIMIT 1")
    MessageEntity getMessageByClientId(String clientMessageId);

    @Query("SELECT * FROM messages WHERE serverMessageId = :serverMessageId LIMIT 1")
    MessageEntity getMessageByServerId(String serverMessageId);

    @Query("SELECT * FROM messages WHERE senderId = :senderId AND clientMessageId = :clientMessageId LIMIT 1")
    MessageEntity getMessageBySenderAndClientId(String senderId, String clientMessageId);

    @Query("UPDATE messages SET status = :status WHERE messageId = :msgId")
    void updateMessageStatus(String msgId, String status);

    @Query("UPDATE messages SET status = :status, serverMessageId = :serverMessageId, serverTimestamp = :serverTimestamp, lastErrorCode = NULL, lastErrorMessage = NULL WHERE clientMessageId = :clientMessageId")
    void applyAck(String clientMessageId, String serverMessageId, String status, long serverTimestamp);

    @Query("UPDATE messages SET status = :status, lastErrorCode = :errorCode, lastErrorMessage = :errorMessage WHERE messageId = :messageId")
    void updateMessageFailure(String messageId, String status, String errorCode, String errorMessage);

    @Query("UPDATE messages SET status = :status, retryCount = retryCount + 1, timestamp = :timestamp WHERE messageId = :messageId")
    void markRetrying(String messageId, String status, long timestamp);

    @Query("SELECT * FROM messages WHERE senderId = :senderId AND (status = 'sending' OR status = 'failed') ORDER BY timestamp ASC")
    List<MessageEntity> getPendingMessages(String senderId);

    @Query("SELECT MAX(timestamp) FROM messages WHERE senderId = :userId OR receiverId = :userId")
    Long getLatestTimestampForUser(String userId);

    @Query("DELETE FROM messages WHERE senderId = :userId OR receiverId = :userId")
    void deleteAllByUser(String userId);
}
