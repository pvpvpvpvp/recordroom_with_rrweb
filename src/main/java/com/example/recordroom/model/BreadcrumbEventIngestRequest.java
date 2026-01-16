package com.example.recordroom.model;

import java.util.Map;

public class BreadcrumbEventIngestRequest {
    private String type; // "breadcrumb"
    private String name;
    private String message;
    private Map<String, String> data;
    private long ts;
    private long seq;

    public BreadcrumbEventIngestRequest() {}

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Map<String, String> getData() { return data; }
    public void setData(Map<String, String> data) { this.data = data; }

    public long getTs() { return ts; }
    public void setTs(long ts) { this.ts = ts; }

    public long getSeq() { return seq; }
    public void setSeq(long seq) { this.seq = seq; }
}
