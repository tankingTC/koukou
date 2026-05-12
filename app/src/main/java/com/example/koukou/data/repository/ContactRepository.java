package com.example.koukou.data.repository;

import androidx.lifecycle.LiveData;

import com.example.koukou.data.local.dao.FriendDao;
import com.example.koukou.data.local.dao.FriendRequestDao;
import com.example.koukou.data.local.dao.UserDao;
import com.example.koukou.data.local.entity.FriendEntity;
import com.example.koukou.data.local.entity.FriendRequestEntity;
import com.example.koukou.data.local.entity.UserEntity;
import com.example.koukou.utils.AppExecutors;

import java.util.List;

public class ContactRepository {
    private static volatile ContactRepository INSTANCE;
    private final FriendDao friendDao;
    private final FriendRequestDao friendRequestDao;
    private final UserDao userDao;
    private final AppExecutors appExecutors;

    private ContactRepository(FriendDao friendDao, FriendRequestDao friendRequestDao, UserDao userDao, AppExecutors appExecutors) {
        this.friendDao = friendDao;
        this.friendRequestDao = friendRequestDao;
        this.userDao = userDao;
        this.appExecutors = appExecutors;
    }

    public static ContactRepository getInstance(FriendDao friendDao, FriendRequestDao friendRequestDao, UserDao userDao, AppExecutors appExecutors) {
        if (INSTANCE == null) {
            synchronized (ContactRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ContactRepository(friendDao, friendRequestDao, userDao, appExecutors);
                }
            }
        }
        return INSTANCE;
    }

    public LiveData<List<UserEntity>> getContacts(String myUserId) {
        return friendDao.getFriendsByOwner(myUserId);
    }

    public void addFriend(String myUserId, String friendUserId, Callback callback) {
        sendFriendRequest(myUserId, friendUserId, "请求添加你为好友", callback);
    }

    public LiveData<List<FriendRequestEntity>> getIncomingRequests(String myUserId) {
        return friendRequestDao.observeIncomingRequests(myUserId);
    }

    public LiveData<Integer> getPendingIncomingCount(String myUserId) {
        return friendRequestDao.observePendingIncomingCount(myUserId);
    }

    public void sendFriendRequest(String myUserId, String friendUserId, String message, Callback callback) {
        appExecutors.diskIO().execute(() -> {
            String identifier = friendUserId == null ? "" : friendUserId.trim();
            UserEntity friend = userDao.getUser(identifier);
            if (friend == null) {
                friend = userDao.getUserByAccount(identifier);
            }
            if (friend == null) {
                appExecutors.mainThread().execute(() -> callback.onError("未找到该扣扣号对应的用户"));
                return;
            }
            if (friend.userId.equals(myUserId)) {
                appExecutors.mainThread().execute(() -> callback.onError("不能添加自己为好友"));
                return;
            }
            if (friendDao.isFriend(myUserId, friend.userId) > 0) {
                appExecutors.mainThread().execute(() -> callback.onError("你们已经是好友了"));
                return;
            }
            if (friendRequestDao.getPendingRequest(myUserId, friend.userId) != null) {
                appExecutors.mainThread().execute(() -> callback.onError("已发送过好友申请，等待对方处理"));
                return;
            }

            UserEntity me = userDao.getUser(myUserId);
            long now = System.currentTimeMillis();
            FriendRequestEntity request = new FriendRequestEntity();
            request.requestId = myUserId + "_" + friend.userId + "_" + now;
            request.fromUserId = myUserId;
            request.fromNickname = me == null ? myUserId : me.nickname;
            request.fromAvatar = me == null ? "" : me.avatarUrl;
            request.toUserId = friend.userId;
            request.message = message == null || message.trim().isEmpty() ? "请求添加你为好友" : message.trim();
            request.status = "pending";
            request.createdAt = now;
            request.updatedAt = now;
            friendRequestDao.insert(request);
            appExecutors.mainThread().execute(() -> callback.onSuccess());
        });
    }

    public void acceptFriendRequest(String myUserId, String requestId, Callback callback) {
        appExecutors.diskIO().execute(() -> {
            FriendRequestEntity request = friendRequestDao.getRequest(requestId);
            if (request == null || !myUserId.equals(request.toUserId)) {
                appExecutors.mainThread().execute(() -> callback.onError("好友申请不存在"));
                return;
            }
            if (!"pending".equals(request.status)) {
                appExecutors.mainThread().execute(() -> callback.onError("该申请已处理"));
                return;
            }
            FriendEntity f1 = new FriendEntity();
            f1.ownerId = request.toUserId;
            f1.friendId = request.fromUserId;

            FriendEntity f2 = new FriendEntity();
            f2.ownerId = request.fromUserId;
            f2.friendId = request.toUserId;

            friendDao.insertFriend(f1, f2);
            friendRequestDao.updateStatus(requestId, "accepted", System.currentTimeMillis());
            appExecutors.mainThread().execute(() -> callback.onSuccess());
        });
    }

    public void rejectFriendRequest(String myUserId, String requestId, Callback callback) {
        appExecutors.diskIO().execute(() -> {
            FriendRequestEntity request = friendRequestDao.getRequest(requestId);
            if (request == null || !myUserId.equals(request.toUserId)) {
                appExecutors.mainThread().execute(() -> callback.onError("好友申请不存在"));
                return;
            }
            if (!"pending".equals(request.status)) {
                appExecutors.mainThread().execute(() -> callback.onError("该申请已处理"));
                return;
            }
            friendRequestDao.updateStatus(requestId, "rejected", System.currentTimeMillis());
            appExecutors.mainThread().execute(() -> callback.onSuccess());
        });
    }

    public void deleteFriend(String myUserId, String friendUserId, Callback callback) {
        appExecutors.diskIO().execute(() -> {
            friendDao.deleteFriend(myUserId, friendUserId);
            appExecutors.mainThread().execute(() -> callback.onSuccess());
        });
    }

    public interface Callback {
        void onSuccess();
        void onError(String msg);
    }
}
