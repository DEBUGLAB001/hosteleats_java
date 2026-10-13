package com.hosteleats;

import com.hosteleats.model.MenuItem;
import com.hosteleats.service.*;

import java.util.*;

/**
 * Standalone test runner that exercises all 6 HostelEats challenge tests
 * and emits structured JSON results consumed by the platform test harness.
 *
 * Run via:
 *   java -cp target/app.jar com.hosteleats.TestRunner
 */
public class TestRunner {

    // ── tiny test infrastructure ──────────────────────────────────────────
    record TestResult(String name, boolean passed, long ms, String error) {}

    private static final List<TestResult> results = new ArrayList<>();

    @FunctionalInterface
    interface TestCase { void run() throws Exception; }

    private static void test(String name, TestCase tc) {
        long start = System.currentTimeMillis();
        try {
            tc.run();
            results.add(new TestResult(name, true, System.currentTimeMillis() - start, null));
        } catch (Throwable t) {
            results.add(new TestResult(name, false, System.currentTimeMillis() - start, t.getMessage()));
        }
    }

    private static void assertEquals(Object expected, Object actual, String msg) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(msg + " — expected: " + expected + ", got: " + actual);
        }
    }

    private static void assertNotNull(Object val, String msg) {
        if (val == null) throw new AssertionError(msg);
    }

    private static void assertNull(Object val, String msg) {
        if (val != null) throw new AssertionError(msg + " — expected null, got: " + val);
    }

    private static void assertTrue(boolean val, String msg) {
        if (!val) throw new AssertionError(msg);
    }

    private static void assertFalse(boolean val, String msg) {
        if (val) throw new AssertionError(msg);
    }

    private static void assertArrayEquals(int[] expected, int[] actual, String msg) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(msg + " — expected: " + Arrays.toString(expected)
                    + ", got: " + Arrays.toString(actual));
        }
    }

    // ── main ─────────────────────────────────────────────────────────────
    public static void main(String[] args) {

        // ── Bug 1: Session Expiration ──
        test("testSessionExpiresAtConfiguredTime", () -> {
            ClockService clock = new ClockService();
            DataStore dataStore = new DataStore();
            AuthService authService = new AuthService(clock, dataStore);

            clock.setTime(1000.0);
            String token = authService.login("aarav", "hostel123");
            assertNotNull(token, "Login should succeed for valid credentials");
            assertNotNull(authService.getSession(token), "Session should be valid immediately after login");

            clock.advanceSeconds(AuthService.TOKEN_TTL_SECONDS + 1);
            assertNull(authService.getSession(token), "Session must expire after TOKEN_TTL_SECONDS + 1");
        });

        // ── Bug 2: Rate Limiter Lockout ──
        test("testRejectedRequestDoesNotExtendLimitWindow", () -> {
            ClockService clock = new ClockService();
            RateLimiterService rateLimiterService = new RateLimiterService(clock);

            String userId = "student_test";
            clock.setTime(100.0);
            assertTrue(rateLimiterService.allowOrder(userId), "Order 1 should be allowed at t=100");

            clock.setTime(110.0);
            assertTrue(rateLimiterService.allowOrder(userId), "Order 2 should be allowed at t=110");

            clock.setTime(120.0);
            assertTrue(rateLimiterService.allowOrder(userId), "Order 3 should be allowed at t=120");

            clock.setTime(121.0);
            assertFalse(rateLimiterService.allowOrder(userId), "Order 4 at t=121 should be rejected (limit 3)");

            clock.setTime(131.0);
            assertTrue(rateLimiterService.allowOrder(userId),
                    "Order at t=131 should be allowed because t=100 expired and rejected order shouldn't count");
        });

        // ── Bug 3: Rush Hour Peak Window Boundary ──
        test("testPeakWindowIncludesFinalValidStart", () -> {
            DataStore dataStore = new DataStore();
            AnalyticsService analyticsService = new AnalyticsService(dataStore);

            List<Integer> list1 = List.of(12, 10, 2, 1, 1);
            Map<String, Object> result1 = analyticsService.calculatePeakWindow(list1, 2);
            assertEquals(22, result1.get("total_orders"), "Total orders for first test should be 22");
            assertEquals(0, result1.get("start_index"), "Start index for first test should be 0");

            List<Integer> list2 = List.of(1, 2, 3, 20);
            Map<String, Object> result2 = analyticsService.calculatePeakWindow(list2, 2);
            assertEquals(23, result2.get("total_orders"), "Total orders for list ending with peak should be 23");
            assertEquals(2, result2.get("start_index"), "Start index must evaluate the final valid window (index 2)");
        });

        // ── Bug 4: Campus Walk Dijkstra ──
        test("testRouteDistanceIncludesFullPath", () -> {
            CampusService campusService = new CampusService();

            Map<String, Map<String, Integer>> testGraph = new HashMap<>();
            testGraph.put("Start", new HashMap<>(Map.of("Oak", 3, "Elm", 7)));
            testGraph.put("Oak",   new HashMap<>(Map.of("Start", 3, "Gate", 5)));
            testGraph.put("Elm",   new HashMap<>(Map.of("Start", 7, "Gate", 6)));
            testGraph.put("Gate",  new HashMap<>(Map.of("Oak", 5, "Elm", 6)));

            Map<String, Object> route = campusService.findShortestPathInGraph(testGraph, "Start", "Gate");
            assertEquals(8, route.get("minutes"), "Cumulative shortest path distance from Start to Gate must be 8");
            assertEquals(List.of("Start", "Oak", "Gate"), route.get("path"), "Path must be [Start, Oak, Gate]");
        });

        // ── Bug 5: Combo Builder 0/1 Knapsack ──
        test("testComboUsesEachMenuItemOnce", () -> {
            DataStore dataStore = new DataStore();
            ComboService comboService = new ComboService(dataStore);

            List<MenuItem> testItems = List.of(
                    new MenuItem("samosa",      "Punjabi Samosa",     "Snacks", 20, 4, "🥟", true),
                    new MenuItem("fruit-cup",   "Seasonal Fruit Cup", "Fresh",  30, 5, "🍉", false),
                    new MenuItem("lemon-rice",  "Lemon Rice Bowl",    "Meals",  45, 7, "🍚", false),
                    new MenuItem("masala-chai", "Masala Chai",        "Drinks", 15, 2, "☕", false)
            );

            Map<String, Object> result = comboService.recommendComboForItems(testItems, 60);

            @SuppressWarnings("unchecked")
            List<MenuItem> selected = (List<MenuItem>) result.get("items");

            assertEquals(9, result.get("total_satisfaction"),
                    "Satisfaction should be 9 under 0/1 knapsack constraint");

            Set<String> itemIds = new HashSet<>();
            for (MenuItem item : selected) {
                assertTrue(itemIds.add(item.getId()),
                        "Item " + item.getId() + " appears more than once!");
            }
        });

        // ── Bug 6: Overlapping Slots Merge ──
        test("testOverlappingSlotsMergeToFurthestEnd", () -> {
            DataStore dataStore = new DataStore();
            SlotService slotService = new SlotService(dataStore);

            List<int[]> intervals = new ArrayList<>();
            intervals.add(new int[]{540, 660});
            intervals.add(new int[]{570, 600});
            intervals.add(new int[]{700, 730});

            List<int[]> merged = slotService.mergeIntervals(intervals);
            assertEquals(2, merged.size(), "Merged intervals should contain 2 blocks");
            assertArrayEquals(new int[]{540, 660}, merged.get(0), "First block should stay [540, 660]");
            assertArrayEquals(new int[]{700, 730}, merged.get(1), "Second block should be [700, 730]");

            dataStore.getBookedSlots().clear();
            dataStore.getBookedSlots().addAll(intervals);
            assertFalse(slotService.isSlotAvailable(630, 640),
                    "Slot [630, 640] falls inside [540, 660] and must NOT be available");
        });

        // ── Emit JSON ────────────────────────────────────────────────────
        long totalMs = results.stream().mapToLong(r -> r.ms()).sum();
        int passed   = (int) results.stream().filter(r ->  r.passed()).count();
        int failed   = (int) results.stream().filter(r -> !r.passed()).count();

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        for (TestResult r : results) {
            sb.append("  \"").append(r.name()).append("\": {\n");
            sb.append("    \"Status\": \"").append(r.passed() ? "passed" : "failed").append("\",\n");
            sb.append("    \"Execution time\": \"").append(r.ms()).append("ms\"");
            if (!r.passed() && r.error() != null) {
                sb.append(",\n    \"Error\": \"")
                  .append(r.error()
                          .replace("\\", "\\\\")
                          .replace("\"", "\\\"")
                          .replace("\n", " "))
                  .append("\"");
            }
            sb.append("\n  },\n");
        }
        sb.append("  \"Passed\": ").append(passed).append(",\n");
        sb.append("  \"Failed\": ").append(failed).append(",\n");
        sb.append("  \"Total bugs\": ").append(failed).append(",\n");
        sb.append("  \"Total Execution time\": \"").append(totalMs).append("ms\"\n");
        sb.append("}");

        System.out.println(sb);
        System.exit(failed > 0 ? 1 : 0);
    }
}
