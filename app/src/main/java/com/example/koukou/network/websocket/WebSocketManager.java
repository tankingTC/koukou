package com.example.koukou.network.websocket;

import android.os.Handler;
import android.os.Looper;

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
    
    private OkHttpClient client;
    private WebSocket webSocket;
    private final Gson gson = new Gson();
    
    // Web socket target url - you should change this later
    private static final String WS_URL = "ws://10.0.2.2:8080/ws";

    private final List<AppWebSocketListener> listeners = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private boolean isConnected = false;

    private WebSocketManager() {
        client = new OkHttpClient.Builder()
                .readTimeout(10, TimeUnit.SECONDS)
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
        if (webSocket != null) {
            webSocket.cancel();
        }
        Request request = new Request.Builder().url(WS_URL).build();
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
                isConnected = true;
                notifyConnect(true);
            }

            @Override
            public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
                try {
                    WebSocketMessage wsMessage = gson.fromJson(text, WebSocketMessage.class);
                    notifyMessageReceived(wsMessage);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                isConnected = false;
                notifyDisconnect();
            }

            @Override
            public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, @Nullable Response response) {
                isConnected = false;
                notifyDisconnect();
                // Optionally implement exponential backoff reconnection here
            }
        });
    }

    public void disconnect() {
        if (webSocket != null) {
            webSocket.close(1000, "User disconnected");
            webSocket = null;
        }
        isConnected = false;
        notifyDisconnect();
    }

    public void sendMessage(WebSocketMessage message) {
        if (webSocket != null && isConnected) {
            String json = gson.toJson(message);
            webSocket.send(json);
        }
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