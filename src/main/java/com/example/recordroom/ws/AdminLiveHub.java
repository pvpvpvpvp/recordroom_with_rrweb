package com.example.recordroom.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AdminLiveHub {
    private static final Logger log = LoggerFactory.getLogger(AdminLiveHub.class);

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper om = new ObjectMapper();

    public void add(WebSocketSession s) {
        sessions.add(s);
    }

    public void remove(WebSocketSession s) {
        sessions.remove(s);
    }

    public int sessionCount() {
        return sessions.size();
    }

    public void emit(Map<String, Object> payload) {
        if (payload == null || sessions.isEmpty()) return;
        try {
            String json = om.writeValueAsString(payload);
            TextMessage msg = new TextMessage(json);
            for (WebSocketSession s : sessions) {
                try {
                    if (s.isOpen()) s.sendMessage(msg);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            log.debug("admin emit failed: {}", e.toString());
        }
    }
}


