package com.example.recordroom.model;

import java.util.Map;

public class BreadcrumbEvent {
    private final String eventId;
    private final String recordId;
    private final String type; // "breadcrumb"
    private final String name; // "click" | "navigation" | "input" | "custom"
    private final String message;
    private final Map<String, String> data;
    private final long ts;
    private final long seq;

    public BreadcrumbEvent(String eventId, String recordId, String type, String name, String message, Map<String, String> data, long ts, long seq) {
        this.eventId = eventId;
        this.recordId = recordId;
        this.type = type;
        this.name = name;
        this.message = message;
        this.data = data;
        this.ts = ts;
        this.seq = seq;
    }

    public String getEventId() { return eventId; }
    public String getRecordId() { return recordId; }
    public String getType() { return type; }
    public String getName() { return name; }
    public String getMessage() { return message; }
    public Map<String, String> getData() { return data; }
    public long getTs() { return ts; }
    public long getSeq() { return seq; }
}
