package com.example.koukou.data.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.example.koukou.data.local.dao.FriendDao;
import com.example.koukou.data.local.dao.FriendRequestDao;
import com.example.koukou.data.local.dao.UserDao;
import com.example.koukou.data.local.entity.FriendEntity;
import com.example.koukou.data.local.entity.FriendRequestEntity;
import com.example.koukou.data.local.entity.UserEntity;
import com.example.koukou.network.api.KoukouApiService;
import com.example.koukou.utils.AppExecutors;
import com.example.koukou.utils.UserHelper;

import java.io.IOException;
import java.util.List;

public class ContactRepository {
    private static volatile ContactRepository INSTANCE;

    private final Context appContext;
    private final FriendDao friendDao;
    private final FriendRequestDao friendRequestDao;
    private final UserDao userDao;
    private final AppExecutors appExecutors;
    private final KoukouApiService apiService;

    private ContactRepository(Context context,
                              FriendDao friendDao,
                              FriendRequestDao friendRequestDao,
                              UserDao userDao,
                              AppExecutors appExecutors) {
        this.appContext = context.getApplicationContext();
        this.friendDao = friendDao;
        this.friendRequestDao = friendRequestDao;
        this.userDao = userDao;
        this.appExecutors = appExecutors;
        this.apiService = KoukouApiService.getInstance();
    }

    public static ContactRepository getInstance(Context context,
                                                FriendDao friendDao,
                                                FriendRequestDao friendRequestDao,
                                                UserDao userDao,
                                                AppExecutors appExecutors) {
        if (INSTANCE == null) {
            synchronized (ContactRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ContactRepository(context, friendDao, friendRequestDao, userDao, appExecutors);
                }
            }
        }
        return INSTANCE;
    }

    public LiveData<List<UserEntity>> getContacts(String myUserId) {
        return friendDao.getFriendsByOwner(myUserId);
    }

    public LiveData<List<FriendRequestEntity>> getIncomingRequests(String myUserId) {
        return friendRequestDao.observeIncomingRequests(myUserId);
    }

    public LiveData<Integer> getPendingIncomingCount(String myUserId) {
        return friendRequestDao.observePendingIncomingCount(myUserId);
    }

    public void refreshRemoteState(String myUserId, Callback callback) {
        String token = UserHelper.getAuthToken(appContext);
        if (token == null || token.trim().isEmpty()) {
            notifyError(callback, "当前登录状态已失效，请重新登录");
            return;
        }

        appExecutors.networkIO().execute(() -> {
            try {
                List<UserEntity> friends = apiService.getFriends(token);
                List<FriendRequestEntity> requests = apiService.getIncomingFriendRequests(token);
                appExecutors.diskIO().execute(() -> {
                    try {
                        syncFriends(myUserId, friends);
                        syncIncomingRequests(myUserId, requests);
                        notifySuccess(callback);
                    } catch (Exception e) {
                        notifyError(callback, "同步联系人失败: " + e.getMessage());
                    }
                });
            } catch (KoukouApiService.ApiException apiError) {
                notifyError(callback, mapApiError(apiError));
            } catch (IOException ioException) {
                notifyError(callback, "无法连接服务器，请检查网络后重试");
            }
        });
    }

    public void addFriend(String myUserId, String friendUserId, Callback callback) {
        String token = UserHelper.getAuthToken(appContext);
        if (token == null || token.trim().isEmpty()) {
            notifyError(callback, "当前登录状态已失效，请重新登录");
            return;
        }

        appExecutors.networkIO().execute(() -> {
            try {
                apiService.sendFriendRequest(token, friendUserId, "请求添加你为好友");
                refreshRemoteState(myUserId, null);
                notifySuccess(callback);
            } catch (KoukouApiService.ApiException apiError) {
                notifyError(callback, mapApiError(apiError));
            } catch (IOException ioException) {
                notifyError(callback, "发送好友申请失败，请检查网络后重试");
            }
        });
    }

    public void acceptFriendRequest(String myUserId, String requestId, Callback callback) {
        mutateRequest(myUserId, requestId, true, callback);
    }

    public void rejectFriendRequest(String myUserId, String requestId, Callback callback) {
        mutateRequest(myUserId, requestId, false, callback);
    }

    public void deleteFriend(String myUserId, String friendUserId, Callback callback) {
        String token = UserHelper.getAuthToken(appContext);
        if (token == null || token.trim().isEmpty()) {
            notifyError(callback, "当前登录状态已失效，请重新登录");
            return;
        }

        appExecutors.networkIO().execute(() -> {
            try {
                apiService.deleteFriend(token, friendUserId);
                appExecutors.diskIO().execute(() -> {
                    friendDao.deleteFriend(myUserId, friendUserId);
                    notifySuccess(callback);
                });
            } catch (KoukouApiService.ApiException apiError) {
                notifyError(callback, mapApiError(apiError));
            } catch (IOException ioException) {
                notifyError(callback, "删除好友失败，请检查网络后重试");
            }
        });
    }

    private void mutateRequest(String myUserId, String requestId, boolean accept, Callback callback) {
        String token = UserHelper.getAuthToken(appContext);
        if (token == null || token.trim().isEmpty()) {
            notifyError(callback, "当前登录状态已失效，请重新登录");
            return;
        }

        appExecutors.networkIO().execute(() -> {
            try {
                if (accept) {
                    apiService.acceptFriendRequest(token, requestId);
                } else {
                    apiService.rejectFriendRequest(token, requestId);
                }
                refreshRemoteState(myUserId, callback);
            } catch (KoukouApiService.ApiException apiError) {
                notifyError(callback, mapApiError(apiError));
            } catch (IOException ioException) {
                notifyError(callback, accept ? "同意好友申请失败，请检查网络后重试" : "拒绝好友申请失败，请检查网络后重试");
            }
        });
    }

    private void syncFriends(String myUserId, List<UserEntity> friends) {
        friendDao.deleteAllByOwner(myUserId);
        if (friends == null) {
            return;
        }
        for (UserEntity user : friends) {
            if (user == null || user.userId == null || user.userId.trim().isEmpty()) {
                continue;
            }
            userDao.insertUser(user);
            FriendEntity entity = new FriendEntity();
            entity.ownerId = myUserId;
            entity.friendId = user.userId;
            friendDao.insertFriend(entity);
        }
    }

    private void syncIncomingRequests(String myUserId, List<FriendRequestEntity> requests) {
        friendRequestDao.deleteIncomingRequestsForUser(myUserId);
        if (requests == null) {
            return;
        }
        for (FriendRequestEntity request : requests) {
            if (request == null || request.requestId == null || request.requestId.trim().isEmpty()) {
                continue;
            }
            friendRequestDao.insert(request);
            UserEntity fromUser = new UserEntity();
            fromUser.userId = safe(request.fromUserId);
            fromUser.account = fromUser.userId;
            fromUser.nickname = safe(request.fromNickname, fromUser.userId);
            fromUser.avatarUrl = safe(request.fromAvatar, "ic_avatar_1");
            fromUser.signature = "";
            userDao.insertUser(fromUser);
        }
    }

    private String mapApiError(KoukouApiService.ApiException apiError) {
        if (apiError == null) {
            return "服务器请求失败";
        }
        if ("user_not_found".equals(apiError.errorCode)) {
            return "未找到该扣扣号对应的用户";
        }
        if ("cannot_add_self".equals(apiError.errorCode)) {
            return "不能添加自己为好友";
        }
        if ("already_friends".equals(apiError.errorCode)) {
            return "你们已经是好友了";
        }
        if ("request_pending".equals(apiError.errorCode)) {
            return "已发送过好友申请，等待对方处理";
        }
        if ("request_not_found".equals(apiError.errorCode)) {
            return "好友申请不存在或已失效";
        }
        if ("request_handled".equals(apiError.errorCode)) {
            return "该好友申请已经处理过了";
        }
        if ("not_friends".equals(apiError.errorCode)) {
            return "你们目前还不是好友";
        }
        return apiError.getMessage();
    }

    private void notifySuccess(Callback callback) {
        if (callback == null) {
            return;
        }
        appExecutors.mainThread().execute(callback::onSuccess);
    }

    private void notifyError(Callback callback, String msg) {
        if (callback == null) {
            return;
        }
        appExecutors.mainThread().execute(() -> callback.onError(msg));
    }

    private String safe(String value) {
        return safe(value, "");
    }

    private String safe(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    public interface Callback {
        void onSuccess();
        void onError(String msg);
    }
}
