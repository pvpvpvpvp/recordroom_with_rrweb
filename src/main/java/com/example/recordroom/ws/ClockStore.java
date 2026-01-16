package com.example.recordroom.ws;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ClockStore {
    private final ConcurrentMap<String, ClockState> map = new ConcurrentHashMap<>();

    public ClockState getOrCreate(String recordId) {
        return map.computeIfAbsent(recordId, ClockState::new);
    }

    public ClockState get(String recordId) {
        return map.get(recordId);
    }

    public void update(String recordId, long tMs, long baseEpochMs, long absEpochMs, String mode, double speed) {
        getOrCreate(recordId).update(tMs, baseEpochMs, absEpochMs, mode, speed);
    }
}
