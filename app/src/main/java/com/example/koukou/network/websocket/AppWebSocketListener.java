package com.example.koukou.network.websocket;

import com.example.koukou.network.model.WebSocketMessage;

public interface AppWebSocketListener {
    void onConnect(boolean isSuccess);
    void onMessageReceived(WebSocketMessage message);
    void onDisconnect();
}
