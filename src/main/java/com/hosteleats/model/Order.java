package com.hosteleats.model;

import java.util.List;
import java.util.Map;

public class Order {
    private String id;
    private String userId;
    private String userName;
    private List<Map<String, Object>> items;
    private int total;
    private String status; // Queued, Preparing, Ready, Completed
    private String pickupTime;
    private double createdAt;

    public Order() {}

    public Order(String id, String userId, String userName, List<Map<String, Object>> items, int total, String status, String pickupTime, double createdAt) {
        this.id = id;
        this.userId = userId;
        this.userName = userName;
        this.items = items;
        this.total = total;
        this.status = status;
        this.pickupTime = pickupTime;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public List<Map<String, Object>> getItems() {
        return items;
    }

    public void setItems(List<Map<String, Object>> items) {
        this.items = items;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPickupTime() {
        return pickupTime;
    }

    public void setPickupTime(String pickupTime) {
        this.pickupTime = pickupTime;
    }

    public double getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(double createdAt) {
        this.createdAt = createdAt;
    }
}
