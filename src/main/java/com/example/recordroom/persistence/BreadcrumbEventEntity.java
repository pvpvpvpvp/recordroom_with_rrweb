package com.example.recordroom.persistence;

import javax.persistence.*;

@Entity
@Table(name = "rr_breadcrumb_event", indexes = {
        @Index(name = "idx_breadcrumb_record_ts_seq", columnList = "recordId,ts,seq")
})
public class BreadcrumbEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 80, nullable = false)
    private String eventId;

    @Column(length = 64, nullable = false)
    private String recordId;

    @Column(length = 64)
    private String name;

    @Lob
    @Column
    private String message;

    @Lob
    @Column
    private String dataJson;

    private long ts;

    private long seq;

    protected BreadcrumbEventEntity() {}

    public BreadcrumbEventEntity(String eventId, String recordId, String name, String message, String dataJson, long ts, long seq) {
        this.eventId = eventId;
        this.recordId = recordId;
        this.name = name;
        this.message = message;
        this.dataJson = dataJson;
        this.ts = ts;
        this.seq = seq;
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getRecordId() { return recordId; }
    public String getName() { return name; }
    public String getMessage() { return message; }
    public String getDataJson() { return dataJson; }
    public long getTs() { return ts; }
    public long getSeq() { return seq; }
}
