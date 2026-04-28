package com.example.koukou.ui.chat;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.example.koukou.data.local.entity.MessageEntity;
import com.example.koukou.data.repository.MessageRepository;

import java.util.List;

public class ChatViewModel extends ViewModel {
    private final MessageRepository repository;
    private final MutableLiveData<String> currentTargetId = new MutableLiveData<>();
    private final LiveData<List<MessageEntity>> messages;

    public ChatViewModel(MessageRepository repository) {
        this.repository = repository;

        messages = Transformations.switchMap(currentTargetId, targetId -> 
                repository.getMessages(targetId)
        );
    }

    public void setTargetId(String targetId) {
        if (!targetId.equals(currentTargetId.getValue())) {
            currentTargetId.setValue(targetId);
        }
    }

    public LiveData<List<MessageEntity>> getMessages() {
        return messages;
    }

    public void sendMessage(String content) {
        sendMessage(content, "text", null);
    }

    public void sendMessage(String content, String msgType, String localPath) {
        String targetId = currentTargetId.getValue();
        if (targetId != null && !content.trim().isEmpty()) {
            repository.sendMessage(targetId, content, msgType, localPath, "single");
        }
    }
}
