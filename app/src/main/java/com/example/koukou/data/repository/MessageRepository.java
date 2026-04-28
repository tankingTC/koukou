package com.example.koukou.data.repository;

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
        this.currentUserId = currentUserId;
        this.currentNickname = currentNickname;
        this.currentAvatarUrl = currentAvatarUrl;
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
            String serverMessageId = UUID.randomUUID().toString();
            MessageEntity entity = new MessageEntity();
            entity.messageId = buildLocalMessageId(currentUserId, serverMessageId);
            entity.conversationId = getConvId(targetId);
            entity.senderId = currentUserId;
            entity.receiverId = targetId;
            entity.content = content; 
            entity.msgType = msgType; 
            entity.localPath = localPath;
            entity.timestamp = System.currentTimeMillis();
            entity.chatType = chatType;
            entity.status = "sending";

            messageDao.insertMessage(entity);
            upsertConversation(currentUserId, targetId, content, entity.timestamp, false, null, null);

            // Local mirror for account switching on one device: receiver account can see this message history.
            MessageEntity mirror = new MessageEntity();
            mirror.messageId = buildLocalMessageId(targetId, serverMessageId);
            mirror.conversationId = targetId + "_" + currentUserId;
            mirror.senderId = currentUserId;
            mirror.receiverId = targetId;
            mirror.content = content; 
            mirror.msgType = msgType; 
            mirror.localPath = localPath;
            mirror.timestamp = entity.timestamp;
            mirror.chatType = chatType;
            mirror.status = "received";
            messageDao.insertMessage(mirror);
            upsertConversation(targetId, currentUserId, content, entity.timestamp, true, currentNickname, currentAvatarUrl);

            WebSocketMessage request = new WebSocketMessage();
            request.type = "message";
            request.from = currentUserId;
            request.to = targetId;
            request.content = content;
            request.timestamp = entity.timestamp;
            request.messageId = serverMessageId;
            request.chatType = chatType;
            request.senderNickname = currentNickname;
            request.senderAvatar = currentAvatarUrl;

            webSocketManager.sendMessage(request);

            appExecutors.networkIO().execute(() -> {
                try {
                    Thread.sleep(500);
                    appExecutors.diskIO().execute(() ->
                        messageDao.updateMessageStatus(entity.messageId, "sent")
                    );
                } catch (InterruptedException ignored) {}
            });
        });
    }

    @Override
    public void onMessageReceived(WebSocketMessage message) {
        if ("profile_update".equals(message.type)) {
            // Received a profile update, update our local conversation record immediately.
            appExecutors.diskIO().execute(() -> {
                String potentialTargetId = message.from;
                String ownerId = currentUserId;
                ConversationEntity conv = conversationDao.getConversationSync(ownerId + "_" + potentialTargetId);
                if (conv != null) {
                    if (message.senderNickname != null) conv.targetName = message.senderNickname;
                    if (message.senderAvatar != null) conv.targetAvatarUrl = message.senderAvatar;
                    conversationDao.insertOrUpdate(conv);
                }
            });
        } else if ("message".equals(message.type)) {
            appExecutors.diskIO().execute(() -> {
                // If this is a message FOR ME, currentUserId should match 'message.to'
                // but since it's a global listener, we should use 'message.to' as owner
                String ownerId = message.to;
                String targetId = message.from;

                // Only process it if we are currently logged into that user    
                if (currentUserId.equals(ownerId)) {
                    String serverMessageId = message.messageId != null && !message.messageId.isEmpty()
                            ? message.messageId
                            : UUID.randomUUID().toString();

                    MessageEntity entity = new MessageEntity();
                    entity.messageId = buildLocalMessageId(ownerId, serverMessageId);
                    entity.conversationId = ownerId + "_" + targetId;
                    entity.senderId = targetId;
                    entity.receiverId = ownerId;
                    entity.content = message.content;
                    entity.timestamp = message.timestamp;
                    entity.chatType = message.chatType;
                    entity.status = "received";

                    messageDao.insertMessage(entity);
                    upsertConversation(ownerId, targetId, message.content, message.timestamp, true, message.senderNickname, message.senderAvatar);
                }
            });
        }
    }

    private String buildLocalMessageId(String ownerId, String serverMessageId) {
        return ownerId + "_" + serverMessageId;
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
        }

        conv.ownerId = ownerId;
        conv.targetId = targetId;

        if (defaultName != null) conv.targetName = defaultName;
        if (defaultAvatar != null) conv.targetAvatarUrl = defaultAvatar;        

        conv.lastMessage = lastMessage;
        conv.lastMessageTime = time;
        if (incrementUnread) {
            conv.unreadCount += 1;
        }
        conversationDao.insertOrUpdate(conv);
    }

    @Override
    public void onConnect(boolean isSuccess) {}

    @Override
    public void onDisconnect() {}
}