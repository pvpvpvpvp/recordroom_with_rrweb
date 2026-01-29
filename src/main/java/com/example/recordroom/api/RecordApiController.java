package com.example.recordroom.api;

import com.example.recordroom.model.CreateRecordRequest;
import com.example.recordroom.model.CreateRecordResponse;
import com.example.recordroom.model.NetworkEvent;
import com.example.recordroom.model.Record;
import com.example.recordroom.model.RecordStats;
import com.example.recordroom.model.ReplayNetworkResponse;
import com.example.recordroom.model.SessionViewResponse;
import com.example.recordroom.model.TimelineResponse;
import com.example.recordroom.model.RrwebListResponse;
import com.example.recordroom.service.RecordroomService;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class RecordApiController {

    private final RecordroomService service;

    public RecordApiController(RecordroomService service) {
        this.service = service;
    }

    @PostMapping(value = "/records", produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateRecordResponse createRecord(@RequestBody CreateRecordRequest req, HttpServletRequest http) {
        String recordId = UUID.randomUUID().toString();
        String sessionId = (req.getSessionId() == null || req.getSessionId().isBlank())
                ? UUID.randomUUID().toString()
                : req.getSessionId();

        long now = System.currentTimeMillis();
        service.createRecord(req, recordId, sessionId, now);

        String baseUrl = baseUrl(http);
        String shareUrl = baseUrl + "/r/" + recordId + "/timeline";
        String ingestWsUrl = baseUrl.replaceFirst("^http", "ws") + "/ws/ingest?recordId=" + recordId;

        return new CreateRecordResponse(recordId, shareUrl, ingestWsUrl);
    }

    @GetMapping(value = "/records/{recordId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Record getRecord(@PathVariable String recordId) {
        Record r = service.getRecord(recordId);
        if (r == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "record not found: " + recordId);
        }
        return r;
    }

    @GetMapping(value = "/records/{recordId}/timeline", produces = MediaType.APPLICATION_JSON_VALUE)
    public TimelineResponse timeline(
            @PathVariable String recordId,
            @RequestParam(required = false) String after,
            @RequestParam(required = false, defaultValue = "200") int limit,
            @RequestParam(required = false) String types,
            @RequestParam(required = false, defaultValue = "all") String consoleLevel,
            @RequestParam(required = false) Integer statusMin,
            @RequestParam(required = false) Long tsFrom,
            @RequestParam(required = false) Long tsTo
    ) {
        if (!service.recordExists(recordId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "record not found: " + recordId);
        }

        RecordroomService.Cursor cursor = RecordroomService.Cursor.parse(after);
        int safeLimit = Math.max(1, Math.min(limit, 500));

        Set<String> kinds = new LinkedHashSet<>(Arrays.asList("console", "network", "breadcrumb"));
        if (types != null && !types.isBlank()) {
            kinds = Arrays.stream(types.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .map(String::toLowerCase)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (kinds.isEmpty()) kinds = new LinkedHashSet<>(Arrays.asList("console","network","breadcrumb"));
        }

        return service.listTimeline(recordId, cursor, safeLimit, kinds, consoleLevel, statusMin, tsFrom, tsTo);
    }

    @GetMapping(value = "/records/{recordId}/console", produces = MediaType.APPLICATION_JSON_VALUE)
    public Object listConsole(
            @PathVariable String recordId,
            @RequestParam(required = false) String after,
            @RequestParam(required = false, defaultValue = "200") int limit,
            @RequestParam(required = false, defaultValue = "all") String level
    ) {
        if (!service.recordExists(recordId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "record not found: " + recordId);
        }
        RecordroomService.Cursor cursor = RecordroomService.Cursor.parse(after);
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return service.listConsole(recordId, cursor, safeLimit, level);
    }

    @GetMapping(value = "/records/{recordId}/network", produces = MediaType.APPLICATION_JSON_VALUE)
    public Object listNetwork(
            @PathVariable String recordId,
            @RequestParam(required = false) String after,
            @RequestParam(required = false, defaultValue = "200") int limit,
            @RequestParam(required = false) Integer statusMin
    ) {
        if (!service.recordExists(recordId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "record not found: " + recordId);
        }
        RecordroomService.Cursor cursor = RecordroomService.Cursor.parse(after);
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return service.listNetwork(recordId, cursor, safeLimit, statusMin);
    }

    @GetMapping(value = "/records/{recordId}/network/{eventId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public NetworkEvent getNetworkDetail(@PathVariable String recordId, @PathVariable String eventId) {
        if (!service.recordExists(recordId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "record not found: " + recordId);
        }
        NetworkEvent e = service.getNetworkDetail(recordId, eventId);
        if (e == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "network event not found: " + eventId);
        }
        return e;
    }


    @PostMapping(value = "/records/{recordId}/network/{eventId}/replay", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReplayNetworkResponse replayNetwork(
            @PathVariable String recordId,
            @PathVariable String eventId,
            @RequestParam(required = false, defaultValue = "false") boolean allowNonIdempotent,
            HttpServletRequest request
    ) {
        if (!service.recordExists(recordId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "record not found: " + recordId);
        }

        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        String baseUrl = scheme + "://" + host + ((port == 80 || port == 443) ? "" : (":" + port));

        ReplayNetworkResponse r = service.replayNetwork(recordId, eventId, baseUrl, allowNonIdempotent);
        if (r == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "network event not found: " + eventId);
        }
        return r;
    }


    @GetMapping(value = "/records/{recordId}/rrweb", produces = MediaType.APPLICATION_JSON_VALUE)
    public RrwebListResponse listRrweb(
            @PathVariable String recordId,
            @RequestParam(required = false) String after,
            @RequestParam(required = false, defaultValue = "2000") int limit
    ) {
        if (!service.recordExists(recordId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "record not found: " + recordId);
        }
        RecordroomService.Cursor cursor = RecordroomService.Cursor.parse(after);
        int safeLimit = Math.max(1, Math.min(limit, 5000));
        return service.listRrweb(recordId, cursor, safeLimit);
    }


    @GetMapping(value = "/records/{recordId}/breadcrumbs", produces = MediaType.APPLICATION_JSON_VALUE)
    public Object listBreadcrumbs(
            @PathVariable String recordId,
            @RequestParam(required = false) String after,
            @RequestParam(required = false, defaultValue = "200") int limit,
            @RequestParam(required = false, defaultValue = "all") String name
    ) {
        if (!service.recordExists(recordId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "record not found: " + recordId);
        }
        RecordroomService.Cursor cursor = RecordroomService.Cursor.parse(after);
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return service.listBreadcrumbs(recordId, cursor, safeLimit, name);
    }

    @GetMapping(value = "/records/{recordId}/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public RecordStats getRecordStats(@PathVariable String recordId) {
        if (!service.recordExists(recordId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "record not found: " + recordId);
        }
        return service.getRecordStats(recordId);
    }

    @GetMapping(value = "/sessions/{sessionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public SessionViewResponse getSessionView(@PathVariable String sessionId) {
        return service.getSessionView(sessionId);
    }

    @GetMapping(value = "/records/{recordId}/search/console", produces = MediaType.APPLICATION_JSON_VALUE)
    public Object searchConsole(
            @PathVariable String recordId,
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(required = false, defaultValue = "100") int limit
    ) {
        if (!service.recordExists(recordId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "record not found: " + recordId);
        }
        return service.searchConsole(recordId, q, limit);
    }

    @GetMapping(value = "/records/{recordId}/search/network", produces = MediaType.APPLICATION_JSON_VALUE)
    public Object searchNetwork(
            @PathVariable String recordId,
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(required = false, defaultValue = "100") int limit
    ) {
        if (!service.recordExists(recordId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "record not found: " + recordId);
        }
        return service.searchNetwork(recordId, q, limit);
    }

    @GetMapping(value = "/records/{recordId}/search/breadcrumbs", produces = MediaType.APPLICATION_JSON_VALUE)
    public Object searchBreadcrumbs(
            @PathVariable String recordId,
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(required = false, defaultValue = "100") int limit
    ) {
        if (!service.recordExists(recordId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "record not found: " + recordId);
        }
        return service.searchBreadcrumbs(recordId, q, limit);
    }

    private String baseUrl(HttpServletRequest req) {
        String scheme = req.getHeader("X-Forwarded-Proto") != null ? req.getHeader("X-Forwarded-Proto") : req.getScheme();
        String host = req.getHeader("X-Forwarded-Host") != null ? req.getHeader("X-Forwarded-Host") : req.getServerName();
        String portHeader = req.getHeader("X-Forwarded-Port");
        int port = portHeader != null ? Integer.parseInt(portHeader) : req.getServerPort();

        boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80) || ("https".equalsIgnoreCase(scheme) && port == 443);
        return defaultPort ? (scheme + "://" + host) : (scheme + "://" + host + ":" + port);
    }
}
