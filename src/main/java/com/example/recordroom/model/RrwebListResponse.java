package com.example.recordroom.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public class RrwebListResponse {
    private List<JsonNode> events;
    private String nextAfter;

    public RrwebListResponse() {}

    public RrwebListResponse(List<JsonNode> events, String nextAfter) {
        this.events = events;
        this.nextAfter = nextAfter;
    }

    public List<JsonNode> getEvents() { return events; }
    public void setEvents(List<JsonNode> events) { this.events = events; }

    public String getNextAfter() { return nextAfter; }
    public void setNextAfter(String nextAfter) { this.nextAfter = nextAfter; }
}
