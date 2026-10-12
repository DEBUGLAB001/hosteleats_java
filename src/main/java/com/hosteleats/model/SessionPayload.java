package com.hosteleats.model;

public class SessionPayload {
    private String username;
    private String role;
    private String name;
    private double expiresAt;

    public SessionPayload() {}

    public SessionPayload(String username, String role, String name, double expiresAt) {
        this.username = username;
        this.role = role;
        this.name = name;
        this.expiresAt = expiresAt;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(double expiresAt) {
        this.expiresAt = expiresAt;
    }
}
