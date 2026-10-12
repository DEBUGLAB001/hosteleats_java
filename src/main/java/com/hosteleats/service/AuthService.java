package com.hosteleats.service;

import com.hosteleats.model.Account;
import com.hosteleats.model.SessionPayload;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {
    public static final String SECRET_KEY = "hosteleats-auth-contest-secret";
    public static final int TOKEN_TTL_SECONDS = 30;

    private final ClockService clock;
    private final DataStore dataStore;
    private final Map<String, SessionPayload> sessions = new ConcurrentHashMap<>();

    public AuthService(ClockService clock, DataStore dataStore) {
        this.clock = clock;
        this.dataStore = dataStore;
    }

    public String login(String username, String password) {
        Account account = dataStore.getAccounts().get(username);
        if (account == null || !account.getPassword().equals(password)) {
            return null;
        }

        String token = "token_" + UUID.randomUUID().toString().replace("-", "");

        // BUG 1 INJECTION:
        // When creating token payload, store expires_at = clock.nowMillis() + (TOKEN_TTL_SECONDS * 1000)
        // Milliseconds stored: ~ 1.7e12
        double expiresAt = clock.nowMillis() + (TOKEN_TTL_SECONDS * 1000.0);

        sessions.put(token, new SessionPayload(account.getUsername(), account.getRole(), account.getName(), expiresAt));
        return token;
    }

    public SessionPayload getSession(String token) {
        if (token == null) return null;
        if (token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }

        SessionPayload session = sessions.get(token);
        if (session == null) {
            return null;
        }

        // BUG 1 INJECTION:
        // Compares clock.nowSeconds() (~1.7e9) with session.getExpiresAt() (~1.7e12)
        // nowSeconds() > expiresAt will remain false for hundreds of years!
        if (clock.nowSeconds() > session.getExpiresAt()) {
            sessions.remove(token);
            return null;
        }

        return session;
    }

    public void logout(String token) {
        if (token == null) return;
        if (token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }
        sessions.remove(token);
    }

    public void clearSessions() {
        sessions.clear();
    }
}
