package com.example.recordroom.model;

import java.util.List;

public class SessionViewResponse {
    private final String sessionId;
    private final String previousSessionId;
    private final String nextSessionId;
    private final List<RecordSummary> records;

    public SessionViewResponse(String sessionId, String previousSessionId, String nextSessionId, List<RecordSummary> records) {
        this.sessionId = sessionId;
        this.previousSessionId = previousSessionId;
        this.nextSessionId = nextSessionId;
        this.records = records;
    }

    public String getSessionId() { return sessionId; }
    public String getPreviousSessionId() { return previousSessionId; }
    public String getNextSessionId() { return nextSessionId; }
    public List<RecordSummary> getRecords() { return records; }

    public static class RecordSummary {
        private final String recordId;
        private final String previousRecordId; // within-session (nullable)
        private final String nextRecordId;     // within-session (nullable)
        private final String pageUrl;
        private final long createdAtEpochMs;
        private final int consoleErrorCount;
        private final int network4xx5xxCount;

        public RecordSummary(String recordId, String previousRecordId, String nextRecordId, String pageUrl, long createdAtEpochMs,
                           int consoleErrorCount, int network4xx5xxCount) {
            this.recordId = recordId;
            this.previousRecordId = previousRecordId;
            this.nextRecordId = nextRecordId;
            this.pageUrl = pageUrl;
            this.createdAtEpochMs = createdAtEpochMs;
            this.consoleErrorCount = consoleErrorCount;
            this.network4xx5xxCount = network4xx5xxCount;
        }

        public String getRecordId() { return recordId; }
        public String getPreviousRecordId() { return previousRecordId; }
        public String getNextRecordId() { return nextRecordId; }
        public String getPageUrl() { return pageUrl; }
        public long getCreatedAtEpochMs() { return createdAtEpochMs; }
        public int getConsoleErrorCount() { return consoleErrorCount; }
        public int getNetwork4xx5xxCount() { return network4xx5xxCount; }
    }
}

