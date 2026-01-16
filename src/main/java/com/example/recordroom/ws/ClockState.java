package com.example.recordroom.ws;

public class ClockState {
    private final String recordId;

    // rrweb 기준(상대 ms) + 절대 epoch ms 둘 다 저장 (동기화 안정화용)
    private volatile long tMs;                  // rrweb relative ms (0..)
    private volatile long baseEpochMs;          // rrweb first event timestamp (epoch ms)
    private volatile long absEpochMs;           // baseEpochMs + tMs

    private volatile String mode;               // play | pause | seek
    private volatile double speed;
    private volatile long updatedAtEpochMs;

    public ClockState(String recordId) {
        this.recordId = recordId;
        this.tMs = 0L;
        this.baseEpochMs = 0L;
        this.absEpochMs = 0L;
        this.mode = "pause";
        this.speed = 1.0;
        this.updatedAtEpochMs = System.currentTimeMillis();
    }

    public String getRecordId() { return recordId; }
    public long getTMs() { return tMs; }
    public long getBaseEpochMs() { return baseEpochMs; }
    public long getAbsEpochMs() { return absEpochMs; }
    public String getMode() { return mode; }
    public double getSpeed() { return speed; }
    public long getUpdatedAtEpochMs() { return updatedAtEpochMs; }

    public void update(long tMs, long baseEpochMs, long absEpochMs, String mode, double speed) {
        this.tMs = Math.max(0L, tMs);
        this.baseEpochMs = Math.max(0L, baseEpochMs);
        this.absEpochMs = Math.max(0L, absEpochMs);
        this.mode = (mode == null || mode.isBlank()) ? "play" : mode;
        this.speed = (speed <= 0) ? 1.0 : speed;
        this.updatedAtEpochMs = System.currentTimeMillis();
    }
}
