package com.example.koukou.data.repository;

import androidx.lifecycle.LiveData;

import com.example.koukou.data.local.dao.ConversationDao;
import com.example.koukou.data.local.dao.FriendDao;
import com.example.koukou.data.local.dao.MessageDao;
import com.example.koukou.data.local.entity.ConversationEntity;
import com.example.koukou.data.local.entity.MessageEntity;
import com.example.koukou.data.local.entity.UserEntity;
import com.example.koukou.utils.AppExecutors;

import java.util.List;

public class ConversationRepository {
    private static volatile ConversationRepository INSTANCE;
    private final ConversationDao conversationDao;
    private final FriendDao friendDao;
    private final MessageDao messageDao;
    private final AppExecutors appExecutors;

    private ConversationRepository(ConversationDao conversationDao, FriendDao friendDao, MessageDao messageDao, AppExecutors appExecutors) {
        this.conversationDao = conversationDao;
        this.friendDao = friendDao;
        this.messageDao = messageDao;
        this.appExecutors = appExecutors;
    }

    public static ConversationRepository getInstance(ConversationDao conversationDao, FriendDao friendDao, MessageDao messageDao, AppExecutors appExecutors) {
        if (INSTANCE == null) {
            synchronized (ConversationRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ConversationRepository(conversationDao, friendDao, messageDao, appExecutors);
                }
            }
        }
        return INSTANCE;
    }

    public LiveData<List<ConversationEntity>> getAllConversations(String ownerId) {
        return conversationDao.getAllConversations(ownerId);
    }

    public void syncFromFriends(String ownerId) {
        appExecutors.diskIO().execute(() -> {
            List<UserEntity> friends = friendDao.getFriendsByOwnerSync(ownerId);
            if (friends == null || friends.isEmpty()) {
                return;
            }

            for (UserEntity friend : friends) {
                if (friend == null || friend.userId == null || friend.userId.isEmpty()) {
                    continue;
                }

                String conversationId = ownerId + "_" + friend.userId;
                ConversationEntity conversation = conversationDao.getConversationSync(conversationId);
                if (conversation == null) {
                    conversation = new ConversationEntity();
                    conversation.conversationId = conversationId;
                    conversation.ownerId = ownerId;
                    conversation.targetId = friend.userId;
                    conversation.unreadCount = 0;
                    conversation.isPinned = false;
                }

                conversation.ownerId = ownerId;
                conversation.targetId = friend.userId;
                conversation.targetName = friend.nickname != null && !friend.nickname.isEmpty() ? friend.nickname : friend.userId;
                conversation.targetAvatarUrl = friend.avatarUrl;

                MessageEntity latestMessage = messageDao.getLatestMessageByConversation(conversationId);
                if (latestMessage != null) {
                    conversation.lastMessage = latestMessage.content;
                    conversation.lastMessageTime = latestMessage.timestamp;
                } else if (conversation.lastMessage == null || conversation.lastMessage.isEmpty()) {
                    conversation.lastMessage = "点击开始聊天";
                    conversation.lastMessageTime = 0L;
                }

                conversationDao.insertOrUpdate(conversation);
            }
        });
    }

    public void clearUnreadCount(String convId) {
        appExecutors.diskIO().execute(() -> {
            conversationDao.clearUnreadCount(convId);
        });
    }

    public void deleteConversation(String convId) {
        appExecutors.diskIO().execute(() -> {
            conversationDao.deleteConversation(convId);
        });
    }
}
