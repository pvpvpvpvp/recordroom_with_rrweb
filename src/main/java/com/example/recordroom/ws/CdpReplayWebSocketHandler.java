package com.example.recordroom.ws;

import com.example.recordroom.model.ConsoleEvent;
import com.example.recordroom.model.NetworkEvent;
import com.example.recordroom.service.RecordroomService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
/**
 * Minimal Chrome DevTools Protocol (CDP) backend for replay.
 *
 * DevTools Frontend (devtools_app.html) connects with:
 *   devtools://devtools/bundled/devtools_app.html?ws=localhost:8080/ws/cdp?recordId=xxxx
 *
 * We only implement a small subset that is enough to show Network + Console.
 */
@Component
public class CdpReplayWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(CdpReplayWebSocketHandler.class);

    private final RecordroomService service;
    private final ClockStore clockStore;
    private final ObjectMapper om = new ObjectMapper();

    // Avoid sending huge CDP payloads in a single WS frame (demo safety)
    private static final int MAX_CDP_BODY_CHARS = 200_000;

    // Keep per-session state
    private final Map<String, State> states = new ConcurrentHashMap<>();

    public CdpReplayWebSocketHandler(RecordroomService service, ClockStore clockStore) {
        this.service = service;
        this.clockStore = clockStore;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String recordId = extractQueryParam(session.getUri(), "recordId");
        String mode = extractQueryParam(session.getUri(), "mode");
        String speedStr = extractQueryParam(session.getUri(), "speed");
        double speed = 1.0;
        try { if (speedStr != null && !speedStr.isBlank()) speed = Double.parseDouble(speedStr.trim()); } catch (Exception ignore) {}
        if (speed <= 0) speed = 1.0;
        boolean timed = "timed".equalsIgnoreCase(mode);
        boolean gated = "gated".equalsIgnoreCase(mode);

        if (recordId == null || recordId.isBlank()) {
            log.warn("CDP replay rejected: missing recordId. uri={}", session.getUri());
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        if (!service.recordExists(recordId)) {
            log.warn("CDP replay rejected: record not found. recordId={} uri={}", recordId, session.getUri());
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        State st = new State(recordId);
        st.timedMode = timed;
        st.gatedMode = gated;
        st.speed = speed;
        states.put(session.getId(), st);

        log.info("CDP replay connected. sessionId={}, recordId={}, mode={}, speed={}", session.getId(), recordId, (mode==null?"":mode), speed);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        State st = states.get(session.getId());
        if (st == null) return;

        JsonNode root;
        try {
            root = om.readTree(message.getPayload());
        } catch (Exception e) {
            log.warn("CDP bad json: {}", message.getPayload());
            return;
        }

        long id = root.has("id") ? root.get("id").asLong() : -1;
        String method = root.has("method") ? root.get("method").asText() : null;
        JsonNode params = root.has("params") ? root.get("params") : null;

        if (method == null) return;

        // Reply helper
        java.util.function.Consumer<Map<String, Object>> reply = (result) -> {
            try {
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("id", id);
                resp.put("result", result != null ? result : Collections.emptyMap());
                sendJson(session, resp);
            } catch (Exception ex) {
                log.warn("CDP reply failed", ex);
            }
        };

        // Many methods can be safely acknowledged with an empty result.
        switch (method) {
            case "Runtime.enable": {
                st.runtimeEnabled = true;
                reply.accept(Collections.emptyMap());

                // Create a default execution context so Console can render.
                Map<String, Object> ctx = new LinkedHashMap<>();
                ctx.put("id", 1);
                ctx.put("origin", "recordroom://replay");
                ctx.put("name", "RecordRoom Replay");
                ctx.put("uniqueId", "recordroom-context-1");
                Map<String, Object> ev = new LinkedHashMap<>();
                ev.put("method", "Runtime.executionContextCreated");
                ev.put("params", Map.of("context", ctx));
                sendJson(session, ev);
                break;
            }
            case "Log.enable": {
                st.logEnabled = true;
                reply.accept(Collections.emptyMap());
                ensureLoaded(st);
                if (!st.consoleSent) {
                    st.consoleSent = true;
                    if (st.gatedMode) {
                        new Thread(() -> {
                            try { streamConsoleGated(session, st); } catch (Exception ignore) {}
                        }, "cdp-console-gated-" + st.recordId).start();
                    } else if (st.timedMode) {
                        new Thread(() -> {
                            try { sendConsoleEvents(session, st); } catch (Exception ignore) {}
                        }, "cdp-console-timed-" + st.recordId).start();
                    } else {
                        sendConsoleEvents(session, st);
                    }
                }
                break;
            }
            case "Network.enable": {
                st.networkEnabled = true;
                reply.accept(Collections.emptyMap());
                ensureLoaded(st);
                if (!st.networkSent) {
                    st.networkSent = true;
                    if (st.gatedMode) {
                        new Thread(() -> {
                            try { streamNetworkGated(session, st); } catch (Exception ignore) {}
                        }, "cdp-network-gated-" + st.recordId).start();
                    } else if (st.timedMode) {
                        new Thread(() -> {
                            try { sendNetworkEvents(session, st); } catch (Exception ignore) {}
                        }, "cdp-network-timed-" + st.recordId).start();
                    } else {
                        sendNetworkEvents(session, st);
                    }
                }
                break;
            }
            case "Page.enable":
            case "Inspector.enable":
            case "DOM.enable":
            case "CSS.enable":
            case "Overlay.enable":
            case "Debugger.enable":
            case "Profiler.enable":
            case "Emulation.setTouchEmulationEnabled":
            case "Emulation.setEmulatedMedia":
            case "Emulation.setDeviceMetricsOverride":
            case "Emulation.setUserAgentOverride":
            case "Target.setAutoAttach":
            case "Target.setDiscoverTargets":
            case "Target.setRemoteLocations":
            case "Security.enable":
            case "Network.setCacheDisabled":
            case "Network.clearBrowserCache":
            case "Network.clearBrowserCookies":
            case "Log.startViolationsReport":
                reply.accept(Collections.emptyMap());
                break;

            case "Network.getResponseBody": {
                // params: { requestId }
                String requestId = params != null && params.has("requestId") ? params.get("requestId").asText() : null;
                ensureLoaded(st);

                NetworkEvent ne = requestId != null ? st.networkByRequestId.get(requestId) : null;
                String body = (ne != null && ne.getResponseBody() != null) ? ne.getResponseBody() : "";
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("body", body);
                result.put("base64Encoded", false);
                reply.accept(result);
                break;
            }

            case "Network.getRequestPostData": {
                String requestId = params != null && params.has("requestId") ? params.get("requestId").asText() : null;
                ensureLoaded(st);

                NetworkEvent ne = requestId != null ? st.networkByRequestId.get(requestId) : null;
                String postData = (ne != null && ne.getRequestBody() != null) ? ne.getRequestBody() : "";
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("postData", postData);
                reply.accept(result);
                break;
            }

            case "Runtime.evaluate": {
                // We do not execute JS in replay. Return undefined.
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("result", Map.of(
                        "type", "undefined",
                        "value", null,
                        "description", "undefined"
                ));
                reply.accept(result);
                break;
            }

            default: {
                // Best-effort ack for unknown calls so DevTools UI doesn't get stuck.
                reply.accept(Collections.emptyMap());
                // log.debug("CDP method ignored: {}", method);
                break;
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        states.remove(session.getId());
        log.info("CDP replay disconnected. sessionId={}, status={}", session.getId(), status);
    }

    private void ensureLoaded(State st) {
        if (st.loaded) return;

        // Load all events (paginated) so we can answer getResponseBody and send ordered events.
        List<NetworkEvent> networks = new ArrayList<>();
        RecordroomService.Cursor nc = new RecordroomService.Cursor(0, 0);
        while (true) {
            List<NetworkEvent> batch = service.listNetwork(st.recordId, nc, 500, null);
            if (batch.isEmpty()) break;
            networks.addAll(batch);
            NetworkEvent last = batch.get(batch.size() - 1);
            nc = new RecordroomService.Cursor(last.getStartedAtEpochMs(), last.getSeq());
            if (batch.size() < 500) break;
        }

        List<ConsoleEvent> consoles = new ArrayList<>();
        RecordroomService.Cursor cc = new RecordroomService.Cursor(0, 0);
        while (true) {
            List<ConsoleEvent> batch = service.listConsole(st.recordId, cc, 500, null);
            if (batch.isEmpty()) break;
            consoles.addAll(batch);
            ConsoleEvent last = batch.get(batch.size() - 1);
            cc = new RecordroomService.Cursor(last.getTs(), last.getSeq());
            if (batch.size() < 500) break;
        }

        long baseMs = Long.MAX_VALUE;
        for (NetworkEvent e : networks) baseMs = Math.min(baseMs, e.getStartedAtEpochMs());
        for (ConsoleEvent e : consoles) baseMs = Math.min(baseMs, e.getTs());
        if (baseMs == Long.MAX_VALUE) baseMs = System.currentTimeMillis();

        st.baseMs = baseMs;

        for (NetworkEvent e : networks) {
            st.networkByRequestId.put(e.getEventId(), e);
        }

        st.networks = networks;
        st.consoles = consoles;
        st.loaded = true;
    }

    private void sendNetworkEvents(WebSocketSession session, State st) throws Exception {
        long __lastMs = st.baseMs;
        for (NetworkEvent e : st.networks) {
            if (st.timedMode) {
                long target = e.getStartedAtEpochMs();
                long diff = target - __lastMs;
                if (diff > 0) {
                    long sleepMs = (long)Math.floor(diff / st.speed);
                    if (sleepMs > 0) {
                        try { Thread.sleep(Math.min(sleepMs, 30_000)); } catch (InterruptedException ignore) {}
                    }
                }
                __lastMs = target;
            }
            String requestId = e.getEventId();

            double wallTime = e.getStartedAtEpochMs() / 1000.0;
            double ts = (e.getStartedAtEpochMs() - st.baseMs) / 1000.0;

            Map<String, Object> request = new LinkedHashMap<>();
            request.put("url", e.getUrl());
            request.put("method", e.getMethod());
            request.put("headers", safeMap(e.getRequestHeaders()));

            Map<String, Object> willParams = new LinkedHashMap<>();
            willParams.put("requestId", requestId);
            willParams.put("loaderId", st.recordId);
            willParams.put("documentURL", e.getUrl());
            willParams.put("request", request);
            willParams.put("timestamp", ts);
            willParams.put("wallTime", wallTime);
            willParams.put("initiator", Map.of("type", "other"));
            willParams.put("type", "Fetch");

            sendJson(session, Map.of("method", "Network.requestWillBeSent", "params", willParams));

            if (e.getError() != null && !e.getError().isBlank()) {
                double failedTs = (e.getStartedAtEpochMs() + Math.max(0, e.getDurationMs()) - st.baseMs) / 1000.0;
                Map<String, Object> failed = new LinkedHashMap<>();
                failed.put("requestId", requestId);
                failed.put("timestamp", failedTs);
                failed.put("type", "Fetch");
                failed.put("errorText", e.getError());
                sendJson(session, Map.of("method", "Network.loadingFailed", "params", failed));
                continue;
            }

            double respTs = (e.getStartedAtEpochMs() + Math.max(0, e.getDurationMs()) - st.baseMs) / 1000.0;

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("url", e.getUrl());
            resp.put("status", e.getStatus());
            resp.put("statusText", "");
            resp.put("headers", safeMap(e.getResponseHeaders()));
            resp.put("mimeType", guessMimeType(e.getResponseHeaders()));
            resp.put("connectionReused", false);
            resp.put("connectionId", 0);
            resp.put("encodedDataLength", e.getResponseBody() != null ? e.getResponseBody().length() : 0);

            Map<String, Object> received = new LinkedHashMap<>();
            received.put("requestId", requestId);
            received.put("loaderId", st.recordId);
            received.put("timestamp", respTs);
            received.put("type", "Fetch");
            received.put("response", resp);

            sendJson(session, Map.of("method", "Network.responseReceived", "params", received));

            Map<String, Object> finished = new LinkedHashMap<>();
            finished.put("requestId", requestId);
            finished.put("timestamp", respTs);
            finished.put("encodedDataLength", e.getResponseBody() != null ? e.getResponseBody().length() : 0);

            sendJson(session, Map.of("method", "Network.loadingFinished", "params", finished));
        }
    }

    private void sendConsoleEvents(WebSocketSession session, State st) throws Exception {
        long __lastMs = st.baseMs;
        for (ConsoleEvent e : st.consoles) {
            if (st.timedMode) {
                long target = e.getTs();
                long diff = target - __lastMs;
                if (diff > 0) {
                    long sleepMs = (long)Math.floor(diff / st.speed);
                    if (sleepMs > 0) {
                        try { Thread.sleep(Math.min(sleepMs, 30_000)); } catch (InterruptedException ignore) {}
                    }
                }
                __lastMs = target;
            }
            double ts = (e.getTs() - st.baseMs) / 1000.0;

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("source", "console-api");
            entry.put("level", toLogLevel(e.getLevel()));
            entry.put("text", e.getMessage() != null ? e.getMessage() : "");
            entry.put("timestamp", ts);
            if (e.getStack() != null && !e.getStack().isBlank()) {
                entry.put("stackTrace", Map.of("callFrames", parseStackFrames(e.getStack())));
            }

            Map<String, Object> params = new LinkedHashMap<>();
            params.put("entry", entry);

            sendJson(session, Map.of("method", "Log.entryAdded", "params", params));
        }
    }

    private List<Map<String, Object>> parseStackFrames(String stack) {
        // Very naive parse: keep lines as "function@file:line:col" etc.
        List<Map<String, Object>> frames = new ArrayList<>();
        for (String line : stack.split("\n")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("functionName", "");
            f.put("url", "");
            f.put("lineNumber", 0);
            f.put("columnNumber", 0);
            f.put("description", t);
            frames.add(f);
        }
        return frames;
    }

    private String toLogLevel(String level) {
        if (level == null) return "info";
        switch (level.toLowerCase(Locale.ROOT)) {
            case "error": return "error";
            case "warn": return "warning";
            case "debug": return "verbose";
            default: return "info";
        }
    }

    private String guessMimeType(Map<String, String> headers) {
        if (headers == null) return "text/plain";
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if ("content-type".equalsIgnoreCase(e.getKey())) {
                String v = e.getValue();
                if (v == null) return "text/plain";
                int idx = v.indexOf(';');
                return (idx > 0 ? v.substring(0, idx) : v).trim();
            }
        }
        return "text/plain";
    }
    private String extractMimeTypeFromHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) return "";
        String ct = null;
        for (Map.Entry<String, String> en : headers.entrySet()) {
            if (en.getKey() != null && en.getKey().equalsIgnoreCase("content-type")) {
                ct = en.getValue();
                break;
            }
        }
        if (ct == null) return "";
        int semi = ct.indexOf(';');
        if (semi >= 0) ct = ct.substring(0, semi);
        return ct.trim();
    }



    private Map<String, String> safeMap(Map<String, String> m) {
        if (m == null) return Collections.emptyMap();
        return m;
    }

    private void sendJson(WebSocketSession session, Map<String, Object> obj) throws Exception {
        if (session == null || !session.isOpen()) return;
        String json = om.writeValueAsString(obj);
        synchronized (session) {
            session.sendMessage(new TextMessage(json));
        }
    }

    private String extractQueryParam(URI uri, String name) {
        if (uri == null) return null;

        // NOTE: use getRawQuery then URL-decode per key/value.
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null) return null;

        Map<String, String> map = new HashMap<>();
        for (String part : rawQuery.split("&")) {
            int idx = part.indexOf('=');
            if (idx > 0) {
                String k = part.substring(0, idx);
                String v = part.substring(idx + 1);
                try {
                    k = java.net.URLDecoder.decode(k, java.nio.charset.StandardCharsets.UTF_8);
                    v = java.net.URLDecoder.decode(v, java.nio.charset.StandardCharsets.UTF_8);
                } catch (Exception ignore) {
                    // keep raw if decoding fails
                }
                map.put(k, v);
            }
        }

        String v = map.get(name);
        if (v == null) return null;

        // Defensive sanitization: if user accidentally pasted devtools:// URL twice, trim the tail.
        int cut = v.indexOf("devtools://");
        if (cut >= 0) v = v.substring(0, cut);
        cut = v.indexOf("chrome-devtools://");
        if (cut >= 0) v = v.substring(0, cut);

        // trim whitespace / newlines
        v = v.trim();
        int ws = v.indexOf(' ');
        if (ws > 0) v = v.substring(0, ws);
        int nl = v.indexOf(' ');
        if (nl > 0) v = v.substring(0, nl);

        return v;
    }


    private void streamConsoleGated(WebSocketSession session, State st) throws Exception {
    ensureLoaded(st);

    int idx = 0;
    long lastClock = -1L;

    while (session.isOpen()) {
        ClockState cs = clockStore.get(st.recordId);
        if (cs == null) {
            Thread.sleep(50);
            continue;
        }

        long clock = cs.getTMs();
        String mode = cs.getMode();

        // DevTools cannot "undo" already emitted events. On backward seek, close session.
        if (lastClock >= 0 && clock + 10 < lastClock) {
            log.warn("CDP gated: seek-backward detected. closing session. recordId={}, from={}, to={}",
                    st.recordId, lastClock, clock);
            session.close(new CloseStatus(1011, "seek-backward"));
            return;
        }
        lastClock = clock;

        if ("pause".equalsIgnoreCase(mode)) {
            Thread.sleep(50);
            continue;
        }

        long cutoffAbsMs = (cs.getAbsEpochMs() > 0) ? cs.getAbsEpochMs() : (st.baseMs + Math.max(0L, clock));

            // rrweb baseEpochMs가 들어오면 DevTools timestamp 기준도 rrweb 기준으로 맞춘다
            if (cs.getBaseEpochMs() > 0 && (st.baseMs <= 0 || cs.getBaseEpochMs() < st.baseMs)) {
                st.baseMs = cs.getBaseEpochMs();
            }

        while (idx < st.consoles.size()) {
            ConsoleEvent e = st.consoles.get(idx);
            if (e.getTs() <= cutoffAbsMs) {
                sendOneConsoleEvent(session, st, e);
                idx++;
            } else {
                break;
            }
        }

        Thread.sleep(50);
    }
}

private void streamNetworkGated(WebSocketSession session, State st) throws Exception {
    ensureLoaded(st);

    int idx = 0;
    long lastClock = -1L;

    while (session.isOpen()) {
        ClockState cs = clockStore.get(st.recordId);
        if (cs == null) {
            Thread.sleep(50);
            continue;
        }

        long clock = cs.getTMs();
        String mode = cs.getMode();

        if (lastClock >= 0 && clock + 10 < lastClock) {
            log.warn("CDP gated: seek-backward detected. closing session. recordId={}, from={}, to={}",
                    st.recordId, lastClock, clock);
            session.close(new CloseStatus(1011, "seek-backward"));
            return;
        }
        lastClock = clock;

        if ("pause".equalsIgnoreCase(mode)) {
            Thread.sleep(50);
            continue;
        }

        long cutoffAbsMs = (cs.getAbsEpochMs() > 0) ? cs.getAbsEpochMs() : (st.baseMs + Math.max(0L, clock));

            // rrweb baseEpochMs가 들어오면 DevTools timestamp 기준도 rrweb 기준으로 맞춘다
            if (cs.getBaseEpochMs() > 0 && (st.baseMs <= 0 || cs.getBaseEpochMs() < st.baseMs)) {
                st.baseMs = cs.getBaseEpochMs();
            }

        while (idx < st.networks.size()) {
            NetworkEvent e = st.networks.get(idx);
            if (e.getStartedAtEpochMs() <= cutoffAbsMs) {
                sendOneNetworkEventBundle(session, st, e);
                idx++;
            } else {
                break;
            }
        }

        Thread.sleep(50);
    }
}

private void sendOneConsoleEvent(WebSocketSession session, State st, ConsoleEvent e) throws Exception {
    double ts = (e.getTs() - st.baseMs) / 1000.0;

    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("source", "console-api");
    entry.put("level", toLogLevel(e.getLevel()));
    entry.put("text", e.getMessage() != null ? e.getMessage() : "");
    entry.put("timestamp", ts);
    if (e.getStack() != null && !e.getStack().isBlank()) {
        entry.put("stackTrace", Map.of("callFrames", parseStackFrames(e.getStack())));
    }

    Map<String, Object> params = new LinkedHashMap<>();
    params.put("entry", entry);

    sendJson(session, Map.of("method", "Log.entryAdded", "params", params));
}

private void sendOneNetworkEventBundle(WebSocketSession session, State st, NetworkEvent e) throws Exception {
    String requestId = e.getEventId();

    // requestWillBeSent (no huge postData here; body는 별도 API로 필요 시 확장)
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("url", e.getUrl());
    request.put("method", e.getMethod());
    request.put("headers", safeMap(e.getRequestHeaders()));

    double ts = (e.getStartedAtEpochMs() - st.baseMs) / 1000.0;
    double wallTime = e.getStartedAtEpochMs() / 1000.0;

    Map<String, Object> willParams = new LinkedHashMap<>();
    willParams.put("requestId", requestId);
    willParams.put("loaderId", st.recordId);
    willParams.put("documentURL", e.getUrl());
    willParams.put("request", request);
    willParams.put("timestamp", ts);
    willParams.put("wallTime", wallTime);
    willParams.put("initiator", Map.of("type", "other"));
    willParams.put("type", "Fetch");

    sendJson(session, Map.of("method", "Network.requestWillBeSent", "params", willParams));

    // responseReceived
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("url", e.getUrl());
    response.put("status", e.getStatus());
    response.put("statusText", "");
    response.put("headers", safeMap(e.getResponseHeaders()));
    response.put("mimeType", guessMimeType(e.getResponseHeaders()));

    Map<String, Object> respParams = new LinkedHashMap<>();
    respParams.put("requestId", requestId);
    respParams.put("loaderId", st.recordId);
    respParams.put("timestamp", ts);
    respParams.put("type", "XHR");
    respParams.put("response", response);

    sendJson(session, Map.of("method", "Network.responseReceived", "params", respParams));

    // loadingFinished
    Map<String, Object> finished = new LinkedHashMap<>();
    finished.put("requestId", requestId);
    finished.put("timestamp", ts);

    sendJson(session, Map.of("method", "Network.loadingFinished", "params", finished));
}

static class State {
        boolean gatedMode;

        boolean timedMode;
        double speed = 1.0;

        final String recordId;
        boolean loaded = false;

        boolean runtimeEnabled = false;
        boolean networkEnabled = false;
        boolean logEnabled = false;

        boolean networkSent = false;
        boolean consoleSent = false;

        long baseMs = 0L;

        List<NetworkEvent> networks = List.of();
        List<ConsoleEvent> consoles = List.of();

        Map<String, NetworkEvent> networkByRequestId = new HashMap<>();

        State(String recordId) {
            this.recordId = recordId;
        }
    }

    private String truncateBody(String s) {
        if (s == null) return "";
        if (s.length() <= MAX_CDP_BODY_CHARS) return s;
        return s.substring(0, MAX_CDP_BODY_CHARS) + "\n...[truncated]";
    }

}