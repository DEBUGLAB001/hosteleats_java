HostelEats
Description
HostelEats is a Java 17 and Spring Boot 3 campus dining and pre-order application for students and canteen staff. It runs as an interactive web service with a glassmorphic browser dashboard, seeded accounts, in-memory catalogs, dynamic tray management, rate-limited order placement, 0/1 knapsack budget combo optimization, guaranteed pickup slot scheduling and interval merging, rolling rush-hour crowd analytics, and shortest-path campus walk navigation via Dijkstra's algorithm.

Repository Structure
hosteleats-java-challenge/
├── challenge.json                    Test suite, runtime dependencies, and challenge definition
├── pom.xml                           Maven build dependencies, plugins, and Java 17 configuration
├── run.bat                           Quick startup launcher script for local execution
├── src/
│   ├── main/
│   │   ├── java/com/hosteleats/
│   │   │   ├── HostelEatsApplication.java
│   │   │   │                         Spring Boot main entry point
│   │   │   ├── config/WebConfig.java CORS mappings and static resource routing
│   │   │   ├── controller/ApiController.java
│   │   │   │                         REST API endpoints for auth, menu, tray orders, limiter, combos, slots, and navigation
│   │   │   ├── model/                Domain objects (Account, MenuItem, Order, SessionPayload, SlotInterval)
│   │   │   └── service/
│   │   │       ├── AuthService.java  Authentication tokens, credentials verification, and session TTL management
│   │   │       ├── RateLimiterService.java
│   │   │       │                     Sliding-window order rate limiter per user identity
│   │   │       ├── AnalyticsService.java
│   │   │       │                     Rolling-window rush-hour density calculation
│   │   │       ├── CampusService.java
│   │   │       │                     Graph network representation and Dijkstra shortest-path walk router
│   │   │       ├── ComboService.java 0/1 knapsack budget combo optimizer
│   │   │       ├── SlotService.java  Pickup time slot schedule and overlapping interval merger
│   │   │       ├── DataStore.java    In-memory data store for accounts, menu items, orders, and graph nodes
│   │   │       └── ClockService.java Controllable virtual clock for deterministic time-based testing
│   │   └── resources/
│   │       ├── application.properties
│   │       │                         Application server port and template configuration
│   │       ├── templates/index.html   Glassmorphic campus dashboard and ordering interface
│   │       └── static/
│   │           ├── css/style.css     Custom CSS styles, glassmorphism tokens, and responsive layout
│   │           └── js/app.js         Client-side REST API client, tab routing, and reactive UI controllers
│   └── test/
│       └── java/com/hosteleats/
│           └── HostelEatsChallengeTest.java
│                                     JUnit 5 behavioral challenge test suite for the 6 bugs
└── README.md                          Candidate-facing application overview and debugging guide

Bugs and Bug Locations
These are the six behavioral bug surfaces covered by the challenge. The named locations identify the owning implementation areas for debugging and review.

1. Session Never Expires (TTL Unit Mismatch)
Bug location: src/main/java/com/hosteleats/service/AuthService.java, login and getSession
How to observe it: Log in with student credentials ("aarav" / "hostel123") and advance the clock past TOKEN_TTL_SECONDS (30 seconds). Call getSession with the bearer token.
Failure: When creating the session payload, expires_at is stored in milliseconds (clock.nowMillis() + TOKEN_TTL_SECONDS * 1000.0), but getSession compares it against clock.nowSeconds(). Since nowSeconds() (~1.7e9) is orders of magnitude smaller than expires_at in milliseconds (~1.7e12), nowSeconds() > expiresAt remains false indefinitely.
Expected: Store expiration in seconds matching clock.nowSeconds() + TOKEN_TTL_SECONDS so sessions accurately expire after 30 seconds and return null on expired token checks.

2. Rate Limiter Traps Users in Lockout Window
Bug location: src/main/java/com/hosteleats/service/RateLimiterService.java, allowOrder
How to observe it: Place 3 orders within the 30-second window, trigger a 4th rejected request, then advance the clock past the expiration of the 1st order.
Failure: The timestamp of every request is unconditionally appended to the history list before checking times.size() > MAX_ORDERS. Rejected requests add timestamps to the user's history, extending the rolling 30-second lockout window indefinitely on repeated attempts.
Expected: Check the order limit before appending timestamps. If times.size() >= MAX_ORDERS, reject the request immediately without adding the timestamp to history.

3. Rush Hour Analytics Omits Final Peak Window
Bug location: src/main/java/com/hosteleats/service/AnalyticsService.java, calculatePeakWindow
How to observe it: Analyze hourly order density with counts such as [1, 2, 3, 20] using a window size of 2.
Failure: The loop condition uses start < counts.size() - windowSize (strict less-than), stopping before inspecting the final window slice [3, 20] starting at index 2.
Expected: The loop condition should be start <= counts.size() - windowSize (or start < counts.size() - windowSize + 1), evaluating all valid rolling windows including the boundary slice.

4. Campus Walk Dijkstra Relaxation Error
Bug location: src/main/java/com/hosteleats/service/CampusService.java, findShortestPath
How to observe it: Compute the shortest walk from "Start" to "Gate" on a multi-hop graph.
Failure: In the Dijkstra relaxation step, the condition checks if neighborWeight < distances.get(neighbor) rather than currentDist + neighborWeight < distances.get(neighbor). This ignores cumulative path distances and records only isolated edge weights.
Expected: Check if cumulative distance (currentDist + edgeWeight) is strictly less than distances.get(neighbor), updating cumulative distance and predecessor links properly.

5. Combo Builder Unbounded Duplicate Items
Bug location: src/main/java/com/hosteleats/service/ComboService.java, buildBestCombo
How to observe it: Request the best meal combo within a budget where a high-satisfaction item can fit multiple times (e.g. Budget 60 with a 30-rupee item).
Failure: The dynamic programming budget loop iterates forward from itemPrice up to maxBudget, implementing an Unbounded Knapsack that allows the same menu item to be selected multiple times.
Expected: Iterate the budget backwards from maxBudget down to itemPrice (or maintain a 2D DP matrix) to enforce strict 0/1 knapsack constraints where each distinct menu item is selected at most once.

6. Overlapping Pickup Slot Shrinkage
Bug location: src/main/java/com/hosteleats/service/SlotService.java, mergeSlots
How to observe it: Merge overlapping pickup intervals where a longer slot is followed by a shorter slot that finishes earlier (e.g. [540, 660] and [570, 600]).
Failure: When merging adjacent overlapping slots, the code sets current.setEnd(next.getEnd()) unconditionally instead of keeping the furthest end boundary. This shrinks interval [540, 660] down to [540, 600].
Expected: Update interval end to Math.max(current.getEnd(), next.getEnd()) so merged slots always extend to the furthest endpoint.

Expected Behaviour After Fixing All Bugs
Sessions expire strictly after 30 seconds of inactivity, rejecting expired tokens with null/401 responses.
Rejected order submissions do not extend the sliding-window lockout or inflate request timestamps past 3 orders.
Rush hour peak window analytics checks all rolling window slices and accurately detects peaks at the end boundary.
Campus shortest-path walk router properly accumulates path weights and returns the true shortest walking distances.
Budget combo optimization produces maximum-satisfaction sets containing strictly distinct, non-duplicate dishes.
Overlapping pickup intervals merge cleanly to the maximum end boundary without shrinking.
All automated tests in src/test/java/com/hosteleats/HostelEatsChallengeTest.java pass with exit code 0 (mvn test).
