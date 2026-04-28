package com.example.koukou.ui.contacts;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.example.koukou.data.local.entity.UserEntity;
import com.example.koukou.data.repository.ContactRepository;

import java.util.List;

public class ContactsViewModel extends ViewModel {
    private final ContactRepository repository;
    private final LiveData<List<UserEntity>> contactsLiveData;

    public ContactsViewModel(ContactRepository repository, String account) {    
        this.repository = repository;
        contactsLiveData = repository.getContacts(account);
    }

    public LiveData<List<UserEntity>> getContacts() {
        return contactsLiveData;
    }

    public void addFriend(String myAccount, String friendAccount, ContactRepository.Callback callback) {
        repository.addFriend(myAccount, friendAccount, callback);
    }

    public void deleteFriend(String myUserId, String friendId, ContactRepository.Callback callback) {
        repository.deleteFriend(myUserId, friendId, callback);
    }
}