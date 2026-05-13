package com.example.koukou.data.repository;

import android.util.Log;

import androidx.lifecycle.LiveData;

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
    private final MessageDao messageDao;
    private final ConversationDao conversationDao;
    private final AppExecutors appExecutors;
    private final WebSocketManager webSocketManager;
    private String currentUserId = "";
    private String currentNickname = "";
    private String currentAvatarUrl = "";

    private MessageRepository(MessageDao messageDao, ConversationDao conversationDao, AppExecutors appExecutors, WebSocketManager webSocketManager) {
        this.messageDao = messageDao;
        this.conversationDao = conversationDao;
        this.appExecutors = appExecutors;
        this.webSocketManager = webSocketManager;
        this.webSocketManager.addListener(this);
    }

    public static MessageRepository getInstance(MessageDao messageDao, ConversationDao conversationDao, AppExecutors appExecutors, WebSocketManager webSocketManager) {
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
        this.currentUserId = currentUserId == null ? "" : currentUserId;
        this.currentNickname = currentNickname == null ? "" : currentNickname;
        this.currentAvatarUrl = currentAvatarUrl == null ? "" : currentAvatarUrl;
    }

    private String getConvId(String targetId) {
        return currentUserId + "_" + targetId;
    }

    public LiveData<List<MessageEntity>> getMessages(String targetId) {
        return messageDao.getMessagesByConversation(getConvId(targetId));
    }

    public LiveData<List<ConversationEntity>> getConversations() {
        return conversationDao.getAllConversations(currentUserId);
    }

    public void sendMessage(String targetId, String content, String msgType, String localPath, String chatType) {
        appExecutors.diskIO().execute(() -> {
            String displayContent = content == null ? "" : content.trim();
            if (displayContent.isEmpty() || currentUserId.isEmpty()) {
                return;
            }

            long now = System.currentTimeMillis();
            String clientMessageId = generateClientMessageId();
            MessageEntity entity = new MessageEntity();
            entity.messageId = buildLocalMessageId(currentUserId, clientMessageId);
            entity.clientMessageId = clientMessageId;
            entity.conversationId = getConvId(targetId);
            entity.senderId = currentUserId;
            entity.receiverId = targetId;
            entity.content = displayContent;
            entity.msgType = safeType(msgType);
            entity.localPath = localPath;
            entity.timestamp = now;
            entity.chatType = safeChatType(chatType);
            entity.status = "sending";
            entity.isRead = false;
            entity.retryCount = 0;
            entity.serverTimestamp = 0L;

            messageDao.insertMessage(entity);
            upsertConversation(currentUserId, targetId, displayContent, now, false, null, null);

            if (!sendOverSocket(entity)) {
                messageDao.updateMessageStatus(entity.messageId, "failed");
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
            if (original.clientMessageId == null || original.clientMessageId.trim().isEmpty()) {
                original.clientMessageId = generateClientMessageId();
            }
            long now = System.currentTimeMillis();
            original.status = "sending";
            original.timestamp = now;
            original.retryCount += 1;
            messageDao.insertMessage(original);
            upsertConversation(original.senderId, original.receiverId, original.content, now, false, null, null);

            if (!sendOverSocket(original)) {
                messageDao.updateMessageStatus(original.messageId, "failed");
                return;
            }
            scheduleAckTimeout(original.messageId);
        });
    }

    @Override
    public void onMessageReceived(WebSocketMessage message) {
        if (message == null || message.type == null || message.type.trim().isEmpty()) {
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
                if (message.clientMessageId == null || message.clientMessageId.trim().isEmpty()) {
                    return;
                }
                String status = message.status == null || message.status.trim().isEmpty() ? "sent" : message.status;
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
                if (message.clientMessageId == null || message.clientMessageId.trim().isEmpty()) {
                    return;
                }
                MessageEntity local = messageDao.getMessageByClientId(message.clientMessageId);
                if (local != null && "sending".equals(local.status)) {
                    messageDao.updateMessageStatus(local.messageId, "failed");
                }
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
                String safeContent = message.content == null ? "" : message.content;

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
                if (currentUserId == null || currentUserId.trim().isEmpty()) {
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
                if (currentUserId == null || currentUserId.trim().isEmpty()) {
                    return;
                }
                List<MessageEntity> pending = messageDao.getPendingMessages(currentUserId);
                if (pending == null) {
                    return;
                }
                for (MessageEntity message : pending) {
                    if ("sending".equals(message.status)) {
                        sendOverSocket(message);
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
                        messageDao.updateMessageStatus(messageId, "failed");
                    }
                });
            } catch (InterruptedException ignored) {
            }
        });
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
        return value == null || value.trim().isEmpty() ? "text" : value;
    }

    private String safeChatType(String value) {
        return value == null || value.trim().isEmpty() ? "single" : value;
    }

    private String firstNonEmpty(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first;
        }
        if (second != null && !second.trim().isEmpty()) {
            return second;
        }
        return null;
    }

    private void upsertConversation(String ownerId, String targetId, String lastMessage, long time, boolean incrementUnread, String defaultName, String defaultAvatar) {
        String convId = ownerId + "_" + targetId;
        ConversationEntity conv = conversationDao.getConversationSync(convId);
        if (conv == null) {
            conv = new ConversationEntity();
            conv.conversationId = convId;
            conv.ownerId = ownerId;
            conv.targetId = targetId;
            String suffix = targetId != null && targetId.length() >= 4 ? targetId.substring(targetId.length() - 4) : targetId;
            conv.targetName = defaultName != null ? defaultName : "Friend_" + suffix;
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
}
