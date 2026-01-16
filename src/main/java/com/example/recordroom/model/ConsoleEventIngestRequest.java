package com.example.recordroom.model;

public class ConsoleEventIngestRequest {
    private String type;   // "console"
    private String level;  // "log" | "warn" | "error"
    private String message;
    private String stack;  // optional
    private long ts;
    private long seq;

    public ConsoleEventIngestRequest() {}

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getStack() { return stack; }
    public void setStack(String stack) { this.stack = stack; }

    public long getTs() { return ts; }
    public void setTs(long ts) { this.ts = ts; }

    public long getSeq() { return seq; }
    public void setSeq(long seq) { this.seq = seq; }
}
