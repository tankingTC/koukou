package com.example.koukou.ui.settings;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.koukou.data.repository.SettingsRepository;
import com.example.koukou.utils.UserHelper;

public class SettingsViewModelFactory implements ViewModelProvider.Factory {
    private final SettingsRepository repository;
    private final String page;
    private final String currentUserId;
    private final String appVersionName;

    public SettingsViewModelFactory(Context context, String page) {
        this.repository = SettingsRepository.getInstance(context);
        this.page = page;
        this.currentUserId = UserHelper.getUserId(context);
        String versionName = "1.0";
        try {
            versionName = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0)
                    .versionName;
        } catch (Exception ignored) {
        }
        this.appVersionName = versionName;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(SettingsViewModel.class)) {
            return (T) new SettingsViewModel(repository, page, currentUserId, appVersionName);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getSimpleName());
    }
}
