package com.example.recordroom.persistence;

import javax.persistence.*;

@Entity
@Table(name = "rr_network_event", indexes = {
        @Index(name = "idx_network_record_ts_seq", columnList = "recordId,startedAtEpochMs,seq"),
        @Index(name = "idx_network_record_eventid", columnList = "recordId,eventId")
})
public class NetworkEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 80, nullable = false)
    private String eventId;

    @Column(length = 64, nullable = false)
    private String recordId;

    @Column(length = 80)
    private String clientRequestId;

    @Column(length = 16, nullable = false)
    private String method;

    @Column(length = 4096, nullable = false)
    private String url;

    private int status;

    @Lob
    @Column
    private String requestHeadersJson;

    @Lob
    @Column
    private String requestBody;

    @Lob
    @Column
    private String responseHeadersJson;

    @Lob
    @Column
    private String responseBody;

    private long startedAtEpochMs;

    private long durationMs;

    @Column(length = 1024)
    private String error;

    private long seq;

    protected NetworkEventEntity() {}

    public NetworkEventEntity(String eventId, String recordId, String clientRequestId, String method, String url, int status,
                              String requestHeadersJson, String requestBody,
                              String responseHeadersJson, String responseBody,
                              long startedAtEpochMs, long durationMs, String error, long seq) {
        this.eventId = eventId;
        this.recordId = recordId;
        this.clientRequestId = clientRequestId;
        this.method = method;
        this.url = url;
        this.status = status;
        this.requestHeadersJson = requestHeadersJson;
        this.requestBody = requestBody;
        this.responseHeadersJson = responseHeadersJson;
        this.responseBody = responseBody;
        this.startedAtEpochMs = startedAtEpochMs;
        this.durationMs = durationMs;
        this.error = error;
        this.seq = seq;
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getRecordId() { return recordId; }
    public String getClientRequestId() { return clientRequestId; }
    public String getMethod() { return method; }
    public String getUrl() { return url; }
    public int getStatus() { return status; }
    public String getRequestHeadersJson() { return requestHeadersJson; }
    public String getRequestBody() { return requestBody; }
    public String getResponseHeadersJson() { return responseHeadersJson; }
    public String getResponseBody() { return responseBody; }
    public long getStartedAtEpochMs() { return startedAtEpochMs; }
    public long getDurationMs() { return durationMs; }
    public String getError() { return error; }
    public long getSeq() { return seq; }
}
