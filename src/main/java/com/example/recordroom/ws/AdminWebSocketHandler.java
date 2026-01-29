package com.example.recordroom.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class AdminWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(AdminWebSocketHandler.class);

    private final AdminLiveHub hub;

    public AdminWebSocketHandler(AdminLiveHub hub) {
        this.hub = hub;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        hub.add(session);
        log.info("ADMIN ws connected. sessionId={} total={}", session.getId(), hub.sessionCount());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        hub.remove(session);
        log.info("ADMIN ws closed. sessionId={} total={}", session.getId(), hub.sessionCount());
    }
}


