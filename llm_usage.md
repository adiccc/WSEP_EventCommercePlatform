# llm_usage.md

## Overall Usage Summary

- Approximate percentage of code influenced by LLMs:
- Main areas where LLMs were used:
- Main areas implemented without LLM assistance:

---

## Feature / Component: Acceptance Tests and Test Naming

- Purpose of LLM use:
  - To improve the structure, naming, and wording of acceptance tests according to the course convention.
- Summary of prompt(s):
  - Requests to write acceptance tests for use cases such as deleting an event, updating an event date by a manager, closing a company by an admin, and lottery-related scenarios.
  - Requests to use the format `Given..._When..._Then...`.
  - Questions about how to structure test setup so tests use services rather than direct domain objects.
- Output received (short description):
  - Suggested test names, Given/When/Then descriptions, expected results, and explanations of why acceptance/system tests should interact with the application services.
- Files / components affected:
  - Test classes related to event management, company management, lottery, and service-layer acceptance/system tests.
- Modifications made:
  - The team adapted the suggested test descriptions and names to the actual service methods, repositories, DTOs, and setup used in the project.
- Initial gaps in understanding (if any):
  - The expected test naming convention and the difference between service-based acceptance tests and direct domain-object tests needed clarification.
- Final understanding (brief explanation in your own words):
  - Acceptance and system tests should represent user-level behavior and should call application services. Test names should clearly describe the condition, the action, and the expected result using the `Given..._When..._Then...` format. Each test should focus on one behavior and have its own independent setup.


---


## Feature / Component: Logging and Log Management

- Purpose of LLM use:
  - To understand how project logs should be managed and how `java.util.logging` can be used in the implementation.
- Summary of prompt(s):
  - Questions about the meaning of a log journal, whether terminal output counts as logs, and how to export logs from `java.util.logging` to JSON.
- Output received (short description):
  - Explanation that logs can be printed to the terminal, written to files, and structured for later review. Also explained that terminal output may count as visible logs during development, but persistent log files are preferable for monitoring and debugging.
- Files / components affected:
  - Logging configuration classes or infrastructure logging setup.
  - Service classes that use `Logger`.
- Modifications made:
  - The team can add or adjust logger configuration to write logs to a file and potentially format them as JSON.
- Initial gaps in understanding (if any):
  - The difference between temporary terminal logs and persistent log files was unclear.
- Final understanding (brief explanation in your own words):
  - A log journal is a record of system events and errors. Logs should include useful context and appropriate severity levels, and should avoid sensitive information. Terminal logs help during development, but file-based logs are better for later inspection.

---

## Feature / Component: Service-Based Setup for Tests

- Purpose of LLM use:
  - To adjust test setup so tests use application services instead of manipulating domain objects directly.
- Summary of prompt(s):
  - Existing test setup code was shared, and assistance was requested to update it so tests use services.
  - A screenshot/error was shared for debugging.
- Output received (short description):
  - Guidance on constructing the required repositories, authentication/token services, application services, and setup flow through service calls.
- Files / components affected:
  - Test setup classes.
  - Event/company/user service tests.
- Modifications made:
  - The team adapted setup logic to initialize the minimal required services and repositories for each test.
- Initial gaps in understanding (if any):
  - It was not fully clear how to avoid direct domain-object manipulation while still creating all required preconditions for tests.
- Final understanding (brief explanation in your own words):
  - Tests should create system state through the same service layer used by clients whenever possible. This keeps acceptance tests aligned with real user flows and avoids depending on internal domain implementation details.

---

## Feature / Component: Lottery Draw Scheduling (ScheduledExecutorService)

- Purpose of LLM use:
  - Conceptual understanding of how to schedule and execute delayed tasks asynchronously in Java.
- Summary of prompt(s):
  - Discussed the implementation of the `LotteryService`, specifically how to trigger the `drawLottery` method automatically when the registration window closes.
- Output received (short description):
  - Explanation of using `java.util.concurrent.ScheduledExecutorService` alongside `ChronoUnit.SECONDS.between()` to calculate the exact delay and execute the background task without blocking the main thread.
- Files / components affected:
  - `LotteryService` (specifically the constructor and `scheduleLotteryDraw` method).
- Modifications made:
  - Initialized a `ScheduledExecutorService` thread pool in the service and implemented the time-difference calculation to dynamically schedule the execution of the lottery draw.
- Initial gaps in understanding (if any):
  - How to automatically trigger an event in the future (like closing a lottery) within the service layer without relying on external triggers or blocking the main application flow.
- Final understanding (brief explanation in your own words):
  - `ScheduledExecutorService` provides a reliable, in-memory mechanism to run background tasks after a specific delay. By calculating the time difference between `LocalDateTime.now()` and the target time, the system can autonomously execute domain logic exactly when needed.

---

## Feature / Component: Retry Mechanism Design Pattern (RetryHelper)

- Purpose of LLM use:
  - Conceptual understanding of software design patterns to handle transient failures cleanly, specifically comparing inline `for` loops versus a Higher-Order Function wrapper.
- Summary of prompt(s):
  - Asked for a simpler way to implement a retry mechanism in the Service layer, suggesting recursion or a `finally` block to recall the function with the same parameters on failure.
- Output received (short description):
  - Explanation of why direct recursion or `finally` blocks are dangerous (risk of `StackOverflow`) and why duplicating `for` loops clutters business logic. Suggested a "Retry Wrapper" pattern using Java's `Callable` interface to encapsulate the technical retry logic.
- Files / components affected:
  - `RetryHelper` (Application logic) and `LotteryService` (or any service that requires concurrent execution safety).
- Modifications made:
  - Created a generic `RetryHelper.executeWithRetry(Callable action)` utility. Refactored the Service methods to wrap their core business logic inside this helper, removing technical loop constraints from the business flow.
- Initial gaps in understanding (if any):
  - How to apply the retry mechanism universally across multiple service methods.
- Final understanding (brief explanation in your own words):
  - Using a Higher-Order Function (a wrapper that accepts a function as an argument) separates concerns effectively. The `RetryHelper` manages the technical complexities (`OptimisticLockingFailureException`), while the Service methods remain clean, focused purely on domain rules and orchestration.

---

## Feature / Component: Venue Map Architecture and Ticket Management

- Purpose of LLM use:
  - Design assistance and discussion of architectural trade-offs regarding entity relationships (Map, Zone, Ticket) and memory management.
- Summary of prompt(s):
  - Asked about the best Object-Oriented way to model the venue map. Discussed whether a dedicated `Zone` class is needed, the differences between seating and standing areas, and who should be responsible for holding the tickets (the Event, the Map, or the Zone).
  - Asked about the performance implications of pre-generating all tickets versus creating them dynamically.
- Output received (short description):
  - Discussed Domain-Driven Design (DDD) principles. Suggested a hierarchy where an `EventMap` aggregates `Zone` objects, with inheritance for specific types like `SeatingZone` and `StandingZone`.
  - Highlighted the trade-off between pre-allocating thousands of `Ticket` objects in memory (heavy and inefficient) versus tracking `capacity` at the `Zone` level and generating `Ticket` entities only upon a successful purchase.
- Files / components affected:
  - Domain layer classes related to mapping: `EventMap`, `Zone`, `SeatingZone`, `StandingZone`, `Ticket`.
- Modifications made:
  - Implemented a polymorphic `Zone` hierarchy. Decided that `Zones` will manage their own capacity and occupancy rules, and `Tickets` will be generated during the Zone flow, pre-initialized in a list.
- Initial gaps in understanding (if any):
  - How to effectively translate real-world stadium structures into software models without violating the Single Responsibility Principle, and how to manage the memory footprint of large-scale events (e.g., 50,000 attendees).
- Final understanding (brief explanation in your own words):
  - A good domain model delegates responsibilities appropriately: The `Event` manages the overall lifecycle, the `EventMap` orchestrates the layout, and individual `Zones` enforce their specific capacity and seating rules. Furthermore, from a performance perspective, tracking numerical capacity is far more scalable than instantiating thousands of idle `Ticket` objects in memory before they are even sold.

---

## Feature / Component: Venue Map Architecture and Ticket Management

- Purpose of LLM use:
  - Design assistance and discussion of architectural trade-offs regarding entity relationships (Map, Zone, Ticket) and memory management.
- Summary of prompt(s):
  - Asked about the best Object-Oriented way to model the venue map. Discussed whether a dedicated `Zone` class is needed, the differences between seating and standing areas, and who should be responsible for holding the tickets (the Event, the Map, or the Zone).
  - Asked about the performance implications of pre-generating all tickets versus creating them dynamically.
- Output received (short description):
  - Discussed Domain-Driven Design (DDD) principles. Suggested a hierarchy where an `EventMap` aggregates `Zone` objects, with inheritance for specific types like `SeatingZone` and `StandingZone`.
  - Highlighted the trade-off between pre-allocating thousands of `Ticket` objects in memory (heavy and inefficient) versus tracking `capacity` at the `Zone` level and generating `Ticket` entities only upon a successful purchase.
- Files / components affected:
  - Domain layer classes related to mapping: `EventMap`, `Zone`, `SeatingZone`, `StandingZone`, `Ticket`.
- Modifications made:
  - Implemented a polymorphic `Zone` hierarchy. Decided that `Zones` will manage their own capacity and occupancy rules, and `Tickets` will be generated during the Zone flow, pre-initialized in a list.
- Initial gaps in understanding (if any):
  - How to effectively translate real-world stadium structures into software models without violating the Single Responsibility Principle, and how to manage the memory footprint of large-scale events (e.g., 50,000 attendees).
- Final understanding (brief explanation in your own words):
  - A good domain model delegates responsibilities appropriately: The `Event` manages the overall lifecycle, the `EventMap` orchestrates the layout, and individual `Zones` enforce their specific capacity and seating rules. Furthermore, from a performance perspective, tracking numerical capacity is far more scalable than instantiating thousands of idle `Ticket` objects in memory before they are even sold.

---

## Feature / Component: Multi-Zone Ticket Selection Architecture

- Purpose of LLM use:
  - Design assistance in evaluating the trade-offs between single-zone and multi-zone ticket selection within a single order.
- Summary of prompt(s):
  - Discussed whether to allow users to select tickets from multiple zones (e.g., seating and standing) in one transaction and how to manage the data retrieval and validation for such a flow.
- Output received (short description):
 - Analysis of how multi-zone selection improves user experience by avoiding multiple checkout processes, while requiring a more robust coordination between different zone types.
- Files / components affected:
  - ActiveOrderService, Event, EventMap, StandingZone, SeatingZone.
- Modifications made:
  - Implemented logic in ActiveOrderService to aggregate selections from different Zone implementations within a single EventMap context.
- Initial gaps in understanding (if any):
  - How to structure the EventMap to efficiently delegate availability checks to both SeatingZone and StandingZone simultaneously.
- Final understanding (brief explanation in your own words):
  - Supporting multi-zone selection requires the EventMap to act as an orchestrator. By allowing a single ActiveOrder to reference multiple zones, we reduce friction for the user, even though it requires more careful validation to ensure capacity is correctly deducted across different zone types.
  
---

## Feature / Component: Active Order Lifecycle and TTL Management
- Purpose of LLM use:
  - Design assistance and validation of timeout behavior for active orders, including cleanup timing, and lifecycle transitions between reservation stages.
- Summary of prompt(s):
  - Asked how to model the lifecycle of an ActiveOrder while supporting temporary reservation, checkout initiation, and automatic expiration.
  - Discussed how to distinguish between the several TTL.
- Output received (short description):
  - Helped separate the lifecycle into explicit stages of active order and define expiration rules per stage.
  - Clarified that timeout logic should be derived from timestamps (createdAt, checkoutStartedAt) rather than mutable counters.
- Files / components affected:
  - ActiveOrder, ActiveOrderRepository,Cleanup / expiration handling logic, Concurrency tests for TTL behavior
- Modifications made:
  - Using the current stage as stage-aware TTL handling to ActiveOrder.
  - Introduced separate expiration logic for viewing map time vs. selecting tickets time.
  - Updated and add tests validating stage transitions and expiration under the new TTL model.
- Initial gaps in understanding (if any):
  - Whether expiration should be enforced only by scheduled cleanup or also immediately when an order is accessed, and how to avoid inconsistent behavior between the two mechanisms.
- Final understanding (brief explanation in your own words):
  - Using the object’s current status as the source of truth makes it possible to derive different behaviors and properties directly from its lifecycle stage. In the case of ActiveOrder, the current status determines which timestamp is relevant, how expiration is calculated, and what the effective end time should be.

---

## Feature / Component: Event Search Consistency Under Concurrent Updates
- Purpose of LLM use:
  - Validate search behavior under concurrent modifications and design reliable test coverage for event visibility during simultaneous reads and writes.
- Summary of prompt(s):
  - Asked how to implement and test searchCompanyEvents while events may be updated or deleted concurrently.
  - Discussed expected behavior when a search occurs at the same time as a deletion or modification.
  - Asked how to structure concurrency tests so search remains consistent without over constraining valid outcomes.
- Output received (short description):
  - Suggested treating concurrent search outcomes as eventually consistent: an event being modified concurrently may be visible or absent, but should never appear in an invalid or corrupted state.
  - Helped define acceptable test assertions for concurrent search (e.g. event is either returned correctly or omitted entirely).
  - Clarified how retry-aware search logic and repository-level synchronization can preserve consistency without forcing strict serialization.
- Files / components affected:
  EventService, EventServiceTest, EventRepository, Concurrency tests for search behavior under concurrent modifications.
- Modifications made:
  - Added concurrency tests validating that search results remain consistent during simultaneous reads and writes.
  - Adjusted expectations so tests validate correctness under concurrency rather than assuming deterministic ordering.
- Initial gaps in understanding (if any):
  - How to define correct validate behavior for search under concurrent writes, especially when I don't know which result I will get
- Final understanding (brief explanation in your own words):
  - In concurrent systems, consistency does not always mean identical results across runs. A correct concurrent search guarantees valid states: results may differ depending on timing, but they must never be partially corrupted, stale in impossible ways, or internally inconsistent.

---
