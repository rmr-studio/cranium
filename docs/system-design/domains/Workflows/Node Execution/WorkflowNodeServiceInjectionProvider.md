---
tags:
  - layer/service
  - component/active
  - architecture/component
Domains:
  - "[[riven/docs/system-design/domains/Workflows/Workflows]]"
Created: 2026-02-08
Updated: 2026-02-08
---
# WorkflowNodeServiceInjectionProvider

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Node Execution]]

## Purpose

Spring implementation of `NodeServiceProvider` that wraps `ApplicationContext` for lazy, on-demand service resolution during workflow node execution — allows nodes to request only the specific services they need without requiring a monolithic service registry.

---

## Responsibilities

- Resolve Spring beans by class type at runtime via `ApplicationContext.getBean()`
- Provide thread-safe service access for concurrent workflow executions
- Throw clear exceptions when requested service is not found

---

## Dependencies

- `ApplicationContext` (Spring) — Bean container for service resolution

## Used By

- [[riven/docs/system-design/domains/Workflows/Execution Engine/WorkflowGraphCoordinationService]] — Passes instance to `WorkflowNodeConfig.execute()` as `services` parameter
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/WorkflowNodeConfig]] implementations — Receive as `services` parameter, call `get()` to resolve needed services (e.g., `EntityService`, `HttpClient`, `EmailService`)

---

## Key Logic

**Single-method implementation:**

```kotlin
override fun <T : Any> get(serviceClass: KClass<T>): T =
    applicationContext.getBean(serviceClass.java)
```

Delegates to Spring's `ApplicationContext.getBean()`. No caching, no custom logic — pure delegation.

**Thread safety:**

Service is thread-safe because it delegates to Spring's `ApplicationContext`, which handles concurrent bean access safely. Multiple workflow executions can request services concurrently without synchronization issues.

**Error handling:**

If requested service is not found, Spring throws `NoSuchBeanDefinitionException` with message indicating which service was requested:

```
No qualifying bean of type 'riven.core.service.EntityService' available
```

Node execution catches this and surfaces as workflow error.

---

## Public Methods

### `get(serviceClass: KClass<T>): T`

Retrieves a Spring-managed service by its class. Returns the service instance. Throws `NoSuchBeanDefinitionException` if service not found.

**Example usage in node config:**

```kotlin
val entityService = services.get(EntityService::class)
val result = entityService.create(entityType, fields)
```

---

## Gotchas

- **No bean caching:** Each `get()` call delegates to `ApplicationContext.getBean()`. Spring handles bean caching internally (singletons cached, prototypes created). No double-caching needed.
- **Requires Spring context:** Service must be registered as Spring bean. If node requests unregistered service, throws `NoSuchBeanDefinitionException` at runtime. Not caught at compile time.
- **Lazy resolution pattern:** Nodes only pay for services they use. If node doesn't need `EmailService`, never resolved. Improves performance vs. injecting all services upfront.
- **Used in both execution and validation:** Passed to both `execute()` and `validate()` methods. Validation can use services to perform application-based checks (e.g., verify entity type exists before execution).

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/WorkflowNodeConfig]] — Receives service provider as `services` parameter
- [[riven/docs/system-design/domains/Workflows/Execution Engine/WorkflowGraphCoordinationService]] — Creates and passes service provider to node execution
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Node Execution]] — Parent subdomain
