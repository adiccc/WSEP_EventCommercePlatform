# System Initialization Guide

This project is a Spring Boot + Vaadin event-commerce system. It boots from `app.App`, reads runtime config from `src/main/resources/application.yml`, and can seed initial data from `src/main/resources/init-state.json` when started with `--db=empty`.

---

## How It Works

Configuration is loaded through Spring Boot properties:

- `system.*` is bound by [`SystemProperties`](src/main/java/app/config/SystemProperties.java)
- `active-order.*` is bound by [`ActiveOrderProperties`](src/main/java/app/config/ActiveOrderProperties.java)

All runtime configuration lives in [`src/main/resources/application.yml`](src/main/resources/application.yml) (Spring/Vaadin/datasource settings together with the `system.*` and `active-order.*` blocks). Tests use [`src/test/resources/application.yml`](src/test/resources/application.yml), which overrides the datasource/profiles and repeats the `system.*` / `active-order.*` blocks (a test-scoped `application.yml` shadows the main one on the classpath, so the values must be present in both).

Startup seeding is handled by [`SystemInitializer`](src/main/java/app/init/SystemInitializer.java):

- it only runs when `system.init-enabled=true` or the property is absent
- it only loads the init file when the app is started with `--db=empty`
- it uses `system.init-state-file` unless `--init-file=...` is provided
- it parses the file as JSON into [`InitStateFile`](src/main/java/app/init/InitStateFile.java)
- each object in the `operations` array is dispatched to a matching handler in `src/main/java/app/init/handlers/`
- values stored with `"store"` can be reused later via `${name}`

**Fail-fast:** if any operation fails, startup aborts with an `InitializationException`.

---

## Correctness Requirements

The following constraints **must** be satisfied by `init-state.json`, otherwise the system will either fail to start

### 1. Admin user must be registered

`AuthConfig` is configured with a hardcoded admin email:

```java
// src/main/java/app/config/AuthConfig.java
Set.of("systemadmin@demo.com")
```

`init-state.json` **must** register a user with that exact email before startup completes. If the admin email is changed in `AuthConfig`, the corresponding `register` step in `init-state.json` must be updated to match.

### 2. Queue capacity must be positive

`application.yml` must have `system.max-concurrent-users` set to a value greater than zero, or the WebQueue will fail to initialize. In the current configuration this value is `50`.

### 3. Variables must be stored before use

If a step references `${varName}`, an earlier step must have used `"store": "varName"` to populate it. Referencing an undefined variable throws `InitializationException` immediately.

### 4. All operations must succeed

Every handler throws `InitializationException` on failure. There is no partial initialization — either all steps succeed or the app does not start. 

---

## Prerequisites

- Java 17
- Maven 3.x
- No external DB is required for the default local setup
- A local `.env` file for environment-specific configuration. To run the system locally, create a `.env` file based on the provided `.env.example` file and fill in the required values.

## Run Locally

The standard Maven commands are:

```bash
mvn clean install
mvn spring-boot:run
```

The default application profile list in [`src/main/resources/application.yml`](src/main/resources/application.yml) is:

- `memory`
- `user-db`
- `suspension-db`
- `lottery-db`
- `company-db`
- `event-db`
- `prod`
- `activeorder-db`

That means the default local run enables the production payment/ticket integrations. To seed data, start with `--db=empty`:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--db=empty"
```

To seed from a custom file:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--db=empty --init-file=/absolute/path/to/init-state.json"
```

## Run Tests

Tests use [`src/test/resources/application.yml`](src/test/resources/application.yml), which:

- carries its own copy of the `system.*` / `active-order.*` blocks
- enables `system.init-enabled=false`
- uses H2 in-memory DB
- enables the `test` profile

Run them with:

```bash
mvn test
```

## Startup Arguments

The following command-line arguments can be passed when launching the application to control database state and initialization file.

### `--db=<mode>`

Controls the database state at startup.

| Value | Behaviour |
|-------|-----------|
| `keep` | Keep existing data — schema is updated if needed (default) |
| `empty` | Drop all tables and recreate them — starts with a completely empty database |

```
java -jar app.jar --db=empty
java -jar app.jar --db=keep
```

In IntelliJ: **Run → Edit Configurations → Program arguments**.

### `--init-file=<path>`

Overrides the init-state file defined in `application.yml` (`system.init-state-file`).

| Value | Behaviour |
|-------|-----------|
| *(omitted)* | Uses the file from `application.yml` (default) |
| `classpath:my-init.json` | Loads a file from the classpath |
| `/absolute/path/to/init.json` | Loads a file from the filesystem |

```
java -jar app.jar --init-file=/home/user/custom-init.json
java -jar app.jar --init-file=classpath:test-init.json
```

### Combined examples

```
# Fresh start with default init data
java -jar app.jar --db=empty

# Fresh start with custom seed data
java -jar app.jar --db=empty --init-file=/path/to/demo-data.json

# Keep existing DB, run a different init script
java -jar app.jar --init-file=/path/to/extra-setup.json
```

---

## Configuration File

The runtime configuration file is [`src/main/resources/application.yml`](src/main/resources/application.yml). Spring Boot loads it automatically (no `spring.config.import` needed).

```yaml
system:
  max-concurrent-users: 50
  init-state-file: classpath:init-state.json
  access-code-chars: "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
  access-code-length: 6

active-order:
  capacity: 20
  selecting-timeout-minutes: 5
  checkout-timeout-minutes: 10
  warning-before-expiry-minutes: 1
```

### `system.*`

- `max-concurrent-users` required, positive integer
- `init-state-file` required, classpath or file path
- `access-code-chars` required, non-blank string
- `access-code-length` required, positive integer

### `active-order.*`

- `capacity` required, positive integer
- `selecting-timeout-minutes` required, positive integer
- `checkout-timeout-minutes` required, positive integer
- `warning-before-expiry-minutes` required, positive integer and must be less than `checkout-timeout-minutes`

---

## Initial-State File

The default init file is [`src/main/resources/init-state.json`](src/main/resources/init-state.json).

Format:

- root object with a single required field `operations`
- `operations`: array of operation objects
- each operation supports:
  - `type` required, string
  - `params` optional, object of string values
  - `store` optional, string

Variable substitution is exact-match only. A value like `"${guest}"` resolves to a stored value; partial interpolation is not supported.

Supported operation types in the current code:

- `enter`
- `register`
- `login`
- `get-user-id`
- `open-company`
- `appoint-owner`
- `appoint-manager`
- `add-company-rule`
- `create-event`
- `define-venue`
- `add-event-discount`
- `create-lottery`
- `add-company-discount`

### Structure

```json
{
  "operations": [
    {
      "type":   "operation-type",
      "params": { "key": "value", "other": "${storedVar}" },
      "store":  "variableName"
    }
  ]
}
```

| Field    | Required | Description |
|----------|----------|-------------|
| `type`   | Yes      | The operation type — must match a registered handler |
| `params` | No       | Key-value parameters passed to the handler |
| `store`  | No       | If set, saves the handler's return value as `${name}` for later steps |

### Variable Substitution

Use `"store"` to capture a result, then reference it with `${varName}` in any later `params`:

```json
{ "type": "enter",        "store": "guest" },
{ "type": "register",     "params": { "guestToken": "${guest}", "email": "systemadmin@demo.com", "firstName": "System", "lastName": "Admin", "password": "Admin123!", "birthDay": "1", "birthMonth": "1", "birthYear": "1990", "address": "1 Admin St", "phone": "050-000-0000" } },
{ "type": "register",     "params": { "guestToken": "${guest}", "email": "u1@demo.com", "firstName": "U1", "lastName": "User", "password": "U1user1!", "birthDay": "1", "birthMonth": "1", "birthYear": "1990", "address": "1 First St", "phone": "050-001-0001" } },
{ "type": "register",     "params": { "guestToken": "${guest}", "email": "u2@demo.com", "firstName": "U2", "lastName": "User", "password": "U2user1!", "birthDay": "1", "birthMonth": "1", "birthYear": "1990", "address": "2 Second St", "phone": "050-002-0002" } },
{ "type": "register",     "params": { "guestToken": "${guest}", "email": "u3@demo.com", "firstName": "U3", "lastName": "User", "password": "U3user1!", "birthDay": "1", "birthMonth": "1", "birthYear": "1990", "address": "3 Third St", "phone": "050-003-0003" } },
{ "type": "register",     "params": { "guestToken": "${guest}", "email": "u4@demo.com", "firstName": "U4", "lastName": "User", "password": "U4user1!", "birthDay": "1", "birthMonth": "1", "birthYear": "1990", "address": "4 Fourth St", "phone": "050-004-0004" } },
{ "type": "login",        "params": { "email": "u1@demo.com", "password": "U1user1!" }, "store": "u1Token" },
{ "type": "open-company", "params": { "token": "${u1Token}", "companyId": "1", "companyName": "p1", "email": "p1@company.com", "phone": "050-100-0001", "bankAccount": "IL-1111-0001" } },
{ "type": "login",        "params": { "email": "u2@demo.com", "password": "U2user1!" }, "store": "u2Token" },
{ "type": "get-user-id",  "params": { "token": "${u2Token}" }, "store": "u2Id" },
{ "type": "appoint-owner","params": { "ownerToken": "${u1Token}", "appointeeToken": "${u2Token}", "companyId": "1", "appointeeId": "${u2Id}" } },
{ "type": "create-event", "params": { "token": "${u2Token}", "companyId": "1", "name": "e1", "date": "+1M", "saleStart": "-1D", "area": "CENTER", "category": "LIVEMUSIC" }, "store": "e1Id" }
```

### Date Format (for `create-event`)

Relative dates are specified as sign + number + unit:

| Format | Meaning          |
|--------|------------------|
| `+1M`  | now + 1 month    |
| `+20D` | now + 20 days    |
| `+1W`  | now + 1 week     |
| `+3m`  | now + 3 minutes  |
| `-1D`  | now − 1 day      |
| `-55m` | now − 55 minutes |

### Current Seed Data

The bundled `init-state.json` seeds:

- `systemadmin@demo.com` as the admin user
- `u1@demo.com`, `u2@demo.com`, `u3@demo.com`, and `u4@demo.com` as additional users
- company `1`
- company `1` owner and manager appointments
- event `e1`
- a default venue for `e1`
- company discount `sale123`

---

## Available Operations

### `enter`
Gets a guest token for use in `register`.

```json
{ "type": "enter", "store": "guest" }
```

**Returns:** guest session token

---

### `register`
Registers a new user account.

| Param        | Required |
|--------------|----------|
| `guestToken` | Yes      |
| `email`      | Yes      |
| `firstName`  | Yes      |
| `lastName`   | Yes      |
| `password`   | Yes      |
| `birthDay`   | Yes      |
| `birthMonth` | Yes      |
| `birthYear`  | Yes      |
| `address`    | Yes      |
| `phone`      | Yes      |

```json
{ "type": "register", "params": { "guestToken": "${guest}", "email": "u1@demo.com", "firstName": "U1", "lastName": "User", "password": "U1user1!", "birthDay": "1", "birthMonth": "1", "birthYear": "1990", "address": "1 First St", "phone": "050-001-0001" } }
```

---

### `login`
Logs in and returns a session token.

| Param      | Required |
|------------|----------|
| `email`    | Yes      |
| `password` | Yes      |

**Returns:** session token

```json
{ "type": "login", "params": { "email": "u1@demo.com", "password": "U1user1!" }, "store": "u1Token" }
```

---

### `get-user-id`
Returns the integer user ID for a logged-in token. Needed before `appoint-owner` / `appoint-manager`.

| Param   | Required |
|---------|----------|
| `token` | Yes      |

**Returns:** user ID (integer)

```json
{ "type": "get-user-id", "params": { "token": "${u2Token}" }, "store": "u2Id" }
```

---

### `open-company`
Creates a production company.

| Param         | Required |
|---------------|----------|
| `token`       | Yes      |
| `companyId`   | Yes      |
| `companyName` | Yes      |
| `email`       | Yes      |
| `phone`       | Yes      |
| `bankAccount` | Yes      |

**Returns:** company ID (integer)

```json
{ "type": "open-company", "params": { "token": "${u1Token}", "companyId": "1", "companyName": "p1", "email": "p1@company.com", "phone": "050-100-0001", "bankAccount": "IL-1111-0001" } }
```

---

### `appoint-owner`
Requests and auto-accepts an owner appointment (both steps handled atomically).

| Param            | Required |
|------------------|----------|
| `ownerToken`     | Yes      |
| `appointeeToken` | Yes      |
| `companyId`      | Yes      |
| `appointeeId`    | Yes — integer, use `get-user-id` first |

```json
{ "type": "appoint-owner", "params": { "ownerToken": "${u1Token}", "appointeeToken": "${u2Token}", "companyId": "1", "appointeeId": "${u2Id}" } }
```

---

### `appoint-manager`
Requests and auto-accepts a manager appointment with specific permissions.

| Param            | Required |
|------------------|----------|
| `ownerToken`     | Yes      |
| `appointeeToken` | Yes      |
| `companyId`      | Yes      |
| `appointeeId`    | Yes — integer, use `get-user-id` first |
| `permissions`    | Yes — comma-separated list |

Available permissions: `VIEW_ORDERS_HISTORY`, `CREATE_EVENT`, `MANAGE_STAFF`, `VIEW_INCOME_REPORTS`

```json
{ "type": "appoint-manager", "params": { "ownerToken": "${u2Token}", "appointeeToken": "${u3Token}", "companyId": "1", "appointeeId": "${u3Id}", "permissions": "CREATE_EVENT,DELETE_EVENT" } }
```

---

### `add-company-rule`
Adds a purchase policy rule to a company.

| Param       | Required |
|-------------|----------|
| `token`     | Yes      |
| `companyId` | Yes      |
| `ruleType`  | Yes — `MIN_AGE`, `MAX_TICKETS`, or `MIN_TICKETS` |
| `value`     | Yes      |

```json
{ "type": "add-company-rule", "params": { "token": "${u2Token}", "companyId": "1", "ruleType": "MIN_AGE", "value": "18" } }
```

---

### `create-event`
Creates an event under a company. The event is inactive until `define-venue` is called.

| Param        | Required | Notes |
|--------------|----------|-------|
| `token`      | Yes      |       |
| `companyId`  | Yes      |       |
| `name`       | Yes      |       |
| `date`       | Yes      | Relative date, e.g. `+1M` |
| `saleStart`  | No       | Default: `-1D` (sale already open) |
| `area`       | Yes      | `CENTER`, `NORTH`, `SOUTH`, `JERUSALEM` |
| `category`   | Yes      | `LIVEMUSIC`, `FESTIVAL`, `SPORTS`, `THEATER`, `CONFERENCE`, `OTHER` |
| `hasLottery` | No       | `true` / `false`, default `false` |

**Returns:** event ID (integer)

```json
{ "type": "create-event", "params": { "token": "${u2Token}", "companyId": "1", "name": "e1", "date": "+1M", "saleStart": "-1D", "area": "CENTER", "category": "LIVEMUSIC" }, "store": "e1Id" }
```

---

### `define-venue`
Activates an event by attaching a default seating map. **Must be called after `create-event`** for the event to be visible and purchasable.

| Param     | Required |
|-----------|----------|
| `token`   | Yes      |
| `eventId` | Yes      |

```json
{ "type": "define-venue", "params": { "token": "${u2Token}", "eventId": "${e1Id}", "standingCapacity": "30", "standingPrice": "50.0", "standingName": "Standing", "seatingRows": "10", "seatingCols": "10", "seatingPrice": "100.0", "seatingName": "Seating" } }
```

---

### `add-event-discount`
Adds a coupon code discount to an event.

| Param               | Required |
|---------------------|----------|
| `token`             | Yes      |
| `eventId`           | Yes      |
| `couponCode`        | Yes      |
| `percent`           | Yes      |
| `expiryDaysFromNow` | Yes      |

```json
{ "type": "add-event-discount", "params": { "token": "${u2Token}", "eventId": "${e1Id}", "couponCode": "sale123", "percent": "20.0", "expiryDaysFromNow": "365" } }
```

---

### `create-lottery`
Creates a scheduled lottery for an event. Requires `hasLottery: true` on the event.

| Param            | Required |
|------------------|----------|
| `token`          | Yes      |
| `eventId`        | Yes      |
| `capacity`       | Yes      |
| `minutesFromNow` | Yes      |
| `expirationHours`| Yes      |

```json
{ "type": "create-lottery", "params": { "token": "${u2Token}", "eventId": "${e1Id}", "capacity": "1", "minutesFromNow": "2", "expirationHours": "24" } }
```

---

## Adding a New Operation

1. **Create a handler** in `src/main/java/app/init/handlers/`:

```java
@Component
public class MyOperationHandler implements InitOperationHandler {

    @Autowired
    private SomeService service;

    @Override
    public String operationType() { return "my-operation"; }

    @Override
    public Object execute(Map<String, String> params, InitContext context) {
        Response<?> response = service.doSomething(params.get("param1"));
        if (response.getValue() == null)
            throw new InitializationException("my-operation failed: " + response.getMessage());
        return response.getValue();
    }
}
```

2. **Add the step** to `init-state.json`:

```json
{ "type": "my-operation", "params": { "param1": "${someVar}" }, "store": "myResult" }
```

No other changes needed — Spring auto-discovers the handler on next startup.

---
