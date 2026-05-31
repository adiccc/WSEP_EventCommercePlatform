# llm_usage.md

## Overall Usage Summary

- Approximate percentage of code influenced by LLMs:
  - Approximately 20–30% of the codebase was influenced by LLM assistance, mainly through conceptual guidance, test skeletons, boilerplate snippets, and design discussions. The core architecture, final implementation decisions, integration, debugging, and adaptation to the project were performed by the team.

- Main areas where LLMs were used:
  - Understanding Java concurrency utilities and testing tools.
  - Improving acceptance-test naming and structure.
  - Discussing architecture trade-offs for services, DTOs, venue maps, queues, permissions, and checkout flows.
  - Getting help with Java API details and repetitive test boilerplate.
  - Improving documentation wording and organization.

- Main areas implemented without LLM assistance:
  - Final system architecture and domain modeling decisions.
  - Core business logic and project-specific service flows.
  - Integration between repositories, services, DTOs, and domain objects.
  - Debugging and adapting code to the actual project structure.
  - Final test assertions and verification of expected behavior.

Version 1:

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

## Feature / Component: DTO and Filtering Design for Event Search

- Purpose of LLM use:
  - Design assistance for structuring DTOs used in event search and transferring category/location data across layers.
- Summary of prompt(s):
  - Asked how to model event filtering, including category, location, and search criteria.
  - Asked how to represent category and geographical filtering as fields in DTO.
- Output received (short description):
  - Recommended keeping DTOs flat and purpose-specific, with filtering objects encapsulating category, location, and optional search constraints.
  - Clarified the boundary between domain models and DTOs so filtering remains flexible without coupling external requests to internal structures.
- Files / components affected:
  - EventSearchFilter, Category and Geographical area DTOs, Event search flow in EventService.
- Modifications made:
  - Introduced dedicated Data Type for filtering input, Category and Geographical area.
  - Refined service interfaces to accept explicit filter objects instead of overloaded parameter sets.
- Initial gaps in understanding (if any):
  - How much filtering logic should be encoded in DTOs versus domain services.
- Final understanding (brief explanation in your own words):
  - DTOs should model request intent. A good filtering DTO captures what the client wants to search by (category, area, constraints), while the domain remains responsible for interpreting and applying those filters. This keeps boundaries clean and makes the API easier to evolve.

---

## Feature / Component: Virtual Queue — Java Concurrency APIs (AtomicInteger CAS Loop, ConcurrentLinkedQueue, Singleton with volatile)

- Purpose of LLM use:
  - To understand Java concurrency classes (`AtomicInteger`, `ConcurrentLinkedQueue`, `ConcurrentHashMap`) and how to use them correctly, and to speed up writing the boilerplate parts of the implementation and tests.
- Summary of prompt(s):
  - Asked what `AtomicInteger.compareAndSet` does and how a CAS loop works in Java, since I had already decided I needed a lock-free counter but was not familiar with the Java API for it.
  - Asked how `ConcurrentLinkedQueue` and `ConcurrentHashMap` work and what their thread-safety guarantees are.
  - Asked about the correct syntax for the double-checked locking singleton pattern with `volatile` in Java.
  - Asked for help writing the boilerplate parts of the unit tests (setup, repeated test patterns) so I could focus on the logic assertions.
- Output received (short description):
  - Explanation of CAS semantics and a code snippet showing the do-while CAS loop pattern.
  - Explanation of `ConcurrentLinkedQueue` as a non-blocking FIFO structure and `ConcurrentHashMap` as a thread-safe map without full locking.
  - Singleton pattern snippet with `volatile` and double-checked locking.
  - Test boilerplate snippets that I adapted to fit my test class setup.
- Files / components affected:
  - `domain/webQueue/WebQueue.java`, `application/AdmissionCallback.java`, `application/UserService.java`, `DTO/QueueEntryResultDTO.java`, `domain/webQueue/WebQueueTest.java`
- Modifications made:
  - I designed the overall structure: the singleton queue, the capacity limit, the O(1) position formula using `sequenceGenerator` and `admittedFromQueue`, the callback interface for admission notification, and the integration with `UserService.logout()` and `leaveStore()`. The LLM helped me use the correct Java APIs to implement the concurrent parts I had already planned, and sped up writing repetitive test cases.
- Initial gaps in understanding (if any):
  - I was not familiar with the exact Java API for atomic compare-and-set or with the `volatile` keyword requirement for singleton publication.
- Final understanding (brief explanation in your own words):
  - The queue uses a CAS loop on `AtomicInteger` to safely admit users without a lock: only the thread that successfully swaps the count gets the slot, everyone else retries or joins the waiting line. Each waiting user gets a sequence number; subtracting the number of users already promoted from the queue gives their current position in O(1). When a user leaves, one waiting user is promoted and their admission callback is fired.

---

## Feature / Component: Assign Additional Owner — Java List.remove() Integer Ambiguity and Acceptance Test Skeletons

- Purpose of LLM use:
  - To get Java-specific help on collection operations and to speed up writing acceptance tests by getting suggested test skeletons.
- Summary of prompt(s):
  - Asked about the correct Java way to remove a boxed `Integer` from a `List<Integer>` by value rather than by index.
  - Asked for help generating acceptance test skeletons for the owner appointment flow (request, accept, reject, error cases) using the project's `Given..._When..._Then...` naming convention.
  - Asked for help generating the concurrency test boilerplate using `CountDownLatch` and `ExecutorService`.
- Output received (short description):
  - Clarification that `list.remove(Integer.valueOf(id))` removes by value while `list.remove(id)` removes by index, with a short example.
  - Acceptance test skeletons with `@Test` names and `assert` calls that I adapted to match my actual service method signatures and DTOs.
  - Concurrency test boilerplate using `CountDownLatch`, `ExecutorService`, and `Future` that I adapted for the appoint-owner race scenario.
- Files / components affected:
  - `domain/company/Permissions.java`, `domain/company/Company.java`, `application/CompanyService.java`, `domain/user/Owner.java`, `application/CompanyServiceUpdatedTest.java`
- Modifications made:
  - I designed the two-step pending flow, the `pandingOwners` list, the guard conditions, and the rule that accepting removes a prior manager role. The LLM helped me avoid a subtle Java bug with boxed-integer removal and generated test skeletons that I then filled in with correct method calls, DTOs, and assertions from my own implementation.
- Initial gaps in understanding (if any):
  - The `list.remove(int)` vs `list.remove(Integer)` ambiguity in Java was not something I was aware of.
- Final understanding (brief explanation in your own words):
  - The owner appointment is a two-step flow: `requestAppointOwner` adds the nominee to a pending list, and `respondOwnerAppointment` either promotes them to the owner set (accept) or simply removes them from pending (reject). If they were previously a manager, their tree entry is removed at the same time to prevent conflicting roles. The service layer also adds the `Owner` role to the user entity and persists both objects.

---

## Feature / Component: Assign Manager Role — HashMap.remove() Return Value and Test Skeletons

- Purpose of LLM use:
  - To understand Java `HashMap` operations for managing the pending manager entries, and to speed up writing the acceptance test cases.
- Summary of prompt(s):
  - Asked how `HashMap.remove(key)` works and what it returns, since I needed to atomically remove the pending entry and use it to populate the active tree in one step.
  - Asked for help generating acceptance test skeletons for accept, reject, empty permissions, and duplicate-appointment cases.
  - Asked for help generating the concurrency test boilerplate for the "two threads race to appoint the same user" scenario.
- Output received (short description):
  - Explanation that `HashMap.remove(key)` returns the removed value (or `null` if absent), enabling the remove-and-use pattern in one line.
  - Test skeletons with `Given..._When..._Then...` names that I adapted to my actual method signatures and permission sets.
  - Concurrency boilerplate using `CountDownLatch` and `Future` that I adapted for the appoint-manager scenario.
- Files / components affected:
  - `domain/company/Permissions.java`, `domain/company/Hierarchy.java`, `domain/company/Company.java`, `application/CompanyService.java`, `domain/user/Manager.java`, `application/CompanyServiceUpdatedTest.java`
- Modifications made:
  - I designed the structure: a separate `pendingManagers` map, requiring a non-empty permission set at appointment time, and the two-step flow. I also designed how acceptance populates both sides of the bidirectional link in the company tree. The LLM helped me use `HashMap.remove` correctly for the remove-and-promote pattern and generated the test skeletons I then completed.
- Initial gaps in understanding (if any):
  - I was not sure whether `HashMap.remove` returned the old value; knowing that it does made the acceptance logic cleaner.
- Final understanding (brief explanation in your own words):
  - Pending managers are stored in a separate map so the main tree is never partially populated. On acceptance, `pendingManagers.remove(managerId)` returns the `Hierarchy` object (already holding the appointer id and permissions), which is then placed directly into `companyTree` and the appointer's appointee list is updated. The `Manager` role is also added to the user entity to keep it in sync.

---

## Feature / Component: Update Manager Permissions — Layered Architecture Guard Placement and Concurrency Race Outcome

- Purpose of LLM use:
  - To understand where in a layered Java architecture an authorization guard like "only the appointing owner can edit this" should live, and to speed up writing tests.
- Summary of prompt(s):
  - Asked whether a constraint that depends on data inside a domain object should be enforced in the service or in the domain class itself, in the context of Java layered architecture.
  - Asked for help generating acceptance test skeletons for the update-permissions use case, including the "manager appointed by a different owner" case.
  - Asked for help with the concurrency test — specifically, what the correct assertion should be when two valid permission updates race.
- Output received (short description):
  - Explanation that constraints depending on data owned by a domain object belong in that domain object, not in the service layer, so future callers cannot bypass them.
  - Test skeletons with named cases that I adapted to my actual service and repo setup.
  - Explanation that when two updates are both valid, last-write-wins is the correct expected outcome, and the assertion should verify the final state equals one of the two submitted sets.
- Files / components affected:
  - `domain/company/Hierarchy.java`, `domain/company/Permissions.java`, `domain/company/Company.java`, `application/CompanyService.java`, `application/CompanyServiceUpdatedTest.java`
- Modifications made:
  - I decided to place the appointer check inside `Hierarchy.setPermissions` and wrote that guard myself. I also wrote the full `updateManagerPermissions` flow in `CompanyService`. The LLM confirmed the layering decision I was already leaning toward and helped generate the test skeletons, including the concurrency test structure with the "final state equals one of two sets" assertion.
- Initial gaps in understanding (if any):
  - I was unsure whether in a concurrent last-write-wins case one of the two requests should return an error or both should succeed. The LLM clarified that both succeed and the last persisted write wins.
- Final understanding (brief explanation in your own words):
  - The appointer check lives in `Hierarchy` because only that object knows its `myManager`. The service layer handles token validation and owner check, then delegates to the domain. Under concurrency, the optimistic-locking retry serializes the two updates; both are valid, so both succeed, and the final persisted permission set is exactly one of the two submitted sets with no corruption.

---

## Feature / Component: Owner Removes Manager — Java List Iteration Safety During Map Mutation and Test Skeletons

- Purpose of LLM use:
  - To understand Java `List` mutation during iteration (to avoid `ConcurrentModificationException` when updating sub-manager pointers), and to speed up writing the acceptance and concurrency tests.
- Summary of prompt(s):
  - Asked about safe ways to iterate over a list of sub-manager IDs and update each one without triggering `ConcurrentModificationException`, since `removeManagerFromTree` needs to update other entries in the same map while iterating.
  - Asked for help generating acceptance test skeletons for all the guard conditions and the cascade sub-manager reassignment case.
  - Asked for help generating the concurrency test boilerplate for two threads racing to remove the same manager.
- Output received (short description):
  - Explanation that iterating over a snapshot (e.g., iterating over a copy of the list or the `getMyAppointees()` list directly rather than the live map) avoids modification issues, since the sub-manager entries are in the same `companyTree` map that is being modified.
  - Test skeletons covering all guard cases and the cascade case, which I adapted to my actual setup.
  - Concurrency test boilerplate using `CountDownLatch` and `Future` that I adapted for the remove-manager race scenario.
- Files / components affected:
  - `domain/company/Permissions.java`, `domain/company/Company.java`, `application/CompanyService.java`, `domain/user/Member.java`, `application/CompanyServiceUpdatedTest.java`
- Modifications made:
  - I designed the cascade-reassignment behavior, the four guard conditions (only appointing owner can remove, target must be a manager, company must be active, cannot remove the only manager), and the requirement to call `removeManagerRole` on the user entity. The LLM helped me safely iterate and update the sub-manager entries without a modification exception, and generated the test skeletons I then completed with correct service calls and assertions.
- Initial gaps in understanding (if any):
  - I was not sure whether iterating over `removed.getMyAppointees()` while also modifying `companyTree` entries in the same loop would cause issues in Java.
- Final understanding (brief explanation in your own words):
  - `removeManagerFromTree` removes the target node, strips it from its appointer's appointee list, appends the target's sub-managers to the appointer's list, and updates each sub-manager's `myManager` pointer. Iterating over the already-removed node's appointee list (a separate `List`) rather than over the live map avoids any concurrent-modification issue. The service layer also calls `removeManagerRole` on the user entity and persists both the company and the user.


## Feature / Component: Token Roles & Unified User Identifier (Guest vs. Member)

- Purpose of LLM use:
  - Validation and refinement of my architectural design for handling guests versus registered members, specifically focusing on JWT role-based permission checks and how they tie into domain entity associations.
- Summary of prompt(s):
  - First, I consulted the LLM on my idea to use a `ROLE` claim (GUEST vs. MEMBER) inside the token to securely differentiate between user types statelessly, block guests from restricted functions, and route shared endpoints appropriately.
  - Following that, I proposed refactoring the `Order` class to use a `String userIdentifier` (handling both emails for members and UUIDs for guests) so that the stateless token identity could be unified under a single order flow.
- Output received (short description):
  - The LLM agreed with my `ROLE` claim direction, providing guidance on how to extract it statelessly to enforce authorization guards. It then validated my subsequent `userIdentifier` design as a highly scalable approach to complete the flow.
- Files / components affected:
  - `TokenService`, `Auth`, `Order`, `ActiveOrder`, `UserService`, `ActiveOrderServiceTest`, `AdminServiceTest`.
- Modifications made:
  - I implemented the `ROLE` extraction in `TokenService` and `Auth` to add permission guards across the services. Once the roles were safely separated, I implemented my `userIdentifier` (String) design across the domain models to unify the checkout process.
- Initial gaps in understanding (if any):
  - I wanted to brainstorm the cleanest way to enforce token authorization stateless, and subsequently ensure that passing these dual-identities into the domain model wouldn't break the database architecture.
- *inal understanding (brief explanation in your own words):
  - Embedding a `ROLE` claim directly in the JWT is a powerful stateless authorization mechanism to accept/reject requests instantly. Once validated, mapping these actors to a "Unified Identifier" (String) allows the system to smoothly process orders for anyone without duplicating domain logic.

---

## Feature / Component: TokenService and Authentication Separation

- **Purpose of LLM use:**
  - Architectural discussion regarding the Single Responsibility Principle in the context of security and token management.
- **Summary of prompt(s):**
  - Discussed how to properly separate the responsibility of generating tokens (especially with the new Guest flows) from the responsibility of verifying authentication across the system.
- **Output received (short description):**
  - Validated the idea that `TokenService` should strictly handle JWT creation and parsing, while a dedicated `Auth` component should manage identity and permission verification.
- **Files / components affected:**
  - `TokenService`, `Auth`, Application Services.
- **Modifications made:**
  - Ensured that application services depend on the `Auth` interface for permission checks, rather than directly coupling them to the `TokenService`'s generation logic.
- **Initial gaps in understanding (if any):**
  - Clarifying the exact boundaries between token manipulation and domain-level authorization.
- **Final understanding (brief explanation in your own words):**
  - Decoupling token mechanics (`TokenService`) from identity verification (`Auth`) adheres strictly to the Single Responsibility Principle. It ensures that services only interact with the `Auth` layer to check permissions, keeping the security architecture modular and easier to test.

---

## Feature / Component: Password Encryption and Security Integration

- **Purpose of LLM use:**
  - Conceptual understanding of how to securely hash passwords using external libraries, aligning with course materials.
- **Summary of prompt(s):**
  - Discussed integrating an external password hashing library (referenced from the course presentation) into the user registration and authentication flow.
- **Output received (short description):**
  - Guidance on securely injecting the external encryption logic into the application layer, ensuring passwords are never stored in plain text.
- **Files / components affected:**
  - `PasswordEncoderUtil`, `UserService`, `Auth`.
- **Modifications made:**
  - Integrated the external library into the `PasswordEncoderUtil`. Updated the registration flow to hash passwords before storing them in `UserRepo`, and updated the login flow to verify raw passwords against the hashed versions.
- **Initial gaps in understanding (if any):**
  - The exact mechanism of bridging the external security library with our custom authentication service was slightly unclear.
- **Final understanding (brief explanation in your own words):**
  - Passwords must be hashed and salted using established cryptographic libraries. Security logic should be encapsulated in a dedicated utility (`PasswordEncoderUtil`) so the `UserService` focuses purely on orchestration, maintaining the Single Responsibility Principle.

---

## Feature / Component: Concurrency and Integration Testing (Race Conditions)

- **Purpose of LLM use:**
  - To learn how to write the specific Java code (boilerplate) needed to run the concurrency tests I planned.
- **Summary of prompt(s):**
  - I came up with some race condition scenarios (like what happens if an admin closes a company exactly when someone tries to view it, or when multiple users login/register at the same time). I asked the LLM how to write a JUnit test that runs these actions at the exact same millisecond.
- **Output received (short description):**
  - The LLM provided code examples using Java's `CountDownLatch` and `ExecutorService` to synchronize threads and run them together.
- **Files / components affected:**
  - `AdminServiceTest`, `UserServiceTest`, `EventCompanyManageServiceTest`.
- **Modifications made:**
  - I took the LLM's boilerplate code and applied it to my specific test cases. I used my actual service methods to check if the system stays stable and handles permissions correctly during concurrent reads and writes.
- **Initial gaps in understanding (if any):**
  - I knew exactly which edge cases I wanted to test, but I didn't know the specific Java classes and syntax needed to force threads to wait and run simultaneously.
- **Final understanding (brief explanation in your own words):**
  - I learned that Java utilities like `CountDownLatch` and `ExecutorService` can pause threads and release them at the exact same time. This is really useful to prove that our optimistic locking works and prevents data corruption or weird permission bugs under heavy load.

---
## Feature / Component: Checkout Edge-Case Handling and Failure-Safe Control Flow

- Purpose of LLM use:
  - To validate the robustness of the checkout flow against edge cases and failure scenarios, especially around payment, ticket issuance, cleanup, and state recovery.
- Summary of prompt(s):
  - Presented checkout edge cases such as payment rejection, ticket issuance failure after successful payment, expired active orders, invalid active-order ownership, and failures during event/order persistence.
  - Asked how to reason about a `try-catch-finally` structure that preserves system consistency while keeping cleanup actions explicit and predictable.
  - Asked for feedback on whether the chosen control-flow structure correctly separates success, failure, refund, ticket release, and active-order deletion responsibilities.
- Output received (short description):
  - Conceptual validation of the failure-handling structure and guidance for checking that each failure path preserves the relevant system invariants.
  - Suggestions for reviewing cleanup flags and ensuring that `finally` executes the required repository updates and resource release steps.
- Files / components affected:
  - `ActiveOrderService`
  - checkout/payment related tests
  - refund and ticket-issuance failure scenarios
- Modifications made:
  - Reviewed the checkout flow to ensure that rejected payments return the active order to checkout state.
  - Ensured that ticket issuance failure after payment triggers refund handling and ticket release.
  - Verified that expired active orders are deleted and their reserved tickets are released.
  - Checked that completed orders are persisted and sold tickets are marked consistently.
- Initial gaps in understanding (if any):
  - The main uncertainty was how to structure the cleanup logic so that every failure path leaves the system in a valid state without duplicating cleanup code across multiple branches.
- Final understanding (brief explanation in your own words):
  - A failure-safe checkout flow should make each side effect explicit: payment state, ticket reservation, order persistence, refund handling, and active-order cleanup must each have a clear owner in the control flow. The `try-catch-finally` structure helps centralize cleanup while still allowing each edge case to return the correct user-facing result.


Version 2:

---

## Feature / Component: Notification Infrastructure — Spring Services, Notifier, and Broadcaster Design

- Purpose of LLM use:
  - To discuss and refine the design of the notification mechanism, especially the responsibility split between application services, `INotifier`, `VaadinNotifier`, and `Broadcaster`.
- Summary of prompt(s):
  - Asked how to design the notifier so it supports both real-time pop-up delivery and delayed notifications for offline users.
  - Discussed whether the notifier should directly depend on `UserRepo`, and whether this creates an incorrect coupling between notification delivery and user persistence.
  - Asked how to reason about the responsibilities of Spring services versus infrastructure-level broadcasting logic.
- Output received (short description):
  - The LLM explained separation-of-concerns trade-offs: the notifier should focus on delivering notifications, while user lookup and delayed-notification persistence should preferably remain in the service/application layer or a dedicated notification service.
  - It also helped clarify the difference between notifying a currently connected UI listener and saving a notification for later retrieval.
- Files / components affected:
  - `INotifier`
  - `VaadinNotifier`
  - `Broadcaster`
  - `UserService`
  - Services that send notifications, especially lottery and checkout-related services.
- Modifications made:
  - Refined the notification flow so services explicitly decide when to send real-time notifications and when to save delayed notifications.
  - Added or adjusted logic around offline users, delayed notifications, and UI listener registration.
  - Opened/refined a design issue to decouple notifier responsibilities from user repository concerns.
- Initial gaps in understanding (if any):
  - It was unclear whether the notifier itself should know how to find and persist users, or whether it should only try to deliver notifications to currently connected listeners.
- Final understanding (brief explanation in your own words):
  - The notification system should have clear responsibility boundaries. `Broadcaster` handles active UI listeners, the notifier abstracts delivery, and application services decide what should happen when delivery fails. This makes the design easier to test, reduces coupling to persistence, and avoids mixing UI delivery concerns with user-domain storage logic.

---