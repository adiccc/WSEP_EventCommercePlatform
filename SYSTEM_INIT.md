# System Initialization Guide

This document explains how the system seeds its initial state on startup, how to configure it, and how to extend it with new operations.

---

## How It Works

On every startup, `SystemInitializer` reads `src/main/resources/init-state.json` and executes each operation in order. Each operation is handled by a dedicated handler bean in `app/init/handlers/`. Results can be stored and referenced by later steps using `${varName}` substitution.

**Fail-fast:** if any operation fails, startup is aborted immediately with an `InitializationException`. The system will not start in a broken state.

To disable initialization entirely (e.g. in tests), set `system.init-enabled=false`.

---

## Correctness Requirements

The following constraints **must** be satisfied by `init-state.json`, otherwise the system will either fail to start or start in a broken state.

### 1. Admin user must be registered

`AuthConfig` is configured with a hardcoded admin email:

```java
// src/main/java/app/config/AuthConfig.java
Set.of("systemadmin@demo.com")
```

`init-state.json` **must** register a user with that exact email before startup completes. If the admin email is changed in `AuthConfig`, the corresponding `register` step in `init-state.json` must be updated to match.

### 2. Queue capacity must be positive

`config.yml` must have `system.max-concurrent-users` set to a value greater than zero, or the WebQueue will fail to initialize.

### 3. Variables must be stored before use

If a step references `${varName}`, an earlier step must have used `"store": "varName"` to populate it. Referencing an undefined variable throws `InitializationException` immediately.

### 4. All operations must succeed

Every handler throws `InitializationException` on failure. There is no partial initialization — either all steps succeed or the app does not start. Wrap risky operations in a separate optional file if needed.

---

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

Overrides the init-state file defined in `config.yml` (`system.init-state-file`).

| Value | Behaviour |
|-------|-----------|
| *(omitted)* | Uses the file from `config.yml` (default) |
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

## Configuration: `config.yml`

Located at `src/main/resources/config.yml`.

```yaml
system:
  max-concurrent-users: 50           # WebQueue capacity — max simultaneous users in system
  active-order-ttl-minutes: 10       # Minutes before an unpaid active order expires
  init-state-file: classpath:init-state.json  # Path to the init operations file
  init-enabled: true                 # Set to false to skip initialization (tests, etc.)
```

---

## Init State File: `init-state.json`

Located at `src/main/resources/init-state.json`.

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
{ "type": "register",     "params": { "guestToken": "${guest}", "email": "..." } },
{ "type": "login",        "params": { "email": "...", "password": "..." }, "store": "token" },
{ "type": "open-company", "params": { "token": "${token}", ... }, "store": "companyId" },
{ "type": "create-event", "params": { "token": "${token}", "companyId": "${companyId}", ... }, "store": "eventId" }
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
{ "type": "register", "params": { "guestToken": "${guest}", "email": "alice@demo.com", "firstName": "Alice", "lastName": "Cohen", "password": "Alice123!", "birthDay": "1", "birthMonth": "1", "birthYear": "1995", "address": "123 Main St", "phone": "050-123-4567" } }
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
{ "type": "login", "params": { "email": "alice@demo.com", "password": "Alice123!" }, "store": "aliceToken" }
```

---

### `get-user-id`
Returns the integer user ID for a logged-in token. Needed before `appoint-owner` / `appoint-manager`.

| Param   | Required |
|---------|----------|
| `token` | Yes      |

**Returns:** user ID (integer)

```json
{ "type": "get-user-id", "params": { "token": "${aliceToken}" }, "store": "aliceId" }
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
{ "type": "open-company", "params": { "token": "${aliceToken}", "companyId": "1", "companyName": "SoundWave Events", "email": "info@sw.com", "phone": "050-111-0001", "bankAccount": "IL-1234-001" } }
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
{ "type": "appoint-owner", "params": { "ownerToken": "${aliceToken}", "appointeeToken": "${daveToken}", "companyId": "1", "appointeeId": "${daveId}" } }
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

Available permissions: `MANAGE_EVENTS_INVENTORY`, `VIEW_ORDERS_HISTORY`, `CREATE_EVENT`, `MANAGE_STAFF`, `VIEW_INCOME_REPORTS`

```json
{ "type": "appoint-manager", "params": { "ownerToken": "${aliceToken}", "appointeeToken": "${eveToken}", "companyId": "1", "appointeeId": "${eveId}", "permissions": "MANAGE_EVENTS_INVENTORY,CREATE_EVENT" } }
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
{ "type": "add-company-rule", "params": { "token": "${aliceToken}", "companyId": "1", "ruleType": "MIN_AGE", "value": "18" } }
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
{ "type": "create-event", "params": { "token": "${aliceToken}", "companyId": "1", "name": "Rock Night", "date": "+1M", "saleStart": "-1D", "area": "CENTER", "category": "LIVEMUSIC" }, "store": "rockEventId" }
```

---

### `define-venue`
Activates an event by attaching a default seating map. **Must be called after `create-event`** for the event to be visible and purchasable.

| Param     | Required |
|-----------|----------|
| `token`   | Yes      |
| `eventId` | Yes      |

```json
{ "type": "define-venue", "params": { "token": "${aliceToken}", "eventId": "${rockEventId}" } }
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
{ "type": "add-event-discount", "params": { "token": "${aliceToken}", "eventId": "${rockEventId}", "couponCode": "ROCK50", "percent": "50.0", "expiryDaysFromNow": "30" } }
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
{ "type": "create-lottery", "params": { "token": "${aliceToken}", "eventId": "${lotteryEventId}", "capacity": "1", "minutesFromNow": "2", "expirationHours": "24" } }
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

## Demo Users (seeded by `init-state.json`)

| Email                  | Password    | Role                        |
|------------------------|-------------|-----------------------------|
| `systemadmin@demo.com` | `Admin123!` | System admin (**required**) |
| `alice@demo.com`       | `Alice123!` | Owner of companies 1–4      |
| `bob@demo.com`         | `Bob1234!`  | Owner of companies 5–7      |
| `charlie@demo.com`     | `Charlie1!` | Owner of companies 8–10     |
| `dave@demo.com`        | `Dave123!`  | Co-owner of company 1       |
| `eve@demo.com`         | `Eve1234!`  | Manager of company 1        |
