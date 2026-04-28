package com.example.koukou.data.repository;

import androidx.lifecycle.LiveData;

import com.example.koukou.data.local.dao.FriendDao;
import com.example.koukou.data.local.dao.UserDao;
import com.example.koukou.data.local.entity.FriendEntity;
import com.example.koukou.data.local.entity.UserEntity;
import com.example.koukou.utils.AppExecutors;

import java.util.List;

public class ContactRepository {
    private static volatile ContactRepository INSTANCE;
    private final FriendDao friendDao;
    private final UserDao userDao;
    private final AppExecutors appExecutors;

    private ContactRepository(FriendDao friendDao, UserDao userDao, AppExecutors appExecutors) {
        this.friendDao = friendDao;
        this.userDao = userDao;
        this.appExecutors = appExecutors;
    }

    public static ContactRepository getInstance(FriendDao friendDao, UserDao userDao, AppExecutors appExecutors) {
        if (INSTANCE == null) {
            synchronized (ContactRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ContactRepository(friendDao, userDao, appExecutors);
                }
            }
        }
        return INSTANCE;
    }

    public LiveData<List<UserEntity>> getContacts(String myUserId) {
        return friendDao.getFriendsByOwner(myUserId);
    }

    public void addFriend(String myUserId, String friendUserId, Callback callback) {
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

            FriendEntity f1 = new FriendEntity();
            f1.ownerId = myUserId;
            f1.friendId = friend.userId;

            FriendEntity f2 = new FriendEntity();
            f2.ownerId = friend.userId;
            f2.friendId = myUserId;

            friendDao.insertFriend(f1, f2);
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
