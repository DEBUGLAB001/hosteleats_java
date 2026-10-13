# HostelEats - AI Context & Implementation Guide

---

HostelEats is a Java 17 and Spring Boot 3 campus dining and pre-order application for students and canteen staff. It runs as an interactive web service with a glassmorphic browser dashboard, seeded accounts, in-memory catalogs, dynamic tray management, rate-limited order placement, 0/1 knapsack budget combo optimization, guaranteed pickup slot scheduling and interval merging, rolling rush-hour crowd analytics, and shortest-path campus walk navigation via Dijkstra''s algorithm.

---

## 1. Repository Structure

```
hosteleats-java-challenge/
├── challenge.json                      Test suite, runtime dependencies, and challenge definition
├── pom.xml                             Maven build dependencies, plugins, and Java 17 configuration
├── start.sh                            File-watcher loop: builds, launches, and hot-reloads on .java changes
├── run.bat                             Quick startup launcher script for local execution
├── tests/
│   └── run_tests.sh                    Recompiles and runs the Java TestRunner, emitting structured JSON
├── src/
│   ├── main/
│   │   ├── java/com/hosteleats/
│   │   │   ├── HostelEatsApplication.java        Spring Boot main entry point
│   │   │   ├── TestRunner.java                   Standalone JSON test runner (no JUnit / Spring needed)
│   │   │   ├── config/WebConfig.java             CORS mappings and static resource routing
│   │   │   ├── controller/ApiController.java     REST API endpoints for auth, menu, tray orders,
│   │   │   │                                     limiter, combos, slots, and navigation
│   │   │   ├── model/                            Domain objects (Account, MenuItem, Order,
│   │   │   │                                     SessionPayload, SlotInterval)
│   │   │   └── service/
│   │   │       ├── AuthService.java              Authentication tokens, credentials verification,
│   │   │       │                                 and session TTL management
│   │   │       ├── RateLimiterService.java       Sliding-window order rate limiter per user identity
│   │   │       ├── AnalyticsService.java         Rolling-window rush-hour density calculation
│   │   │       ├── CampusService.java            Graph network and Dijkstra shortest-path walk router
│   │   │       ├── ComboService.java             0/1 knapsack budget combo optimizer
│   │   │       ├── SlotService.java              Pickup time slot schedule and interval merger
│   │   │       ├── DataStore.java                In-memory store for accounts, menus, orders, graph
│   │   │       └── ClockService.java             Controllable virtual clock for deterministic testing
│   │   └── resources/
│   │       ├── application.properties            Server binds to 0.0.0.0:3000
│   │       ├── templates/index.html              Glassmorphic campus dashboard and ordering interface
│   │       └── static/
│   │           ├── css/style.css                 Custom CSS, glassmorphism tokens, responsive layout
│   │           └── js/app.js                     Client-side REST client, tab routing, reactive UI
│   └── test/
│       └── java/com/hosteleats/
│           └── HostelEatsChallengeTest.java      JUnit 5 behavioral challenge test suite (6 bugs)
└── README.md                                     Candidate-facing overview and debugging guide
```

---

## 2. Bugs & Bug Locations

These are the six behavioral bug surfaces covered by the challenge. The named locations identify the owning implementation areas for debugging and review.

---

**Bug 1: Session Never Expires (TTL Unit Mismatch)**
- **Location**: `src/main/java/com/hosteleats/service/AuthService.java` — `login` and `getSession`
- **How to observe**: Log in with student credentials (`aarav` / `hostel123`) and advance the clock past `TOKEN_TTL_SECONDS` (30 seconds). Call `getSession` with the bearer token.
- **Failure**: `expires_at` is stored in milliseconds (`clock.nowMillis() + TOKEN_TTL_SECONDS * 1000.0`), but `getSession` compares it against `clock.nowSeconds()`. Since `nowSeconds()` (~1.7e9) is orders of magnitude smaller than `expires_at` in milliseconds (~1.7e12), `nowSeconds() > expiresAt` is always `false`.
- **Expected**: Store expiration in seconds — `clock.nowSeconds() + TOKEN_TTL_SECONDS` — so sessions expire accurately after 30 seconds.

---

**Bug 2: Rate Limiter Traps Users in Lockout Window**
- **Location**: `src/main/java/com/hosteleats/service/RateLimiterService.java` — `allowOrder`
- **How to observe**: Place 3 orders within the 30-second window, trigger a 4th rejected request, then advance the clock past the expiration of the 1st order.
- **Failure**: Every request timestamp is unconditionally appended to history *before* checking `times.size() > MAX_ORDERS`. Rejected requests add timestamps, extending the rolling 30-second lockout window indefinitely.
- **Expected**: Check the order limit *before* appending. If `times.size() >= MAX_ORDERS`, reject immediately without recording the timestamp.

---

**Bug 3: Rush Hour Analytics Omits Final Peak Window**
- **Location**: `src/main/java/com/hosteleats/service/AnalyticsService.java` — `calculatePeakWindow`
- **How to observe**: Analyze hourly order density with counts like `[1, 2, 3, 20]` using window size `2`.
- **Failure**: Loop condition uses `start < counts.size() - windowSize` (strict less-than), stopping before the final window `[3, 20]` at index 2.
- **Expected**: Change to `start <= counts.size() - windowSize` so all valid rolling windows including the boundary slice are evaluated.

---

**Bug 4: Campus Walk Dijkstra Relaxation Error**
- **Location**: `src/main/java/com/hosteleats/service/CampusService.java` — `findShortestPath`
- **How to observe**: Compute the shortest walk from `"Start"` to `"Gate"` on a multi-hop graph.
- **Failure**: Relaxation checks `neighborWeight < distances.get(neighbor)` instead of `currentDist + neighborWeight < distances.get(neighbor)` — cumulative path distances are ignored, only isolated edge weights are recorded.
- **Expected**: Evaluate `currentDist + edgeWeight < distances.get(neighbor)` and update cumulative distance and predecessor links correctly.

---

**Bug 5: Combo Builder Unbounded Duplicate Items**
- **Location**: `src/main/java/com/hosteleats/service/ComboService.java` — `buildBestCombo`
- **How to observe**: Request the best meal combo within a budget where a high-satisfaction item fits multiple times (e.g. budget 60 with a 30-rupee item).
- **Failure**: The DP budget loop iterates *forward* from `itemPrice` to `maxBudget`, implementing an Unbounded Knapsack that allows the same item to be selected multiple times.
- **Expected**: Iterate *backwards* from `maxBudget` down to `itemPrice` to enforce strict 0/1 knapsack — each distinct menu item selected at most once.

---

**Bug 6: Overlapping Pickup Slot Shrinkage**
- **Location**: `src/main/java/com/hosteleats/service/SlotService.java` — `mergeSlots`
- **How to observe**: Merge overlapping intervals where a longer slot is followed by a shorter one (e.g. `[540, 660]` then `[570, 600]`).
- **Failure**: `current.setEnd(next.getEnd())` is called unconditionally, shrinking `[540, 660]` down to `[540, 600]`.
- **Expected**: Use `Math.max(current.getEnd(), next.getEnd())` so merged slots always extend to the furthest endpoint.

---

## 3. Expected Behaviour After Fixing All Bugs

- Sessions expire strictly after 30 seconds, rejecting expired tokens with `null` / `401` responses.
- Rejected order submissions do not extend the sliding-window lockout or inflate request timestamps.
- Rush hour peak window analytics checks all rolling window slices, accurately detecting peaks at the end boundary.
- Campus shortest-path walk router properly accumulates path weights and returns true shortest walking distances.
- Budget combo optimization produces maximum-satisfaction sets with strictly distinct, non-duplicate dishes.
- Overlapping pickup intervals merge cleanly to the maximum end boundary without shrinking.
- All automated tests in `HostelEatsChallengeTest.java` pass with exit code `0` (`mvn test`).