package com.example.recordroom.ws;

import com.example.recordroom.model.BreadcrumbEventIngestRequest;
import com.example.recordroom.model.ConsoleEventIngestRequest;
import com.example.recordroom.model.NetworkEventIngestRequest;
import com.example.recordroom.model.RrwebBatchIngestRequest;
import com.example.recordroom.service.RecordroomService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@Component
public class IngestWebSocketHandler extends TextWebSocketHandler {

    private final RecordroomService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IngestWebSocketHandler(RecordroomService service) {
        this.service = service;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String recordId = extractQueryParam(session.getUri(), "recordId");
        if (recordId == null || recordId.isBlank() || !service.recordExists(recordId)) {
            session.close(CloseStatus.BAD_DATA);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String recordId = extractQueryParam(session.getUri(), "recordId");
        if (recordId == null || recordId.isBlank() || !service.recordExists(recordId)) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        JsonNode node = objectMapper.readTree(message.getPayload());
        String type = node.has("type") ? node.get("type").asText() : "";

        try {
            if ("console".equals(type)) {
                ConsoleEventIngestRequest req = objectMapper.treeToValue(node, ConsoleEventIngestRequest.class);
                service.saveConsole(recordId, req);
            } else if ("network".equals(type)) {
                NetworkEventIngestRequest req = objectMapper.treeToValue(node, NetworkEventIngestRequest.class);
                service.saveNetwork(recordId, req);
            } else if ("breadcrumb".equals(type)) {
                BreadcrumbEventIngestRequest req = objectMapper.treeToValue(node, BreadcrumbEventIngestRequest.class);
                service.saveBreadcrumb(recordId, req);
            } else if ("rrweb".equals(type)) {
                RrwebBatchIngestRequest req = objectMapper.treeToValue(node, RrwebBatchIngestRequest.class);
                service.saveRrwebBatch(recordId, req);
            }
        } catch (Exception ignored) {
            // ignore malformed event
        }
    }

    private String extractQueryParam(URI uri, String name) {
        if (uri == null) return null;
        String query = uri.getQuery();
        if (query == null) return null;
        Map<String, String> map = new HashMap<>();
        for (String part : query.split("&")) {
            int idx = part.indexOf('=');
            if (idx > 0) {
                map.put(part.substring(0, idx), part.substring(idx + 1));
            }
        }
        return map.get(name);
    }
}
