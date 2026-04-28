package com.example.koukou.data.repository;

import android.content.Context;
import android.os.Build;

import androidx.datastore.preferences.core.MutablePreferences;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.datastore.preferences.rxjava3.RxPreferenceDataStoreBuilder;
import androidx.datastore.rxjava3.RxDataStore;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.bumptech.glide.Glide;
import com.example.koukou.data.local.AppDatabase;
import com.example.koukou.data.local.entity.UserEntity;
import com.example.koukou.ui.settings.model.SettingsState;
import com.example.koukou.ui.settings.model.SettingsStorageStats;
import com.example.koukou.utils.AppExecutors;
import com.example.koukou.utils.UserHelper;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class SettingsRepository {
    public interface ResultCallback {
        void onSuccess(String message);
        void onError(String message);
    }

    public interface StorageCallback {
        void onLoaded(SettingsStorageStats stats);
    }

    private static final String STORE_NAME = "koukou_settings";
    private static volatile SettingsRepository instance;

    private static final Preferences.Key<Boolean> KEY_NOTIFICATIONS = PreferencesKeys.booleanKey("notifications_enabled");
    private static final Preferences.Key<Boolean> KEY_SOUND = PreferencesKeys.booleanKey("sound_enabled");
    private static final Preferences.Key<Boolean> KEY_VIBRATION = PreferencesKeys.booleanKey("vibration_enabled");
    private static final Preferences.Key<Boolean> KEY_PREVIEW = PreferencesKeys.booleanKey("message_preview_enabled");
    private static final Preferences.Key<Boolean> KEY_DND = PreferencesKeys.booleanKey("dnd_enabled");
    private static final Preferences.Key<Boolean> KEY_PASSCODE = PreferencesKeys.booleanKey("local_passcode_enabled");
    private static final Preferences.Key<Boolean> KEY_ALLOW_SEARCH = PreferencesKeys.booleanKey("allow_search_by_id");
    private static final Preferences.Key<Boolean> KEY_EFFECTS = PreferencesKeys.booleanKey("immersive_effects_enabled");
    private static final Preferences.Key<String> KEY_VERIFY_MODE = PreferencesKeys.stringKey("friend_verify_mode");
    private static final Preferences.Key<String> KEY_THEME = PreferencesKeys.stringKey("theme_mode");
    private static final Preferences.Key<String> KEY_BACKGROUND = PreferencesKeys.stringKey("chat_background");
    private static final Preferences.Key<String> KEY_FONT = PreferencesKeys.stringKey("font_size");
    private static final Preferences.Key<String> KEY_CACHED_NICKNAME = PreferencesKeys.stringKey("cached_nickname");
    private static final Preferences.Key<String> KEY_CACHED_SIGNATURE = PreferencesKeys.stringKey("cached_signature");
    private static final Preferences.Key<String> KEY_LOCAL_PASSCODE_VALUE = PreferencesKeys.stringKey("local_passcode_value");
    private static final Preferences.Key<Set<String>> KEY_BLACKLIST = PreferencesKeys.stringSetKey("blacklist_ids");

    private final Context appContext;
    private final AppExecutors executors;
    private final AppDatabase database;
    private final UserRepository userRepository;
    private final RxDataStore<Preferences> dataStore;
    private final MutableLiveData<SettingsState> settingsLiveData = new MutableLiveData<>(new SettingsState());

    public static SettingsRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (SettingsRepository.class) {
                if (instance == null) {
                    instance = new SettingsRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private SettingsRepository(Context context) {
        appContext = context;
        executors = AppExecutors.getInstance();
        database = AppDatabase.getInstance(context);
        userRepository = new UserRepository(database.userDao(), executors);
        dataStore = new RxPreferenceDataStoreBuilder(appContext, STORE_NAME).build();
        subscribeSettings();
    }

    public LiveData<SettingsState> getSettingsLiveData() {
        return settingsLiveData;
    }

    public void setNotificationsEnabled(boolean enabled) {
        updateBoolean(KEY_NOTIFICATIONS, enabled);
    }

    public void setSoundEnabled(boolean enabled) {
        updateBoolean(KEY_SOUND, enabled);
    }

    public void setVibrationEnabled(boolean enabled) {
        updateBoolean(KEY_VIBRATION, enabled);
    }

    public void setMessagePreviewEnabled(boolean enabled) {
        updateBoolean(KEY_PREVIEW, enabled);
    }

    public void setDoNotDisturbEnabled(boolean enabled) {
        updateBoolean(KEY_DND, enabled);
    }

    public void setLocalPasscodeEnabled(boolean enabled) {
        updateBoolean(KEY_PASSCODE, enabled);
    }

    public void saveLocalPasscode(String passcode, ResultCallback callback) {
        String safePasscode = safeString(passcode, "");
        if (safePasscode.length() < 4) {
            postError(callback, "本地密码至少需要 4 位");
            return;
        }
        updateData(preferences -> {
            preferences.set(KEY_LOCAL_PASSCODE_VALUE, safePasscode);
            preferences.set(KEY_PASSCODE, true);
        }, () -> callback.onSuccess("本地密码保护已开启"));
    }

    public void clearLocalPasscode(String passcode, ResultCallback callback) {
        SettingsState current = settingsLiveData.getValue();
        String storedPasscode = current == null ? "" : safeString(current.localPasscode, "");
        if (storedPasscode.isEmpty()) {
            updateData(preferences -> {
                preferences.set(KEY_LOCAL_PASSCODE_VALUE, "");
                preferences.set(KEY_PASSCODE, false);
            }, () -> callback.onSuccess("本地密码保护已关闭"));
            return;
        }
        if (!storedPasscode.equals(safeString(passcode, ""))) {
            postError(callback, "本地密码不正确");
            return;
        }
        updateData(preferences -> {
            preferences.set(KEY_LOCAL_PASSCODE_VALUE, "");
            preferences.set(KEY_PASSCODE, false);
        }, () -> callback.onSuccess("本地密码保护已关闭"));
    }

    public void setAllowSearchById(boolean enabled) {
        updateBoolean(KEY_ALLOW_SEARCH, enabled);
    }

    public void setImmersiveEffectsEnabled(boolean enabled) {
        updateBoolean(KEY_EFFECTS, enabled);
    }

    public void setFriendVerificationMode(String mode) {
        updateString(KEY_VERIFY_MODE, mode);
    }

    public void setThemeMode(String mode) {
        updateString(KEY_THEME, mode);
    }

    public void setChatBackground(String background) {
        updateString(KEY_BACKGROUND, background);
    }

    public void setFontSize(String fontSize) {
        updateString(KEY_FONT, fontSize);
    }

    public void syncProfileSnapshot(String nickname, String signature) {
        updateData(preferences -> {
            preferences.set(KEY_CACHED_NICKNAME, safeString(nickname, "koukou_user"));
            preferences.set(KEY_CACHED_SIGNATURE, safeString(signature, "这个人很神秘，暂未留下签名"));
        }, null);
    }

    public void addToBlacklist(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return;
        }
        updateData(preferences -> {
            Set<String> current = preferences.get(KEY_BLACKLIST);
            Set<String> updated = new LinkedHashSet<>(current == null ? Collections.emptySet() : current);
            updated.add(userId.trim());
            preferences.set(KEY_BLACKLIST, updated);
        }, null);
    }

    public void removeFromBlacklist(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return;
        }
        updateData(preferences -> {
            Set<String> current = preferences.get(KEY_BLACKLIST);
            Set<String> updated = new LinkedHashSet<>(current == null ? Collections.emptySet() : current);
            updated.remove(userId.trim());
            preferences.set(KEY_BLACKLIST, updated);
        }, null);
    }

    public void updatePassword(String userId, String oldPassword, String newPassword, ResultCallback callback) {
        executors.diskIO().execute(() -> {
            UserEntity user = database.userDao().getUser(userId);
            if (user == null) {
                postError(callback, "当前账号不存在");
                return;
            }
            if (oldPassword == null || !oldPassword.equals(user.password)) {
                postError(callback, "原密码不正确");
                return;
            }
            String nextPassword = newPassword == null ? "" : newPassword.trim();
            if (nextPassword.length() < 6) {
                postError(callback, "新密码至少需要 6 位");
                return;
            }

            user.password = nextPassword;
            database.userDao().insertUser(user);
            executors.mainThread().execute(() -> {
                UserHelper.saveUser(appContext, user.account, nextPassword, user.userId);
                UserHelper.saveLoginHistory(appContext, user.account, nextPassword, user.nickname, user.avatarUrl);
                callback.onSuccess("密码已更新");
            });
        });
    }

    public void updateProfile(String userId, String nickname, String avatarUrl, String signature, UserRepository.Callback callback) {
        userRepository.updateProfile(appContext, userId, nickname, avatarUrl, signature, new UserRepository.Callback() {
            @Override
            public void onSuccess(UserEntity user) {
                syncProfileSnapshot(user.nickname, user.signature);
                if (callback != null) {
                    callback.onSuccess(user);
                }
            }

            @Override
            public void onError(String msg) {
                if (callback != null) {
                    callback.onError(msg);
                }
            }
        });
    }

    public void loadStorageStats(StorageCallback callback) {
        executors.diskIO().execute(() -> {
            SettingsStorageStats stats = new SettingsStorageStats();
            File dbFile = appContext.getDatabasePath("koukou_database");
            stats.databaseBytes = safeLength(dbFile);
            stats.cacheBytes = getDirectorySize(appContext.getCacheDir()) + getDirectorySize(appContext.getExternalCacheDir());
            stats.totalChatBytes = getDirectorySize(appContext.getFilesDir());
            executors.mainThread().execute(() -> callback.onLoaded(stats));
        });
    }

    public void clearImageCache(ResultCallback callback) {
        executors.diskIO().execute(() -> {
            try {
                Glide.get(appContext).clearDiskCache();
                deleteChildren(appContext.getCacheDir());
                File externalCache = appContext.getExternalCacheDir();
                if (externalCache != null) {
                    deleteChildren(externalCache);
                }
                executors.mainThread().execute(() -> {
                    Glide.get(appContext).clearMemory();
                    callback.onSuccess("缓存已清理");
                });
            } catch (Exception e) {
                postError(callback, "清理缓存失败: " + e.getMessage());
            }
        });
    }

    public void clearChatHistory(String userId, ResultCallback callback) {
        executors.diskIO().execute(() -> {
            try {
                database.messageDao().deleteAllByUser(userId);
                database.conversationDao().deleteAllByOwner(userId);
                executors.mainThread().execute(() -> callback.onSuccess("聊天记录已清空"));
            } catch (Exception e) {
                postError(callback, "清空聊天记录失败: " + e.getMessage());
            }
        });
    }

    public String getCurrentDeviceName() {
        return Build.MANUFACTURER + " " + Build.MODEL;
    }

    public String getCurrentSystemVersion() {
        return "Android " + Build.VERSION.RELEASE;
    }

    private void subscribeSettings() {
        dataStore.data()
                .subscribeOn(Schedulers.io())
                .subscribe(
                        preferences -> settingsLiveData.postValue(map(preferences)),
                        throwable -> settingsLiveData.postValue(new SettingsState())
                );
    }

    private SettingsState map(Preferences preferences) {
        SettingsState state = new SettingsState();
        state.notificationsEnabled = getBoolean(preferences, KEY_NOTIFICATIONS, true);
        state.soundEnabled = getBoolean(preferences, KEY_SOUND, true);
        state.vibrationEnabled = getBoolean(preferences, KEY_VIBRATION, true);
        state.messagePreviewEnabled = getBoolean(preferences, KEY_PREVIEW, true);
        state.doNotDisturbEnabled = getBoolean(preferences, KEY_DND, false);
        state.localPasscodeEnabled = getBoolean(preferences, KEY_PASSCODE, false);
        state.allowSearchById = getBoolean(preferences, KEY_ALLOW_SEARCH, true);
        state.immersiveEffectsEnabled = getBoolean(preferences, KEY_EFFECTS, true);
        state.friendVerificationMode = getString(preferences, KEY_VERIFY_MODE, "need_verify");
        state.themeMode = getString(preferences, KEY_THEME, "system");
        state.chatBackground = getString(preferences, KEY_BACKGROUND, "butterfly");
        state.fontSize = getString(preferences, KEY_FONT, "medium");
        state.cachedNickname = getString(preferences, KEY_CACHED_NICKNAME, "koukou_user");
        state.cachedSignature = getString(preferences, KEY_CACHED_SIGNATURE, "这个人很神秘，暂未留下签名");
        state.localPasscode = getString(preferences, KEY_LOCAL_PASSCODE_VALUE, "");
        Set<String> blacklist = preferences.get(KEY_BLACKLIST);
        state.blacklistUserIds = blacklist == null ? new LinkedHashSet<>() : new LinkedHashSet<>(blacklist);
        return state;
    }

    private boolean getBoolean(Preferences preferences, Preferences.Key<Boolean> key, boolean defaultValue) {
        Boolean value = preferences.get(key);
        return value == null ? defaultValue : value;
    }

    private String getString(Preferences preferences, Preferences.Key<String> key, String defaultValue) {
        String value = preferences.get(key);
        return value == null || value.trim().isEmpty() ? defaultValue : value;
    }

    private void updateBoolean(Preferences.Key<Boolean> key, boolean value) {
        updateData(preferences -> preferences.set(key, value), null);
    }

    private void updateString(Preferences.Key<String> key, String value) {
        updateData(preferences -> preferences.set(key, safeString(value, "")), null);
    }

    private void updateData(MutablePreferenceAction action, Runnable onSuccess) {
        dataStore.updateDataAsync(current -> Single.fromCallable(() -> {
            MutablePreferences mutablePreferences = current.toMutablePreferences();
            action.apply(mutablePreferences);
            return mutablePreferences;
        })).subscribeOn(Schedulers.io()).subscribe(
                preferences -> {
                    if (onSuccess != null) {
                        executors.mainThread().execute(onSuccess);
                    }
                },
                throwable -> {
                }
        );
    }

    private long safeLength(File file) {
        return file != null && file.exists() ? file.length() : 0L;
    }

    private long getDirectorySize(File dir) {
        if (dir == null || !dir.exists()) {
            return 0L;
        }
        if (dir.isFile()) {
            return dir.length();
        }
        long total = 0L;
        File[] files = dir.listFiles();
        if (files == null) {
            return 0L;
        }
        for (File file : files) {
            total += getDirectorySize(file);
        }
        return total;
    }

    private void deleteChildren(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                deleteChildren(child);
            }
            child.delete();
        }
    }

    private String safeString(String value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? defaultValue : trimmed;
    }

    private void postError(ResultCallback callback, String message) {
        executors.mainThread().execute(() -> callback.onError(message));
    }

    private interface MutablePreferenceAction {
        void apply(MutablePreferences preferences);
    }
}
