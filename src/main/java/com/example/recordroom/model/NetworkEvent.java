package com.example.recordroom.model;

import java.util.Map;

public class NetworkEvent {
    private final String eventId;
    private final String recordId;
    private final String type; // "network"
    private final String clientRequestId; // optional id from client (useful for debugging)
    private final String method;
    private final String url;
    private final int status; // 0 if failed before response
    private final Map<String, String> requestHeaders;
    private final String requestBody;
    private final Map<String, String> responseHeaders;
    private final String responseBody;
    private final long startedAtEpochMs;
    private final long durationMs;
    private final String error; // optional
    private final long seq;

    public NetworkEvent(
            String eventId,
            String recordId,
            String type,
            String clientRequestId,
            String method,
            String url,
            int status,
            Map<String, String> requestHeaders,
            String requestBody,
            Map<String, String> responseHeaders,
            String responseBody,
            long startedAtEpochMs,
            long durationMs,
            String error,
            long seq
    ) {
        this.eventId = eventId;
        this.recordId = recordId;
        this.type = type;
        this.clientRequestId = clientRequestId;
        this.method = method;
        this.url = url;
        this.status = status;
        this.requestHeaders = requestHeaders;
        this.requestBody = requestBody;
        this.responseHeaders = responseHeaders;
        this.responseBody = responseBody;
        this.startedAtEpochMs = startedAtEpochMs;
        this.durationMs = durationMs;
        this.error = error;
        this.seq = seq;
    }

    public String getEventId() { return eventId; }
    public String getRecordId() { return recordId; }
    public String getType() { return type; }
    public String getClientRequestId() { return clientRequestId; }
    public String getMethod() { return method; }
    public String getUrl() { return url; }
    public int getStatus() { return status; }
    public Map<String, String> getRequestHeaders() { return requestHeaders; }
    public String getRequestBody() { return requestBody; }
    public Map<String, String> getResponseHeaders() { return responseHeaders; }
    public String getResponseBody() { return responseBody; }
    public long getStartedAtEpochMs() { return startedAtEpochMs; }
    public long getDurationMs() { return durationMs; }
    public String getError() { return error; }
    public long getSeq() { return seq; }
}
