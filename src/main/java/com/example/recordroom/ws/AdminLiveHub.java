package com.example.recordroom.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class AdminLiveHub {
    private static final Logger log = LoggerFactory.getLogger(AdminLiveHub.class);
    private static final long BUFFER_DURATION_MS = 5 * 60 * 1000; // 5 minutes

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<LiveEvent> eventBuffer = new ConcurrentLinkedQueue<>();
    private final ObjectMapper om = new ObjectMapper();

    public static class LiveEvent {
        private final String type;
        private final long timestampMs;
        private final Map<String, Object> payload;

        public LiveEvent(String type, long timestampMs, Map<String, Object> payload) {
            this.type = type;
            this.timestampMs = timestampMs;
            this.payload = payload;
        }

        public String getType() { return type; }
        public long getTimestampMs() { return timestampMs; }
        public Map<String, Object> getPayload() { return payload; }
    }

    public void add(WebSocketSession s) {
        sessions.add(s);
        // Send buffered events (last 5 minutes) to new connection
        sendBufferedEvents(s);
    }

    public void remove(WebSocketSession s) {
        sessions.remove(s);
    }

    public int sessionCount() {
        return sessions.size();
    }

    public void emit(Map<String, Object> payload) {
        if (payload == null) return;

        String type = (String) payload.getOrDefault("type", "unknown");
        long now = System.currentTimeMillis();

        // Add to buffer
        eventBuffer.offer(new LiveEvent(type, now, new HashMap<>(payload)));

        // Clean old events (older than 5 minutes)
        long cutoff = now - BUFFER_DURATION_MS;
        while (!eventBuffer.isEmpty()) {
            LiveEvent first = eventBuffer.peek();
            if (first != null && first.getTimestampMs() >= cutoff) break;
            eventBuffer.poll(); // remove old event
        }

        // Broadcast to active sessions
        if (sessions.isEmpty()) return;
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

    private void sendBufferedEvents(WebSocketSession session) {
        if (!session.isOpen() || eventBuffer.isEmpty()) return;

        long now = System.currentTimeMillis();
        long cutoff = now - BUFFER_DURATION_MS;
        List<LiveEvent> recent = new ArrayList<>();

        // Collect events from last 5 minutes
        for (LiveEvent evt : eventBuffer) {
            if (evt.getTimestampMs() >= cutoff) {
                recent.add(evt);
            }
        }

        if (recent.isEmpty()) return;

        try {
            // Send "history_start" marker
            Map<String, Object> marker = new HashMap<>();
            marker.put("type", "history_start");
            marker.put("timestampMs", now);
            marker.put("count", recent.size());
            String markerJson = om.writeValueAsString(marker);
            session.sendMessage(new TextMessage(markerJson));

            // Send buffered events
            for (LiveEvent evt : recent) {
                Map<String, Object> p = new HashMap<>(evt.getPayload());
                p.put("_buffered", true); // mark as buffered
                p.put("_bufferedAgeMs", now - evt.getTimestampMs()); // age in ms
                String json = om.writeValueAsString(p);
                session.sendMessage(new TextMessage(json));
            }

            // Send "history_end" marker
            Map<String, Object> endMarker = new HashMap<>();
            endMarker.put("type", "history_end");
            endMarker.put("timestampMs", now);
            String endMarkerJson = om.writeValueAsString(endMarker);
            session.sendMessage(new TextMessage(endMarkerJson));
        } catch (Exception e) {
            log.debug("sendBufferedEvents failed: {}", e.toString());
        }
    }
}


