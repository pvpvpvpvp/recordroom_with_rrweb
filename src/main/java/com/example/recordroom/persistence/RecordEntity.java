package com.example.recordroom.persistence;

import javax.persistence.*;

@Entity
@Table(name = "rr_record")
public class RecordEntity {

    @Id
    @Column(length = 64)
    private String recordId;

    @Column(length = 64, nullable = false)
    private String sessionId;

    @Column(length = 64)
    private String previousRecordId;

    @Column(length = 2048)
    private String pageUrl;

    @Column(length = 512)
    private String userAgent;

    @Column(length = 64)
    private String appVersion;

    private long createdAtEpochMs;

    protected RecordEntity() {}

    public RecordEntity(String recordId, String sessionId, String previousRecordId, String pageUrl,
                        String userAgent, String appVersion, long createdAtEpochMs) {
        this.recordId = recordId;
        this.sessionId = sessionId;
        this.previousRecordId = previousRecordId;
        this.pageUrl = pageUrl;
        this.userAgent = userAgent;
        this.appVersion = appVersion;
        this.createdAtEpochMs = createdAtEpochMs;
    }

    public String getRecordId() { return recordId; }
    public String getSessionId() { return sessionId; }
    public String getPreviousRecordId() { return previousRecordId; }
    public String getPageUrl() { return pageUrl; }
    public String getUserAgent() { return userAgent; }
    public String getAppVersion() { return appVersion; }
    public long getCreatedAtEpochMs() { return createdAtEpochMs; }
}
