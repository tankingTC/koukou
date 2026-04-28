package com.example.koukou.data.repository;

import android.content.Context;

import com.example.koukou.data.local.dao.UserDao;
import com.example.koukou.data.local.entity.UserEntity;
import com.example.koukou.utils.AppExecutors;
import com.example.koukou.utils.UserHelper;

public class UserRepository {
    private static final int KOUKOU_ID_LENGTH = 10;
    private static final String DEFAULT_SIGNATURE = "这个人很神秘，暂未留下签名";

    private final UserDao userDao;
    private final AppExecutors appExecutors;

    public UserRepository(UserDao userDao, AppExecutors appExecutors) {
        this.userDao = userDao;
        this.appExecutors = appExecutors;
    }

    public interface Callback {
        void onSuccess(UserEntity user);
        void onError(String msg);
    }

    public interface IdCallback {
        void onSuccess(String koukouId);
        void onError(String msg);
    }

    public void login(String account, String password, Callback callback) {
        appExecutors.diskIO().execute(() -> {
            try {
                String loginAccount = account == null ? "" : account.trim();
                UserEntity user = userDao.getUser(loginAccount);
                if (user == null) {
                    user = userDao.getUserByAccount(loginAccount);
                }

                if (user == null) {
                    appExecutors.mainThread().execute(() -> callback.onError("该扣扣号不存在，请先注册"));
                    return;
                }

                if (user.password != null && !user.password.equals(password)) {
                    appExecutors.mainThread().execute(() -> callback.onError("密码错误"));
                    return;
                }

                UserEntity finalUser = user;
                appExecutors.mainThread().execute(() -> callback.onSuccess(finalUser));
            } catch (Exception e) {
                appExecutors.mainThread().execute(() -> callback.onError("登录异常: " + e.getMessage()));
            }
        });
    }

    public void register(String nickname, String preferredKoukouId, String password, Callback callback) {
        appExecutors.diskIO().execute(() -> {
            try {
                String normalizedNickname = nickname == null ? "" : nickname.trim();
                if (normalizedNickname.isEmpty()) {
                    appExecutors.mainThread().execute(() -> callback.onError("昵称不能为空"));
                    return;
                }

                String koukouId = resolveRegisterKoukouId(preferredKoukouId);

                UserEntity user = new UserEntity();
                user.userId = koukouId;
                user.account = koukouId;
                user.password = password;
                user.nickname = normalizedNickname;
                user.avatarUrl = "ic_avatar_" + ((int) (Math.random() * 6) + 1);
                user.signature = DEFAULT_SIGNATURE;

                userDao.insertUser(user);
                appExecutors.mainThread().execute(() -> callback.onSuccess(user));
            } catch (Exception e) {
                appExecutors.mainThread().execute(() -> callback.onError("注册异常: " + e.getMessage()));
            }
        });
    }

    public void generateAvailableKoukouId(IdCallback callback) {
        appExecutors.diskIO().execute(() -> {
            try {
                String koukouId = generateRandomUniqueKoukouId();
                appExecutors.mainThread().execute(() -> callback.onSuccess(koukouId));
            } catch (Exception e) {
                appExecutors.mainThread().execute(() -> callback.onError("生成扣扣号失败: " + e.getMessage()));
            }
        });
    }

    public void updateProfile(Context context, String userId, String nickname, String avatarUrl, String signature, Callback callback) {
        appExecutors.diskIO().execute(() -> {
            UserEntity user = userDao.getUser(userId);
            if (user == null) {
                appExecutors.mainThread().execute(() -> {
                    if (callback != null) {
                        callback.onError("用户不存在");
                    }
                });
                return;
            }

            String oldAccount = user.account;
            String displayName = nickname == null ? "" : nickname.trim();
            if (displayName.isEmpty()) {
                displayName = user.nickname;
            }
            if (displayName == null || displayName.isEmpty()) {
                displayName = "扣扣用户";
            }

            user.nickname = displayName;
            user.account = user.userId;
            user.avatarUrl = avatarUrl;
            user.signature = signature == null || signature.trim().isEmpty() ? DEFAULT_SIGNATURE : signature.trim();
            userDao.insertUser(user);

            appExecutors.mainThread().execute(() -> {
                UserHelper.updateLoginIdentity(context, oldAccount, user.account, user.password, user.userId, user.nickname, user.avatarUrl);
                UserHelper.saveProfile(context, user.nickname, avatarUrl, user.signature);
                if (callback != null) {
                    callback.onSuccess(user);
                }
            });
        });
    }

    private String resolveRegisterKoukouId(String preferredKoukouId) {
        String candidate = normalizeKoukouId(preferredKoukouId);
        if (preferredKoukouId == null || preferredKoukouId.trim().isEmpty()) {
            return generateRandomUniqueKoukouId();
        }

        if (candidate.isEmpty()) {
            throw new IllegalArgumentException("扣扣号必须是 10 位数字");
        }

        if (isKoukouIdTaken(candidate)) {
            throw new IllegalArgumentException("该扣扣号已被注册，请更换后重试");
        }
        return candidate;
    }

    private String generateRandomUniqueKoukouId() {
        String generated;
        do {
            generated = generateRandomKoukouId();
        } while (isKoukouIdTaken(generated));
        return generated;
    }

    private boolean isKoukouIdTaken(String candidate) {
        if (candidate.isEmpty()) {
            return false;
        }
        if (userDao.getUser(candidate) == null) {
            return userDao.getUserByAccount(candidate) != null;
        }
        return true;
    }

    private String normalizeKoukouId(String koukouId) {
        if (koukouId == null) {
            return "";
        }
        String trimmed = koukouId.trim();
        if (trimmed.length() != KOUKOU_ID_LENGTH) {
            return "";
        }
        for (int i = 0; i < trimmed.length(); i++) {
            if (!Character.isDigit(trimmed.charAt(i))) {
                return "";
            }
        }
        return trimmed;
    }

    private String generateRandomKoukouId() {
        long randomId = (long) (Math.random() * 9000000000L) + 1000000000L;
        return String.valueOf(randomId);
    }
}
