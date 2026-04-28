package com.example.koukou.ui.settings.model;

import java.util.LinkedHashSet;
import java.util.Set;

public class SettingsState {
    public boolean notificationsEnabled = true;
    public boolean soundEnabled = true;
    public boolean vibrationEnabled = true;
    public boolean messagePreviewEnabled = true;
    public boolean doNotDisturbEnabled = false;
    public boolean localPasscodeEnabled = false;
    public boolean allowSearchById = true;
    public boolean immersiveEffectsEnabled = true;

    public String friendVerificationMode = "need_verify";
    public String themeMode = "system";
    public String chatBackground = "butterfly";
    public String fontSize = "medium";
    public String cachedNickname = "koukou_user";
    public String cachedSignature = "这个人很神秘，暂未留下签名";
    public String localPasscode = "";
    public Set<String> blacklistUserIds = new LinkedHashSet<>();
}
