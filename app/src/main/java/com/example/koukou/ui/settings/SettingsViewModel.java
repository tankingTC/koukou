package com.example.koukou.ui.settings;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.koukou.data.repository.SettingsRepository;
import com.example.koukou.ui.settings.model.SettingsItem;
import com.example.koukou.ui.settings.model.SettingsPage;
import com.example.koukou.ui.settings.model.SettingsState;
import com.example.koukou.ui.settings.model.SettingsStorageStats;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SettingsViewModel extends ViewModel {
    public static final String KEY_ACCOUNT_SECURITY = "account_security";
    public static final String KEY_PRIVACY = "privacy";
    public static final String KEY_NOTIFICATIONS = "notifications";
    public static final String KEY_APPEARANCE = "appearance";
    public static final String KEY_GENERAL_STORAGE = "general_storage";
    public static final String KEY_ABOUT = "about";
    public static final String KEY_LOGOUT = "logout";

    public static final String KEY_CHANGE_PASSWORD = "change_password";
    public static final String KEY_LOCAL_PASSCODE = "local_passcode";
    public static final String KEY_DEVICE_MANAGEMENT = "device_management";
    public static final String KEY_CURRENT_ACCOUNT = "current_account";
    public static final String KEY_CURRENT_DEVICE = "current_device";
    public static final String KEY_SYSTEM_VERSION = "system_version";

    public static final String KEY_ALLOW_SEARCH = "allow_search";
    public static final String KEY_VERIFY_MODE = "verify_mode";
    public static final String KEY_BLACKLIST = "blacklist";
    public static final String KEY_ADD_BLACKLIST = "add_blacklist";

    public static final String KEY_SWITCH_NOTIFICATIONS = "switch_notifications";
    public static final String KEY_SWITCH_SOUND = "switch_sound";
    public static final String KEY_SWITCH_VIBRATION = "switch_vibration";
    public static final String KEY_SWITCH_PREVIEW = "switch_preview";
    public static final String KEY_SWITCH_DND = "switch_dnd";

    public static final String KEY_THEME_MODE = "theme_mode";
    public static final String KEY_CHAT_BACKGROUND = "chat_background";
    public static final String KEY_FONT_SIZE = "font_size";
    public static final String KEY_EFFECTS = "effects";

    public static final String KEY_STORAGE_USAGE = "storage_usage";
    public static final String KEY_STORAGE_DB = "storage_db";
    public static final String KEY_STORAGE_CACHE = "storage_cache";
    public static final String KEY_STORAGE_FILES = "storage_files";
    public static final String KEY_CLEAR_CACHE = "clear_cache";
    public static final String KEY_CLEAR_CHATS = "clear_chats";

    public static final String KEY_VERSION_INFO = "version_info";
    public static final String KEY_APP_VERSION = "app_version";

    private final SettingsRepository repository;
    private final String page;
    private final String currentUserId;
    private final String appVersionName;
    private final MediatorLiveData<List<SettingsItem>> itemsLiveData = new MediatorLiveData<>();
    private final MutableLiveData<SettingsStorageStats> storageStatsLiveData = new MutableLiveData<>(new SettingsStorageStats());

    public SettingsViewModel(SettingsRepository repository, String page, String currentUserId, String appVersionName) {
        this.repository = repository;
        this.page = page;
        this.currentUserId = currentUserId;
        this.appVersionName = appVersionName == null || appVersionName.trim().isEmpty() ? "1.0" : appVersionName;
        itemsLiveData.addSource(repository.getSettingsLiveData(), state -> rebuild(state, storageStatsLiveData.getValue()));
        itemsLiveData.addSource(storageStatsLiveData, stats -> rebuild(repository.getSettingsLiveData().getValue(), stats));
        refreshStorageStats();
    }

    public LiveData<List<SettingsItem>> getItemsLiveData() {
        return itemsLiveData;
    }

    public LiveData<SettingsState> getSettingsState() {
        return repository.getSettingsLiveData();
    }

    public void refreshStorageStats() {
        repository.loadStorageStats(storageStatsLiveData::setValue);
    }

    public void onSwitchChanged(String key, boolean checked) {
        switch (key) {
            case KEY_SWITCH_NOTIFICATIONS:
                repository.setNotificationsEnabled(checked);
                break;
            case KEY_SWITCH_SOUND:
                repository.setSoundEnabled(checked);
                break;
            case KEY_SWITCH_VIBRATION:
                repository.setVibrationEnabled(checked);
                break;
            case KEY_SWITCH_PREVIEW:
                repository.setMessagePreviewEnabled(checked);
                break;
            case KEY_SWITCH_DND:
                repository.setDoNotDisturbEnabled(checked);
                break;
            case KEY_ALLOW_SEARCH:
                repository.setAllowSearchById(checked);
                break;
            case KEY_EFFECTS:
                repository.setImmersiveEffectsEnabled(checked);
                break;
            default:
                break;
        }
    }

    public void setFriendVerificationMode(String mode) {
        repository.setFriendVerificationMode(mode);
    }

    public void setThemeMode(String mode) {
        repository.setThemeMode(mode);
    }

    public void setChatBackground(String background) {
        repository.setChatBackground(background);
    }

    public void setFontSize(String fontSize) {
        repository.setFontSize(fontSize);
    }

    public void addToBlacklist(String userId) {
        repository.addToBlacklist(userId);
    }

    public void removeFromBlacklist(String userId) {
        repository.removeFromBlacklist(userId);
    }

    public void updatePassword(String oldPassword, String newPassword, SettingsRepository.ResultCallback callback) {
        repository.updatePassword(currentUserId, oldPassword, newPassword, callback);
    }

    public void saveLocalPasscode(String passcode, SettingsRepository.ResultCallback callback) {
        repository.saveLocalPasscode(passcode, callback);
    }

    public void clearLocalPasscode(String passcode, SettingsRepository.ResultCallback callback) {
        repository.clearLocalPasscode(passcode, callback);
    }

    public void clearCache(SettingsRepository.ResultCallback callback) {
        repository.clearImageCache(callback);
    }

    public void clearChats(SettingsRepository.ResultCallback callback) {
        repository.clearChatHistory(currentUserId, callback);
    }

    public String getPageTitle() {
        switch (page) {
            case SettingsPage.ACCOUNT_SECURITY:
                return "账号与安全";
            case SettingsPage.DEVICE_MANAGEMENT:
                return "设备管理";
            case SettingsPage.PRIVACY_CONTACTS:
                return "隐私与联系人";
            case SettingsPage.BLACKLIST:
                return "黑名单";
            case SettingsPage.NOTIFICATIONS:
                return "新消息通知";
            case SettingsPage.APPEARANCE:
                return "外观与显示";
            case SettingsPage.GENERAL_STORAGE:
                return "通用与存储";
            case SettingsPage.ABOUT:
                return "关于";
            default:
                return "设置";
        }
    }

    public String getPageSubtitle() {
        switch (page) {
            case SettingsPage.ACCOUNT_SECURITY:
                return "保护账号，管理当前设备和本地安全能力。";
            case SettingsPage.DEVICE_MANAGEMENT:
                return "查看当前登录环境与设备信息。";
            case SettingsPage.PRIVACY_CONTACTS:
                return "决定谁能找到你，以及好友验证方式。";
            case SettingsPage.BLACKLIST:
                return "被加入黑名单的用户将无法正常与你互动。";
            case SettingsPage.NOTIFICATIONS:
                return "控制消息提醒、震动和预览方式。";
            case SettingsPage.APPEARANCE:
                return "主题、背景、字体与动效都在这里调整。";
            case SettingsPage.GENERAL_STORAGE:
                return "管理缓存、聊天记录和本地占用空间。";
            case SettingsPage.ABOUT:
                return "查看版本、日志与当前运行环境。";
            default:
                return "极简设置树，专注高频操作与清晰层级。";
        }
    }

    public String getCurrentDeviceName() {
        return repository.getCurrentDeviceName();
    }

    public String getCurrentSystemVersion() {
        return repository.getCurrentSystemVersion();
    }

    private void rebuild(SettingsState state, SettingsStorageStats stats) {
        SettingsState safeState = state == null ? new SettingsState() : state;
        SettingsStorageStats safeStats = stats == null ? new SettingsStorageStats() : stats;
        itemsLiveData.setValue(buildItems(safeState, safeStats));
    }

    private List<SettingsItem> buildItems(SettingsState state, SettingsStorageStats stats) {
        List<SettingsItem> items = new ArrayList<>();
        String accountId = currentUserId == null || currentUserId.trim().isEmpty() ? "未知" : currentUserId;
        switch (page) {
            case SettingsPage.ROOT:
                items.add(SettingsItem.title("账号"));
                items.add(SettingsItem.arrow(KEY_ACCOUNT_SECURITY, android.R.drawable.ic_lock_lock, "账号与安全", "扣扣号 " + accountId));
                items.add(SettingsItem.arrow(KEY_PRIVACY, android.R.drawable.ic_menu_myplaces, "隐私与联系人", verifyModeLabel(state.friendVerificationMode)));
                items.add(SettingsItem.arrow(KEY_NOTIFICATIONS, android.R.drawable.ic_dialog_email, "新消息通知", state.notificationsEnabled ? "已开启" : "已关闭"));
                items.add(SettingsItem.title("体验"));
                items.add(SettingsItem.arrow(KEY_APPEARANCE, android.R.drawable.ic_menu_gallery, "外观与显示", backgroundLabel(state.chatBackground)));
                items.add(SettingsItem.arrow(KEY_GENERAL_STORAGE, android.R.drawable.ic_menu_manage, "通用与存储", formatSize(stats.getTotalBytes())));
                items.add(SettingsItem.arrow(KEY_ABOUT, android.R.drawable.ic_menu_info_details, "关于", "v" + appVersionName));
                items.add(SettingsItem.action(KEY_LOGOUT, "退出登录", true));
                break;
            case SettingsPage.ACCOUNT_SECURITY:
                items.add(SettingsItem.title("登录与保护"));
                items.add(SettingsItem.arrow(KEY_CURRENT_ACCOUNT, android.R.drawable.ic_menu_info_details, "当前账号", accountId));
                items.add(SettingsItem.arrow(KEY_CHANGE_PASSWORD, android.R.drawable.ic_lock_lock, "修改登录密码", ""));
                items.add(SettingsItem.arrow(KEY_LOCAL_PASSCODE, android.R.drawable.ic_lock_lock, "本地密码保护", state.localPasscodeEnabled ? "已启用" : "未启用"));
                items.add(SettingsItem.arrow(KEY_DEVICE_MANAGEMENT, android.R.drawable.ic_menu_recent_history, "设备管理", repository.getCurrentDeviceName()));
                break;
            case SettingsPage.DEVICE_MANAGEMENT:
                items.add(SettingsItem.title("当前设备"));
                items.add(SettingsItem.arrow(KEY_CURRENT_DEVICE, android.R.drawable.ic_menu_compass, "设备名称", repository.getCurrentDeviceName()));
                items.add(SettingsItem.arrow(KEY_SYSTEM_VERSION, android.R.drawable.ic_menu_manage, "系统版本", repository.getCurrentSystemVersion()));
                items.add(SettingsItem.arrow(KEY_CURRENT_ACCOUNT, android.R.drawable.ic_menu_info_details, "当前账号", accountId));
                break;
            case SettingsPage.PRIVACY_CONTACTS:
                items.add(SettingsItem.title("好友关系"));
                items.add(SettingsItem.toggle(KEY_ALLOW_SEARCH, android.R.drawable.ic_menu_search, "允许通过扣扣号找到我", state.allowSearchById ? "已开启" : "已关闭", state.allowSearchById));
                items.add(SettingsItem.arrow(KEY_VERIFY_MODE, android.R.drawable.ic_dialog_info, "加我为好友时", verifyModeLabel(state.friendVerificationMode)));
                items.add(SettingsItem.arrow(KEY_BLACKLIST, android.R.drawable.ic_delete, "黑名单管理", state.blacklistUserIds.size() + " 人"));
                break;
            case SettingsPage.BLACKLIST:
                items.add(SettingsItem.title("拦截列表"));
                items.add(SettingsItem.action(KEY_ADD_BLACKLIST, "添加黑名单", false));
                if (state.blacklistUserIds.isEmpty()) {
                    items.add(SettingsItem.arrow("readonly_empty", android.R.drawable.ic_menu_close_clear_cancel, "暂无黑名单用户", ""));
                } else {
                    for (String blockedId : state.blacklistUserIds) {
                        items.add(SettingsItem.arrow("blacklist_user:" + blockedId, android.R.drawable.ic_delete, blockedId, "点击移除"));
                    }
                }
                break;
            case SettingsPage.NOTIFICATIONS:
                items.add(SettingsItem.title("提醒方式"));
                items.add(SettingsItem.toggle(KEY_SWITCH_NOTIFICATIONS, android.R.drawable.ic_dialog_email, "接收新消息通知", state.notificationsEnabled ? "已开启" : "已关闭", state.notificationsEnabled));
                items.add(SettingsItem.toggle(KEY_SWITCH_SOUND, android.R.drawable.ic_lock_silent_mode_off, "通知声音", state.soundEnabled ? "已开启" : "已关闭", state.soundEnabled));
                items.add(SettingsItem.toggle(KEY_SWITCH_VIBRATION, android.R.drawable.ic_lock_idle_alarm, "震动反馈", state.vibrationEnabled ? "已开启" : "已关闭", state.vibrationEnabled));
                items.add(SettingsItem.toggle(KEY_SWITCH_PREVIEW, android.R.drawable.ic_menu_view, "消息预览", state.messagePreviewEnabled ? "已显示" : "已隐藏", state.messagePreviewEnabled));
                items.add(SettingsItem.toggle(KEY_SWITCH_DND, android.R.drawable.ic_lock_silent_mode, "消息免打扰", state.doNotDisturbEnabled ? "已开启" : "已关闭", state.doNotDisturbEnabled));
                break;
            case SettingsPage.APPEARANCE:
                items.add(SettingsItem.title("界面风格"));
                items.add(SettingsItem.arrow(KEY_THEME_MODE, android.R.drawable.ic_menu_manage, "主题模式", themeLabel(state.themeMode)));
                items.add(SettingsItem.arrow(KEY_CHAT_BACKGROUND, android.R.drawable.ic_menu_gallery, "聊天背景", backgroundLabel(state.chatBackground)));
                items.add(SettingsItem.arrow(KEY_FONT_SIZE, android.R.drawable.ic_menu_edit, "字体大小", fontLabel(state.fontSize)));
                items.add(SettingsItem.toggle(KEY_EFFECTS, android.R.drawable.ic_menu_slideshow, "沉浸流光动效", state.immersiveEffectsEnabled ? "已开启" : "已关闭", state.immersiveEffectsEnabled));
                break;
            case SettingsPage.GENERAL_STORAGE:
                items.add(SettingsItem.title("空间统计"));
                items.add(SettingsItem.arrow(KEY_STORAGE_USAGE, android.R.drawable.ic_menu_manage, "总占用", formatSize(stats.getTotalBytes())));
                items.add(SettingsItem.arrow(KEY_STORAGE_DB, android.R.drawable.ic_menu_agenda, "数据库", formatSize(stats.databaseBytes)));
                items.add(SettingsItem.arrow(KEY_STORAGE_CACHE, android.R.drawable.ic_menu_crop, "缓存文件", formatSize(stats.cacheBytes)));
                items.add(SettingsItem.arrow(KEY_STORAGE_FILES, android.R.drawable.ic_menu_save, "聊天附件", formatSize(stats.totalChatBytes)));
                items.add(SettingsItem.title("清理"));
                items.add(SettingsItem.action(KEY_CLEAR_CACHE, "一键清理缓存", false));
                items.add(SettingsItem.action(KEY_CLEAR_CHATS, "清空所有聊天记录", true));
                break;
            case SettingsPage.ABOUT:
                items.add(SettingsItem.title("应用信息"));
                items.add(SettingsItem.arrow(KEY_APP_VERSION, android.R.drawable.ic_menu_info_details, "当前版本", "v" + appVersionName));
                items.add(SettingsItem.arrow(KEY_VERSION_INFO, android.R.drawable.ic_menu_info_details, "版本信息与更新日志", "查看详情"));
                items.add(SettingsItem.arrow(KEY_CURRENT_DEVICE, android.R.drawable.ic_menu_compass, "当前设备", repository.getCurrentSystemVersion()));
                break;
            default:
                break;
        }
        return items;
    }

    public String verifyModeLabel(String mode) {
        if ("allow_all".equals(mode)) {
            return "允许任何人";
        }
        if ("deny_all".equals(mode)) {
            return "拒绝所有人";
        }
        return "需要验证";
    }

    public String themeLabel(String mode) {
        if ("light".equals(mode)) {
            return "浅色";
        }
        if ("dark".equals(mode)) {
            return "深色";
        }
        return "跟随系统";
    }

    public String backgroundLabel(String background) {
        if ("stardust".equals(background)) {
            return "全息晶尘";
        }
        if ("cyber".equals(background)) {
            return "电子科幻";
        }
        if ("matrix".equals(background)) {
            return "代码雨";
        }
        if ("minimal_white".equals(background)) {
            return "极简白色";
        }
        if ("minimal".equals(background)) {
            return "极简暗调";
        }
        return "蝴蝶流光";
    }

    public String fontLabel(String size) {
        if ("small".equals(size)) {
            return "小";
        }
        if ("large".equals(size)) {
            return "大";
        }
        return "中";
    }

    public String formatSize(long bytes) {
        if (bytes <= 0) {
            return "0 KB";
        }
        float kb = bytes / 1024f;
        if (kb < 1024f) {
            return String.format(Locale.getDefault(), "%.0f KB", kb);
        }
        float mb = kb / 1024f;
        if (mb < 1024f) {
            return String.format(Locale.getDefault(), "%.1f MB", mb);
        }
        float gb = mb / 1024f;
        return String.format(Locale.getDefault(), "%.2f GB", gb);
    }
}
