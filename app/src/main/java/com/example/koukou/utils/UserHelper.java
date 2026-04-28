package com.example.koukou.utils;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class UserHelper {
    private static final String PREF_NAME = "koukou_prefs";
    private static final String KEY_ACCOUNT = "current_account";
    private static final String KEY_PASSWORD = "current_password";
    private static final String KEY_USER_ID = "current_user_id";
    private static final String KEY_NICKNAME = "current_nickname";
    private static final String KEY_AVATAR = "current_avatar";
    private static final String KEY_SIGNATURE = "current_signature";
    private static final String KEY_LOGIN_HISTORY = "login_history";
    private static final int MAX_LOGIN_HISTORY = 10;
    private static final String DEFAULT_NICKNAME = "koukou_user";
    private static final String DEFAULT_AVATAR = "ic_avatar_1";
    private static final String DEFAULT_SIGNATURE = "这个人很神秘，暂未留下签名";

    public static class SavedLogin {
        public final String account;
        public final String password;
        public final String nickname;
        public final String avatar;

        public SavedLogin(String account, String password) {
            this(account, password, "", "");
        }

        public SavedLogin(String account, String password, String nickname, String avatar) {
            this.account = account;
            this.password = password;
            this.nickname = nickname;
            this.avatar = avatar;
        }
    }

    private UserHelper() {
    }

    public static void saveUser(Context context, String account, String password, String userId) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_ACCOUNT, account)
                .putString(KEY_PASSWORD, password)
                .putString(KEY_USER_ID, userId)
                .apply();
    }

    public static void updateLoginIdentity(Context context, String oldAccount, String newAccount, String password, String userId, String nickname, String avatar) {
        if (newAccount == null || newAccount.trim().isEmpty()) {
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit()
                .putString(KEY_ACCOUNT, newAccount)
                .putString(KEY_USER_ID, userId);
        if (password != null) {
            editor.putString(KEY_PASSWORD, password);
        }
        editor.apply();

        if (oldAccount != null && !oldAccount.equals(newAccount)) {
            removeLoginHistory(context, oldAccount);
        }
        if (password != null) {
            saveLoginHistory(context, newAccount, password, nickname, avatar);
        }
    }

    public static void saveProfile(Context context, String nickname, String avatar) {
        saveProfile(context, nickname, avatar, getSignature(context));
    }

    public static void saveProfile(Context context, String nickname, String avatar, String signature) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_NICKNAME, safeString(nickname, DEFAULT_NICKNAME))
                .putString(KEY_AVATAR, safeString(avatar, DEFAULT_AVATAR))
                .putString(KEY_SIGNATURE, safeString(signature, DEFAULT_SIGNATURE))
                .apply();
    }

    public static void saveLoginHistory(Context context, String account, String password) {
        saveLoginHistory(context, account, password, "", "");
    }

    public static void saveLoginHistory(Context context, String account, String password, String nickname, String avatar) {
        if (account == null || account.trim().isEmpty() || password == null) {
            return;
        }

        List<SavedLogin> current = getSavedLogins(context);
        List<SavedLogin> updated = new ArrayList<>();
        updated.add(new SavedLogin(account, password, nickname, avatar));

        for (SavedLogin item : current) {
            if (!account.equals(item.account)) {
                updated.add(item);
            }
            if (updated.size() >= MAX_LOGIN_HISTORY) {
                break;
            }
        }

        JSONArray array = new JSONArray();
        try {
            for (SavedLogin item : updated) {
                JSONObject obj = new JSONObject();
                obj.put("account", item.account);
                obj.put("password", item.password);
                obj.put("nickname", item.nickname);
                obj.put("avatar", item.avatar);
                array.put(obj);
            }
        } catch (Exception ignored) {
        }

        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LOGIN_HISTORY, array.toString()).apply();
    }

    public static List<SavedLogin> getSavedLogins(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_LOGIN_HISTORY, "[]");
        List<SavedLogin> result = new ArrayList<>();

        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) {
                    continue;
                }
                String account = obj.optString("account", "");
                String password = obj.optString("password", "");
                String nickname = obj.optString("nickname", "");
                String avatar = obj.optString("avatar", "");
                if (!account.isEmpty()) {
                    result.add(new SavedLogin(account, password, nickname, avatar));
                }
            }
        } catch (Exception ignored) {
        }

        return result;
    }

    public static String getPasswordByAccount(Context context, String account) {
        if (account == null || account.trim().isEmpty()) {
            return null;
        }
        List<SavedLogin> logins = getSavedLogins(context);
        for (SavedLogin login : logins) {
            if (account.equals(login.account)) {
                return login.password;
            }
        }
        return null;
    }

    public static void clearSavedUser(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .remove(KEY_ACCOUNT)
                .remove(KEY_PASSWORD)
                .remove(KEY_USER_ID)
                .remove(KEY_NICKNAME)
                .remove(KEY_AVATAR)
                .remove(KEY_SIGNATURE)
                .apply();
    }

    public static String getAccount(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_ACCOUNT, null);
    }

    public static String getPassword(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_PASSWORD, null);
    }

    public static String getUserId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_USER_ID, null);
    }

    public static String getNickname(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_NICKNAME, DEFAULT_NICKNAME);
    }

    public static String getAvatar(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_AVATAR, DEFAULT_AVATAR);
    }

    public static String getSignature(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_SIGNATURE, DEFAULT_SIGNATURE);
    }

    public static void removeLoginHistory(Context context, String account) {
        if (account == null) {
            return;
        }
        List<SavedLogin> current = getSavedLogins(context);
        List<SavedLogin> updated = new ArrayList<>();
        for (SavedLogin login : current) {
            if (!login.account.equals(account)) {
                updated.add(login);
            }
        }
        JSONArray array = new JSONArray();
        try {
            for (SavedLogin item : updated) {
                JSONObject obj = new JSONObject();
                obj.put("account", item.account);
                obj.put("password", item.password);
                obj.put("nickname", item.nickname);
                obj.put("avatar", item.avatar);
                array.put(obj);
            }
        } catch (Exception ignored) {
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LOGIN_HISTORY, array.toString()).apply();
    }

    private static String safeString(String value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? defaultValue : trimmed;
    }
}
