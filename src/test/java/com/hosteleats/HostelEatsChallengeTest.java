package com.hosteleats;

import com.hosteleats.model.MenuItem;
import com.hosteleats.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Challenge Test Suite for HostelEats.
 * Tests 1 to 6 verify the correct, fixed behavior.
 * In the initial challenge buggy state, all 6 tests are expected to FAIL.
 * When the 6 bugs are fixed, all 6 tests will PASS.
 */
public class HostelEatsChallengeTest {

    private ClockService clock;
    private DataStore dataStore;
    private AuthService authService;
    private RateLimiterService rateLimiterService;
    private AnalyticsService analyticsService;
    private CampusService campusService;
    private ComboService comboService;
    private SlotService slotService;

    @BeforeEach
    public void setUp() {
        clock = new ClockService();
        dataStore = new DataStore();
        authService = new AuthService(clock, dataStore);
        rateLimiterService = new RateLimiterService(clock);
        analyticsService = new AnalyticsService(dataStore);
        campusService = new CampusService();
        comboService = new ComboService(dataStore);
        slotService = new SlotService(dataStore);

        dataStore.resetData();
        authService.clearSessions();
        rateLimiterService.clear();
        clock.reset();
    }

    @AfterEach
    public void tearDown() {
        dataStore.resetData();
        authService.clearSessions();
        rateLimiterService.clear();
        clock.reset();
    }

    /**
     * Bug 1: Session Expiration
     * sets clock, logs in, advances clock by TOKEN_TTL_SECONDS + 1, verifies session expires.
     */
    @Test
    public void testSessionExpiresAtConfiguredTime() {
        clock.setTime(1000.0);
        String token = authService.login("aarav", "hostel123");
        assertNotNull(token, "Login should succeed for valid credentials");

        // Immediately valid
        assertNotNull(authService.getSession(token), "Session should be valid immediately after login");

        // Advance clock by TOKEN_TTL_SECONDS + 1 (31 seconds)
        clock.advanceSeconds(AuthService.TOKEN_TTL_SECONDS + 1);

        // Should expire and return null
        assertNull(authService.getSession(token), "Session must expire after TOKEN_TTL_SECONDS + 1");
    }

    /**
     * Bug 2: Rate Limiter Cooldown Lockout
     * places 3 orders at t=100, 110, 120. Rejection at t=121. Advances time to t=131. Verifies order is accepted.
     */
    @Test
    public void testRejectedRequestDoesNotExtendLimitWindow() {
        String userId = "student_test";

        // Place 3 allowed orders at t=100, t=110, t=120
        clock.setTime(100.0);
        assertTrue(rateLimiterService.allowOrder(userId), "Order 1 should be allowed at t=100");

        clock.setTime(110.0);
        assertTrue(rateLimiterService.allowOrder(userId), "Order 2 should be allowed at t=110");

        clock.setTime(120.0);
        assertTrue(rateLimiterService.allowOrder(userId), "Order 3 should be allowed at t=120");

        // 4th order at t=121 is rejected (3 orders already in window [121 - 30 = 91, 121])
        clock.setTime(121.0);
        assertFalse(rateLimiterService.allowOrder(userId), "Order 4 at t=121 should be rejected (limit 3)");

        // Advance to t=131 (t=100 falls outside 30s window [131 - 30 = 101, 131])
        // If bug is present, the rejected order at t=121 was added to the history,
        // so window [101, 131] still has 3 timestamps (110, 120, 121), falsely rejecting this request!
        clock.setTime(131.0);
        assertTrue(rateLimiterService.allowOrder(userId), "Order at t=131 should be allowed because t=100 expired and rejected order at t=121 shouldn't count");
    }

    /**
     * Bug 3: Rush Hour Peak Window Boundary
     * checks [12, 10, 2, 1, 1] with window 2 -> total 22, start 0;
     * and checks [1, 2, 3, 20] with window 2 -> total 23, start 2.
     */
    @Test
    public void testPeakWindowIncludesFinalValidStart() {
        List<Integer> list1 = List.of(12, 10, 2, 1, 1);
        Map<String, Object> result1 = analyticsService.calculatePeakWindow(list1, 2);
        assertEquals(22, result1.get("total_orders"), "Total orders for first test should be 22");
        assertEquals(0, result1.get("start_index"), "Start index for first test should be 0");

        List<Integer> list2 = List.of(1, 2, 3, 20);
        Map<String, Object> result2 = analyticsService.calculatePeakWindow(list2, 2);
        // The peak window is [3, 20] which starts at index 2 (final valid window)
        // With bug (start < counts.size() - windowSize), index 2 is never evaluated!
        assertEquals(23, result2.get("total_orders"), "Total orders for list ending with peak should be 23");
        assertEquals(2, result2.get("start_index"), "Start index must evaluate the final valid window (index 2)");
    }

    /**
     * Bug 4: Campus Walk Dijkstra Relaxation
     * verifies graph with multi-hop cumulative distance (Start -> Oak -> Gate: 8 mins, path ["Start", "Oak", "Gate"]).
     */
    @Test
    public void testRouteDistanceIncludesFullPath() {
        // Create custom graph:
        // Start -> Oak (3)
        // Oak -> Gate (5)  (total Start->Gate = 8)
        // Start -> Elm (7)
        // Elm -> Gate (6)  (total Start->Gate = 13, but individual edge Elm->Gate weight is 6, while Oak->Gate is 5)
        Map<String, Map<String, Integer>> testGraph = new HashMap<>();
        testGraph.put("Start", new HashMap<>(Map.of("Oak", 3, "Elm", 7)));
        testGraph.put("Oak", new HashMap<>(Map.of("Start", 3, "Gate", 5)));
        testGraph.put("Elm", new HashMap<>(Map.of("Start", 7, "Gate", 6)));
        testGraph.put("Gate", new HashMap<>(Map.of("Oak", 5, "Elm", 6)));

        Map<String, Object> route = campusService.findShortestPathInGraph(testGraph, "Start", "Gate");
        assertEquals(8, route.get("minutes"), "Cumulative shortest path distance from Start to Gate must be 8");
        assertEquals(List.of("Start", "Oak", "Gate"), route.get("path"), "Path must be [Start, Oak, Gate]");
    }

    /**
     * Bug 5: Combo Builder Distinct Items (0/1 Knapsack)
     * verifies budget 60 returns distinct items with total score 9 and no duplicate item IDs.
     */
    @Test
    public void testComboUsesEachMenuItemOnce() {
        // Items available:
        // samosa: price 20, satisfaction 4
        // lemon-rice: price 45, satisfaction 7
        // masala-chai: price 15, satisfaction 3
        // fruit-cup: price 30, satisfaction 5
        // Under budget 60:
        // In unbounded knapsack: 3 x samosa (cost 60, satisfaction 12) or 4 x chai (cost 60, satisfaction 12)
        // In 0/1 knapsack (unique items):
        // (lemon-rice 45 + chai 15) = cost 60, satisfaction 7 + 3 = 10
        // Or if only samosa, fruit-cup, etc.
        List<MenuItem> testItems = List.of(
                new MenuItem("samosa", "Punjabi Samosa", "Snacks", 20, 4, "🥟", true),
                new MenuItem("fruit-cup", "Seasonal Fruit Cup", "Fresh", 30, 5, "🍉", false),
                new MenuItem("lemon-rice", "Lemon Rice Bowl", "Meals", 45, 7, "🍚", false),
                new MenuItem("masala-chai", "Masala Chai", "Drinks", 15, 2, "☕", false)
        );

        // Budget 60:
        // Combos possible:
        // samosa (20, 4) + fruit-cup (30, 5) -> cost 50, satisfaction 9
        // lemon-rice (45, 7) + masala-chai (15, 2) -> cost 60, satisfaction 9
        // Under unbounded knapsack, samosa x 3 -> cost 60, satisfaction 12 (duplicates!)
        Map<String, Object> result = comboService.recommendComboForItems(testItems, 60);

        @SuppressWarnings("unchecked")
        List<MenuItem> selected = (List<MenuItem>) result.get("items");

        // Verify total satisfaction is 9 (not 12 from 3x samosas)
        assertEquals(9, result.get("total_satisfaction"), "Satisfaction should be 9 under 0/1 knapsack constraint");

        // Verify all item IDs are distinct (no duplicates)
        Set<String> itemIds = new HashSet<>();
        for (MenuItem item : selected) {
            assertTrue(itemIds.add(item.getId()), "Item " + item.getId() + " appears more than once! Each menu item must be used at most once.");
        }
    }

    /**
     * Bug 6: Overlapping Slots Merging
     * intervals [[540, 660], [570, 600], [700, 730]] merge to [[540, 660], [700, 730]],
     * and slot (630, 640) is not available.
     */
    @Test
    public void testOverlappingSlotsMergeToFurthestEnd() {
        List<int[]> intervals = new ArrayList<>();
        intervals.add(new int[]{540, 660});
        intervals.add(new int[]{570, 600}); // ends earlier than 660
        intervals.add(new int[]{700, 730});

        List<int[]> merged = slotService.mergeIntervals(intervals);

        assertEquals(2, merged.size(), "Merged intervals should contain 2 blocks");
        assertArrayEquals(new int[]{540, 660}, merged.get(0), "First block should stay [540, 660]");
        assertArrayEquals(new int[]{700, 730}, merged.get(1), "Second block should be [700, 730]");

        // Put these intervals in dataStore and check if (630, 640) is available
        dataStore.getBookedSlots().clear();
        dataStore.getBookedSlots().addAll(intervals);

        // Slot [630, 640] is inside [540, 660], so it must NOT be available
        assertFalse(slotService.isSlotAvailable(630, 640), "Slot [630, 640] falls inside [540, 660] and must NOT be available");
    }
}
