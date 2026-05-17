package com.example.koukou.data.repository;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.koukou.data.local.dao.ConversationDao;
import com.example.koukou.data.local.dao.MessageDao;
import com.example.koukou.data.local.entity.ConversationEntity;
import com.example.koukou.data.local.entity.MessageEntity;
import com.example.koukou.network.model.WebSocketMessage;
import com.example.koukou.network.websocket.AppWebSocketListener;
import com.example.koukou.network.websocket.WebSocketManager;
import com.example.koukou.utils.AppExecutors;

import java.util.List;
import java.util.UUID;

public class MessageRepository implements AppWebSocketListener {
    private static final long ACK_TIMEOUT_MS = 15_000L;
    private static final String TAG = "MessageRepository";
    private static volatile MessageRepository INSTANCE;

    public static final class UiFeedback {
        public final long id;
        public final String message;

        public UiFeedback(String message) {
            this.id = System.currentTimeMillis();
            this.message = message;
        }
    }

    private final MessageDao messageDao;
    private final ConversationDao conversationDao;
    private final AppExecutors appExecutors;
    private final WebSocketManager webSocketManager;
    private final MutableLiveData<UiFeedback> feedbackLiveData = new MutableLiveData<>();

    private String currentUserId = "";
    private String currentNickname = "";
    private String currentAvatarUrl = "";

    private MessageRepository(MessageDao messageDao,
                              ConversationDao conversationDao,
                              AppExecutors appExecutors,
                              WebSocketManager webSocketManager) {
        this.messageDao = messageDao;
        this.conversationDao = conversationDao;
        this.appExecutors = appExecutors;
        this.webSocketManager = webSocketManager;
        this.webSocketManager.addListener(this);
    }

    public static MessageRepository getInstance(MessageDao messageDao,
                                                ConversationDao conversationDao,
                                                AppExecutors appExecutors,
                                                WebSocketManager webSocketManager) {
        if (INSTANCE == null) {
            synchronized (MessageRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new MessageRepository(messageDao, conversationDao, appExecutors, webSocketManager);
                }
            }
        }
        return INSTANCE;
    }

    public void setCurrentUser(String currentUserId, String currentNickname, String currentAvatarUrl) {
        this.currentUserId = safe(currentUserId);
        this.currentNickname = safe(currentNickname);
        this.currentAvatarUrl = safe(currentAvatarUrl);
    }

    public LiveData<List<MessageEntity>> getMessages(String targetId) {
        return messageDao.getMessagesByConversation(conversationIdOf(targetId));
    }

    public LiveData<List<ConversationEntity>> getConversations() {
        return conversationDao.getAllConversations(currentUserId);
    }

    public LiveData<UiFeedback> getFeedbackLiveData() {
        return feedbackLiveData;
    }

    public void sendMessage(String targetId, String content, String msgType, String localPath, String chatType) {
        appExecutors.diskIO().execute(() -> {
            String displayContent = safe(content);
            if (displayContent.isEmpty() || currentUserId.isEmpty()) {
                return;
            }

            long now = System.currentTimeMillis();
            String clientMessageId = generateClientMessageId();
            MessageEntity entity = new MessageEntity();
            entity.messageId = buildLocalMessageId(currentUserId, clientMessageId);
            entity.clientMessageId = clientMessageId;
            entity.conversationId = conversationIdOf(targetId);
            entity.senderId = currentUserId;
            entity.receiverId = targetId;
            entity.content = displayContent;
            entity.msgType = safeType(msgType);
            entity.localPath = localPath;
            entity.timestamp = now;
            entity.chatType = safeChatType(chatType);
            entity.status = "sending";
            entity.lastErrorCode = null;
            entity.lastErrorMessage = null;
            entity.isRead = false;
            entity.retryCount = 0;
            entity.serverTimestamp = 0L;

            messageDao.insertMessage(entity);
            upsertConversation(currentUserId, targetId, displayContent, now, false, null, null);

            if (!sendOverSocket(entity)) {
                failMessage(entity.messageId, "socket_disconnected", "当前连接不可用，消息未发送");
                return;
            }
            scheduleAckTimeout(entity.messageId);
        });
    }

    public void retryMessage(String messageId) {
        appExecutors.diskIO().execute(() -> {
            MessageEntity original = messageDao.getMessageById(messageId);
            if (original == null || !"failed".equals(original.status)) {
                return;
            }
            if (safe(original.clientMessageId).isEmpty()) {
                original.clientMessageId = generateClientMessageId();
            }
            long now = System.currentTimeMillis();
            original.status = "sending";
            original.timestamp = now;
            original.retryCount += 1;
            original.lastErrorCode = null;
            original.lastErrorMessage = null;
            messageDao.insertMessage(original);
            upsertConversation(original.senderId, original.receiverId, original.content, now, false, null, null);

            if (!sendOverSocket(original)) {
                failMessage(original.messageId, "socket_disconnected", "当前连接不可用，消息未发送");
                return;
            }
            scheduleAckTimeout(original.messageId);
        });
    }

    @Override
    public void onMessageReceived(WebSocketMessage message) {
        if (message == null || safe(message.type).isEmpty()) {
            return;
        }
        try {
            switch (message.type) {
                case "error":
                    handleServerError(message);
                    break;
                case "message_ack":
                case "ack":
                    handleAck(message);
                    break;
                case "chat_message":
                case "message":
                    handleIncomingMessage(message);
                    break;
                case "sync_response":
                    handleSyncResponse(message);
                    break;
                case "profile_update":
                    handleProfileUpdate(message);
                    break;
                default:
                    break;
            }
        } catch (Throwable t) {
            Log.e(TAG, "Unhandled websocket message crashed repository, type=" + message.type, t);
        }
    }

    @Override
    public void onConnect(boolean isSuccess) {
        if (isSuccess) {
            requestOfflineSync();
            resendSendingMessages();
        }
    }

    @Override
    public void onDisconnect() {
    }

    private boolean sendOverSocket(MessageEntity entity) {
        WebSocketMessage request = new WebSocketMessage();
        request.type = "chat_message";
        request.clientMessageId = entity.clientMessageId;
        request.messageId = entity.serverMessageId;
        request.fromUserId = entity.senderId;
        request.toUserId = entity.receiverId;
        request.from = entity.senderId;
        request.to = entity.receiverId;
        request.conversationId = buildRemoteConversationId(entity.senderId, entity.receiverId);
        request.chatType = safeChatType(entity.chatType);
        request.msgType = safeType(entity.msgType);
        request.content = entity.content;
        request.timestamp = entity.timestamp;
        request.senderNickname = currentNickname;
        request.senderAvatar = currentAvatarUrl;
        return webSocketManager.sendMessage(request);
    }

    private void handleAck(WebSocketMessage message) {
        appExecutors.diskIO().execute(() -> {
            try {
                if (safe(message.clientMessageId).isEmpty()) {
                    return;
                }
                String status = safe(message.status, "sent");
                long serverTime = message.serverTimestamp > 0 ? message.serverTimestamp : System.currentTimeMillis();
                messageDao.applyAck(message.clientMessageId, message.messageId, status, serverTime);
            } catch (Throwable t) {
                Log.e(TAG, "Failed to handle websocket ack", t);
            }
        });
    }

    private void handleServerError(WebSocketMessage message) {
        appExecutors.diskIO().execute(() -> {
            try {
                if (safe(message.clientMessageId).isEmpty()) {
                    postFeedback(mapServerErrorMessage(message));
                    return;
                }
                MessageEntity local = messageDao.getMessageByClientId(message.clientMessageId);
                String reason = mapServerErrorMessage(message);
                if (local != null && "sending".equals(local.status)) {
                    messageDao.updateMessageFailure(local.messageId, "failed", safe(message.errorCode), reason);
                }
                postFeedback(reason);
            } catch (Throwable t) {
                Log.e(TAG, "Failed to handle websocket server error", t);
            }
        });
    }

    private void handleIncomingMessage(WebSocketMessage message) {
        appExecutors.diskIO().execute(() -> {
            try {
                String ownerId = firstNonEmpty(message.toUserId, message.to);
                String targetId = firstNonEmpty(message.fromUserId, message.from);
                if (ownerId == null || targetId == null || !currentUserId.equals(ownerId)) {
                    return;
                }

                String serverMessageId = firstNonEmpty(message.messageId, message.clientMessageId);
                if (serverMessageId != null && messageDao.getMessageByServerId(serverMessageId) != null) {
                    return;
                }
                if (message.clientMessageId != null && messageDao.getMessageBySenderAndClientId(targetId, message.clientMessageId) != null) {
                    return;
                }

                long time = message.serverTimestamp > 0
                        ? message.serverTimestamp
                        : (message.timestamp > 0 ? message.timestamp : System.currentTimeMillis());
                String safeContent = safe(message.content);

                MessageEntity entity = new MessageEntity();
                entity.messageId = buildLocalMessageId(ownerId, serverMessageId != null ? serverMessageId : generateClientMessageId());
                entity.clientMessageId = message.clientMessageId;
                entity.serverMessageId = message.messageId;
                entity.conversationId = ownerId + "_" + targetId;
                entity.senderId = targetId;
                entity.receiverId = ownerId;
                entity.content = safeContent;
                entity.msgType = safeType(message.msgType);
                entity.timestamp = time;
                entity.chatType = safeChatType(message.chatType);
                entity.status = "received";
                entity.lastErrorCode = null;
                entity.lastErrorMessage = null;
                entity.isRead = false;
                entity.retryCount = 0;
                entity.serverTimestamp = message.serverTimestamp;

                messageDao.insertMessage(entity);
                upsertConversation(ownerId, targetId, safeContent, time, true, message.senderNickname, message.senderAvatar);
            } catch (Throwable t) {
                Log.e(TAG, "Failed to persist incoming websocket message", t);
            }
        });
    }

    private void handleSyncResponse(WebSocketMessage message) {
        if (message.messages == null || message.messages.isEmpty()) {
            return;
        }
        for (WebSocketMessage item : message.messages) {
            handleIncomingMessage(item);
        }
    }

    private void handleProfileUpdate(WebSocketMessage message) {
        appExecutors.diskIO().execute(() -> {
            try {
                String potentialTargetId = firstNonEmpty(message.fromUserId, message.from);
                if (potentialTargetId == null) {
                    return;
                }
                ConversationEntity conv = conversationDao.getConversationSync(currentUserId + "_" + potentialTargetId);
                if (conv != null) {
                    if (message.senderNickname != null) {
                        conv.targetName = message.senderNickname;
                    }
                    if (message.senderAvatar != null) {
                        conv.targetAvatarUrl = message.senderAvatar;
                    }
                    conversationDao.insertOrUpdate(conv);
                }
            } catch (Throwable t) {
                Log.e(TAG, "Failed to handle profile update message", t);
            }
        });
    }

    private void requestOfflineSync() {
        appExecutors.diskIO().execute(() -> {
            try {
                if (currentUserId.isEmpty()) {
                    return;
                }
                Long latest = messageDao.getLatestTimestampForUser(currentUserId);
                WebSocketMessage request = new WebSocketMessage();
                request.type = "sync_request";
                request.fromUserId = currentUserId;
                request.from = currentUserId;
                request.lastMessageTime = latest == null ? 0L : latest;
                webSocketManager.sendMessage(request);
            } catch (Throwable t) {
                Log.e(TAG, "Failed to request offline sync", t);
            }
        });
    }

    private void resendSendingMessages() {
        appExecutors.diskIO().execute(() -> {
            try {
                if (currentUserId.isEmpty()) {
                    return;
                }
                List<MessageEntity> pending = messageDao.getPendingMessages(currentUserId);
                if (pending == null) {
                    return;
                }
                for (MessageEntity message : pending) {
                    if ("sending".equals(message.status)) {
                        if (!sendOverSocket(message)) {
                            failMessage(message.messageId, "socket_disconnected", "当前连接不可用，消息未发送");
                            continue;
                        }
                        scheduleAckTimeout(message.messageId);
                    }
                }
            } catch (Throwable t) {
                Log.e(TAG, "Failed to resend pending messages", t);
            }
        });
    }

    private void scheduleAckTimeout(String messageId) {
        appExecutors.networkIO().execute(() -> {
            try {
                Thread.sleep(ACK_TIMEOUT_MS);
                appExecutors.diskIO().execute(() -> {
                    MessageEntity latest = messageDao.getMessageById(messageId);
                    if (latest != null && "sending".equals(latest.status)) {
                        failMessage(messageId, "timeout", "发送超时，请检查网络后重试");
                    }
                });
            } catch (InterruptedException ignored) {
            }
        });
    }

    private void failMessage(String messageId, String errorCode, String errorMessage) {
        messageDao.updateMessageFailure(messageId, "failed", errorCode, errorMessage);
        postFeedback(errorMessage);
    }

    private void postFeedback(String message) {
        if (safe(message).isEmpty()) {
            return;
        }
        appExecutors.mainThread().execute(() -> feedbackLiveData.setValue(new UiFeedback(message)));
    }

    private String mapServerErrorMessage(WebSocketMessage message) {
        switch (safe(message.errorCode)) {
            case "missing_receiver":
                return "发送失败：缺少接收方";
            case "receiver_not_found":
                return "发送失败：对方不存在";
            case "not_friends":
                return "发送失败：你们还不是好友";
            case "unauthorized":
                return "发送失败：登录状态已失效，请重新登录";
            case "unknown_type":
                return "发送失败：消息类型暂不支持";
            case "server_error":
                return safe(message.errorMessage, "发送失败：服务器处理异常");
            default:
                return safe(message.errorMessage, "发送失败，请稍后重试");
        }
    }

    private String conversationIdOf(String targetId) {
        return currentUserId + "_" + targetId;
    }

    private String buildLocalMessageId(String ownerId, String id) {
        return ownerId + "_" + id;
    }

    private String buildRemoteConversationId(String userA, String userB) {
        if (userA == null || userB == null) {
            return "single_" + userA + "_" + userB;
        }
        return userA.compareTo(userB) <= 0
                ? "single_" + userA + "_" + userB
                : "single_" + userB + "_" + userA;
    }

    private String generateClientMessageId() {
        return "local_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String safeType(String value) {
        return safe(value, "text");
    }

    private String safeChatType(String value) {
        return safe(value, "single");
    }

    private String firstNonEmpty(String first, String second) {
        if (!safe(first).isEmpty()) {
            return first;
        }
        if (!safe(second).isEmpty()) {
            return second;
        }
        return null;
    }

    private void upsertConversation(String ownerId,
                                    String targetId,
                                    String lastMessage,
                                    long time,
                                    boolean incrementUnread,
                                    String defaultName,
                                    String defaultAvatar) {
        String convId = ownerId + "_" + targetId;
        ConversationEntity conv = conversationDao.getConversationSync(convId);
        if (conv == null) {
            conv = new ConversationEntity();
            conv.conversationId = convId;
            conv.ownerId = ownerId;
            conv.targetId = targetId;
            String suffix = targetId != null && targetId.length() >= 4 ? targetId.substring(targetId.length() - 4) : targetId;
            conv.targetName = defaultName != null ? defaultName : "好友_" + suffix;
            conv.targetAvatarUrl = defaultAvatar != null ? defaultAvatar : "ic_avatar_1";
            conv.unreadCount = 0;
            conv.isPinned = false;
            conv.isMuted = false;
        }

        conv.ownerId = ownerId;
        conv.targetId = targetId;

        if (defaultName != null) {
            conv.targetName = defaultName;
        }
        if (defaultAvatar != null) {
            conv.targetAvatarUrl = defaultAvatar;
        }

        conv.lastMessage = lastMessage;
        conv.lastMessageTime = time;
        if (incrementUnread && !conv.isMuted) {
            conv.unreadCount += 1;
        }
        conversationDao.insertOrUpdate(conv);
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
}
