package com.example.koukou.network.websocket;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.koukou.network.ServerEndpointPolicy;
import com.example.koukou.network.UnsafeTlsSupport;
import com.example.koukou.network.model.WebSocketMessage;
import com.google.gson.Gson;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class WebSocketManager {
    private static final String TAG = "WebSocketManager";
    private static volatile WebSocketManager INSTANCE;

    private final OkHttpClient standardClient;
    private final OkHttpClient fallbackClient;
    private final Gson gson = new Gson();
    private final List<AppWebSocketListener> listeners = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler socketHandler = new Handler(Looper.getMainLooper());

    private WebSocket webSocket;
    private boolean isConnected = false;
    private boolean manuallyClosed = false;
    private String authToken;
    private int reconnectAttempts = 0;
    private int missedPongs = 0;
    private List<String> candidateUrls = Collections.emptyList();
    private int currentCandidateIndex = 0;

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
        standardClient = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build();
        fallbackClient = UnsafeTlsSupport.apply(new OkHttpClient.Builder()
                        .readTimeout(0, TimeUnit.SECONDS)
                        .writeTimeout(10, TimeUnit.SECONDS)
                        .pingInterval(30, TimeUnit.SECONDS))
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
        authToken = token == null ? null : token.trim();
        manuallyClosed = false;
        reconnectAttempts = 0;
        candidateUrls = ServerEndpointPolicy.webSocketUrls(authToken);
        connectToCandidate(0);
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
            return webSocket.send(gson.toJson(message));
        }
        return false;
    }

    public boolean isConnected() {
        return isConnected;
    }

    private void connectToCandidate(int index) {
        currentCandidateIndex = index;
        stopHeartbeat();
        isConnected = false;
        if (webSocket != null) {
            webSocket.cancel();
            webSocket = null;
        }

        if (candidateUrls == null || candidateUrls.isEmpty() || index >= candidateUrls.size()) {
            notifyConnect(false);
            notifyDisconnect();
            return;
        }

        String socketUrl = candidateUrls.get(index);
        Request request;
        try {
            request = new Request.Builder().url(socketUrl).build();
        } catch (Exception e) {
            Log.e(TAG, "Invalid websocket url: " + socketUrl, e);
            if (!tryNextCandidate()) {
                notifyConnect(false);
                notifyDisconnect();
            }
            return;
        }

        OkHttpClient activeClient = ServerEndpointPolicy.requiresUnsafeTls(socketUrl) ? fallbackClient : standardClient;
        try {
            webSocket = activeClient.newWebSocket(request, createListener(index, socketUrl));
        } catch (Exception e) {
            Log.e(TAG, "WebSocket startup failed for " + socketUrl, e);
            if (!tryNextCandidate()) {
                notifyConnect(false);
                notifyDisconnect();
            }
        }
    }

    private WebSocketListener createListener(int candidateIndex, String socketUrl) {
        return new WebSocketListener() {
            @Override
            public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
                isConnected = true;
                reconnectAttempts = 0;
                missedPongs = 0;
                currentCandidateIndex = candidateIndex;
                startHeartbeat();
                notifyConnect(true);
                Log.i(TAG, "WebSocket connected via " + socketUrl);
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
                    Log.e(TAG, "Failed to parse websocket message: " + text, e);
                }
            }

            @Override
            public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                isConnected = false;
                stopHeartbeat();
                notifyDisconnect();
                if (!manuallyClosed && !tryNextCandidate()) {
                    scheduleReconnect();
                }
            }

            @Override
            public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, @Nullable Response response) {
                isConnected = false;
                stopHeartbeat();
                notifyDisconnect();
                Log.e(TAG, "WebSocket failure on " + socketUrl, t);
                if (!manuallyClosed && !tryNextCandidate()) {
                    scheduleReconnect();
                }
            }
        };
    }

    private boolean tryNextCandidate() {
        int nextIndex = currentCandidateIndex + 1;
        if (candidateUrls == null || nextIndex >= candidateUrls.size()) {
            return false;
        }
        socketHandler.post(() -> connectToCandidate(nextIndex));
        return true;
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
                candidateUrls = ServerEndpointPolicy.webSocketUrls(authToken);
                connectToCandidate(0);
            }
        }, delay);
    }

    private void notifyConnect(boolean isSuccess) {
        mainHandler.post(() -> {
            for (AppWebSocketListener listener : new ArrayList<>(listeners)) {
                try {
                    listener.onConnect(isSuccess);
                } catch (Throwable t) {
                    Log.e(TAG, "WebSocket connect callback crashed", t);
                }
            }
        });
    }

    private void notifyMessageReceived(WebSocketMessage message) {
        mainHandler.post(() -> {
            for (AppWebSocketListener listener : new ArrayList<>(listeners)) {
                try {
                    listener.onMessageReceived(message);
                } catch (Throwable t) {
                    Log.e(TAG, "WebSocket message callback crashed", t);
                }
            }
        });
    }

    private void notifyDisconnect() {
        mainHandler.post(() -> {
            for (AppWebSocketListener listener : new ArrayList<>(listeners)) {
                try {
                    listener.onDisconnect();
                } catch (Throwable t) {
                    Log.e(TAG, "WebSocket disconnect callback crashed", t);
                }
            }
        });
    }
}
