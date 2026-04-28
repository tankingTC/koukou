package com.example.koukou.ui.settings.model;

public class SettingsItem {
    public static final int TYPE_TITLE = 0;
    public static final int TYPE_SWITCH = 1;
    public static final int TYPE_ARROW = 2;
    public static final int TYPE_ACTION = 3;

    public final int type;
    public final String key;
    public final int iconRes;
    public final String title;
    public final String value;
    public final boolean checked;
    public final boolean destructive;

    private SettingsItem(int type, String key, int iconRes, String title, String value, boolean checked, boolean destructive) {
        this.type = type;
        this.key = key;
        this.iconRes = iconRes;
        this.title = title;
        this.value = value;
        this.checked = checked;
        this.destructive = destructive;
    }

    public static SettingsItem title(String title) {
        return new SettingsItem(TYPE_TITLE, title, 0, title, "", false, false);
    }

    public static SettingsItem toggle(String key, int iconRes, String title, String value, boolean checked) {
        return new SettingsItem(TYPE_SWITCH, key, iconRes, title, value, checked, false);
    }

    public static SettingsItem arrow(String key, int iconRes, String title, String value) {
        return new SettingsItem(TYPE_ARROW, key, iconRes, title, value, false, false);
    }

    public static SettingsItem action(String key, String title, boolean destructive) {
        return new SettingsItem(TYPE_ACTION, key, 0, title, "", false, destructive);
    }
}
