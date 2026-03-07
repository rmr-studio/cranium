---
name: db:sql
description: "Diff db/schema/ changes between main and current branch, generate migration scripts, and execute them after user approval"
allowed-tools:
  - Read
  - Write
  - Edit
  - Glob
  - Grep
  - Bash
  - AskUserQuestion
  - mcp__plugin_supabase_supabase__execute_sql
  - mcp__plugin_supabase_supabase__list_projects
  - mcp__plugin_supabase_supabase__get_project
---

<objective>
Diff all `db/schema/` SQL file changes between `main` and the current branch, analyze the structural differences, generate safe migration scripts that will bring the target database in sync, and execute them after user approval.

This command produces **migration scripts only** — it does NOT regenerate or re-run the full schema. It generates targeted `ALTER TABLE`, `CREATE INDEX`, `DROP INDEX`, `CREATE OR REPLACE FUNCTION`, etc. statements that represent the delta between main and the current branch.
</objective>

<important_rules>
- NEVER generate destructive `DROP TABLE` or `DROP COLUMN` statements without explicit user confirmation in the plan phase.
- NEVER include `DROP` statements for columns/tables that were merely modified — use `ALTER` instead.
- Always use `IF NOT EXISTS` / `IF EXISTS` guards where appropriate to make scripts idempotent.
- Always wrap multi-statement migrations in a transaction (`BEGIN; ... COMMIT;`) unless the statements cannot run inside a transaction (e.g., `CREATE INDEX CONCURRENTLY`).
- Respect the schema directory execution order documented in `db/schema/README.md` when ordering migration statements.
- For `CREATE OR REPLACE FUNCTION` and `CREATE OR REPLACE VIEW`, the full replacement body is safe — include the complete new definition.
- For index changes: generate `DROP INDEX IF EXISTS` + `CREATE INDEX` pairs. Use `CONCURRENTLY` variants only if the user requests minimal-downtime migration.
</important_rules>

<process>

## Step 1: Branch Validation

```bash
# Verify we're not on main
git branch --show-current

# Verify there are schema changes
git diff main...HEAD --name-only -- 'db/schema/'
```

If on `main` or no schema files changed, inform the user and stop.

## Step 2: Gather Schema Diffs

Run these commands to understand the full scope of changes:

```bash
# Summary of changed schema files
git diff main...HEAD --stat -- 'db/schema/'

# Full diff of all schema changes
git diff main...HEAD -- 'db/schema/'
```

Also read the current (branch) version of each changed file to understand the full context — diffs alone may not show enough surrounding context for correct migration generation.

For each changed file, read:
- The **main branch version**: `git show main:{filepath}`
- The **current branch version**: Read the file directly

## Step 3: Classify Changes

Categorize each changed file by its schema directory and change type:

| File | Directory | Change Type | Description |
|------|-----------|-------------|-------------|
| `entities.sql` | `01_tables/` | Modified | Added `foo_column` to `entity_types` |
| `entity_indexes.sql` | `02_indexes/` | Modified | Added index on `entity_relationships` |

Group changes into migration categories:

1. **Table modifications** (`01_tables/`) — `ALTER TABLE` statements (add/drop/modify columns, constraints)
2. **Index changes** (`02_indexes/`) — `CREATE INDEX` / `DROP INDEX`
3. **Function changes** (`03_functions/`) — `CREATE OR REPLACE FUNCTION`
4. **Constraint changes** (`04_constraints/`) — `ALTER TABLE ADD/DROP CONSTRAINT`
5. **RLS changes** (`05_rls/`) — `CREATE POLICY` / `ALTER POLICY` / `DROP POLICY`
6. **Type changes** (`06_types/`) — `ALTER TYPE` / `CREATE TYPE`
7. **View changes** (`07_views/`) — `CREATE OR REPLACE VIEW`
8. **Trigger changes** (`08_triggers/`) — `CREATE OR REPLACE TRIGGER` / `DROP TRIGGER`
9. **Grant changes** (`09_grants/`) — `GRANT` / `REVOKE`

## Step 4: Present Migration Plan & Ask Execution Target

Present the migration plan inline (do NOT use `EnterPlanMode`/`ExitPlanMode`) and ask for approval and execution target in the same message:

### Migration Plan

**Branch:** `{branch_name}`
**Schema files changed:** `{count}`

For each migration statement, show:

```
1. [CATEGORY] Description
   Table/Object: {name}
   Operation: {ALTER TABLE ADD COLUMN / CREATE INDEX / etc.}
   Reversible: {yes/no}
   Risk: {low/medium/high}

   SQL:
   ```sql
   -- The actual migration SQL
   ```
```

Order the statements following the schema execution order:
1. Extensions
2. Types (must exist before tables reference them)
3. Tables (column additions/modifications)
4. Indexes
5. Functions
6. Constraints
7. RLS policies
8. Triggers
9. Grants

Flag any **destructive operations** (drops, column removals, type changes that may lose data) with a clear warning.

At the end of the plan, ask the user to approve and choose an execution target in one question:

> If this plan looks good, where should I execute these migration scripts?
> 1. **Supabase** — Execute via the Supabase MCP against a project
> 2. **Local** — Output the SQL to a file for manual execution
> 3. **Custom** — You tell me how to run it

Wait for the user's response before proceeding to Step 5.

## Step 5: Execute Migration

### Option 1: Supabase MCP

1. Use `mcp__plugin_supabase_supabase__list_projects` to show available projects
2. Ask the user to confirm which project to target
3. Execute each migration statement sequentially via `mcp__plugin_supabase_supabase__execute_sql`
4. Report success/failure for each statement
5. If any statement fails, STOP and report the error — do not continue with remaining statements

### Option 2: Local File

1. Write the complete migration script to `db/migrations/{YYYY-MM-DD}_{branch-name}.sql`
2. Include a header comment with branch name, date, and description of changes
3. Inform the user of the file location

### Option 3: Custom

Follow the user's instructions for execution.

## Step 6: Verification Summary

After execution, present:

### Migration Results

| # | Operation | Status | Notes |
|---|-----------|--------|-------|
| 1 | `ALTER TABLE entity_types ADD COLUMN ...` | Success | |
| 2 | `CREATE INDEX ...` | Success | |

**Statements executed:** {n}/{total}
**Errors:** {count}

If there were errors, provide the error details and suggest remediation.

</process>

<edge_cases>

### Renamed files
If a schema file was renamed (git shows R status), compare content between old and new — the migration is based on SQL content differences, not file names.

### New schema files
If an entirely new file was added (e.g., a new domain's tables), the migration script should include the full file content since it represents net-new schema objects.

### Deleted schema files
If a schema file was deleted, flag this prominently — it likely means objects should be dropped. Require explicit user confirmation before generating any DROP statements.

### JSONB column changes
For JSONB columns, migrations typically don't need structural changes (the schema is application-level). Note this in the plan if detected.

### Enum/type changes
PostgreSQL enums can only have values added (not removed or renamed) without dropping and recreating. Flag any enum modifications that require special handling.

</edge_cases>
