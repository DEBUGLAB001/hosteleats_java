package com.hosteleats.service;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SlotService {
    private final DataStore dataStore;

    public SlotService(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    public List<int[]> mergeIntervals(List<int[]> intervals) {
        if (intervals == null || intervals.isEmpty()) {
            return new ArrayList<>();
        }

        List<int[]> sortedIntervals = new ArrayList<>(intervals);
        sortedIntervals.sort(Comparator.comparingInt(a -> a[0]));

        List<int[]> merged = new ArrayList<>();
        for (int[] interval : sortedIntervals) {
            if (merged.isEmpty() || interval[0] > merged.get(merged.size() - 1)[1]) {
                merged.add(new int[]{interval[0], interval[1]});
            } else {
                merged.get(merged.size() - 1)[1] = interval[1];
            }
        }

        return merged;
    }

    public synchronized boolean isSlotAvailable(int start, int end) {
        if (start >= end) {
            return false;
        }
        List<int[]> merged = mergeIntervals(dataStore.getBookedSlots());
        for (int[] interval : merged) {
            if (start < interval[1] && end > interval[0]) {
                return false;
            }
        }
        return true;
    }

    public synchronized boolean reserveSlot(int start, int end) {
        if (!isSlotAvailable(start, end)) {
            return false;
        }
        dataStore.getBookedSlots().add(new int[]{start, end});
        return true;
    }

    public synchronized Map<String, Object> getSlotsData() {
        List<int[]> raw = new ArrayList<>(dataStore.getBookedSlots());
        List<int[]> merged = mergeIntervals(raw);

        Map<String, Object> res = new HashMap<>();
        res.put("booked_slots", raw);
        res.put("merged_slots", merged);
        return res;
    }
}
