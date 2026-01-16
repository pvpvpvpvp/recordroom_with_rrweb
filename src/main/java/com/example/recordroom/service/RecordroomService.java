package com.example.recordroom.service;

import com.example.recordroom.model.BreadcrumbEvent;
import com.example.recordroom.model.BreadcrumbEventIngestRequest;
import com.example.recordroom.model.ConsoleEvent;
import com.example.recordroom.model.ConsoleEventIngestRequest;
import com.example.recordroom.model.CreateRecordRequest;
import com.example.recordroom.model.CreateRecordResponse;
import com.example.recordroom.model.NetworkEvent;
import com.example.recordroom.model.NetworkEventIngestRequest;
import com.example.recordroom.model.Record;
import com.example.recordroom.model.RrwebBatchIngestRequest;
import com.example.recordroom.model.RrwebEventEnvelope;
import com.example.recordroom.model.RrwebListResponse;
import com.example.recordroom.model.TimelineResponse;
import com.example.recordroom.persistence.*;
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

    private final ObjectMapper om = new ObjectMapper();

    public RecordroomService(RecordRepository recordRepository,
                             ConsoleEventRepository consoleRepo,
                             NetworkEventRepository networkRepo,
                             BreadcrumbEventRepository breadcrumbRepo,
                             RrwebEventRepository rrwebRepo) {
        this.recordRepository = recordRepository;
        this.consoleRepo = consoleRepo;
        this.networkRepo = networkRepo;
        this.breadcrumbRepo = breadcrumbRepo;
        this.rrwebRepo = rrwebRepo;
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

        RecordEntity entity = new RecordEntity(recordId, sessionId, previous, pageUrl, userAgent, appVersion, nowEpochMs);
        recordRepository.save(entity);

        return new Record(entity.getRecordId(), entity.getSessionId(), entity.getPreviousRecordId(),
                entity.getPageUrl(), entity.getUserAgent(), entity.getAppVersion(), entity.getCreatedAtEpochMs());
    }

    public Record getRecord(String recordId) {
        RecordEntity e = recordRepository.findById(recordId).orElse(null);
        if (e == null) return null;
        return new Record(e.getRecordId(), e.getSessionId(), e.getPreviousRecordId(),
                e.getPageUrl(), e.getUserAgent(), e.getAppVersion(), e.getCreatedAtEpochMs());
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

    public TimelineResponse listTimeline(String recordId, Cursor cursor, int limit, Set<String> kinds, String consoleLevel, Integer statusMin) {
        int per = Math.max(limit, 1);

        List<TimelineResponse.TimelineItem> items = new ArrayList<>();

        if (kinds.contains("console")) {
            for (ConsoleEvent c : listConsole(recordId, cursor, per, consoleLevel)) {
                items.add(new TimelineResponse.TimelineItem("console", c.getEventId(), c.getTs(), c.getSeq(),
                        c.getLevel(), c.getMessage(), c.getStack(),
                        null, null, 0, null));
            }
        }

        if (kinds.contains("network")) {
            for (NetworkEvent n : listNetwork(recordId, cursor, per, statusMin)) {
                items.add(new TimelineResponse.TimelineItem("network", n.getEventId(), n.getStartedAtEpochMs(), n.getSeq(),
                        null, null, null,
                        n.getMethod(), n.getUrl(), n.getStatus(), null));
            }
        }

        if (kinds.contains("breadcrumb")) {
            for (BreadcrumbEvent b : listBreadcrumbs(recordId, cursor, per, null)) {
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



    // ---------- replay (week6 MVP) ----------
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
}