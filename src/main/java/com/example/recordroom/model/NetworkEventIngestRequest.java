package com.example.recordroom.model;

import java.util.Map;

public class NetworkEventIngestRequest {
    private String type; // "network"
    private String clientRequestId;
    private String method;
    private String url;
    private int status;
    private Map<String, String> requestHeaders;
    private String requestBody;
    private Map<String, String> responseHeaders;
    private String responseBody;
    private long startedAtEpochMs;
    private long durationMs;
    private String error;
    private long seq;

    public NetworkEventIngestRequest() {}

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getClientRequestId() { return clientRequestId; }
    public void setClientRequestId(String clientRequestId) { this.clientRequestId = clientRequestId; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public Map<String, String> getRequestHeaders() { return requestHeaders; }
    public void setRequestHeaders(Map<String, String> requestHeaders) { this.requestHeaders = requestHeaders; }

    public String getRequestBody() { return requestBody; }
    public void setRequestBody(String requestBody) { this.requestBody = requestBody; }

    public Map<String, String> getResponseHeaders() { return responseHeaders; }
    public void setResponseHeaders(Map<String, String> responseHeaders) { this.responseHeaders = responseHeaders; }

    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }

    public long getStartedAtEpochMs() { return startedAtEpochMs; }
    public void setStartedAtEpochMs(long startedAtEpochMs) { this.startedAtEpochMs = startedAtEpochMs; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public long getSeq() { return seq; }
    public void setSeq(long seq) { this.seq = seq; }
}
