package com.example.recordroom.model;

import java.util.List;

public class AdminOverviewResponse {
    private final Totals totals;
    private final StorageUsage storage;
    private final List<DomainStat> topDomains;
    private final Segments segments;
    private final List<RecordRow> records;

    public AdminOverviewResponse(Totals totals, StorageUsage storage, List<DomainStat> topDomains, Segments segments, List<RecordRow> records) {
        this.totals = totals;
        this.storage = storage;
        this.topDomains = topDomains;
        this.segments = segments;
        this.records = records;
    }

    public Totals getTotals() { return totals; }
    public StorageUsage getStorage() { return storage; }
    public List<DomainStat> getTopDomains() { return topDomains; }
    public Segments getSegments() { return segments; }
    public List<RecordRow> getRecords() { return records; }

    public static class SegmentStat {
        private final String key;
        private final long count;

        public SegmentStat(String key, long count) {
            this.key = key;
            this.count = count;
        }

        public String getKey() { return key; }
        public long getCount() { return count; }
    }

    public static class Segments {
        private final List<SegmentStat> topBrowsers;
        private final List<SegmentStat> topOs;
        private final List<SegmentStat> topPlatforms;
        private final List<SegmentStat> topLangs;

        public Segments(List<SegmentStat> topBrowsers, List<SegmentStat> topOs, List<SegmentStat> topPlatforms, List<SegmentStat> topLangs) {
            this.topBrowsers = topBrowsers;
            this.topOs = topOs;
            this.topPlatforms = topPlatforms;
            this.topLangs = topLangs;
        }

        public List<SegmentStat> getTopBrowsers() { return topBrowsers; }
        public List<SegmentStat> getTopOs() { return topOs; }
        public List<SegmentStat> getTopPlatforms() { return topPlatforms; }
        public List<SegmentStat> getTopLangs() { return topLangs; }
    }

    public static class Totals {
        private final long recordCount;
        private final long sessionCount;
        private final long consoleEventCount;
        private final long networkEventCount;
        private final long breadcrumbEventCount;
        private final long rrwebEventCount;

        public Totals(long recordCount, long sessionCount, long consoleEventCount, long networkEventCount, long breadcrumbEventCount, long rrwebEventCount) {
            this.recordCount = recordCount;
            this.sessionCount = sessionCount;
            this.consoleEventCount = consoleEventCount;
            this.networkEventCount = networkEventCount;
            this.breadcrumbEventCount = breadcrumbEventCount;
            this.rrwebEventCount = rrwebEventCount;
        }

        public long getRecordCount() { return recordCount; }
        public long getSessionCount() { return sessionCount; }
        public long getConsoleEventCount() { return consoleEventCount; }
        public long getNetworkEventCount() { return networkEventCount; }
        public long getBreadcrumbEventCount() { return breadcrumbEventCount; }
        public long getRrwebEventCount() { return rrwebEventCount; }
    }

    public static class StorageUsage {
        private final long consoleBytes;
        private final long networkBytes;
        private final long breadcrumbBytes;
        private final long rrwebBytes;
        private final long totalBytes;

        public StorageUsage(long consoleBytes, long networkBytes, long breadcrumbBytes, long rrwebBytes) {
            this.consoleBytes = consoleBytes;
            this.networkBytes = networkBytes;
            this.breadcrumbBytes = breadcrumbBytes;
            this.rrwebBytes = rrwebBytes;
            this.totalBytes = consoleBytes + networkBytes + breadcrumbBytes + rrwebBytes;
        }

        public long getConsoleBytes() { return consoleBytes; }
        public long getNetworkBytes() { return networkBytes; }
        public long getBreadcrumbBytes() { return breadcrumbBytes; }
        public long getRrwebBytes() { return rrwebBytes; }
        public long getTotalBytes() { return totalBytes; }
    }

    public static class DomainStat {
        private final String domain;
        private final long requestCount;
        private final long approxBytes;

        public DomainStat(String domain, long requestCount, long approxBytes) {
            this.domain = domain;
            this.requestCount = requestCount;
            this.approxBytes = approxBytes;
        }

        public String getDomain() { return domain; }
        public long getRequestCount() { return requestCount; }
        public long getApproxBytes() { return approxBytes; }
    }

    public static class RecordRow {
        private final String recordId;
        private final String sessionId;
        private final long createdAtEpochMs;
        private final String pageUrl;
        private final String userAgent;
        private final String deviceInfo;
        private final String userId;
        private final String userEmail;

        private final long consoleErrorCount;
        private final long networkHttpErrorCount;
        private final long networkSlowCount;

        private final long approxBytes;

        public RecordRow(String recordId, String sessionId, long createdAtEpochMs, String pageUrl,
                         String userAgent, String deviceInfo, String userId, String userEmail,
                         long consoleErrorCount, long networkHttpErrorCount, long networkSlowCount,
                         long approxBytes) {
            this.recordId = recordId;
            this.sessionId = sessionId;
            this.createdAtEpochMs = createdAtEpochMs;
            this.pageUrl = pageUrl;
            this.userAgent = userAgent;
            this.deviceInfo = deviceInfo;
            this.userId = userId;
            this.userEmail = userEmail;
            this.consoleErrorCount = consoleErrorCount;
            this.networkHttpErrorCount = networkHttpErrorCount;
            this.networkSlowCount = networkSlowCount;
            this.approxBytes = approxBytes;
        }

        public String getRecordId() { return recordId; }
        public String getSessionId() { return sessionId; }
        public long getCreatedAtEpochMs() { return createdAtEpochMs; }
        public String getPageUrl() { return pageUrl; }
        public String getUserAgent() { return userAgent; }
        public String getDeviceInfo() { return deviceInfo; }
        public String getUserId() { return userId; }
        public String getUserEmail() { return userEmail; }
        public long getConsoleErrorCount() { return consoleErrorCount; }
        public long getNetworkHttpErrorCount() { return networkHttpErrorCount; }
        public long getNetworkSlowCount() { return networkSlowCount; }
        public long getApproxBytes() { return approxBytes; }
    }
}


