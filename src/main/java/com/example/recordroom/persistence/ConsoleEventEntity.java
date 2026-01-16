package com.example.recordroom.persistence;

import javax.persistence.*;

@Entity
@Table(name = "rr_console_event", indexes = {
        @Index(name = "idx_console_record_ts_seq", columnList = "recordId,ts,seq")
})
public class ConsoleEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 80, nullable = false)
    private String eventId;

    @Column(length = 64, nullable = false)
    private String recordId;

    @Column(length = 16, nullable = false)
    private String level;

    @Lob
    @Column
    private String message;

    @Lob
    @Column
    private String stack;

    private long ts;

    private long seq;

    protected ConsoleEventEntity() {}

    public ConsoleEventEntity(String eventId, String recordId, String level, String message, String stack, long ts, long seq) {
        this.eventId = eventId;
        this.recordId = recordId;
        this.level = level;
        this.message = message;
        this.stack = stack;
        this.ts = ts;
        this.seq = seq;
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getRecordId() { return recordId; }
    public String getLevel() { return level; }
    public String getMessage() { return message; }
    public String getStack() { return stack; }
    public long getTs() { return ts; }
    public long getSeq() { return seq; }
}
