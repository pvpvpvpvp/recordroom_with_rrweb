package com.example.recordroom.model;

import com.fasterxml.jackson.databind.JsonNode;

public class RrwebEventEnvelope {
    private long ts;
    private long seq;
    private JsonNode payload; // rrweb event object

    public RrwebEventEnvelope() {}

    public long getTs() { return ts; }
    public void setTs(long ts) { this.ts = ts; }

    public long getSeq() { return seq; }
    public void setSeq(long seq) { this.seq = seq; }

    public JsonNode getPayload() { return payload; }
    public void setPayload(JsonNode payload) { this.payload = payload; }
}
