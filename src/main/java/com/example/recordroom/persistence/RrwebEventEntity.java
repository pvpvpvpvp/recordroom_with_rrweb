package com.example.recordroom.persistence;

import javax.persistence.*;

@Entity
@Table(name = "rr_rrweb_event", indexes = {
        @Index(name = "idx_rrweb_record_ts_seq", columnList = "recordId,tsEpochMs,seq")
})
public class RrwebEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 80, nullable = false)
    private String eventId;

    @Column(length = 64, nullable = false)
    private String recordId;

    private long tsEpochMs;

    private long seq;

    @Lob
    @Column
    private String payloadJson;

    protected RrwebEventEntity() {}

    public RrwebEventEntity(String eventId, String recordId, long tsEpochMs, long seq, String payloadJson) {
        this.eventId = eventId;
        this.recordId = recordId;
        this.tsEpochMs = tsEpochMs;
        this.seq = seq;
        this.payloadJson = payloadJson;
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getRecordId() { return recordId; }
    public long getTsEpochMs() { return tsEpochMs; }
    public long getSeq() { return seq; }
    public String getPayloadJson() { return payloadJson; }
}
