package com.example.koukou.data.repository;

import android.content.Context;

import com.example.koukou.data.local.dao.UserDao;
import com.example.koukou.data.local.entity.UserEntity;
import com.example.koukou.network.api.KoukouApiService;
import com.example.koukou.utils.AppExecutors;
import com.example.koukou.utils.UserHelper;

import java.io.IOException;

public class UserRepository {
    private static final int KOUKOU_ID_LENGTH = 10;
    private static final String DEFAULT_SIGNATURE = "这个人很神秘，暂未留下签名";

    private final UserDao userDao;
    private final AppExecutors appExecutors;
    private final KoukouApiService apiService;

    public UserRepository(UserDao userDao, AppExecutors appExecutors) {
        this.userDao = userDao;
        this.appExecutors = appExecutors;
        this.apiService = KoukouApiService.getInstance();
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
        appExecutors.networkIO().execute(() -> {
            try {
                String loginAccount = safe(account);
                KoukouApiService.AuthResult remote = apiService.login(loginAccount, password);
                UserEntity user = remote.toUserEntity(password);
                persistUser(user);
                appExecutors.mainThread().execute(() -> callback.onSuccess(user));
            } catch (KoukouApiService.ApiException apiError) {
                if (shouldFallbackToLocal(apiError)) {
                    loginLocal(account, password, callback);
                    return;
                }
                appExecutors.mainThread().execute(() -> callback.onError(mapLoginApiError(apiError)));
            } catch (IOException ioException) {
                loginLocal(account, password, callback);
            } catch (Exception e) {
                appExecutors.mainThread().execute(() -> callback.onError("登录异常：" + safe(e.getMessage(), "未知错误")));
            }
        });
    }

    public void register(String nickname, String preferredKoukouId, String password, Callback callback) {
        appExecutors.networkIO().execute(() -> {
            try {
                String normalizedNickname = safe(nickname);
                if (normalizedNickname.isEmpty()) {
                    appExecutors.mainThread().execute(() -> callback.onError("昵称不能为空"));
                    return;
                }

                String koukouId = resolveRegisterKoukouId(preferredKoukouId);
                KoukouApiService.AuthResult remote = apiService.register(normalizedNickname, koukouId, password);
                UserEntity user = remote.toUserEntity(password);
                persistUser(user);
                appExecutors.mainThread().execute(() -> callback.onSuccess(user));
            } catch (KoukouApiService.ApiException apiError) {
                appExecutors.mainThread().execute(() -> callback.onError(mapRegisterApiError(apiError)));
            } catch (IOException ioException) {
                String message = KoukouApiService.describeNetworkError(ioException);
                appExecutors.mainThread().execute(() -> callback.onError(message));
            } catch (Exception e) {
                appExecutors.mainThread().execute(() -> callback.onError("注册异常：" + safe(e.getMessage(), "未知错误")));
            }
        });
    }

    public void generateAvailableKoukouId(IdCallback callback) {
        appExecutors.networkIO().execute(() -> {
            try {
                String koukouId;
                try {
                    koukouId = apiService.generateAvailableKoukouId();
                } catch (Exception ignored) {
                    koukouId = generateRandomUniqueKoukouId();
                }
                String finalKoukouId = koukouId;
                appExecutors.mainThread().execute(() -> callback.onSuccess(finalKoukouId));
            } catch (Exception e) {
                appExecutors.mainThread().execute(() -> callback.onError("生成扣扣号失败：" + safe(e.getMessage(), "未知错误")));
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
            String displayName = safe(nickname, user.nickname);
            if (displayName.isEmpty()) {
                displayName = "扣扣用户";
            }

            user.nickname = displayName;
            user.account = user.userId;
            user.avatarUrl = avatarUrl;
            user.signature = safe(signature, DEFAULT_SIGNATURE);
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

    private void loginLocal(String account, String password, Callback callback) {
        appExecutors.diskIO().execute(() -> {
            try {
                String loginAccount = safe(account);
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
                appExecutors.mainThread().execute(() -> callback.onError("登录异常：" + safe(e.getMessage(), "未知错误")));
            }
        });
    }

    private void persistUser(UserEntity user) {
        if (user == null) {
            return;
        }
        UserEntity localUser = new UserEntity();
        localUser.userId = safe(user.userId);
        localUser.account = safe(user.account, localUser.userId);
        localUser.password = user.password;
        localUser.nickname = safe(user.nickname, localUser.account);
        localUser.avatarUrl = safe(user.avatarUrl, "ic_avatar_1");
        localUser.signature = safe(user.signature, DEFAULT_SIGNATURE);
        userDao.insertUser(localUser);
    }

    private String resolveRegisterKoukouId(String preferredKoukouId) {
        String candidate = normalizeKoukouId(preferredKoukouId);
        if (preferredKoukouId == null || preferredKoukouId.trim().isEmpty()) {
            return generateRandomKoukouId();
        }
        if (candidate.isEmpty()) {
            throw new IllegalArgumentException("扣扣号必须是 10 位数字");
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
        long randomId = (long) (Math.random() * 9_000_000_000L) + 1_000_000_000L;
        return String.valueOf(randomId);
    }

    private boolean shouldFallbackToLocal(KoukouApiService.ApiException apiError) {
        return apiError != null && apiError.httpCode >= 500;
    }

    private String mapLoginApiError(KoukouApiService.ApiException apiError) {
        if (apiError == null) {
            return "登录失败";
        }
        if ("invalid_credentials".equals(apiError.errorCode)) {
            return "扣扣号或密码错误";
        }
        if ("unauthorized".equals(apiError.errorCode)) {
            return "登录状态无效，请重新登录";
        }
        return "服务器登录失败：" + safe(apiError.getMessage(), "未知错误");
    }

    private String mapRegisterApiError(KoukouApiService.ApiException apiError) {
        if (apiError == null) {
            return "注册失败";
        }
        if (apiError.httpCode == 409 || "user_exists".equals(apiError.errorCode)) {
            return "该扣扣号已被注册，请更换后重试";
        }
        if ("invalid_account_format".equals(apiError.errorCode)) {
            return "扣扣号必须是 10 位数字";
        }
        if ("account_and_password_required".equals(apiError.errorCode)) {
            return "请完整填写扣扣号和密码";
        }
        return "服务器注册失败：" + safe(apiError.getMessage(), "未知错误");
    }

    private String safe(String value) {
        return safe(value, "");
    }

    private String safe(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
