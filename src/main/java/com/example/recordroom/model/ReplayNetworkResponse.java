package com.example.recordroom.model;

public class ReplayNetworkResponse {
    private final String recordId;
    private final String eventId;
    private final String method;
    private final String originalUrl;
    private final String replayUrl;
    private final int originalStatus;
    private final int replayStatus;
    private final long durationMs;
    private final String responseBody;

    public ReplayNetworkResponse(
            String recordId,
            String eventId,
            String method,
            String originalUrl,
            String replayUrl,
            int originalStatus,
            int replayStatus,
            long durationMs,
            String responseBody
    ) {
        this.recordId = recordId;
        this.eventId = eventId;
        this.method = method;
        this.originalUrl = originalUrl;
        this.replayUrl = replayUrl;
        this.originalStatus = originalStatus;
        this.replayStatus = replayStatus;
        this.durationMs = durationMs;
        this.responseBody = responseBody;
    }

    public String getRecordId() { return recordId; }
    public String getEventId() { return eventId; }
    public String getMethod() { return method; }
    public String getOriginalUrl() { return originalUrl; }
    public String getReplayUrl() { return replayUrl; }
    public int getOriginalStatus() { return originalStatus; }
    public int getReplayStatus() { return replayStatus; }
    public long getDurationMs() { return durationMs; }
    public String getResponseBody() { return responseBody; }
}
