package com.example.recordroom.model;

public class Record {
    private final String recordId;
    private final String sessionId;
    private final String previousRecordId; // nullable
    private final String pageUrl;
    private final String userAgent;
    private final String appVersion;
    private final String deviceInfo;
    private final String userId;
    private final String userEmail;
    private final long createdAtEpochMs;

    public Record(String recordId, String sessionId, String previousRecordId, String pageUrl,
                  String userAgent, String appVersion, String deviceInfo, String userId, String userEmail, long createdAtEpochMs) {
        this.recordId = recordId;
        this.sessionId = sessionId;
        this.previousRecordId = previousRecordId;
        this.pageUrl = pageUrl;
        this.userAgent = userAgent;
        this.appVersion = appVersion;
        this.deviceInfo = deviceInfo;
        this.userId = userId;
        this.userEmail = userEmail;
        this.createdAtEpochMs = createdAtEpochMs;
    }

    public String getRecordId() { return recordId; }
    public String getSessionId() { return sessionId; }
    public String getPreviousRecordId() { return previousRecordId; }
    public String getPageUrl() { return pageUrl; }
    public String getUserAgent() { return userAgent; }
    public String getAppVersion() { return appVersion; }
    public String getDeviceInfo() { return deviceInfo; }
    public String getUserId() { return userId; }
    public String getUserEmail() { return userEmail; }
    public long getCreatedAtEpochMs() { return createdAtEpochMs; }
}
