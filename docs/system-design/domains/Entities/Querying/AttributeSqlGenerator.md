---
tags:
  - component/active
  - layer/service
  - architecture/component
Created: 2026-02-08
Updated: 2026-03-09
Domains:
  - "[[riven/docs/system-design/domains/Entities/Entities]]"
---
Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/Querying]]

# AttributeSqlGenerator

---

## Purpose

Generates parameterized SQL fragments for filtering entities by attribute values stored in the normalized `entity_attributes` table. All operators produce EXISTS or NOT EXISTS subqueries correlating on `entity_id = {entityAlias}.id`, enabling indexed lookups.

---

## Responsibilities

**This component owns:**
- Converting FilterOperator conditions to EXISTS/NOT EXISTS subqueries against `entity_attributes`
- JSONB value comparison using `value = :val::jsonb` for equality and `(value #>> '{}')` for text extraction
- Regex-guarded numeric casts for comparisons (prevent PostgreSQL errors)
- Escaping LIKE metacharacters for safe pattern matching
- Handling entity alias parameterization for nested relationship filters
- Generating ALWAYS_TRUE/ALWAYS_FALSE fragments for empty IN/NOT_IN lists
- Serializing values to JSONB literals via `serializeJsonbValue()` helper

**Explicitly NOT responsible for:**
- Validating filter structure or attribute IDs (delegated to validation layer)
- Executing queries (delegated to query executor)
- Resolving template expressions (delegated to workflow layer)
- Managing ParameterNameGenerator lifecycle (caller provides shared instance)

---

## Dependencies

### Internal Dependencies

|Component|Purpose|Coupling|
|---|---|---|
|[[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/SqlFragment]]|Immutable container for SQL with parameters|High|
|[[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/ParameterNameGenerator]]|Generates unique parameter names|High|

### External Dependencies

None. ObjectMapper dependency removed — JSONB serialization handled by `serializeJsonbValue()` helper.

### Injected Dependencies

```kotlin
@Component
class AttributeSqlGenerator
```

No constructor dependencies. Stateless component.

---

## Consumed By

|Component|How It Uses This|Notes|
|---|---|---|
|[[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/AttributeFilterVisitor]]|Calls generate() for each ATTRIBUTE filter node|Main consumer - delegates all attribute SQL generation|
|[[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/RelationshipSqlGenerator]]|Calls generate() for nested attribute filters in TargetMatches|Passes custom entityAlias for target entity table|

---

## Public Interface

### Key Methods

#### `generate(attributeId: UUID, operator: FilterOperator, value: Any?, paramGen: ParameterNameGenerator, entityAlias: String = "e"): SqlFragment`

- **Purpose:** Generates an EXISTS/NOT EXISTS subquery fragment for filtering by an attribute value in the `entity_attributes` table
- **When to use:** For each attribute comparison in a filter tree
- **Side effects:** Increments paramGen counter (pure function otherwise)
- **Throws:** None (all operators supported, invalid inputs produce safe SQL)
- **Returns:** SqlFragment with parameterized SQL and bound values

**Parameters:**
- `attributeId`: UUID of the attribute in the `entity_attributes` table
- `operator`: Comparison operator (EQUALS, GREATER_THAN, CONTAINS, etc.)
- `value`: Value to compare against (may be null for IS_NULL/IS_NOT_NULL)
- `paramGen`: Generator for unique parameter names (shared across query tree)
- `entityAlias`: Table alias for the entity being filtered (default "e" for root entity, "t_N" for relationship targets)

**Operator dispatch:**
```kotlin
when (operator) {
    EQUALS -> generateEquals(...)
    NOT_EQUALS -> generateNotEquals(...)
    GREATER_THAN -> generateNumericComparison(">", ...)
    GREATER_THAN_OR_EQUALS -> generateNumericComparison(">=", ...)
    LESS_THAN -> generateNumericComparison("<", ...)
    LESS_THAN_OR_EQUALS -> generateNumericComparison("<=", ...)
    IN -> generateIn(...)
    NOT_IN -> generateNotIn(...)
    CONTAINS -> generateContains(...)
    NOT_CONTAINS -> generateNotContains(...)
    IS_NULL -> generateIsNull(...)
    IS_NOT_NULL -> generateIsNotNull(...)
    STARTS_WITH -> generateStartsWith(...)
    ENDS_WITH -> generateEndsWith(...)
}
```

---

## Key Logic

### EXISTS Subquery Pattern

**All operators use EXISTS/NOT EXISTS subqueries against `entity_attributes`:**

```sql
EXISTS (
    SELECT 1 FROM entity_attributes a_0
    WHERE a_0.entity_id = e.id
    AND a_0.attribute_id = :eq_attr_1
    AND a_0.value = :eq_val_2::jsonb
    AND a_0.deleted = false
)
```

**Why EXISTS subqueries instead of JSONB operators:**
- Enables B-tree indexing on `(attribute_id, type_id, value)` for fast lookups
- Eliminates GIN index dependency (`jsonb_path_ops` no longer needed)
- Each subquery correlates on `entity_id = {entityAlias}.id` for correct entity scoping
- `deleted = false` filter in every subquery respects soft-delete semantics

**Value comparison approaches:**
- **Equality:** `a.value = :val::jsonb` — direct JSONB comparison (strings quoted, numbers raw)
- **Text extraction:** `(a.value #>> '{}')` — extracts top-level JSON value as text for ILIKE, IN, numeric casts
- **Serialization:** `serializeJsonbValue()` helper converts values to JSONB literals (strings → `"quoted"`, numbers/booleans → raw)

### Attribute Existence Semantics

**EXISTS vs NOT EXISTS handles null/missing correctly:**

- **EQUALS:** EXISTS subquery — only matches if attribute row exists with matching value
- **NOT_EQUALS:** EXISTS with `!=` — only matches if attribute row exists with different value. Entities missing the attribute are excluded (no row = no match in EXISTS).
- **IS_NULL:** NOT EXISTS — matches when no attribute row exists (attribute absent or soft-deleted)
- **IS_NOT_NULL:** EXISTS — matches when attribute row exists (regardless of value)

**Key difference from JSONB approach:**
Previously, NOT_EQUALS required an explicit `jsonb_exists()` check to exclude missing keys. With normalized rows, EXISTS naturally excludes entities without the attribute.

### Numeric Comparison Type Safety

**Regex-guarded cast prevents PostgreSQL errors:**

```sql
EXISTS (SELECT 1 FROM entity_attributes a_0
    WHERE a_0.entity_id = e.id
    AND a_0.attribute_id = :num_attr_1
    AND a_0.deleted = false
    AND CASE
        WHEN (a_0.value #>> '{}') ~ '^-?[0-9]+(\.[0-9]+)?$'
        THEN (a_0.value #>> '{}')::numeric > :num_val_2
        ELSE false
    END)
```

**Why regex guard is needed:**

Without regex guard:
- Non-numeric values cause PostgreSQL cast error: `ERROR: invalid input syntax for type numeric: "hello"`
- Query execution fails

With regex guard:
- Non-numeric values silently fail to match (ELSE false)
- Query executes successfully
- Correct semantics: non-numeric values don't match numeric comparisons

**Regex pattern:** `^-?[0-9]+(\.[0-9]+)?$`
- Matches: `123`, `-456`, `78.9`, `-0.123`
- Rejects: `abc`, `12.34.56`, `1e5`, `NaN`

**Value coercion:**
The value parameter is coerced to numeric:
```kotlin
val numericValue = when (value) {
    is Number -> value
    is String -> value.toBigDecimalOrNull() ?: 0
    null -> 0
    else -> value.toString().toBigDecimalOrNull() ?: 0
}
```

### LIKE Pattern Escaping

**Escapes metacharacters to prevent injection:**

```kotlin
private fun escapeLikePattern(value: String): String =
    value
        .replace("\\", "\\\\")   // Backslash first to avoid double-escaping
        .replace("%", "\\%")     // Wildcard "any characters"
        .replace("_", "\\_")     // Wildcard "single character"
```

**Why escape order matters:**

Correct (backslash first):
- Input: `a%b`
- After `\\` escape: `a%b` (no backslashes)
- After `%` escape: `a\%b` (correct)

Incorrect (backslash last):
- Input: `a%b`
- After `%` escape: `a\%b`
- After `\\` escape: `a\\%b` (wrong - escaped the escape)

**SQL ESCAPE clause:**
All LIKE queries use `ESCAPE '\\'` to declare backslash as escape character:
```sql
(a.value #>> '{}') ILIKE :val ESCAPE '\\'
```

**Operators using escaping:**
- CONTAINS: `%{escaped}%`
- NOT_CONTAINS: `NOT ... ILIKE %{escaped}%`
- STARTS_WITH: `{escaped}%`
- ENDS_WITH: `%{escaped}`

### Entity Alias Parameterization

**Supports nested relationship filters with custom table aliases:**

Default (root entity):
```kotlin
generator.generate(attrId, EQUALS, "Active", paramGen)
// SQL: EXISTS (SELECT 1 FROM entity_attributes a_0 WHERE a_0.entity_id = e.id AND ...)
```

Custom alias (relationship target):
```kotlin
generator.generate(attrId, EQUALS, "Active", paramGen, entityAlias = "t_1")
// SQL: EXISTS (SELECT 1 FROM entity_attributes a_0 WHERE a_0.entity_id = t_1.id AND ...)
```

**Why this is needed:**

Relationship filters with TargetMatches generate EXISTS subqueries that JOIN to a target entity table with a different alias:

```sql
EXISTS (
    SELECT 1 FROM entity_relationships r_0
    JOIN entities t_1 ON r_0.target_entity_id = t_1.id
    WHERE r_0.source_entity_id = e.id
      AND r_0.relationship_field_id = :rel_2
      AND EXISTS (SELECT 1 FROM entity_attributes a_4 WHERE a_4.entity_id = t_1.id AND ...)  -- Correlates on t_1, not e
)
```

The RelationshipSqlGenerator passes `entityAlias = "t_1"` when calling AttributeSqlGenerator for nested filters.

**Default value:** `"e"` preserves backward compatibility with existing callers.

### Empty List Handling

**IN with empty list returns ALWAYS_FALSE:**

```kotlin
if (values.isEmpty()) {
    return SqlFragment.ALWAYS_FALSE  // "1=0"
}
```

**NOT_IN with empty list returns ALWAYS_TRUE:**

```kotlin
if (values.isEmpty()) {
    return SqlFragment.ALWAYS_TRUE  // "1=1"
}
```

**Why:**
- `x IN ()` never matches (empty set)
- `x NOT IN ()` always matches (not in empty set)
- Prevents PostgreSQL syntax errors from empty IN lists

---

## Per-Operator SQL Patterns

### EQUALS

**EXISTS subquery with JSONB equality:**
```sql
EXISTS (SELECT 1 FROM entity_attributes a_0
    WHERE a_0.entity_id = e.id AND a_0.attribute_id = :eq_attr_1
    AND a_0.value = :eq_val_2::jsonb AND a_0.deleted = false)
```

**Parameters:**
- `eq_attr_N`: Attribute UUID
- `eq_val_N`: JSONB literal (via serializeJsonbValue)

**Edge case:** If value is null, delegates to IS_NULL.

---

### NOT_EQUALS

**EXISTS subquery with JSONB inequality:**
```sql
EXISTS (SELECT 1 FROM entity_attributes a_0
    WHERE a_0.entity_id = e.id AND a_0.attribute_id = :neq_attr_1
    AND a_0.value != :neq_val_2::jsonb AND a_0.deleted = false)
```

**Parameters:**
- `neq_attr_N`: Attribute UUID
- `neq_val_N`: JSONB literal (via serializeJsonbValue)

**Edge case:** If value is null, delegates to IS_NOT_NULL.

---

### GREATER_THAN / GREATER_THAN_OR_EQUALS / LESS_THAN / LESS_THAN_OR_EQUALS

**EXISTS subquery with regex-guarded numeric comparison:**
```sql
EXISTS (SELECT 1 FROM entity_attributes a_0
    WHERE a_0.entity_id = e.id AND a_0.attribute_id = :num_attr_1
    AND a_0.deleted = false
    AND CASE WHEN (a_0.value #>> '{}') ~ '^-?[0-9]+(\.[0-9]+)?$'
        THEN (a_0.value #>> '{}')::numeric > :num_val_2 ELSE false END)
```

**Parameters:**
- `num_attr_N`: Attribute UUID
- `num_val_N`: Numeric value

**Non-numeric values:** Fail to match (ELSE false).

---

### IN

**EXISTS subquery with list membership:**
```sql
EXISTS (SELECT 1 FROM entity_attributes a_0
    WHERE a_0.entity_id = e.id AND a_0.attribute_id = :in_attr_1
    AND (a_0.value #>> '{}') IN (:in_vals_2) AND a_0.deleted = false)
```

**Parameters:**
- `in_attr_N`: Attribute UUID
- `in_vals_N`: List of string values

**Value extraction:**
- `List<*>` → used as-is
- Single value → wrapped as single-element list
- `null` → empty list → ALWAYS_FALSE

**Empty list:** Returns `SqlFragment.ALWAYS_FALSE`.

---

### NOT_IN

**EXISTS subquery with list non-membership:**
```sql
EXISTS (SELECT 1 FROM entity_attributes a_0
    WHERE a_0.entity_id = e.id AND a_0.attribute_id = :nin_attr_1
    AND (a_0.value #>> '{}') NOT IN (:nin_vals_2) AND a_0.deleted = false)
```

**Parameters:**
- `nin_attr_N`: Attribute UUID
- `nin_vals_N`: List of string values

**Empty list:** Returns `SqlFragment.ALWAYS_TRUE`.

---

### CONTAINS

**EXISTS subquery with case-insensitive substring match:**
```sql
EXISTS (SELECT 1 FROM entity_attributes a_0
    WHERE a_0.entity_id = e.id AND a_0.attribute_id = :contains_attr_1
    AND (a_0.value #>> '{}') ILIKE :contains_val_2 ESCAPE '\\' AND a_0.deleted = false)
```

**Parameters:**
- `contains_attr_N`: Attribute UUID
- `contains_val_N`: `%{escaped}%` pattern

**Case sensitivity:** ILIKE is case-insensitive.

---

### NOT_CONTAINS

**NOT EXISTS subquery with case-insensitive substring match:**
```sql
NOT EXISTS (SELECT 1 FROM entity_attributes a_0
    WHERE a_0.entity_id = e.id AND a_0.attribute_id = :nc_attr_1
    AND (a_0.value #>> '{}') ILIKE :nc_val_2 ESCAPE '\\' AND a_0.deleted = false)
```

**Parameters:**
- `ncontains_attr_N`: Attribute UUID
- `ncontains_val_N`: `%{escaped}%` pattern

---

### STARTS_WITH

**EXISTS subquery with case-insensitive prefix match:**
```sql
EXISTS (SELECT 1 FROM entity_attributes a_0
    WHERE a_0.entity_id = e.id AND a_0.attribute_id = :starts_attr_1
    AND (a_0.value #>> '{}') ILIKE :starts_val_2 ESCAPE '\\' AND a_0.deleted = false)
```

**Parameters:**
- `starts_attr_N`: Attribute UUID
- `starts_val_N`: `{escaped}%` pattern

---

### ENDS_WITH

**EXISTS subquery with case-insensitive suffix match:**
```sql
EXISTS (SELECT 1 FROM entity_attributes a_0
    WHERE a_0.entity_id = e.id AND a_0.attribute_id = :ends_attr_1
    AND (a_0.value #>> '{}') ILIKE :ends_val_2 ESCAPE '\\' AND a_0.deleted = false)
```

**Parameters:**
- `ends_attr_N`: Attribute UUID
- `ends_val_N`: `%{escaped}` pattern

---

### IS_NULL

**NOT EXISTS subquery — matches when no active attribute row exists:**
```sql
NOT EXISTS (SELECT 1 FROM entity_attributes a_0
    WHERE a_0.entity_id = e.id AND a_0.attribute_id = :isnull_attr_1
    AND a_0.deleted = false)
```

**Parameters:**
- `isnull_attr_N`: Attribute UUID

**Matches:** Entities where no active attribute row exists (attribute absent or soft-deleted).

---

### IS_NOT_NULL

**EXISTS subquery — matches when an active attribute row exists:**
```sql
EXISTS (SELECT 1 FROM entity_attributes a_0
    WHERE a_0.entity_id = e.id AND a_0.attribute_id = :notnull_attr_1
    AND a_0.deleted = false)
```

**Parameters:**
- `notnull_attr_N`: Attribute UUID

**Matches:** Entities where an active attribute row exists.

---

## Error Handling

### Errors Thrown

None - all operators are supported and invalid inputs produce safe SQL.

### Errors Handled

|Error/Exception|Source|Recovery Strategy|
|---|---|---|
|Non-numeric value in numeric comparison|JSONB value doesn't match regex|CASE returns false (no match)|
|Empty IN/NOT_IN list|Filter validation missed edge case|Return ALWAYS_FALSE/ALWAYS_TRUE|
|Null value for operator|Caller passes null|EQUALS/NOT_EQUALS delegate to IS_NULL/IS_NOT_NULL|

---

## Gotchas & Edge Cases

> [!warning] Composite Index Required
> The EXISTS subqueries rely on the composite index `(attribute_id, type_id, value)` on `entity_attributes`:
> ```sql
> CREATE INDEX idx_entity_attributes_type_attr_value ON entity_attributes (attribute_id, type_id, value);
> ```
>
> **Without this index:** Sequential scan of `entity_attributes` for each filter condition.

> [!warning] Soft-Delete Filter in Subqueries
> Every EXISTS subquery includes `AND a.deleted = false` to respect soft-delete semantics. This is critical because the subqueries use raw table references (not Hibernate entities), so `@SQLRestriction` does not apply.

> [!warning] Numeric Comparison Regex is Strict
> The regex `^-?[0-9]+(\.[0-9]+)?$` accepts standard numeric formats but rejects:
> - Scientific notation: `1e5`, `2.5E-3`
> - Special values: `NaN`, `Infinity`
> - Leading zeros: `01`, `00.5` (actually these match - false alarm)
>
> **Impact:** Entities with non-standard numeric formats won't match numeric comparisons.
>
> **Workaround:** Store numeric values in standard format in JSONB.

> [!warning] ILIKE is Case-Insensitive
> All string matching operators (CONTAINS, STARTS_WITH, ENDS_WITH) use ILIKE (case-insensitive):
>
> - `CONTAINS "corp"` matches "Corporation", "CORP INC", "Acme Corp"
> - `STARTS_WITH "test"` matches "Testing", "TEST", "test"
>
> **For case-sensitive matching:** Use EQUALS operator (containment is case-sensitive).

> [!warning] IN/NOT_IN Convert Values to Strings
> The IN and NOT_IN operators convert all values to strings via `toString()`:
> ```kotlin
> valsParam to values.map { it?.toString() ?: "" }
> ```
>
> **Impact:**
> - Numeric IN: `IN [1, 2, 3]` becomes `IN ("1", "2", "3")`
> - JSONB values are also strings, so comparison works
> - But type coercion may produce unexpected results if JSONB stores numbers as JSON numbers
>
> **Watch out:** Ensure JSONB values are stored as strings for reliable IN/NOT_IN matching.

### Known Limitations

- No support for array operations (check if JSONB array contains value)
- No support for scientific notation in numeric comparisons
- CONTAINS/STARTS_WITH/ENDS_WITH are always case-insensitive (no LIKE variant)
- IN/NOT_IN convert values to strings (may have type coercion issues)

### Thread Safety / Concurrency

**Thread-safe** with Spring singleton:
- Singleton Spring bean shared across threads
- Stateless (no mutable fields)
- Each method call is independent
- No synchronization needed

**Concurrency model:**
- Safe for concurrent use across multiple requests
- Each generate() call is pure (no side effects except paramGen counter increment)

---

## Testing

### Unit Test Coverage

- **Location:** `src/test/kotlin/riven/core/service/entity/query/AttributeSqlGeneratorTest.kt`
- **Key scenarios covered:**
  - EQUALS generates EXISTS subquery with JSONB equality
  - EQUALS with null value delegates to IS_NULL
  - NOT_EQUALS generates EXISTS subquery with inequality
  - Numeric comparisons use regex-guarded CASE in EXISTS subquery
  - CONTAINS/STARTS_WITH/ENDS_WITH escape LIKE metacharacters in EXISTS subquery
  - IN with empty list returns ALWAYS_FALSE
  - NOT_IN with empty list returns ALWAYS_TRUE
  - IS_NULL generates NOT EXISTS subquery
  - IS_NOT_NULL generates EXISTS subquery
  - Custom entityAlias appears in correlated subquery

### How to Test Manually

1. Create AttributeSqlGenerator (no constructor dependencies)
2. Create ParameterNameGenerator
3. Call generate() with test operator and value
4. Inspect returned SqlFragment:
   - SQL contains correct operator and structure
   - Parameters map includes bound values
   - Parameter names are unique
5. Verify SQL syntax by running in PostgreSQL

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/Querying]] - Parent subdomain
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/AttributeFilterVisitor]] - Main consumer
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/RelationshipSqlGenerator]] - Sibling generator for relationships
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/SqlFragment]] - Immutable SQL container
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Querying/ParameterNameGenerator]] - Unique parameter name generation

---

## Changelog

|Date|Change|Reason|
|---|---|---|
|2026-02-08|Initial documentation|Phase 2 - Entities domain documentation (Plan 02-03)|
|2026-03-09|Rewritten for normalized `entity_attributes` table|Entity Attributes Normalization|

### 2026-03-09 — Rewritten for normalized entity_attributes table

- All SQL patterns changed from JSONB operators (`@>`, `->`, `->>`) to EXISTS/NOT EXISTS subqueries against `entity_attributes` table.
- ObjectMapper dependency removed — JSONB serialization handled by new `serializeJsonbValue()` helper.
- GIN index optimization replaced by composite B-tree index on `(attribute_id, type_id, value)`.
- Value extraction uses `(a.value #>> '{}')` instead of `(e.payload->:key->>'value')`.
- Key existence checks (`jsonb_exists()`) no longer needed — EXISTS subquery semantics handle missing attributes naturally.
- Each subquery includes `AND a.deleted = false` for soft-delete compliance.
