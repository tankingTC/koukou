package com.example.koukou.network.api;

import android.util.Log;

import com.example.koukou.data.local.entity.FriendRequestEntity;
import com.example.koukou.data.local.entity.UserEntity;
import com.example.koukou.network.ServerEndpointPolicy;
import com.example.koukou.network.UnsafeTlsSupport;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class KoukouApiService {
    private static final String TAG = "KoukouApiService";

    public static final class ApiException extends Exception {
        public final int httpCode;
        public final String errorCode;

        public ApiException(int httpCode, String errorCode, String message) {
            super(message == null || message.trim().isEmpty() ? "server_error" : message);
            this.httpCode = httpCode;
            this.errorCode = errorCode == null ? "" : errorCode;
        }
    }

    public static final class AuthResult {
        public String userId;
        public String account;
        public String nickname;
        public String avatarUrl;
        public String signature;
        public String token;

        public UserEntity toUserEntity(String password) {
            UserEntity user = new UserEntity();
            user.userId = safe(userId);
            user.account = safe(account, user.userId);
            user.password = password;
            user.nickname = safe(nickname, user.account);
            user.avatarUrl = safe(avatarUrl, "ic_avatar_1");
            user.signature = safe(signature, "这个人很神秘，暂未留下签名");
            user.authToken = token;
            return user;
        }
    }

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static volatile KoukouApiService INSTANCE;

    private final OkHttpClient standardClient;
    private final OkHttpClient fallbackClient;
    private final Gson gson = new Gson();

    private KoukouApiService() {
        standardClient = new OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build();
        fallbackClient = UnsafeTlsSupport.apply(new OkHttpClient.Builder()
                        .connectTimeout(12, TimeUnit.SECONDS)
                        .readTimeout(15, TimeUnit.SECONDS)
                        .writeTimeout(15, TimeUnit.SECONDS))
                .build();
    }

    public static KoukouApiService getInstance() {
        if (INSTANCE == null) {
            synchronized (KoukouApiService.class) {
                if (INSTANCE == null) {
                    INSTANCE = new KoukouApiService();
                }
            }
        }
        return INSTANCE;
    }

    public AuthResult login(String account, String password) throws IOException, ApiException {
        JsonObject body = new JsonObject();
        body.addProperty("account", safe(account));
        body.addProperty("password", safe(password));
        JsonObject json = executeJson("POST", "/api/login", body, null);
        return parseAuthResult(json);
    }

    public AuthResult register(String nickname, String account, String password) throws IOException, ApiException {
        JsonObject body = new JsonObject();
        body.addProperty("nickname", safe(nickname));
        body.addProperty("account", safe(account));
        body.addProperty("password", safe(password));
        JsonObject json = executeJson("POST", "/api/register", body, null);
        return parseAuthResult(json);
    }

    public String generateAvailableKoukouId() throws IOException, ApiException {
        JsonObject json = executeJson("GET", "/api/users/available-id", null, null);
        return getString(json, "koukouId", "");
    }

    public UserEntity findUser(String identifier, String authToken) throws IOException, ApiException {
        JsonObject json = executeJson("GET", "/api/users/" + safe(identifier), null, authToken);
        return parseUser(json.getAsJsonObject("user"));
    }

    public List<UserEntity> getFriends(String authToken) throws IOException, ApiException {
        JsonObject json = executeJson("GET", "/api/friends", null, authToken);
        List<UserEntity> result = new ArrayList<>();
        JsonArray items = json.has("items") && json.get("items").isJsonArray() ? json.getAsJsonArray("items") : new JsonArray();
        for (JsonElement element : items) {
            if (element != null && element.isJsonObject()) {
                result.add(parseUser(element.getAsJsonObject()));
            }
        }
        return result;
    }

    public List<FriendRequestEntity> getIncomingFriendRequests(String authToken) throws IOException, ApiException {
        JsonObject json = executeJson("GET", "/api/friends/requests", null, authToken);
        List<FriendRequestEntity> result = new ArrayList<>();
        JsonArray items = json.has("items") && json.get("items").isJsonArray() ? json.getAsJsonArray("items") : new JsonArray();
        for (JsonElement element : items) {
            if (element != null && element.isJsonObject()) {
                result.add(parseFriendRequest(element.getAsJsonObject()));
            }
        }
        return result;
    }

    public void sendFriendRequest(String authToken, String targetId, String message) throws IOException, ApiException {
        JsonObject body = new JsonObject();
        body.addProperty("targetId", safe(targetId));
        body.addProperty("message", safe(message));
        executeJson("POST", "/api/friends/requests", body, authToken);
    }

    public void acceptFriendRequest(String authToken, String requestId) throws IOException, ApiException {
        executeJson("POST", "/api/friends/requests/" + safe(requestId) + "/accept", new JsonObject(), authToken);
    }

    public void rejectFriendRequest(String authToken, String requestId) throws IOException, ApiException {
        executeJson("POST", "/api/friends/requests/" + safe(requestId) + "/reject", new JsonObject(), authToken);
    }

    public void deleteFriend(String authToken, String friendId) throws IOException, ApiException {
        executeJson("DELETE", "/api/friends/" + safe(friendId), null, authToken);
    }

    public static String describeNetworkError(IOException exception) {
        Throwable cause = rootCause(exception);
        if (cause instanceof UnknownHostException) {
            return "无法解析服务器地址，请检查域名配置或网络环境";
        }
        if (cause instanceof SSLHandshakeException || cause instanceof SSLException) {
            return "服务器证书校验失败，请检查 HTTPS 配置";
        }
        if (cause instanceof SocketTimeoutException) {
            return "连接服务器超时，请稍后重试";
        }
        if (cause instanceof ConnectException) {
            return "无法连接到服务器，请确认服务器已经启动";
        }
        String message = cause == null ? "" : safe(cause.getMessage());
        if (message.isEmpty()) {
            return "网络连接异常，请检查服务器状态后重试";
        }
        return "网络连接异常：" + message;
    }

    private JsonObject executeJson(String method, String path, JsonObject body, String authToken) throws IOException, ApiException {
        IOException lastIoException = null;
        ApiException lastApiException = null;

        for (String baseUrl : ServerEndpointPolicy.apiBaseUrls()) {
            Request request = buildRequest(baseUrl, method, path, body, authToken);
            OkHttpClient activeClient = ServerEndpointPolicy.requiresUnsafeTls(baseUrl) ? fallbackClient : standardClient;
            try (Response response = activeClient.newCall(request).execute()) {
                String rawBody = response.body() == null ? "" : response.body().string();
                JsonObject json = parseObject(rawBody);
                if (!response.isSuccessful()) {
                    ApiException apiException = toApiException(response.code(), json, rawBody);
                    if (shouldTryNextEndpoint(response.code())) {
                        lastApiException = apiException;
                        Log.w(TAG, "HTTP " + response.code() + " from " + baseUrl + path + ", trying next endpoint");
                        continue;
                    }
                    throw apiException;
                }
                return json;
            } catch (ApiException apiException) {
                lastApiException = apiException;
                throw apiException;
            } catch (IOException ioException) {
                lastIoException = ioException;
                Log.w(TAG, "Request failed on " + baseUrl + path + ": " + ioException.getMessage());
            }
        }

        if (lastApiException != null) {
            throw lastApiException;
        }
        if (lastIoException != null) {
            throw lastIoException;
        }
        throw new IOException("No available server endpoint");
    }

    private Request buildRequest(String baseUrl, String method, String path, JsonObject body, String authToken) {
        Request.Builder builder = new Request.Builder().url(ServerEndpointPolicy.appendPath(baseUrl, path));
        addBearer(builder, authToken);
        if ("GET".equals(method)) {
            builder.get();
        } else if ("DELETE".equals(method)) {
            builder.delete();
        } else {
            RequestBody requestBody = RequestBody.create(gson.toJson(body == null ? new JsonObject() : body), JSON);
            builder.method(method, requestBody);
        }
        return builder.build();
    }

    private boolean shouldTryNextEndpoint(int httpCode) {
        return httpCode == 403 || httpCode == 404 || httpCode >= 500;
    }

    private void addBearer(Request.Builder builder, String authToken) {
        if (authToken != null && !authToken.trim().isEmpty()) {
            builder.header("Authorization", "Bearer " + authToken.trim());
        }
    }

    private ApiException toApiException(int httpCode, JsonObject json, String rawBody) {
        String errorCode = getString(json, "error", "");
        String message = getString(json, "message", "");
        if (message.isEmpty()) {
            message = rawBody == null || rawBody.trim().isEmpty() ? "server_error" : rawBody;
        }
        return new ApiException(httpCode, errorCode, message);
    }

    private JsonObject parseObject(String text) {
        try {
            JsonElement element = JsonParser.parseString(text == null ? "{}" : text);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        } catch (Exception ignored) {
            return new JsonObject();
        }
    }

    private AuthResult parseAuthResult(JsonObject json) {
        AuthResult result = new AuthResult();
        result.userId = getString(json, "userId", "");
        result.account = getString(json, "account", result.userId);
        result.nickname = getString(json, "nickname", result.account);
        result.avatarUrl = getString(json, "avatarUrl", "ic_avatar_1");
        result.signature = getString(json, "signature", "");
        result.token = getString(json, "token", "");
        return result;
    }

    private UserEntity parseUser(JsonObject json) {
        UserEntity user = new UserEntity();
        if (json == null) {
            user.userId = "";
            return user;
        }
        user.userId = getString(json, "userId", getString(json, "user_id", ""));
        user.account = getString(json, "account", user.userId);
        user.nickname = getString(json, "nickname", user.account);
        user.avatarUrl = getString(json, "avatarUrl", getString(json, "avatar_url", "ic_avatar_1"));
        user.signature = getString(json, "signature", "");
        return user;
    }

    private FriendRequestEntity parseFriendRequest(JsonObject json) {
        FriendRequestEntity entity = new FriendRequestEntity();
        entity.requestId = getString(json, "requestId", "");
        entity.fromUserId = getString(json, "fromUserId", "");
        entity.fromNickname = getString(json, "fromNickname", entity.fromUserId);
        entity.fromAvatar = getString(json, "fromAvatar", "ic_avatar_1");
        entity.toUserId = getString(json, "toUserId", "");
        entity.message = getString(json, "message", "");
        entity.status = getString(json, "status", "pending");
        entity.createdAt = getLong(json, "createdAt", System.currentTimeMillis());
        entity.updatedAt = getLong(json, "updatedAt", entity.createdAt);
        return entity;
    }

    private static String getString(JsonObject json, String key, String fallback) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            String value = json.get(key).getAsString();
            return value == null || value.trim().isEmpty() ? fallback : value;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long getLong(JsonObject json, String key, long fallback) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return json.get(key).getAsLong();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current == null ? throwable : current;
    }

    private static String safe(String value) {
        return safe(value, "");
    }

    private static String safe(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
