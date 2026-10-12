package com.hosteleats.service;

import com.hosteleats.model.MenuItem;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ComboService {
    private final DataStore dataStore;

    public ComboService(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    private List<MenuItem> append(List<MenuItem> list, MenuItem item) {
        List<MenuItem> res = new ArrayList<>(list);
        res.add(item);
        return res;
    }

    public Map<String, Object> recommendCombo(int budget) {
        List<MenuItem> items = new ArrayList<>(dataStore.getMenuItems().values());
        return recommendComboForItems(items, budget);
    }

    public Map<String, Object> recommendComboForItems(List<MenuItem> items, int budget) {
        if (budget <= 0 || items == null || items.isEmpty()) {
            return Map.of("items", List.of(), "total_cost", 0, "total_satisfaction", 0, "budget", budget);
        }

        int[] dp = new int[budget + 1];
        List<List<MenuItem>> selected = new ArrayList<>(budget + 1);
        for (int i = 0; i <= budget; i++) {
            selected.add(new ArrayList<>());
        }

        // BUG 5 INJECTION:
        // Forward iteration makes it an Unbounded Knapsack (allowing duplicates)
        for (MenuItem item : items) {
            int price = item.getPrice();
            int satisfaction = item.getSatisfaction();

            for (int capacity = price; capacity <= budget; capacity++) {
                int candidate = dp[capacity - price] + satisfaction;
                if (candidate > dp[capacity]) {
                    dp[capacity] = candidate;
                    selected.set(capacity, append(selected.get(capacity - price), item));
                }
            }
        }

        int bestCapacity = 0;
        int maxSatisfaction = 0;
        for (int c = 0; c <= budget; c++) {
            if (dp[c] > maxSatisfaction) {
                maxSatisfaction = dp[c];
                bestCapacity = c;
            }
        }

        List<MenuItem> bestItems = selected.get(bestCapacity);
        int totalCost = bestItems.stream().mapToInt(MenuItem::getPrice).sum();

        Map<String, Object> result = new HashMap<>();
        result.put("items", bestItems);
        result.put("total_cost", totalCost);
        result.put("total_satisfaction", maxSatisfaction);
        result.put("budget", budget);
        return result;
    }
}
