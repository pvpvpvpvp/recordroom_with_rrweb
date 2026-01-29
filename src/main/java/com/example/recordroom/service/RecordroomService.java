package com.example.recordroom.service;

import com.example.recordroom.model.BreadcrumbEvent;
import com.example.recordroom.model.BreadcrumbEventIngestRequest;
import com.example.recordroom.model.ConsoleEvent;
import com.example.recordroom.model.ConsoleEventIngestRequest;
import com.example.recordroom.model.CreateRecordRequest;
import com.example.recordroom.model.NetworkEvent;
import com.example.recordroom.model.NetworkEventIngestRequest;
import com.example.recordroom.model.Record;
import com.example.recordroom.model.RrwebBatchIngestRequest;
import com.example.recordroom.model.RrwebEventEnvelope;
import com.example.recordroom.model.RecordStats;
import com.example.recordroom.model.AdminOverviewResponse;
import com.example.recordroom.model.RrwebListResponse;
import com.example.recordroom.model.SessionViewResponse;
import com.example.recordroom.model.TimelineResponse;
import com.example.recordroom.persistence.*;
import com.example.recordroom.ws.AdminLiveHub;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import com.example.recordroom.persistence.RrwebEventEntity;
import java.util.UUID;

@Service
public class RecordroomService {

    private final RecordRepository recordRepository;
    private final ConsoleEventRepository consoleRepo;
    private final NetworkEventRepository networkRepo;
    private final BreadcrumbEventRepository breadcrumbRepo;
    private final RrwebEventRepository rrwebRepo;
    private final AdminLiveHub adminLiveHub;

    private final ObjectMapper om = new ObjectMapper();

    public RecordroomService(RecordRepository recordRepository,
                             ConsoleEventRepository consoleRepo,
                             NetworkEventRepository networkRepo,
                             BreadcrumbEventRepository breadcrumbRepo,
                             RrwebEventRepository rrwebRepo,
                             AdminLiveHub adminLiveHub) {
        this.recordRepository = recordRepository;
        this.consoleRepo = consoleRepo;
        this.networkRepo = networkRepo;
        this.breadcrumbRepo = breadcrumbRepo;
        this.rrwebRepo = rrwebRepo;
        this.adminLiveHub = adminLiveHub;
    }

    public boolean recordExists(String recordId) {
        return recordRepository.existsById(recordId);
    }

    @Transactional
    public Record createRecord(CreateRecordRequest req, String recordId, String sessionId, long nowEpochMs) {
        String previous = (req.getPreviousRecordId() == null || req.getPreviousRecordId().isBlank()) ? null : req.getPreviousRecordId();
        String pageUrl = req.getPageUrl() == null ? "" : req.getPageUrl();
        String userAgent = req.getUserAgent() == null ? "" : req.getUserAgent();
        String appVersion = req.getAppVersion() == null ? "" : req.getAppVersion();
        String deviceInfo = req.getDeviceInfo() == null ? "" : req.getDeviceInfo();
        String userId = req.getUserId() == null ? "" : req.getUserId();
        String userEmail = req.getUserEmail() == null ? "" : req.getUserEmail();

        RecordEntity entity = new RecordEntity(recordId, sessionId, previous, pageUrl, userAgent, appVersion, deviceInfo, userId, userEmail, nowEpochMs);
        recordRepository.save(entity);

        // realtime: new record (QA)
        try {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("type", "record_created");
            p.put("ts", nowEpochMs);
            p.put("recordId", recordId);
            p.put("sessionId", sessionId);
            p.put("pageUrl", pageUrl);
            p.put("userId", userId);
            p.put("userEmail", userEmail);
            p.put("deviceInfo", deviceInfo);
            adminLiveHub.emit(p);
        } catch (Exception ignored) {}

        return new Record(entity.getRecordId(), entity.getSessionId(), entity.getPreviousRecordId(),
                entity.getPageUrl(), entity.getUserAgent(), entity.getAppVersion(), entity.getDeviceInfo(),
                entity.getUserId(), entity.getUserEmail(), entity.getCreatedAtEpochMs());
    }

    public Record getRecord(String recordId) {
        RecordEntity e = recordRepository.findById(recordId).orElse(null);
        if (e == null) return null;
        return new Record(e.getRecordId(), e.getSessionId(), e.getPreviousRecordId(),
                e.getPageUrl(), e.getUserAgent(), e.getAppVersion(), e.getDeviceInfo(),
                e.getUserId(), e.getUserEmail(), e.getCreatedAtEpochMs());
    }

    // ---------- ingest ----------
    @Transactional
    public ConsoleEvent saveConsole(String recordId, ConsoleEventIngestRequest req) {
        String eventId = "c_" + UUID.randomUUID();
        String level = (req.getLevel() == null || req.getLevel().isBlank()) ? "log" : req.getLevel();
        String message = req.getMessage() == null ? "" : req.getMessage();
        String stack = req.getStack();

        ConsoleEventEntity e = new ConsoleEventEntity(eventId, recordId, level, message, stack, req.getTs(), req.getSeq());
        consoleRepo.save(e);

        // realtime: console error/warn (QA)
        try {
            if ("error".equalsIgnoreCase(level) || "warn".equalsIgnoreCase(level)) {
                RecordEntity r = recordRepository.findById(recordId).orElse(null);
                String sid = (r == null) ? null : r.getSessionId();
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("type", "console_" + level.toLowerCase());
                p.put("ts", req.getTs());
                p.put("recordId", recordId);
                p.put("sessionId", sid);
                p.put("eventId", eventId);
                p.put("message", message.length() > 500 ? message.substring(0, 500) : message);
                p.put("stack", (stack != null && stack.length() > 2000) ? stack.substring(0, 2000) : stack);
                adminLiveHub.emit(p);
            }
        } catch (Exception ignored) {}

        return new ConsoleEvent(eventId, recordId, "console", level, message, stack, req.getTs(), req.getSeq());
    }

    @Transactional
    public NetworkEvent saveNetwork(String recordId, NetworkEventIngestRequest req) {
        String eventId = "n_" + UUID.randomUUID();
        String method = (req.getMethod() == null || req.getMethod().isBlank()) ? "GET" : req.getMethod();
        String url = (req.getUrl() == null) ? "" : req.getUrl();

        String reqHeadersJson = toJson(req.getRequestHeaders());
        String resHeadersJson = toJson(req.getResponseHeaders());

        NetworkEventEntity e = new NetworkEventEntity(
                eventId,
                recordId,
                req.getClientRequestId(),
                method,
                url,
                req.getStatus(),
                reqHeadersJson,
                req.getRequestBody(),
                resHeadersJson,
                req.getResponseBody(),
                req.getStartedAtEpochMs(),
                req.getDurationMs(),
                req.getError(),
                req.getSeq()
        );
        networkRepo.save(e);

        // realtime: 4xx/5xx/slow (QA)
        try {
            int status = req.getStatus();
            long dur = req.getDurationMs();
            boolean httpErr = status >= 400;
            boolean slow = dur > 2000;
            if (httpErr || slow) {
                RecordEntity r = recordRepository.findById(recordId).orElse(null);
                String sid = (r == null) ? null : r.getSessionId();
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("type", httpErr ? "network_http_error" : "network_slow");
                p.put("ts", req.getStartedAtEpochMs());
                p.put("recordId", recordId);
                p.put("sessionId", sid);
                p.put("eventId", eventId);
                p.put("method", method);
                p.put("url", url.length() > 800 ? url.substring(0, 800) : url);
                p.put("status", status);
                p.put("durationMs", dur);
                adminLiveHub.emit(p);
            }
        } catch (Exception ignored) {}

        return new NetworkEvent(
                eventId,
                recordId,
                "network",
                req.getClientRequestId(),
                method,
                url,
                req.getStatus(),
                safeMap(req.getRequestHeaders()),
                req.getRequestBody(),
                safeMap(req.getResponseHeaders()),
                req.getResponseBody(),
                req.getStartedAtEpochMs(),
                req.getDurationMs(),
                req.getError(),
                req.getSeq()
        );
    }

    @Transactional
    public BreadcrumbEvent saveBreadcrumb(String recordId, BreadcrumbEventIngestRequest req) {
        String eventId = "b_" + UUID.randomUUID();
        String name = req.getName() == null ? "" : req.getName();
        String message = req.getMessage() == null ? "" : req.getMessage();
        String dataJson = toJson(req.getData());

        BreadcrumbEventEntity e = new BreadcrumbEventEntity(eventId, recordId, name, message, dataJson, req.getTs(), req.getSeq());
        breadcrumbRepo.save(e);

        return new BreadcrumbEvent(eventId, recordId, "breadcrumb", name, message, safeMap(req.getData()), req.getTs(), req.getSeq());
    }


    @Transactional
    public int saveRrwebBatch(String recordId, RrwebBatchIngestRequest req) {
        if (req == null || req.getEvents() == null || req.getEvents().isEmpty()) return 0;

        int saved = 0;
        for (RrwebEventEnvelope ev : req.getEvents()) {
            if (ev == null) continue;
            String eventId = "r_" + UUID.randomUUID();
            long ts = ev.getTs();
            long seq = ev.getSeq();

            String payloadJson = "{}";
            try {
                if (ev.getPayload() != null) payloadJson = om.writeValueAsString(ev.getPayload());
            } catch (Exception ignored) {}

            rrwebRepo.save(new RrwebEventEntity(eventId, recordId, ts, seq, payloadJson));
            saved += 1;
        }
        return saved;
    }

    // ---------- list with cursor ----------
    public List<ConsoleEvent> listConsole(String recordId, Cursor cursor, int limit, String level) {
        PageRequest pr = PageRequest.of(0, limit);
        List<ConsoleEventEntity> rows;
        if (level != null && !level.isBlank() && !"all".equalsIgnoreCase(level)) {
            rows = consoleRepo.findAfterWithLevel(recordId, level, cursor.ts, cursor.seq, pr);
        } else {
            rows = consoleRepo.findAfter(recordId, cursor.ts, cursor.seq, pr);
        }
        List<ConsoleEvent> out = new ArrayList<>();
        for (ConsoleEventEntity e : rows) {
            out.add(new ConsoleEvent(e.getEventId(), e.getRecordId(), "console", e.getLevel(), e.getMessage(), e.getStack(), e.getTs(), e.getSeq()));
        }
        return out;
    }

    public List<NetworkEvent> listNetwork(String recordId, Cursor cursor, int limit, Integer statusMin) {
        PageRequest pr = PageRequest.of(0, limit);
        List<NetworkEventEntity> rows;
        if (statusMin != null) rows = networkRepo.findAfterWithStatusMin(recordId, statusMin, cursor.ts, cursor.seq, pr);
        else rows = networkRepo.findAfter(recordId, cursor.ts, cursor.seq, pr);

        List<NetworkEvent> out = new ArrayList<>();
        for (NetworkEventEntity e : rows) {
            out.add(toModel(e));
        }
        return out;
    }

    public NetworkEvent getNetworkDetail(String recordId, String eventId) {
        NetworkEventEntity e = networkRepo.findByRecordIdAndEventId(recordId, eventId);
        if (e == null) return null;
        return toModel(e);
    }

    public List<BreadcrumbEvent> listBreadcrumbs(String recordId, Cursor cursor, int limit, String name) {
        PageRequest pr = PageRequest.of(0, limit);
        List<BreadcrumbEventEntity> rows;
        if (name != null && !name.isBlank() && !"all".equalsIgnoreCase(name)) {
            rows = breadcrumbRepo.findAfterWithName(recordId, name, cursor.ts, cursor.seq, pr);
        } else {
            rows = breadcrumbRepo.findAfter(recordId, cursor.ts, cursor.seq, pr);
        }
        List<BreadcrumbEvent> out = new ArrayList<>();
        for (BreadcrumbEventEntity e : rows) {
            out.add(new BreadcrumbEvent(e.getEventId(), e.getRecordId(), "breadcrumb",
                    e.getName(), e.getMessage(), fromJsonMap(e.getDataJson()), e.getTs(), e.getSeq()));
        }
        return out;
    }

    public RrwebListResponse listRrweb(String recordId, Cursor cursor, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 5000));
        PageRequest pr = PageRequest.of(0, safeLimit);

        List<RrwebEventEntity> rows = rrwebRepo.findAfter(recordId, cursor.ts, cursor.seq, pr);

        List<JsonNode> events = new ArrayList<>();
        long lastTs = cursor.ts;
        long lastSeq = cursor.seq;

        for (RrwebEventEntity e : rows) {
            lastTs = e.getTsEpochMs();
            lastSeq = e.getSeq();

            try {
                JsonNode n = om.readTree(e.getPayloadJson() == null ? "{}" : e.getPayloadJson());
                events.add(n);
            } catch (Exception ignored) {
                // skip malformed json
            }
        }

        String nextAfter = cursor.toString();
        if (!rows.isEmpty()) {
            nextAfter = lastTs + "_" + lastSeq;
        }
        return new RrwebListResponse(events, nextAfter);
    }

    public TimelineResponse listTimeline(String recordId, Cursor cursor, int limit, Set<String> kinds, String consoleLevel, Integer statusMin, Long tsFrom, Long tsTo) {
        int per = Math.max(limit, 1);

        List<TimelineResponse.TimelineItem> items = new ArrayList<>();

        if (kinds.contains("console")) {
            for (ConsoleEvent c : listConsole(recordId, cursor, per, consoleLevel)) {
                if (tsFrom != null && c.getTs() < tsFrom) continue;
                if (tsTo != null && c.getTs() > tsTo) continue;
                items.add(new TimelineResponse.TimelineItem("console", c.getEventId(), c.getTs(), c.getSeq(),
                        c.getLevel(), c.getMessage(), c.getStack(),
                        null, null, 0, null));
            }
        }

        if (kinds.contains("network")) {
            for (NetworkEvent n : listNetwork(recordId, cursor, per, statusMin)) {
                if (tsFrom != null && n.getStartedAtEpochMs() < tsFrom) continue;
                if (tsTo != null && n.getStartedAtEpochMs() > tsTo) continue;
                items.add(new TimelineResponse.TimelineItem("network", n.getEventId(), n.getStartedAtEpochMs(), n.getSeq(),
                        null, null, null,
                        n.getMethod(), n.getUrl(), n.getStatus(), null));
            }
        }

        if (kinds.contains("breadcrumb")) {
            for (BreadcrumbEvent b : listBreadcrumbs(recordId, cursor, per, null)) {
                if (tsFrom != null && b.getTs() < tsFrom) continue;
                if (tsTo != null && b.getTs() > tsTo) continue;
                items.add(new TimelineResponse.TimelineItem("breadcrumb", b.getEventId(), b.getTs(), b.getSeq(),
                        null, b.getMessage(), null,
                        null, null, 0, b.getName()));
            }
        }

        items.sort(Comparator.comparingLong(TimelineResponse.TimelineItem::getTs)
                .thenComparingLong(TimelineResponse.TimelineItem::getSeq));

        if (items.size() > limit) items = items.subList(0, limit);

        String nextAfter = cursor.toString();
        if (!items.isEmpty()) {
            TimelineResponse.TimelineItem last = items.get(items.size() - 1);
            nextAfter = last.getTs() + "_" + last.getSeq();
        }

        return new TimelineResponse(items, nextAfter);
    }

    // ---------- cursor ----------
    public static class Cursor {
        public final long ts;
        public final long seq;

        public Cursor(long ts, long seq) {
            this.ts = ts;
            this.seq = seq;
        }

        public static Cursor parse(String s) {
            if (s == null || s.isBlank()) return new Cursor(0, 0);
            String t = s.trim();
            String[] parts = t.contains("_") ? t.split("_", 2) : t.split(",", 2);
            try {
                long ts = Long.parseLong(parts[0].trim());
                long seq = (parts.length > 1) ? Long.parseLong(parts[1].trim()) : 0;
                return new Cursor(ts, seq);
            } catch (Exception e) {
                return new Cursor(0, 0);
            }
        }

        @Override
        public String toString() { return ts + "_" + seq; }
    }

    // ---------- helpers ----------
    private String toJson(Map<String, String> m) {
        if (m == null) return "{}";
        try { return om.writeValueAsString(m); }
        catch (Exception e) { return "{}"; }
    }

    private Map<String, String> fromJsonMap(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            return om.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }



    // ---------- replay ----------
    public com.example.recordroom.model.ReplayNetworkResponse replayNetwork(String recordId, String eventId, String baseUrl, boolean allowNonIdempotent) {
        NetworkEventEntity e = networkRepo.findByRecordIdAndEventId(recordId, eventId);
        if (e == null) return null;

        String method = (e.getMethod() == null) ? "GET" : e.getMethod().toUpperCase();
        boolean isIdempotent = method.equals("GET") || method.equals("HEAD") || method.equals("OPTIONS");
        if (!isIdempotent && !allowNonIdempotent) {
            throw new IllegalArgumentException("Non-idempotent method blocked. Set allowNonIdempotent=true for method=" + method);
        }

        String url = (e.getUrl() == null) ? "" : e.getUrl();
        String replayUrl = url;
        if (url.startsWith("/")) {
            replayUrl = baseUrl + url;
        }

        // Only allow same-origin replays (demo safety)
        if (replayUrl.startsWith("http://") || replayUrl.startsWith("https://")) {
            if (!replayUrl.startsWith(baseUrl)) {
                throw new IllegalArgumentException("Replay blocked (different origin). baseUrl=" + baseUrl + ", url=" + replayUrl);
            }
        } else {
            // unknown schema
            throw new IllegalArgumentException("Replay blocked (unsupported url): " + replayUrl);
        }

        org.springframework.web.client.RestTemplate rt = new org.springframework.web.client.RestTemplate();

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        java.util.Map<String, String> reqHeaders = fromJsonMap(e.getRequestHeadersJson());
        for (java.util.Map.Entry<String, String> kv : reqHeaders.entrySet()) {
            String k = kv.getKey();
            if (k == null) continue;
            String lk = k.toLowerCase();
            // safety: don't forward sensitive / hop-by-hop / browser-only headers
            if (lk.equals("cookie") || lk.equals("authorization") || lk.equals("host") || lk.equals("content-length") || lk.equals("origin") || lk.equals("referer")) continue;
            if (lk.startsWith("sec-") || lk.startsWith("proxy-") || lk.equals("connection") || lk.equals("accept-encoding")) continue;
            headers.add(k, kv.getValue());
        }

        String body = e.getRequestBody();
        org.springframework.http.HttpEntity<String> entity;
        if (body != null && !body.isBlank() && !(method.equals("GET") || method.equals("HEAD"))) {
            // if content-type is missing but body looks like json, set a default
            if (!headers.containsKey(org.springframework.http.HttpHeaders.CONTENT_TYPE)) {
                String t = body.trim();
                if ((t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"))) {
                    headers.set(org.springframework.http.HttpHeaders.CONTENT_TYPE, org.springframework.http.MediaType.APPLICATION_JSON_VALUE);
                }
            }
            entity = new org.springframework.http.HttpEntity<>(body, headers);
        } else {
            entity = new org.springframework.http.HttpEntity<>(headers);
        }

        long started = System.currentTimeMillis();
        org.springframework.http.ResponseEntity<String> res;
        try {
            res = rt.exchange(replayUrl, org.springframework.http.HttpMethod.valueOf(method), entity, String.class);
        } catch (org.springframework.web.client.HttpStatusCodeException ex) {
            // capture body even for error status
            long dur = System.currentTimeMillis() - started;
            String rb = ex.getResponseBodyAsString();
            rb = truncate(rb, 20000);
            return new com.example.recordroom.model.ReplayNetworkResponse(
                    recordId,
                    eventId,
                    method,
                    url,
                    replayUrl,
                    e.getStatus(),
                    ex.getRawStatusCode(),
                    dur,
                    rb
            );
        }
        long dur = System.currentTimeMillis() - started;
        String rb = res.getBody();
        rb = truncate(rb, 20000);

        return new com.example.recordroom.model.ReplayNetworkResponse(
                recordId,
                eventId,
                method,
                url,
                replayUrl,
                e.getStatus(),
                res.getStatusCodeValue(),
                dur,
                rb
        );
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        if (s.length() <= max) return s;
        return s.substring(0, max) + "\n...[truncated]";
    }


    private Map<String, String> safeMap(Map<String, String> m) {
        if (m == null) return new LinkedHashMap<>();
        return new LinkedHashMap<>(m);
    }

    private NetworkEvent toModel(NetworkEventEntity e) {
        return new NetworkEvent(
                e.getEventId(),
                e.getRecordId(),
                "network",
                e.getClientRequestId(),
                e.getMethod(),
                e.getUrl(),
                e.getStatus(),
                fromJsonMap(e.getRequestHeadersJson()),
                e.getRequestBody(),
                fromJsonMap(e.getResponseHeadersJson()),
                e.getResponseBody(),
                e.getStartedAtEpochMs(),
                e.getDurationMs(),
                e.getError(),
                e.getSeq()
        );
    }

    // ---------- stats ----------
    public RecordStats getRecordStats(String recordId) {
        List<ConsoleEventEntity> allConsole = consoleRepo.findLatest(recordId, PageRequest.of(0, 10000));
        int consoleErrorCount = 0;
        int consoleWarnCount = 0;
        for (ConsoleEventEntity e : allConsole) {
            if ("error".equalsIgnoreCase(e.getLevel())) consoleErrorCount++;
            else if ("warn".equalsIgnoreCase(e.getLevel())) consoleWarnCount++;
        }

        List<NetworkEventEntity> allNetwork = networkRepo.findLatest(recordId, PageRequest.of(0, 10000));
        int network4xxCount = 0;
        int network5xxCount = 0;
        int networkSlowCount = 0;
        List<Long> durations = new ArrayList<>();
        for (NetworkEventEntity e : allNetwork) {
            int status = e.getStatus();
            if (status >= 400 && status < 500) network4xxCount++;
            else if (status >= 500) network5xxCount++;
            if (e.getDurationMs() > 2000) networkSlowCount++;
            if (e.getDurationMs() > 0) durations.add(e.getDurationMs());
        }

        long avgDurationMs = 0;
        long p95DurationMs = 0;
        if (!durations.isEmpty()) {
            durations.sort(Long::compareTo);
            long sum = durations.stream().mapToLong(Long::longValue).sum();
            avgDurationMs = sum / durations.size();
            int p95Index = (int) Math.ceil(durations.size() * 0.95) - 1;
            p95DurationMs = durations.get(Math.max(0, p95Index));
        }

        return new RecordStats(consoleErrorCount, consoleWarnCount, network4xxCount, network5xxCount,
                networkSlowCount, avgDurationMs, p95DurationMs);
    }

    // ---------- session view ----------
    public SessionViewResponse getSessionView(String sessionId) {
        List<RecordEntity> records = recordRepository.findBySessionId(sessionId);
        List<SessionViewResponse.RecordSummary> summaries = new ArrayList<>();

        // compute previous / next session based on record linkage
        String previousSessionId = null;
        String nextSessionId = null;

        if (!records.isEmpty()) {
            // records are sorted by createdAt asc (see repository query)
            RecordEntity first = records.get(0);
            RecordEntity last = records.get(records.size() - 1);

            // previous session: follow previousRecordId of the earliest record
            String prevRecordId = first.getPreviousRecordId();
            if (prevRecordId != null && !prevRecordId.isBlank()) {
                RecordEntity prevRecord = recordRepository.findById(prevRecordId).orElse(null);
                if (prevRecord != null && !sessionId.equals(prevRecord.getSessionId())) {
                    previousSessionId = prevRecord.getSessionId();
                }
            }

            // next session: find a record whose previousRecordId points to the last record
            RecordEntity nextRecord = recordRepository.findFirstByPreviousRecordId(last.getRecordId());
            if (nextRecord != null && !sessionId.equals(nextRecord.getSessionId())) {
                nextSessionId = nextRecord.getSessionId();
            }
        }

        // Build within-session linkage map: prev -> current (so current has previousRecordId, and prev gets nextRecordId)
        Map<String, RecordEntity> byId = new HashMap<>();
        for (RecordEntity r : records) byId.put(r.getRecordId(), r);

        Map<String, String> nextById = new HashMap<>();
        for (RecordEntity r : records) {
            String prevId = r.getPreviousRecordId();
            if (prevId != null && !prevId.isBlank() && byId.containsKey(prevId)) {
                // If multiple records point to same prev, keep the earliest by createdAt (stable-ish)
                if (!nextById.containsKey(prevId)) {
                    nextById.put(prevId, r.getRecordId());
                }
            }
        }

        for (RecordEntity r : records) {
            List<ConsoleEventEntity> consoleErrors = consoleRepo.findLatest(r.getRecordId(), PageRequest.of(0, 1000));
            int consoleErrorCount = 0;
            for (ConsoleEventEntity e : consoleErrors) {
                if ("error".equalsIgnoreCase(e.getLevel())) consoleErrorCount++;
            }

            List<NetworkEventEntity> networks = networkRepo.findLatest(r.getRecordId(), PageRequest.of(0, 1000));
            int network4xx5xxCount = 0;
            for (NetworkEventEntity e : networks) {
                int status = e.getStatus();
                if (status >= 400) network4xx5xxCount++;
            }

            summaries.add(new SessionViewResponse.RecordSummary(
                    r.getRecordId(),
                    (r.getPreviousRecordId() != null && byId.containsKey(r.getPreviousRecordId())) ? r.getPreviousRecordId() : null,
                    nextById.getOrDefault(r.getRecordId(), null),
                    r.getPageUrl(),
                    r.getCreatedAtEpochMs(),
                    consoleErrorCount, network4xx5xxCount));
        }

        return new SessionViewResponse(sessionId, previousSessionId, nextSessionId, summaries);
    }

    // ---------- search ----------
    public List<ConsoleEvent> searchConsole(String recordId, String query, int limit) {
        if (query == null || query.isBlank()) return new ArrayList<>();
        PageRequest pr = PageRequest.of(0, Math.min(limit, 500));
        List<ConsoleEventEntity> rows = consoleRepo.search(recordId, query, pr);
        List<ConsoleEvent> out = new ArrayList<>();
        for (ConsoleEventEntity e : rows) {
            out.add(new ConsoleEvent(e.getEventId(), e.getRecordId(), "console", e.getLevel(), e.getMessage(), e.getStack(), e.getTs(), e.getSeq()));
        }
        return out;
    }

    public List<NetworkEvent> searchNetwork(String recordId, String query, int limit) {
        if (query == null || query.isBlank()) return new ArrayList<>();
        PageRequest pr = PageRequest.of(0, Math.min(limit, 500));
        List<NetworkEventEntity> rows = networkRepo.search(recordId, query, pr);
        List<NetworkEvent> out = new ArrayList<>();
        for (NetworkEventEntity e : rows) {
            out.add(toModel(e));
        }
        return out;
    }

    public List<BreadcrumbEvent> searchBreadcrumbs(String recordId, String query, int limit) {
        if (query == null || query.isBlank()) return new ArrayList<>();
        PageRequest pr = PageRequest.of(0, Math.min(limit, 500));
        List<BreadcrumbEventEntity> rows = breadcrumbRepo.search(recordId, query, pr);
        List<BreadcrumbEvent> out = new ArrayList<>();
        for (BreadcrumbEventEntity e : rows) {
            out.add(new BreadcrumbEvent(e.getEventId(), e.getRecordId(), "breadcrumb",
                    e.getName(), e.getMessage(), fromJsonMap(e.getDataJson()), e.getTs(), e.getSeq()));
        }
        return out;
    }

    // ---------- admin overview ----------
    public AdminOverviewResponse getAdminOverview(String query, boolean errorsOnly, Long fromTs, Long toTs, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        List<RecordEntity> recent = recordRepository.findLatest(PageRequest.of(0, safeLimit));

        String q = (query == null) ? "" : query.trim().toLowerCase();
        if (!q.isBlank()) {
            List<RecordEntity> filtered = new ArrayList<>();
            for (RecordEntity r : recent) {
                if (containsAny(r.getRecordId(), q) ||
                        containsAny(r.getSessionId(), q) ||
                        containsAny(r.getPageUrl(), q) ||
                        containsAny(r.getUserAgent(), q) ||
                        containsAny(r.getDeviceInfo(), q) ||
                        containsAny(r.getUserId(), q) ||
                        containsAny(r.getUserEmail(), q)) {
                    filtered.add(r);
                }
            }
            recent = filtered;
        }

        List<AdminOverviewResponse.RecordRow> rows = new ArrayList<>();
        for (RecordEntity r : recent) {
            long consoleErr = consoleRepo.countErrors(r.getRecordId());
            long netErr = networkRepo.countHttpErrors(r.getRecordId());
            long netSlow = networkRepo.countSlow(r.getRecordId());

            if (errorsOnly && (consoleErr + netErr) <= 0) continue;

            long bytes = 0;
            bytes += consoleRepo.sumApproxBytesByRecordId(r.getRecordId());
            bytes += networkRepo.sumApproxBytesByRecordId(r.getRecordId());
            bytes += breadcrumbRepo.sumApproxBytesByRecordId(r.getRecordId());
            bytes += rrwebRepo.sumApproxBytesByRecordId(r.getRecordId());

            rows.add(new AdminOverviewResponse.RecordRow(
                    r.getRecordId(),
                    r.getSessionId(),
                    r.getCreatedAtEpochMs(),
                    r.getPageUrl(),
                    r.getUserAgent(),
                    r.getDeviceInfo(),
                    r.getUserId(),
                    r.getUserEmail(),
                    consoleErr,
                    netErr,
                    netSlow,
                    bytes
            ));
        }

        AdminOverviewResponse.Segments segments = buildSegments(rows);

        long recordCount = recordRepository.count();
        long sessionCount = recordRepository.countDistinctSessionIds();
        long consoleCount = consoleRepo.countInRange(fromTs, toTs);
        long networkCount = networkRepo.countInRange(fromTs, toTs);
        long breadcrumbCount = breadcrumbRepo.countInRange(fromTs, toTs);
        long rrwebCount = rrwebRepo.countInRange(fromTs, toTs);

        long consoleBytes = consoleRepo.sumApproxBytesInRange(fromTs, toTs);
        long networkBytes = networkRepo.sumApproxBytesInRange(fromTs, toTs);
        long breadcrumbBytes = breadcrumbRepo.sumApproxBytesInRange(fromTs, toTs);
        long rrwebBytes = rrwebRepo.sumApproxBytesInRange(fromTs, toTs);

        AdminOverviewResponse.Totals totals = new AdminOverviewResponse.Totals(
                recordCount, sessionCount, consoleCount, networkCount, breadcrumbCount, rrwebCount
        );
        AdminOverviewResponse.StorageUsage storage = new AdminOverviewResponse.StorageUsage(
                consoleBytes, networkBytes, breadcrumbBytes, rrwebBytes
        );

        // domain stats from recent network events (bounded)
        Map<String, long[]> domainAgg = new HashMap<>(); // [0]=count, [1]=bytes
        List<NetworkEventEntity> netRecent = networkRepo.findRecentInRange(fromTs, toTs, PageRequest.of(0, 5000));
        for (NetworkEventEntity e : netRecent) {
            String host = extractHost(e.getUrl());
            if (host == null || host.isBlank()) host = "(unknown)";
            long approx = approxNetworkEventBytes(e);
            long[] v = domainAgg.get(host);
            if (v == null) v = new long[]{0, 0};
            v[0] += 1;
            v[1] += approx;
            domainAgg.put(host, v);
        }

        List<AdminOverviewResponse.DomainStat> domainStats = new ArrayList<>();
        for (Map.Entry<String, long[]> kv : domainAgg.entrySet()) {
            domainStats.add(new AdminOverviewResponse.DomainStat(kv.getKey(), kv.getValue()[0], kv.getValue()[1]));
        }
        domainStats.sort((a, b) -> Long.compare(b.getRequestCount(), a.getRequestCount()));
        if (domainStats.size() > 15) domainStats = domainStats.subList(0, 15);

        return new AdminOverviewResponse(totals, storage, domainStats, segments, rows);
    }

    private boolean containsAny(String hay, String needleLower) {
        if (needleLower == null || needleLower.isBlank()) return true;
        if (hay == null) return false;
        return hay.toLowerCase().contains(needleLower);
    }

    private String extractHost(String url) {
        if (url == null || url.isBlank()) return "";
        try {
            // supports absolute and relative urls
            java.net.URL base = new java.net.URL("http://dummy");
            java.net.URL u = new java.net.URL(base, url);
            return u.getHost();
        } catch (Exception ignored) {}
        return "";
    }

    private long approxNetworkEventBytes(NetworkEventEntity e) {
        long v = 0;
        v += safeLen(e.getMethod());
        v += safeLen(e.getUrl());
        v += safeLen(e.getClientRequestId());
        v += safeLen(e.getRequestHeadersJson());
        v += safeLen(e.getRequestBody());
        v += safeLen(e.getResponseHeadersJson());
        v += safeLen(e.getResponseBody());
        v += safeLen(e.getError());
        return v;
    }

    private long safeLen(String s) {
        return (s == null) ? 0 : s.length();
    }

    // ---------- segments (QA) ----------
    private AdminOverviewResponse.Segments buildSegments(List<AdminOverviewResponse.RecordRow> rows) {
        Map<String, Long> browsers = new HashMap<>();
        Map<String, Long> oses = new HashMap<>();
        Map<String, Long> platforms = new HashMap<>();
        Map<String, Long> langs = new HashMap<>();

        for (AdminOverviewResponse.RecordRow r : rows) {
            String ua = r.getUserAgent() == null ? "" : r.getUserAgent();
            String di = r.getDeviceInfo() == null ? "" : r.getDeviceInfo();

            inc(browsers, guessBrowser(ua));
            inc(oses, guessOs(ua));
            inc(platforms, extractDeviceInfoField(di, "platform"));
            inc(langs, extractDeviceInfoField(di, "lang"));
        }

        return new AdminOverviewResponse.Segments(
                topN(browsers, 8),
                topN(oses, 8),
                topN(platforms, 8),
                topN(langs, 8)
        );
    }

    private void inc(Map<String, Long> m, String k) {
        String key = (k == null || k.isBlank()) ? "unknown" : k;
        m.put(key, m.getOrDefault(key, 0L) + 1L);
    }

    private List<AdminOverviewResponse.SegmentStat> topN(Map<String, Long> m, int n) {
        List<Map.Entry<String, Long>> list = new ArrayList<>(m.entrySet());
        list.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        List<AdminOverviewResponse.SegmentStat> out = new ArrayList<>();
        for (Map.Entry<String, Long> e : list) {
            out.add(new AdminOverviewResponse.SegmentStat(e.getKey(), e.getValue()));
            if (out.size() >= n) break;
        }
        return out;
    }

    private String extractDeviceInfoField(String deviceInfo, String key) {
        if (deviceInfo == null || deviceInfo.isBlank() || key == null) return "";
        // format: "platform=... | lang=... | screen=... | dpr=..."
        String[] parts = deviceInfo.split("\\s*\\|\\s*");
        for (String p : parts) {
            int idx = p.indexOf('=');
            if (idx <= 0) continue;
            String k = p.substring(0, idx).trim();
            if (!key.equalsIgnoreCase(k)) continue;
            return p.substring(idx + 1).trim();
        }
        return "";
    }

    private String guessBrowser(String ua) {
        if (ua == null) return "unknown";
        if (ua.contains("Edg/")) return "Edge";
        if (ua.contains("OPR/") || ua.contains("Opera")) return "Opera";
        if (ua.contains("Firefox/")) return "Firefox";
        if (ua.contains("Chrome/")) return "Chrome";
        if (ua.contains("Safari/")) return "Safari";
        return "unknown";
    }

    private String guessOs(String ua) {
        if (ua == null) return "unknown";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iPod")) return "iOS";
        if (ua.contains("Windows")) return "Windows";
        if (ua.contains("Mac OS X") || ua.contains("Macintosh")) return "macOS";
        if (ua.contains("Linux")) return "Linux";
        return "unknown";
    }
}