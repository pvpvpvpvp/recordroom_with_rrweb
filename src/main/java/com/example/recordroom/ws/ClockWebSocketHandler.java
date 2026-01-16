package com.example.recordroom.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class ClockWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(ClockWebSocketHandler.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final ClockStore clockStore;

    public ClockWebSocketHandler(ClockStore clockStore) {
        this.clockStore = clockStore;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("CLOCK ws connected. sessionId={}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode root = mapper.readTree(message.getPayload());
        String type = root.path("type").asText("");
        if (!"clock".equals(type)) return;

        String recordId = root.path("recordId").asText("");
        if (recordId == null || recordId.isBlank()) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        long tMs = root.path("tMs").asLong(0L);
        long baseEpochMs = root.path("baseEpochMs").asLong(0L);
        long absEpochMs = root.path("absEpochMs").asLong(0L);

        // absEpochMs가 안 오면 baseEpochMs + tMs로 계산
        if (absEpochMs <= 0 && baseEpochMs > 0) {
            absEpochMs = baseEpochMs + Math.max(0L, tMs);
        }

        String mode = root.path("mode").asText("play");
        double speed = root.path("speed").asDouble(1.0);

        clockStore.update(recordId, tMs, baseEpochMs, absEpochMs, mode, speed);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("CLOCK ws disconnected. sessionId={}, status={}", session.getId(), status);
    }
}
