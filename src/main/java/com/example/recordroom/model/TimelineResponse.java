package com.example.recordroom.model;

import java.util.List;

public class TimelineResponse {

    private final List<TimelineItem> items;
    private final String nextAfter;

    public TimelineResponse(List<TimelineItem> items, String nextAfter) {
        this.items = items;
        this.nextAfter = nextAfter;
    }

    public List<TimelineItem> getItems() { return items; }
    public String getNextAfter() { return nextAfter; }

    public static class TimelineItem {
        private final String kind;
        private final String eventId;
        private final long ts;
        private final long seq;

        private final String level;   // console
        private final String message; // console/breadcrumb
        private final String stack;   // console

        private final String method;  // network
        private final String url;     // network
        private final int status;     // network

        private final String name;    // breadcrumb

        public TimelineItem(String kind, String eventId, long ts, long seq,
                            String level, String message, String stack,
                            String method, String url, int status,
                            String name) {
            this.kind = kind;
            this.eventId = eventId;
            this.ts = ts;
            this.seq = seq;
            this.level = level;
            this.message = message;
            this.stack = stack;
            this.method = method;
            this.url = url;
            this.status = status;
            this.name = name;
        }

        public String getKind() { return kind; }
        public String getEventId() { return eventId; }
        public long getTs() { return ts; }
        public long getSeq() { return seq; }
        public String getLevel() { return level; }
        public String getMessage() { return message; }
        public String getStack() { return stack; }
        public String getMethod() { return method; }
        public String getUrl() { return url; }
        public int getStatus() { return status; }
        public String getName() { return name; }
    }
}
