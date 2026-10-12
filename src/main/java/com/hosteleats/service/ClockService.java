package com.hosteleats.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ClockService {
    private final AtomicReference<Double> fixedSeconds = new AtomicReference<>(null);

    public double nowSeconds() {
        Double fixed = fixedSeconds.get();
        if (fixed != null) {
            return fixed;
        }
        return Instant.now().toEpochMilli() / 1000.0;
    }

    public long nowMillis() {
        return (long) (nowSeconds() * 1000.0);
    }

    public void setTime(double seconds) {
        this.fixedSeconds.set(seconds);
    }

    public void advanceSeconds(double seconds) {
        double current = nowSeconds();
        this.fixedSeconds.set(current + seconds);
    }

    public void reset() {
        this.fixedSeconds.set(null);
    }
}
