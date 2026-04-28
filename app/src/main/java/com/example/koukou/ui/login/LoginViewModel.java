package com.example.koukou.ui.login;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.koukou.data.local.entity.UserEntity;
import com.example.koukou.data.repository.UserRepository;

public class LoginViewModel extends ViewModel {
    private final UserRepository userRepository;

    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<UserEntity> loginSuccess = new MutableLiveData<>();
    private final MutableLiveData<UserEntity> registerSuccess = new MutableLiveData<>();
    private final MutableLiveData<String> generatedKoukouId = new MutableLiveData<>();

    public LoginViewModel(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<UserEntity> getLoginSuccess() {
        return loginSuccess;
    }

    public LiveData<UserEntity> getRegisterSuccess() {
        return registerSuccess;
    }

    public LiveData<String> getGeneratedKoukouId() {
        return generatedKoukouId;
    }

    public void login(String account, String password) {
        if (account == null || account.trim().isEmpty() || password == null || password.isEmpty()) {
            error.setValue("扣扣号或密码不能为空");
            return;
        }

        loading.setValue(true);
        userRepository.login(account, password, new UserRepository.Callback() {
            @Override
            public void onSuccess(UserEntity user) {
                loading.setValue(false);
                loginSuccess.setValue(user);
            }

            @Override
            public void onError(String errorMsg) {
                loading.setValue(false);
                error.setValue(errorMsg);
            }
        });
    }

    public void register(String nickname, String koukouId, String password) {
        if (nickname == null || nickname.trim().isEmpty() || password == null || password.isEmpty()) {
            error.setValue("昵称或密码不能为空");
            return;
        }

        loading.setValue(true);
        userRepository.register(nickname, koukouId, password, new UserRepository.Callback() {
            @Override
            public void onSuccess(UserEntity user) {
                loading.setValue(false);
                registerSuccess.setValue(user);
            }

            @Override
            public void onError(String errorMsg) {
                loading.setValue(false);
                error.setValue(errorMsg);
            }
        });
    }

    public void refreshKoukouId() {
        userRepository.generateAvailableKoukouId(new UserRepository.IdCallback() {
            @Override
            public void onSuccess(String koukouId) {
                generatedKoukouId.setValue(koukouId);
            }

            @Override
            public void onError(String msg) {
                error.setValue(msg);
            }
        });
    }
}
