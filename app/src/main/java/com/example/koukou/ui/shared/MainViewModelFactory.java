package com.example.koukou.ui.shared;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.koukou.data.local.AppDatabase;
import com.example.koukou.data.repository.ContactRepository;
import com.example.koukou.data.repository.ConversationRepository;
import com.example.koukou.ui.contacts.ContactsViewModel;
import com.example.koukou.ui.conversations.ConversationsViewModel;
import com.example.koukou.utils.AppExecutors;
import com.example.koukou.utils.UserHelper;

public class MainViewModelFactory implements ViewModelProvider.Factory {        
    private final ConversationRepository conversationRepo;
    private final ContactRepository contactRepo;
    private final String currentUserId;

    public MainViewModelFactory(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        AppExecutors executors = AppExecutors.getInstance();
        currentUserId = UserHelper.getUserId(context);
        conversationRepo = ConversationRepository.getInstance(db.conversationDao(), db.friendDao(), db.messageDao(), executors);
        contactRepo = ContactRepository.getInstance(db.friendDao(), db.userDao(), executors);
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {       
        if (modelClass.isAssignableFrom(ConversationsViewModel.class)) {        
            return (T) new ConversationsViewModel(conversationRepo, currentUserId);
        } else if (modelClass.isAssignableFrom(ContactsViewModel.class)) {      
            return (T) new ContactsViewModel(contactRepo, currentUserId);       
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}