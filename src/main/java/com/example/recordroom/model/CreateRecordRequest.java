package com.example.recordroom.model;

public class CreateRecordRequest {
    private String pageUrl;
    private String userAgent;
    private String appVersion;
    private String sessionId;
    private String previousRecordId;

    public CreateRecordRequest() {}

    public String getPageUrl() { return pageUrl; }
    public void setPageUrl(String pageUrl) { this.pageUrl = pageUrl; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getAppVersion() { return appVersion; }
    public void setAppVersion(String appVersion) { this.appVersion = appVersion; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getPreviousRecordId() { return previousRecordId; }
    public void setPreviousRecordId(String previousRecordId) { this.previousRecordId = previousRecordId; }
}
