package com.example.recordroom.model;

import java.util.List;

public class RrwebBatchIngestRequest {
    private String type; // "rrweb"
    private List<RrwebEventEnvelope> events;

    public RrwebBatchIngestRequest() {}

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public List<RrwebEventEnvelope> getEvents() { return events; }
    public void setEvents(List<RrwebEventEnvelope> events) { this.events = events; }
}
