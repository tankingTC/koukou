package com.example.koukou.ui.settings.model;

public class SettingsStorageStats {
    public long databaseBytes;
    public long cacheBytes;
    public long totalChatBytes;

    public long getTotalBytes() {
        return databaseBytes + cacheBytes + totalChatBytes;
    }
}
