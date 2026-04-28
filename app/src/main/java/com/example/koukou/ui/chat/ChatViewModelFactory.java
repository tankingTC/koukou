package com.example.koukou.ui.chat;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.example.koukou.data.local.AppDatabase;
import com.example.koukou.data.repository.MessageRepository;
import com.example.koukou.network.websocket.WebSocketManager;
import com.example.koukou.utils.AppExecutors;
import com.example.koukou.utils.UserHelper;

public class ChatViewModelFactory implements ViewModelProvider.Factory {        
    private final MessageRepository repository;

    public ChatViewModelFactory(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        repository = MessageRepository.getInstance(
                db.messageDao(),
                db.conversationDao(),
                AppExecutors.getInstance(),
                WebSocketManager.getInstance()
        );
        
        String userId = UserHelper.getUserId(context);
        String nickname = UserHelper.getNickname(context);
        String avatar = UserHelper.getAvatar(context);
        
        repository.setCurrentUser(userId, nickname, avatar);
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {       
        if (modelClass.isAssignableFrom(ChatViewModel.class)) {
            return (T) new ChatViewModel(repository);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}