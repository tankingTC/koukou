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

    public ContactsViewModel(ContactRepository repository, String currentUserId) {
        this.repository = repository;
        this.currentUserId = currentUserId;
        contactsLiveData = repository.getContacts(currentUserId);
        incomingRequestsLiveData = repository.getIncomingRequests(currentUserId);
        pendingIncomingCountLiveData = repository.getPendingIncomingCount(currentUserId);
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

    public void refreshRemoteState(ContactRepository.Callback callback) {
        repository.refreshRemoteState(currentUserId, callback);
    }

    public void searchUser(String identifier, ContactRepository.UserLookupCallback callback) {
        repository.searchUser(identifier, callback);
    }

    public void addFriend(String friendAccount, ContactRepository.Callback callback) {
        repository.addFriend(currentUserId, friendAccount, callback);
    }

    public void acceptFriendRequest(String requestId, ContactRepository.Callback callback) {
        repository.acceptFriendRequest(currentUserId, requestId, callback);
    }

    public void rejectFriendRequest(String requestId, ContactRepository.Callback callback) {
        repository.rejectFriendRequest(currentUserId, requestId, callback);
    }

    public void deleteFriend(String friendId, ContactRepository.Callback callback) {
        repository.deleteFriend(currentUserId, friendId, callback);
    }
}
