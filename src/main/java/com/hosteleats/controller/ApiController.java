package com.hosteleats.controller;

import com.hosteleats.model.MenuItem;
import com.hosteleats.model.Order;
import com.hosteleats.model.SessionPayload;
import com.hosteleats.service.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final String bootId = UUID.randomUUID().toString();
    private final long startedAt = System.currentTimeMillis();

    private final AuthService authService;
    private final RateLimiterService rateLimiterService;
    private final AnalyticsService analyticsService;
    private final CampusService campusService;
    private final ComboService comboService;
    private final SlotService slotService;
    private final DataStore dataStore;
    private final ClockService clockService;

    public ApiController(AuthService authService,
                         RateLimiterService rateLimiterService,
                         AnalyticsService analyticsService,
                         CampusService campusService,
                         ComboService comboService,
                         SlotService slotService,
                         DataStore dataStore,
                         ClockService clockService) {
        this.authService = authService;
        this.rateLimiterService = rateLimiterService;
        this.analyticsService = analyticsService;
        this.campusService = campusService;
        this.comboService = comboService;
        this.slotService = slotService;
        this.dataStore = dataStore;
        this.clockService = clockService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "boot_id", bootId,
                "started_at", String.valueOf(startedAt)
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        String token = authService.login(username, password);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials"));
        }

        SessionPayload session = authService.getSession(token);
        return ResponseEntity.ok(Map.of(
                "token", token,
                "role", session.getRole(),
                "name", session.getName(),
                "expires_in", AuthService.TOKEN_TTL_SECONDS
        ));
    }

    @GetMapping("/auth/session")
    public ResponseEntity<Map<String, Object>> getSession(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Missing or invalid token"));
        }

        SessionPayload session = authService.getSession(authHeader);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Session expired or invalid"));
        }

        return ResponseEntity.ok(Map.of(
                "username", session.getUsername(),
                "role", session.getRole(),
                "name", session.getName(),
                "expires_at", session.getExpiresAt()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null) {
            authService.logout(authHeader);
        }
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @GetMapping("/menu")
    public ResponseEntity<Map<String, Object>> getMenu() {
        Collection<MenuItem> items = dataStore.getMenuItems().values();
        Set<String> categories = new LinkedHashSet<>();
        categories.add("All Items");
        for (MenuItem item : items) {
            categories.add(item.getCategory());
        }

        return ResponseEntity.ok(Map.of(
                "items", items,
                "categories", categories
        ));
    }

    @GetMapping("/orders")
    public ResponseEntity<Map<String, Object>> getOrders(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        SessionPayload session = authService.getSession(authHeader);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required to view orders"));
        }

        List<Order> allOrders = dataStore.getOrders();
        List<Order> filtered;
        if ("staff".equalsIgnoreCase(session.getRole())) {
            filtered = new ArrayList<>(allOrders);
        } else {
            filtered = allOrders.stream()
                    .filter(o -> o.getUserId().equalsIgnoreCase(session.getUsername()))
                    .toList();
        }

        return ResponseEntity.ok(Map.of(
                "orders", filtered,
                "role", session.getRole()
        ));
    }

    @PostMapping("/orders")
    public ResponseEntity<Map<String, Object>> createOrder(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> orderReq) {

        SessionPayload session = authService.getSession(authHeader);
        String userId = (session != null) ? session.getUsername() : "guest";
        String userName = (session != null) ? session.getName() : "Guest Student";

        boolean allowed = rateLimiterService.allowOrder(userId);
        if (!allowed) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                    "allowed", false,
                    "error", "Rate limit exceeded. Maximum 3 orders per 30 seconds."
            ));
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) orderReq.getOrDefault("items", Collections.emptyList());
        int total = 0;
        for (Map<String, Object> itemMap : items) {
            int price = ((Number) itemMap.getOrDefault("price", 0)).intValue();
            int qty = ((Number) itemMap.getOrDefault("qty", 1)).intValue();
            total += price * qty;
        }

        String pickupTime = (String) orderReq.getOrDefault("pickup_time", "Immediate");
        String orderId = "HE-" + (2400 + dataStore.getOrders().size() + 1);

        Order newOrder = new Order(orderId, userId, userName, items, total, "Queued", pickupTime, clockService.nowSeconds());
        dataStore.getOrders().add(0, newOrder);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "allowed", true,
                "order", newOrder,
                "message", "Order placed successfully!"
        ));
    }

    @PatchMapping("/orders/{id}/status")
    public ResponseEntity<Map<String, Object>> updateOrderStatus(
            @PathVariable("id") String id,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, String> body) {

        SessionPayload session = authService.getSession(authHeader);
        if (session == null || !"staff".equalsIgnoreCase(session.getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Staff authorization required"));
        }

        String newStatus = body.get("status");
        if (newStatus == null || newStatus.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Status is required"));
        }

        for (Order order : dataStore.getOrders()) {
            if (order.getId().equalsIgnoreCase(id)) {
                order.setStatus(newStatus);
                return ResponseEntity.ok(Map.of("message", "Status updated", "order", order));
            }
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Order not found"));
    }

    @PostMapping("/combo/recommend")
    public ResponseEntity<Map<String, Object>> recommendCombo(@RequestParam(value = "budget", defaultValue = "60") int budget) {
        Map<String, Object> recommendation = comboService.recommendCombo(budget);
        return ResponseEntity.ok(recommendation);
    }

    @GetMapping("/slots")
    public ResponseEntity<Map<String, Object>> getSlots() {
        return ResponseEntity.ok(slotService.getSlotsData());
    }

    @PostMapping("/slots/reserve")
    public ResponseEntity<Map<String, Object>> reserveSlot(@RequestBody Map<String, Integer> req) {
        Integer start = req.get("start");
        Integer end = req.get("end");
        if (start == null || end == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "start and end minutes are required"));
        }

        boolean success = slotService.reserveSlot(start, end);
        if (!success) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "success", false,
                    "error", "Requested slot overlaps with existing reservations or is invalid"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Slot reserved successfully",
                "slot", List.of(start, end)
        ));
    }

    @GetMapping("/campus/route")
    public ResponseEntity<Map<String, Object>> getCampusRoute(
            @RequestParam(value = "source", defaultValue = "Hostel A") String source,
            @RequestParam(value = "target", defaultValue = "Canteen") String target) {
        Map<String, Object> route = campusService.findShortestPath(source, target);
        return ResponseEntity.ok(route);
    }

    @GetMapping("/analytics/rush-hour")
    public ResponseEntity<Map<String, Object>> getRushHour(@RequestParam(value = "window", defaultValue = "2") int window) {
        Map<String, Object> analytics = analyticsService.getRushHourAnalytics(window);
        return ResponseEntity.ok(analytics);
    }

    @GetMapping("/limiter/status")
    public ResponseEntity<Map<String, Object>> getLimiterStatus(@RequestParam(value = "userId", defaultValue = "guest") String userId) {
        int used = rateLimiterService.getRecentOrderCount(userId);
        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "used", used,
                "max", RateLimiterService.MAX_ORDERS,
                "windowSeconds", RateLimiterService.WINDOW_SECONDS
        ));
    }

    @PostMapping("/admin/reset")
    public ResponseEntity<Map<String, Object>> resetData() {
        dataStore.resetData();
        authService.clearSessions();
        rateLimiterService.clear();
        clockService.reset();
        return ResponseEntity.ok(Map.of("message", "Data store and services reset to initial seed state"));
    }
}
