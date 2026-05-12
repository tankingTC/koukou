package com.example.koukou.ui.contacts;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.example.koukou.data.local.entity.FriendRequestEntity;
import com.example.koukou.data.local.entity.UserEntity;
import com.example.koukou.data.repository.ContactRepository;

import java.util.List;

public class ContactsViewModel extends ViewModel {
    private final ContactRepository repository;
    private final LiveData<List<UserEntity>> contactsLiveData;
    private final LiveData<List<FriendRequestEntity>> incomingRequestsLiveData;
    private final LiveData<Integer> pendingIncomingCountLiveData;
    private final String currentUserId;

    public ContactsViewModel(ContactRepository repository, String account) {    
        this.repository = repository;
        this.currentUserId = account;
        contactsLiveData = repository.getContacts(account);
        incomingRequestsLiveData = repository.getIncomingRequests(account);
        pendingIncomingCountLiveData = repository.getPendingIncomingCount(account);
    }

    public LiveData<List<UserEntity>> getContacts() {
        return contactsLiveData;
    }

    public LiveData<List<FriendRequestEntity>> getIncomingRequests() {
        return incomingRequestsLiveData;
    }

    public LiveData<Integer> getPendingIncomingCount() {
        return pendingIncomingCountLiveData;
    }

    public void addFriend(String myAccount, String friendAccount, ContactRepository.Callback callback) {
        repository.addFriend(myAccount, friendAccount, callback);
    }

    public void acceptFriendRequest(String requestId, ContactRepository.Callback callback) {
        repository.acceptFriendRequest(currentUserId, requestId, callback);
    }

    public void rejectFriendRequest(String requestId, ContactRepository.Callback callback) {
        repository.rejectFriendRequest(currentUserId, requestId, callback);
    }

    public void deleteFriend(String myUserId, String friendId, ContactRepository.Callback callback) {
        repository.deleteFriend(myUserId, friendId, callback);
    }
}