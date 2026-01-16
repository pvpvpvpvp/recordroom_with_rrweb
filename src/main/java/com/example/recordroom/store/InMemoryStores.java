package com.example.recordroom.store;

import com.example.recordroom.model.BreadcrumbEvent;
import com.example.recordroom.model.ConsoleEvent;
import com.example.recordroom.model.NetworkEvent;
import com.example.recordroom.model.Record;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryStores {

    public static class RecordStore {
        private final Map<String, Record> records = new ConcurrentHashMap<>();

        public Record save(Record record) {
            records.put(record.getRecordId(), record);
            return record;
        }

        public Record get(String recordId) {
            return records.get(recordId);
        }

        public boolean exists(String recordId) {
            return records.containsKey(recordId);
        }
    }

    public static class EventStore {
        private final Map<String, List<ConsoleEvent>> consoleEvents = new ConcurrentHashMap<>();
        private final Map<String, List<NetworkEvent>> networkEvents = new ConcurrentHashMap<>();
        private final Map<String, List<BreadcrumbEvent>> breadcrumbEvents = new ConcurrentHashMap<>();

        private final AtomicLong consoleSeq = new AtomicLong(0);
        private final AtomicLong networkSeq = new AtomicLong(0);
        private final AtomicLong breadcrumbSeq = new AtomicLong(0);

        public ConsoleEvent appendConsoleEvent(String recordId, String level, String message, String stack, long ts, long seqFromClient) {
            long idNum = consoleSeq.incrementAndGet();
            String eventId = String.format("c_%08d", idNum);

            ConsoleEvent e = new ConsoleEvent(
                    eventId,
                    recordId,
                    "console",
                    level,
                    message,
                    stack,
                    ts,
                    seqFromClient
            );

            consoleEvents.computeIfAbsent(recordId, k -> Collections.synchronizedList(new ArrayList<>())).add(e);
            return e;
        }

        public NetworkEvent appendNetworkEvent(
                String recordId,
                String clientRequestId,
                String method,
                String url,
                int status,
                Map<String, String> requestHeaders,
                String requestBody,
                Map<String, String> responseHeaders,
                String responseBody,
                long startedAtEpochMs,
                long durationMs,
                String error,
                long seqFromClient
        ) {
            long idNum = networkSeq.incrementAndGet();
            String eventId = String.format("n_%08d", idNum);

            NetworkEvent e = new NetworkEvent(
                    eventId,
                    recordId,
                    "network",
                    clientRequestId,
                    method,
                    url,
                    status,
                    requestHeaders,
                    requestBody,
                    responseHeaders,
                    responseBody,
                    startedAtEpochMs,
                    durationMs,
                    error,
                    seqFromClient
            );

            networkEvents.computeIfAbsent(recordId, k -> Collections.synchronizedList(new ArrayList<>())).add(e);
            return e;
        }

        public BreadcrumbEvent appendBreadcrumbEvent(
                String recordId,
                String name,
                String message,
                Map<String, String> data,
                long ts,
                long seqFromClient
        ) {
            long idNum = breadcrumbSeq.incrementAndGet();
            String eventId = String.format("b_%08d", idNum);

            BreadcrumbEvent e = new BreadcrumbEvent(
                    eventId,
                    recordId,
                    "breadcrumb",
                    name,
                    message,
                    data,
                    ts,
                    seqFromClient
            );

            breadcrumbEvents.computeIfAbsent(recordId, k -> Collections.synchronizedList(new ArrayList<>())).add(e);
            return e;
        }

        public List<ConsoleEvent> listConsoleEvents(String recordId) {
            List<ConsoleEvent> list = consoleEvents.getOrDefault(recordId, Collections.emptyList());
            List<ConsoleEvent> copied = new ArrayList<>(list);
            copied.sort(Comparator.comparingLong(ConsoleEvent::getTs).thenComparingLong(ConsoleEvent::getSeq));
            return copied;
        }

        public List<NetworkEvent> listNetworkEvents(String recordId) {
            List<NetworkEvent> list = networkEvents.getOrDefault(recordId, Collections.emptyList());
            List<NetworkEvent> copied = new ArrayList<>(list);
            copied.sort(Comparator.comparingLong(NetworkEvent::getStartedAtEpochMs).thenComparingLong(NetworkEvent::getSeq));
            return copied;
        }

        public NetworkEvent getNetworkEvent(String recordId, String eventId) {
            List<NetworkEvent> list = networkEvents.getOrDefault(recordId, Collections.emptyList());
            for (NetworkEvent e : list) {
                if (e.getEventId().equals(eventId)) return e;
            }
            return null;
        }

        public List<BreadcrumbEvent> listBreadcrumbEvents(String recordId) {
            List<BreadcrumbEvent> list = breadcrumbEvents.getOrDefault(recordId, Collections.emptyList());
            List<BreadcrumbEvent> copied = new ArrayList<>(list);
            copied.sort(Comparator.comparingLong(BreadcrumbEvent::getTs).thenComparingLong(BreadcrumbEvent::getSeq));
            return copied;
        }
    }
}
