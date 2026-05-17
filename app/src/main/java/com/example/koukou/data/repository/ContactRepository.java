package com.example.koukou.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.lifecycle.LiveData;

import com.example.koukou.data.local.dao.FriendDao;
import com.example.koukou.data.local.dao.FriendRequestDao;
import com.example.koukou.data.local.dao.UserDao;
import com.example.koukou.data.local.entity.FriendEntity;
import com.example.koukou.data.local.entity.FriendRequestEntity;
import com.example.koukou.data.local.entity.UserEntity;
import com.example.koukou.network.api.KoukouApiService;
import com.example.koukou.network.model.WebSocketMessage;
import com.example.koukou.network.websocket.AppWebSocketListener;
import com.example.koukou.network.websocket.WebSocketManager;
import com.example.koukou.utils.AppExecutors;
import com.example.koukou.utils.UserHelper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ContactRepository implements AppWebSocketListener {
    private static final String TAG = "ContactRepository";
    private static volatile ContactRepository INSTANCE;

    private final Context appContext;
    private final FriendDao friendDao;
    private final FriendRequestDao friendRequestDao;
    private final UserDao userDao;
    private final AppExecutors appExecutors;
    private final KoukouApiService apiService;
    private final WebSocketManager webSocketManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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
        this.webSocketManager = WebSocketManager.getInstance();
        this.webSocketManager.addListener(this);
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

        String ownerId = safe(myUserId, UserHelper.getUserId(appContext));
        appExecutors.networkIO().execute(() -> {
            try {
                List<UserEntity> friends = apiService.getFriends(token);
                List<FriendRequestEntity> requests = apiService.getIncomingFriendRequests(token);
                appExecutors.diskIO().execute(() -> {
                    try {
                        syncFriends(ownerId, friends);
                        syncIncomingRequests(ownerId, requests);
                        notifySuccess(callback);
                    } catch (Exception e) {
                        notifyError(callback, "同步联系人失败：" + safe(e.getMessage(), "未知错误"));
                    }
                });
            } catch (KoukouApiService.ApiException apiError) {
                notifyError(callback, mapApiError(apiError));
            } catch (IOException ioException) {
                notifyError(callback, KoukouApiService.describeNetworkError(ioException));
            }
        });
    }

    public void searchUser(String identifier, UserLookupCallback callback) {
        String token = UserHelper.getAuthToken(appContext);
        if (token == null || token.trim().isEmpty()) {
            notifyLookupError(callback, "当前登录状态已失效，请重新登录");
            return;
        }
        appExecutors.networkIO().execute(() -> {
            try {
                UserEntity user = apiService.findUser(identifier, token);
                if (user != null) {
                    appExecutors.diskIO().execute(() -> userDao.insertUser(user));
                }
                notifyLookupSuccess(callback, user);
            } catch (KoukouApiService.ApiException apiError) {
                notifyLookupError(callback, mapApiError(apiError));
            } catch (IOException ioException) {
                notifyLookupError(callback, KoukouApiService.describeNetworkError(ioException));
            }
        });
    }

    public void addFriend(String myUserId, String friendUserId, Callback callback) {
        String token = UserHelper.getAuthToken(appContext);
        if (token == null || token.trim().isEmpty()) {
            notifyError(callback, "当前登录状态已失效，请重新登录");
            return;
        }

        String ownerId = safe(myUserId, UserHelper.getUserId(appContext));
        appExecutors.networkIO().execute(() -> {
            try {
                apiService.sendFriendRequest(token, friendUserId, "请求添加你为好友");
                refreshRemoteState(ownerId, null);
                notifySuccess(callback);
            } catch (KoukouApiService.ApiException apiError) {
                notifyError(callback, mapApiError(apiError));
            } catch (IOException ioException) {
                notifyError(callback, KoukouApiService.describeNetworkError(ioException));
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

        String ownerId = safe(myUserId, UserHelper.getUserId(appContext));
        appExecutors.networkIO().execute(() -> {
            try {
                apiService.deleteFriend(token, friendUserId);
                appExecutors.diskIO().execute(() -> {
                    friendDao.deleteFriend(ownerId, friendUserId);
                    notifySuccess(callback);
                });
            } catch (KoukouApiService.ApiException apiError) {
                notifyError(callback, mapApiError(apiError));
            } catch (IOException ioException) {
                notifyError(callback, KoukouApiService.describeNetworkError(ioException));
            }
        });
    }

    @Override
    public void onMessageReceived(WebSocketMessage message) {
        if (message == null || message.type == null) {
            return;
        }
        switch (message.type) {
            case "friend_request":
                handleRealtimeFriendRequest(message);
                break;
            case "friend_request_status":
                handleRealtimeFriendRequestStatus(message);
                break;
            default:
                break;
        }
    }

    @Override
    public void onConnect(boolean isSuccess) {
    }

    @Override
    public void onDisconnect() {
    }

    private void mutateRequest(String myUserId, String requestId, boolean accept, Callback callback) {
        String token = UserHelper.getAuthToken(appContext);
        if (token == null || token.trim().isEmpty()) {
            notifyError(callback, "当前登录状态已失效，请重新登录");
            return;
        }

        String ownerId = safe(myUserId, UserHelper.getUserId(appContext));
        appExecutors.networkIO().execute(() -> {
            try {
                if (accept) {
                    apiService.acceptFriendRequest(token, requestId);
                } else {
                    apiService.rejectFriendRequest(token, requestId);
                }
                refreshRemoteState(ownerId, callback);
            } catch (KoukouApiService.ApiException apiError) {
                notifyError(callback, mapApiError(apiError));
            } catch (IOException ioException) {
                notifyError(callback, KoukouApiService.describeNetworkError(ioException));
            }
        });
    }

    private void handleRealtimeFriendRequest(WebSocketMessage message) {
        String currentUserId = UserHelper.getUserId(appContext);
        if (currentUserId == null || currentUserId.trim().isEmpty()) {
            return;
        }

        FriendRequestEntity request = parseRequest(message.request);
        if (request == null || !currentUserId.equals(request.toUserId)) {
            return;
        }

        appExecutors.diskIO().execute(() -> {
            try {
                friendRequestDao.insert(request);

                UserEntity fromUser = new UserEntity();
                fromUser.userId = safe(request.fromUserId);
                fromUser.account = fromUser.userId;
                fromUser.nickname = safe(request.fromNickname, fromUser.userId);
                fromUser.avatarUrl = safe(request.fromAvatar, "ic_avatar_1");
                fromUser.signature = "";
                userDao.insertUser(fromUser);

                showRealtimeToast("收到来自 " + fromUser.nickname + " 的好友申请");
            } catch (Exception e) {
                Log.e(TAG, "Failed to persist realtime friend request", e);
            }
        });
    }

    private void handleRealtimeFriendRequestStatus(WebSocketMessage message) {
        String currentUserId = UserHelper.getUserId(appContext);
        if (currentUserId == null || currentUserId.trim().isEmpty()) {
            return;
        }

        String requestId = safe(message.requestId);
        String status = safe(message.status, "pending");
        String fromUserId = safe(message.fromUserId, safe(message.from));
        String fromNickname = safe(message.senderNickname, safe(fromUserId, "对方"));

        appExecutors.diskIO().execute(() -> {
            try {
                if (!requestId.isEmpty()) {
                    friendRequestDao.updateStatus(requestId, status, System.currentTimeMillis());
                }
                if ("accepted".equals(status) || "rejected".equals(status)) {
                    refreshRemoteState(currentUserId, null);
                }
                if ("accepted".equals(status)) {
                    showRealtimeToast(fromNickname + " 已通过你的好友申请");
                } else if ("rejected".equals(status)) {
                    showRealtimeToast(fromNickname + " 拒绝了你的好友申请");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to handle friend request status push", e);
            }
        });
    }

    private void syncFriends(String myUserId, List<UserEntity> friends) {
        friendDao.deleteAllByOwner(myUserId);
        if (friends == null) {
            return;
        }
        for (UserEntity user : friends) {
            if (user == null || safe(user.userId).isEmpty()) {
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
            if (request == null || safe(request.requestId).isEmpty()) {
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

    private FriendRequestEntity parseRequest(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        FriendRequestEntity entity = new FriendRequestEntity();
        entity.requestId = valueOf(payload.get("requestId"));
        entity.fromUserId = valueOf(payload.get("fromUserId"));
        entity.fromNickname = valueOf(payload.get("fromNickname"));
        entity.fromAvatar = valueOf(payload.get("fromAvatar"));
        entity.toUserId = valueOf(payload.get("toUserId"));
        entity.message = valueOf(payload.get("message"));
        entity.status = valueOf(payload.get("status"), "pending");
        entity.createdAt = longValueOf(payload.get("createdAt"), System.currentTimeMillis());
        entity.updatedAt = longValueOf(payload.get("updatedAt"), entity.createdAt);
        if (safe(entity.requestId).isEmpty()) {
            return null;
        }
        return entity;
    }

    private String mapApiError(KoukouApiService.ApiException apiError) {
        if (apiError == null) {
            return "服务器请求失败";
        }
        switch (apiError.errorCode) {
            case "user_not_found":
                return "未找到该扣扣号对应的用户";
            case "cannot_add_self":
                return "不能添加自己为好友";
            case "already_friends":
                return "你们已经是好友了";
            case "request_pending":
                return "你已经发送过好友申请了";
            case "request_not_found":
                return "好友申请不存在或已失效";
            case "request_handled":
                return "这条好友申请已经处理过了";
            case "not_friends":
                return "你们当前还不是好友";
            case "unauthorized":
                return "登录状态已失效，请重新登录";
            default:
                return safe(apiError.getMessage(), "服务器请求失败");
        }
    }

    private void showRealtimeToast(String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        mainHandler.post(() -> Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show());
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

    private void notifyLookupSuccess(UserLookupCallback callback, UserEntity user) {
        if (callback == null) {
            return;
        }
        appExecutors.mainThread().execute(() -> callback.onSuccess(user));
    }

    private void notifyLookupError(UserLookupCallback callback, String message) {
        if (callback == null) {
            return;
        }
        appExecutors.mainThread().execute(() -> callback.onError(message));
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

    private String valueOf(Object value) {
        return valueOf(value, "");
    }

    private String valueOf(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private long longValueOf(Object value, long fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public interface Callback {
        void onSuccess();
        void onError(String msg);
    }

    public interface UserLookupCallback {
        void onSuccess(UserEntity user);
        void onError(String msg);
    }
}
