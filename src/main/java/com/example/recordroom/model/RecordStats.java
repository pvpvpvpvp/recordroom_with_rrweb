package com.example.recordroom.model;

public class RecordStats {
    private final int consoleErrorCount;
    private final int consoleWarnCount;
    private final int network4xxCount;
    private final int network5xxCount;
    private final int networkSlowCount; // durationMs > 2000
    private final long avgDurationMs;
    private final long p95DurationMs;

    public RecordStats(int consoleErrorCount, int consoleWarnCount,
                      int network4xxCount, int network5xxCount, int networkSlowCount,
                      long avgDurationMs, long p95DurationMs) {
        this.consoleErrorCount = consoleErrorCount;
        this.consoleWarnCount = consoleWarnCount;
        this.network4xxCount = network4xxCount;
        this.network5xxCount = network5xxCount;
        this.networkSlowCount = networkSlowCount;
        this.avgDurationMs = avgDurationMs;
        this.p95DurationMs = p95DurationMs;
    }

    public int getConsoleErrorCount() { return consoleErrorCount; }
    public int getConsoleWarnCount() { return consoleWarnCount; }
    public int getNetwork4xxCount() { return network4xxCount; }
    public int getNetwork5xxCount() { return network5xxCount; }
    public int getNetworkSlowCount() { return networkSlowCount; }
    public long getAvgDurationMs() { return avgDurationMs; }
    public long getP95DurationMs() { return p95DurationMs; }
}

