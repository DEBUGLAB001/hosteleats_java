package com.hosteleats.service;

import com.hosteleats.model.Account;
import com.hosteleats.model.MenuItem;
import com.hosteleats.model.Order;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class DataStore {
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final Map<String, MenuItem> menuItems = new LinkedHashMap<>();
    private final List<Order> orders = new CopyOnWriteArrayList<>();
    private final List<int[]> bookedSlots = new CopyOnWriteArrayList<>();
    private final List<Integer> hourlyOrderCounts = new ArrayList<>();
    private final List<String> hourLabels = new ArrayList<>();

    public DataStore() {
        initDefaultData();
    }

    public synchronized void resetData() {
        accounts.clear();
        menuItems.clear();
        orders.clear();
        bookedSlots.clear();
        hourlyOrderCounts.clear();
        hourLabels.clear();
        initDefaultData();
    }

    private void initDefaultData() {
        // Accounts
        accounts.put("aarav", new Account("aarav", "hostel123", "student", "Aarav Mehta"));
        accounts.put("canteen", new Account("canteen", "kitchen123", "staff", "Canteen Team"));

        // Menu Items
        addMenuItem(new MenuItem("masala-dosa", "Masala Dosa", "South Indian", 65, 9, "🥞", true));
        addMenuItem(new MenuItem("samosa", "Punjabi Samosa", "Snacks", 20, 4, "🥟", true));
        addMenuItem(new MenuItem("masala-chai", "Masala Chai", "Drinks", 15, 3, "☕", false));
        addMenuItem(new MenuItem("fruit-cup", "Seasonal Fruit Cup", "Fresh", 30, 5, "🍉", false));
        addMenuItem(new MenuItem("paneer-roll", "Paneer Tikka Roll", "Wraps", 55, 8, "🌯", true));
        addMenuItem(new MenuItem("lemon-rice", "Lemon Rice Bowl", "Meals", 45, 7, "🍚", false));
        addMenuItem(new MenuItem("filter-coffee", "Filter Coffee", "Drinks", 20, 4, "🥤", false));

        // Initial Seed Orders
        List<Map<String, Object>> items1 = List.of(Map.of("id", "masala-dosa", "name", "Masala Dosa", "qty", 1, "price", 65));
        orders.add(new Order("HE-2401", "aarav", "Aarav Mehta", items1, 65, "Queued", "12:30 PM", 1700000000.0));

        List<Map<String, Object>> items2 = List.of(
                Map.of("id", "samosa", "name", "Punjabi Samosa", "qty", 2, "price", 20),
                Map.of("id", "masala-chai", "name", "Masala Chai", "qty", 1, "price", 15)
        );
        orders.add(new Order("HE-2402", "aarav", "Aarav Mehta", items2, 55, "Preparing", "01:00 PM", 1700000030.0));

        List<Map<String, Object>> items3 = List.of(Map.of("id", "paneer-roll", "name", "Paneer Tikka Roll", "qty", 1, "price", 55));
        orders.add(new Order("HE-2403", "student2", "Priya Sharma", items3, 55, "Ready", "01:15 PM", 1700000060.0));

        // Initial booked intervals: [[690, 750], [705, 720], [810, 840]]
        bookedSlots.add(new int[]{690, 750});
        bookedSlots.add(new int[]{705, 720});
        bookedSlots.add(new int[]{810, 840});

        // Hourly order counts: [2, 4, 7, 5, 8, 6, 11, 9, 12, 8, 20, 38]
        hourlyOrderCounts.addAll(List.of(2, 4, 7, 5, 8, 6, 11, 9, 12, 8, 20, 38));

        // Hour labels: ["8 AM", "9 AM", "10 AM", "11 AM", "12 PM", "1 PM", "2 PM", "3 PM", "4 PM", "5 PM", "6 PM", "7 PM"]
        hourLabels.addAll(List.of("8 AM", "9 AM", "10 AM", "11 AM", "12 PM", "1 PM", "2 PM", "3 PM", "4 PM", "5 PM", "6 PM", "7 PM"));
    }

    private void addMenuItem(MenuItem item) {
        menuItems.put(item.getId(), item);
    }

    public Map<String, Account> getAccounts() {
        return accounts;
    }

    public Map<String, MenuItem> getMenuItems() {
        return menuItems;
    }

    public List<Order> getOrders() {
        return orders;
    }

    public List<int[]> getBookedSlots() {
        return bookedSlots;
    }

    public List<Integer> getHourlyOrderCounts() {
        return hourlyOrderCounts;
    }

    public List<String> getHourLabels() {
        return hourLabels;
    }
}
