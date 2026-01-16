package com.example.recordroom.model;

public class ConsoleEvent {
    private final String eventId;
    private final String recordId;
    private final String type;   // "console"
    private final String level;  // "log" | "warn" | "error"
    private final String message;
    private final String stack;  // optional
    private final long ts;
    private final long seq;

    public ConsoleEvent(String eventId, String recordId, String type, String level, String message, String stack, long ts, long seq) {
        this.eventId = eventId;
        this.recordId = recordId;
        this.type = type;
        this.level = level;
        this.message = message;
        this.stack = stack;
        this.ts = ts;
        this.seq = seq;
    }

    public String getEventId() { return eventId; }
    public String getRecordId() { return recordId; }
    public String getType() { return type; }
    public String getLevel() { return level; }
    public String getMessage() { return message; }
    public String getStack() { return stack; }
    public long getTs() { return ts; }
    public long getSeq() { return seq; }
}
