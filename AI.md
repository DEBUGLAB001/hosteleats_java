## Description
HostelEats is a Java 17 and Spring Boot 3 campus dining and pre-order platform for students and canteen staff. It provides menu browsing, tray ordering with sliding-window rate limiting, 0/1 knapsack budget combo optimization, pickup slot scheduling, rush-hour analytics, and shortest-path campus navigation via Dijkstra's algorithm.

## Repository Structure
```
hosteleats-java/
├── challenge.json                      Challenge configuration and test commands
├── pom.xml                             Maven project configuration and build dependencies
├── README.md                           Candidate-facing overview and debugging instructions
├── start.sh                            File-watcher script: builds, runs, and reloads service
├── tests/
│   └── run_tests.sh                    Recompiles and executes TestRunner, emitting JSON results
├── src/
│   ├── main/
│   │   ├── java/com/hosteleats/
│   │   │   ├── HostelEatsApplication.java  Spring Boot main application entry point
│   │   │   ├── TestRunner.java             Standalone JSON test runner
│   │   │   ├── config/
│   │   │   │   └── WebConfig.java          CORS mappings and static resource routing
│   │   │   ├── controller/
│   │   │   │   ├── ApiController.java      REST API endpoints for auth, menu, tray, limiter, combos, slots, and routes
│   │   │   │   └── PageController.java     Web controller serving index dashboard view
│   │   │   ├── model/
│   │   │   │   ├── Account.java            User account entity with credentials and role
│   │   │   │   ├── MenuItem.java           Menu item domain model with price and satisfaction
│   │   │   │   ├── Order.java              Order representation with item trays
│   │   │   │   └── SessionPayload.java     Auth session token payload with expiration
│   │   │   └── service/
│   │   │       ├── AnalyticsService.java   Rolling-window rush-hour density calculation
│   │   │       ├── AuthService.java        Authentication tokens, credentials verification, and session TTL management
│   │   │       ├── CampusService.java      Campus graph network and Dijkstra shortest-path walk router
│   │   │       ├── ClockService.java       Controllable virtual clock for deterministic testing
│   │   │       ├── ComboService.java       0/1 knapsack budget combo optimizer
│   │   │       ├── DataStore.java          In-memory store for accounts, menus, orders, and graph
│   │   │       ├── RateLimiterService.java Sliding-window order rate limiter per user identity
│   │   │       └── SlotService.java        Pickup time slot schedule and overlapping interval merger
│   │   └── resources/
│   │       ├── application.properties      Application server port and template configuration
│   │       ├── static/
│   │       │   ├── css/style.css           Custom CSS styles, glassmorphism tokens, and responsive layout
│   │       │   └── js/app.js               Client-side REST API client, tab routing, and reactive UI controllers
│   │       └── templates/
│   │           └── index.html              Glassmorphic campus dashboard and ordering interface
│   └── test/
│       └── java/com/hosteleats/
│           └── HostelEatsChallengeTest.java JUnit 5 behavioral challenge test suite for the 6 bugs
```

## Bugs and Bug Locations

### 1. Session Never Expires (TTL Unit Mismatch)
- **Bug location:** src/main/java/com/hosteleats/service/AuthService.java
- **How to observe it:** Log in with student credentials ("aarav" / "hostel123"), advance the clock past TOKEN_TTL_SECONDS (30 seconds), and call getSession with the bearer token.
- **Failure:** Expiration timestamp is stored in milliseconds (clock.nowMillis() + TOKEN_TTL_SECONDS * 1000.0) but getSession compares it against clock.nowSeconds(), so nowSeconds() > expiresAt is always false and sessions never expire.
- **Expected:** Expiration timestamp should be stored in seconds matching clock.nowSeconds() + TOKEN_TTL_SECONDS so sessions expire after 30 seconds and return null on expired token checks.

### 2. Rate Limiter Traps Users in Lockout Window
- **Bug location:** src/main/java/com/hosteleats/service/RateLimiterService.java
- **How to observe it:** Place 3 orders within the 30-second window, trigger a 4th rejected request, then advance the clock past the expiration of the 1st order.
- **Failure:** Request timestamps are unconditionally appended to history before checking times.size() > MAX_ORDERS, adding timestamps for rejected requests and extending the rolling 30-second lockout window indefinitely.
- **Expected:** Check the order limit before appending timestamps; if times.size() >= MAX_ORDERS, reject the request immediately without adding the timestamp to history.

### 3. Rush Hour Analytics Omits Final Peak Window
- **Bug location:** src/main/java/com/hosteleats/service/AnalyticsService.java
- **How to observe it:** Analyze hourly order density with counts such as [1, 2, 3, 20] using a window size of 2.
- **Failure:** Loop condition uses start < counts.size() - windowSize (strict less-than), stopping before inspecting the final window slice [3, 20] starting at index 2.
- **Expected:** Loop condition should be start <= counts.size() - windowSize so all valid rolling windows including the boundary slice are evaluated.

### 4. Campus Walk Dijkstra Relaxation Error
- **Bug location:** src/main/java/com/hosteleats/service/CampusService.java
- **How to observe it:** Compute the shortest walk from "Start" to "Gate" on a multi-hop graph using findShortestPathInGraph.
- **Failure:** Dijkstra relaxation checks neighborWeight < distances.get(neighbor) instead of currentDist + neighborWeight < distances.get(neighbor), ignoring cumulative path distances and recording only isolated edge weights.
- **Expected:** Check whether cumulative distance currentDist + edgeWeight is strictly less than distances.get(neighbor), updating cumulative distance and predecessor links properly.

### 5. Combo Builder Unbounded Duplicate Items
- **Bug location:** src/main/java/com/hosteleats/service/ComboService.java
- **How to observe it:** Request the best meal combo within a budget where a high-satisfaction item fits multiple times (e.g., budget 60 with a 30-rupee item).
- **Failure:** The DP budget loop iterates forward from itemPrice up to maxBudget, implementing an unbounded knapsack that allows the same menu item to be selected multiple times.
- **Expected:** Iterate the budget backwards from maxBudget down to itemPrice to enforce strict 0/1 knapsack constraints where each distinct menu item is selected at most once.

### 6. Overlapping Pickup Slot Shrinkage
- **Bug location:** src/main/java/com/hosteleats/service/SlotService.java
- **How to observe it:** Merge overlapping pickup intervals where a longer slot is followed by a shorter slot that finishes earlier (e.g., [540, 660] and [570, 600]).
- **Failure:** The code sets current.setEnd(next.getEnd()) unconditionally instead of keeping the furthest end boundary, shrinking interval [540, 660] down to [540, 600].
- **Expected:** Update interval end to Math.max(current.getEnd(), next.getEnd()) so merged slots always extend to the furthest endpoint.

## Expected Behaviour After Fixing All Bugs

- Sessions expire strictly after 30 seconds of inactivity, rejecting expired tokens with null/401 responses.
- Rejected order submissions do not extend the sliding-window lockout or inflate request timestamps past 3 orders.
- Rush hour peak window analytics checks all rolling window slices and accurately detects peaks at the end boundary.
- Campus shortest-path walk router properly accumulates path weights and returns the true shortest walking distances.
- Budget combo optimization produces maximum-satisfaction sets containing strictly distinct, non-duplicate dishes.
- Overlapping pickup intervals merge cleanly to the maximum end boundary without shrinking.
- All automated tests in HostelEatsChallengeTest.java pass with exit code 0 (mvn test).