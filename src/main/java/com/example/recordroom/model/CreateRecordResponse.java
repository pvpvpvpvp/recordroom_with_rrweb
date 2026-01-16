package com.example.recordroom.model;

public class CreateRecordResponse {
    private final String recordId;
    private final String shareUrl;
    private final String ingestWsUrl;

    public CreateRecordResponse(String recordId, String shareUrl, String ingestWsUrl) {
        this.recordId = recordId;
        this.shareUrl = shareUrl;
        this.ingestWsUrl = ingestWsUrl;
    }

    public String getRecordId() { return recordId; }
    public String getShareUrl() { return shareUrl; }
    public String getIngestWsUrl() { return ingestWsUrl; }
}
