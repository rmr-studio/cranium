---
Created:
  - "[[riven/docs/system-design/domains/Workflows/Workflows]]"
Updated: 2026-02-09
---
# WorkflowHttpRequestActionConfig

---

## Purpose

Makes HTTP requests to external URLs with template-resolved parameters and SSRF protection to prevent internal network access.

---

## Responsibilities

- Configure HTTP request parameters (URL, method, headers, body, timeout)
- Validate URL, method, headers, and body template syntax
- Execute HTTP requests via Spring WebClient
- Prevent SSRF attacks by blocking private IPs and metadata endpoints
- Return HTTP response with status, headers, and body

**Explicitly NOT responsible for:**
- Authentication/authorization of external APIs (workflow must provide credentials via templates)
- Retry logic (single request attempt only)
- Response parsing (returns raw body string)

---

## Dependencies

### Internal Dependencies

| Component | Purpose | Coupling |
|---|---|---|
| [[riven/docs/system-design/domains/Workflows/Node Execution/WorkflowNodeConfigValidationService]] | Validates template syntax and config fields | Medium |
| [[riven/apps/client/lib/types/docs/WorkflowNodeConfig]] | Sealed parent class for all node configurations | High |

### External Dependencies

| Service/Library | Purpose | Failure Impact |
|---|---|---|
| Spring WebClient | Reactive HTTP client for external requests | Node execution fails, workflow blocks |

---

## Consumed By

| Component | How It Uses This | Notes |
|---|---|---|
| [[riven/docs/system-design/domains/Workflows/Node Execution/WorkflowNodeConfigRegistry]] | Discovers at startup via classpath scan | Auto-registration |
| [[riven/apps/client/lib/types/docs/WorkflowNode]] | Executes via `execute()` method | At workflow runtime |

---

## Config Fields

| Field | Type | Required | Description |
|---|---|---|---|
| `url` | TEMPLATE | Yes | URL to request (supports templates like `{{ steps.x.output.apiUrl }}`) |
| `method` | ENUM | Yes | HTTP method: GET, POST, PUT, DELETE, PATCH |
| `headers` | KEY_VALUE | No | HTTP headers (values support templates) |
| `body` | KEY_VALUE | No | Request body for POST/PUT/PATCH (values support templates) |
| `timeoutSeconds` | DURATION | No | Optional timeout override in seconds |

---

## JSON Examples

### Configuration

```json
{
  "version": 1,
  "type": "ACTION",
  "subType": "HTTP_REQUEST",
  "url": "https://api.example.com/users",
  "method": "POST",
  "headers": {
    "Content-Type": "application/json",
    "X-API-Key": "{{ steps.get_api_key.output.key }}"
  },
  "body": {
    "email": "{{ steps.fetch_lead.output.email }}",
    "name": "{{ steps.fetch_lead.output.name }}"
  }
}
```

### Output

```json
{
  "statusCode": 201,
  "headers": {
    "Content-Type": "application/json",
    "X-Request-Id": "abc123"
  },
  "body": "{\"id\": \"user-123\", \"email\": \"lead@example.com\"}",
  "url": "https://api.example.com/users",
  "method": "POST"
}
```

---

## Key Logic

### SSRF Validation

The `validateUrl()` private method prevents Server-Side Request Forgery attacks by blocking requests to internal infrastructure:

**Blocked hosts:**
- `localhost`, `127.0.0.1`, `::1` (loopback addresses)
- `10.x.x.x` (private class A networks)
- `192.168.x.x` (private class C networks)
- `172.x.x.x` (private class B networks - basic check only)
- `169.254.169.254` (cloud metadata endpoints - AWS, GCP, Azure)

**Enforcement:** Throws `SecurityException` if URL resolves to any blocked host.

**Limitations:**
- Only checks hostname string prefix (doesn't resolve DNS)
- 172.x check is overly broad (blocks some public IPs in 172.0-15.x, 172.32-255.x)
- Doesn't block IPv6 private ranges beyond `::1`
- Doesn't prevent DNS rebinding attacks

### HTTP Execution Flow

```kotlin
webClient
  .method(HttpMethod.valueOf(resolvedMethod))
  .uri(resolvedUrl)
  .headers { h -> resolvedHeaders.forEach { (k, v) -> h.set(k, v) } }
  .bodyValue(resolvedBody ?: emptyMap())
  .retrieve()
  .toEntity(String::class.java)
  .block()  // BLOCKING call on reactive chain
```

**Key behaviors:**
1. Converts method string to `HttpMethod` enum
2. Sets all headers from config (including sensitive auth headers)
3. Serializes body map to JSON via WebClient
4. Blocks thread until response received (`.block()`)
5. Returns entire response entity (status, headers, body)

---

## Validation Rules

1. **url**:
   - Must not be blank
   - Must have valid template syntax if templated

2. **method**:
   - Must not be blank
   - Must be one of: GET, POST, PUT, DELETE, PATCH

3. **headers** (if provided):
   - All values must have valid template syntax if templated

4. **body** (if provided):
   - All values must have valid template syntax if templated

5. **timeoutSeconds** (if provided):
   - Must be non-negative

**Runtime validation:**
- URL SSRF check performed in `execute()` after template resolution

---

## Gotchas & Edge Cases

### Sensitive Headers Defined But Not Filtered

The code defines `SENSITIVE_HEADERS` constant:

```kotlin
private val SENSITIVE_HEADERS = setOf(
    "authorization",
    "x-api-key",
    "api-key",
    "cookie",
    "set-cookie"
)
```

**Current usage:** Only used for log filtering (headers aren't logged individually). Sensitive headers ARE included in actual HTTP requests, which is correct behavior.

**Misleading name:** The constant suggests these headers are blocked, but they're actually just excluded from detailed logging.

### Blocking Call on Reactive Chain

The `.block()` call converts the reactive WebClient chain into a synchronous operation:

```kotlin
.block() ?: throw RuntimeException("HTTP request returned null")
```

**Impact:**
- Blocks the workflow execution thread until HTTP response received
- Cannot cancel in-progress requests
- Thread pool exhaustion risk if many workflows wait on slow external APIs

**Why blocking:** Temporal workflow execution model is synchronous. Reactive chains must be resolved before returning from activity.

### No Retry Logic

Single request attempt only. Network failures, timeouts, or 5xx errors fail the workflow node immediately.

**Workaround:** Temporal provides activity retry policies at the workflow level.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Actions/Action Nodes]] — category-level overview of all action node types
- [[riven/apps/client/lib/types/docs/WorkflowNodeConfig]] — sealed parent class defining node configuration contract
