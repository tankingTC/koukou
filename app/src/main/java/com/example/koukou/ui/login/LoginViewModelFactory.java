package com.example.koukou.ui.login;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.koukou.data.local.AppDatabase;
import com.example.koukou.data.local.dao.UserDao;
import com.example.koukou.data.repository.UserRepository;
import com.example.koukou.utils.AppExecutors;

public class LoginViewModelFactory implements ViewModelProvider.Factory {       

    private final UserRepository userRepository;

    public LoginViewModelFactory(android.content.Context context) {
        UserDao userDao = AppDatabase.getInstance(context).userDao();
        userRepository = new UserRepository(userDao, AppExecutors.getInstance());
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {       
        if (modelClass.isAssignableFrom(LoginViewModel.class)) {
            return (T) new LoginViewModel(userRepository);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}