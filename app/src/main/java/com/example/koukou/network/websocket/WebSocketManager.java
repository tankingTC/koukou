package com.example.koukou.network.websocket;

import android.os.Handler;
import android.os.Looper;

import com.example.koukou.BuildConfig;
import com.example.koukou.network.model.WebSocketMessage;
import com.google.gson.Gson;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class WebSocketManager {
    private static volatile WebSocketManager INSTANCE;
    
    private final OkHttpClient client;
    private WebSocket webSocket;
    private final Gson gson = new Gson();

    private final List<AppWebSocketListener> listeners = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler socketHandler = new Handler(Looper.getMainLooper());

    private boolean isConnected = false;
    private boolean manuallyClosed = false;
    private String authToken;
    private int reconnectAttempts = 0;
    private int missedPongs = 0;

    private final Runnable heartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isConnected) {
                return;
            }
            missedPongs += 1;
            if (missedPongs > 3) {
                closeForReconnect();
                scheduleReconnect();
                return;
            }
            WebSocketMessage ping = new WebSocketMessage();
            ping.type = "heartbeat_ping";
            ping.timestamp = System.currentTimeMillis();
            sendMessage(ping);
            socketHandler.postDelayed(this, 25_000L);
        }
    };

    private WebSocketManager() {
        client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build();
    }

    public static WebSocketManager getInstance() {
        if (INSTANCE == null) {
            synchronized (WebSocketManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new WebSocketManager();
                }
            }
        }
        return INSTANCE;
    }

    public void addListener(AppWebSocketListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(AppWebSocketListener listener) {
        listeners.remove(listener);
    }

    public void connect() {
        connect(authToken);
    }

    public void connect(String token) {
        authToken = token;
        manuallyClosed = false;
        if (webSocket != null) {
            webSocket.cancel();
        }
        Request request = new Request.Builder()
                .url(buildSocketUrl(token))
                .build();
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
                isConnected = true;
                reconnectAttempts = 0;
                missedPongs = 0;
                startHeartbeat();
                notifyConnect(true);
            }

            @Override
            public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
                try {
                    WebSocketMessage wsMessage = gson.fromJson(text, WebSocketMessage.class);
                    if (wsMessage != null && "heartbeat_pong".equals(wsMessage.type)) {
                        missedPongs = 0;
                    }
                    notifyMessageReceived(wsMessage);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                isConnected = false;
                stopHeartbeat();
                notifyDisconnect();
                if (!manuallyClosed) {
                    scheduleReconnect();
                }
            }

            @Override
            public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, @Nullable Response response) {
                isConnected = false;
                stopHeartbeat();
                notifyDisconnect();
                if (!manuallyClosed) {
                    scheduleReconnect();
                }
            }
        });
    }

    public void disconnect() {
        manuallyClosed = true;
        stopHeartbeat();
        if (webSocket != null) {
            webSocket.close(1000, "User disconnected");
            webSocket = null;
        }
        isConnected = false;
        notifyDisconnect();
    }

    public boolean sendMessage(WebSocketMessage message) {
        if (webSocket != null && isConnected) {
            String json = gson.toJson(message);
            return webSocket.send(json);
        }
        return false;
    }

    public boolean isConnected() {
        return isConnected;
    }

    private String buildSocketUrl(String token) {
        if (token == null || token.trim().isEmpty()) {
            return BuildConfig.WS_URL;
        }
        String separator = BuildConfig.WS_URL.contains("?") ? "&" : "?";
        return BuildConfig.WS_URL + separator + "token=" + token.trim();
    }

    private void startHeartbeat() {
        stopHeartbeat();
        socketHandler.postDelayed(heartbeatRunnable, 25_000L);
    }

    private void stopHeartbeat() {
        socketHandler.removeCallbacks(heartbeatRunnable);
        missedPongs = 0;
    }

    private void closeForReconnect() {
        if (webSocket != null) {
            webSocket.cancel();
            webSocket = null;
        }
        isConnected = false;
        stopHeartbeat();
        notifyDisconnect();
    }

    private void scheduleReconnect() {
        if (authToken == null || authToken.trim().isEmpty()) {
            return;
        }
        long delay = Math.min(30_000L, (1L << Math.min(reconnectAttempts, 4)) * 1_000L);
        reconnectAttempts += 1;
        socketHandler.postDelayed(() -> {
            if (!manuallyClosed && !isConnected) {
                connect(authToken);
            }
        }, delay);
    }

    private void notifyConnect(boolean isSuccess) {
        mainHandler.post(() -> {
            for (AppWebSocketListener listener : new ArrayList<>(listeners)) {
                listener.onConnect(isSuccess);
            }
        });
    }

    private void notifyMessageReceived(WebSocketMessage message) {
        mainHandler.post(() -> {
            for (AppWebSocketListener listener : new ArrayList<>(listeners)) {
                listener.onMessageReceived(message);
            }
        });
    }

    private void notifyDisconnect() {
        mainHandler.post(() -> {
            for (AppWebSocketListener listener : new ArrayList<>(listeners)) {
                listener.onDisconnect();
            }
        });
    }
}
