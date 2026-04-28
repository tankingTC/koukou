package com.example.koukou.ui.conversations;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.example.koukou.data.local.entity.ConversationEntity;
import com.example.koukou.data.repository.ConversationRepository;

import java.util.List;

public class ConversationsViewModel extends ViewModel {
    private final ConversationRepository repository;
    private final LiveData<List<ConversationEntity>> conversationsLiveData;
    private final String ownerId;

    public ConversationsViewModel(ConversationRepository repository, String ownerId) {
        this.repository = repository;
        this.ownerId = ownerId;
        conversationsLiveData = repository.getAllConversations(ownerId);
    }

    public LiveData<List<ConversationEntity>> getConversations() {
        return conversationsLiveData;
    }

    public void clearUnreadCount(String convId) {
        repository.clearUnreadCount(convId);
    }

    public void delete(String convId) {
        repository.deleteConversation(convId);
    }

    public void refreshConversations() {
        if (ownerId != null && !ownerId.isEmpty()) {
            repository.syncFromFriends(ownerId);
        }
    }
}