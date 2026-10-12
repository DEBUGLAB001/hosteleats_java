package com.hosteleats.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class RateLimiterService {
    public static final double WINDOW_SECONDS = 30.0;
    public static final int MAX_ORDERS = 3;

    private final ClockService clock;
    private final Map<String, List<Double>> orderTimes = new ConcurrentHashMap<>();

    public RateLimiterService(ClockService clock) {
        this.clock = clock;
    }

    public synchronized boolean allowOrder(String userId) {
        double now = clock.nowSeconds();
        List<Double> times = orderTimes.getOrDefault(userId, new ArrayList<>());
        times = times.stream().filter(t -> now - t < WINDOW_SECONDS).collect(Collectors.toList());

        // BUG 2 INJECTION:
        // timestamp added BEFORE checking limit!
        // Rejected requests extend the lockout window forever
        times.add(now);
        orderTimes.put(userId, times);

        if (times.size() > MAX_ORDERS) {
            return false;
        }
        return true;
    }

    public synchronized int getRecentOrderCount(String userId) {
        double now = clock.nowSeconds();
        List<Double> times = orderTimes.getOrDefault(userId, new ArrayList<>());
        return (int) times.stream().filter(t -> now - t < WINDOW_SECONDS).count();
    }

    public synchronized void clear() {
        orderTimes.clear();
    }
}
