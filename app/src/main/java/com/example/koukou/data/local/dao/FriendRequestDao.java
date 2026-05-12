package com.example.koukou.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.koukou.data.local.entity.FriendRequestEntity;

import java.util.List;

@Dao
public interface FriendRequestDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(FriendRequestEntity request);

    @Query("SELECT * FROM friend_requests WHERE toUserId = :userId ORDER BY createdAt DESC")
    LiveData<List<FriendRequestEntity>> observeIncomingRequests(String userId);

    @Query("SELECT * FROM friend_requests WHERE toUserId = :userId ORDER BY createdAt DESC")
    List<FriendRequestEntity> getIncomingRequestsSync(String userId);

    @Query("SELECT * FROM friend_requests WHERE fromUserId = :fromUserId AND toUserId = :toUserId AND status = 'pending' LIMIT 1")
    FriendRequestEntity getPendingRequest(String fromUserId, String toUserId);

    @Query("SELECT * FROM friend_requests WHERE requestId = :requestId LIMIT 1")
    FriendRequestEntity getRequest(String requestId);

    @Query("UPDATE friend_requests SET status = :status, updatedAt = :updatedAt WHERE requestId = :requestId")
    void updateStatus(String requestId, String status, long updatedAt);

    @Query("SELECT COUNT(*) FROM friend_requests WHERE toUserId = :userId AND status = 'pending'")
    LiveData<Integer> observePendingIncomingCount(String userId);
}
