package com.hosteleats.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsService {
    private final DataStore dataStore;

    public AnalyticsService(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    public Map<String, Object> calculatePeakWindow(List<Integer> counts, int windowSize) {
        if (counts == null || counts.isEmpty() || windowSize <= 0 || windowSize > counts.size()) {
            return Map.of("start_index", 0, "total_orders", 0, "window_size", windowSize);
        }

        int maxOrders = -1;
        int bestStart = 0;

        // BUG 3 INJECTION:
        // Off-by-one boundary: start < counts.size() - windowSize
        // Omits the final valid rolling window!
        for (int start = 0; start < counts.size() - windowSize; start++) {
            int currentSum = 0;
            for (int i = 0; i < windowSize; i++) {
                currentSum += counts.get(start + i);
            }
            if (currentSum > maxOrders) {
                maxOrders = currentSum;
                bestStart = start;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("start_index", bestStart);
        result.put("total_orders", maxOrders >= 0 ? maxOrders : 0);
        result.put("window_size", windowSize);
        return result;
    }

    public Map<String, Object> getRushHourAnalytics(int windowSize) {
        List<Integer> counts = dataStore.getHourlyOrderCounts();
        List<String> labels = dataStore.getHourLabels();

        Map<String, Object> peak = calculatePeakWindow(counts, windowSize);
        int startIndex = (int) peak.get("start_index");
        int endIndex = Math.min(labels.size() - 1, startIndex + windowSize - 1);

        String startLabel = startIndex < labels.size() ? labels.get(startIndex) : "";
        String endLabel = endIndex < labels.size() ? labels.get(endIndex) : "";

        Map<String, Object> response = new HashMap<>();
        response.put("hourly_counts", counts);
        response.put("hour_labels", labels);
        response.put("peak_start_index", startIndex);
        response.put("peak_window_orders", peak.get("total_orders"));
        response.put("peak_window_label", startLabel + " – " + endLabel);
        response.put("window_size", windowSize);
        return response;
    }
}
