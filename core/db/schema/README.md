# Riven Database Schema

This directory contains the database schema for Riven, organized into logical components for better maintainability and clarity.

## Directory Structure

```
db/schema/
├── 00_extensions/         # PostgreSQL extensions (uuid-ossp, etc.)
├── 01_tables/            # Table definitions
├── 02_indexes/           # Index definitions
├── 03_functions/         # PostgreSQL functions
├── 04_constraints/       # Table constraints (excluding FK in table definitions)
├── 05_rls/              # Row Level Security policies
├── 06_types/            # Custom types and enums
├── 07_views/            # Database views
├── 08_triggers/         # Trigger definitions
├── 09_grants/           # Permission grants
└── README.md            # This file
```

## Execution Order

When setting up a new database, execute the SQL files in the following order:

### 1. Extensions (00_extensions/)
```bash
psql -d your_database -f 00_extensions/extensions.sql
```

### 2. Tables (01_tables/)
Execute in this order to respect foreign key dependencies:
```bash
psql -d your_database -f 01_tables/workspace.sql
psql -d your_database -f 01_tables/user.sql
psql -d your_database -f 01_tables/activity.sql
psql -d your_database -f 01_tables/blocks.sql
psql -d your_database -f 01_tables/entities.sql
psql -d your_database -f 01_tables/integrations.sql
psql -d your_database -f 01_tables/catalog.sql
psql -d your_database -f 01_tables/integrations_stale_migration.sql
```

**Dependencies:**
- `user.sql` depends on `workspace.sql` (FK: users.default_workspace_id)
- `blocks.sql` depends on `workspace.sql` and `user.sql`
- `entities.sql` depends on `workspace.sql`, `user.sql`, and `blocks.sql` (block_tree_layouts references entities)
- `integrations.sql` has no table dependencies
- `catalog.sql` has no table dependencies (manifest_catalog is self-contained)
- `integrations_stale_migration.sql` depends on `integrations.sql`

### 3. Indexes (02_indexes/)
```bash
psql -d your_database -f 02_indexes/workspace_indexes.sql
psql -d your_database -f 02_indexes/user_indexes.sql
psql -d your_database -f 02_indexes/activity_indexes.sql
psql -d your_database -f 02_indexes/block_indexes.sql
psql -d your_database -f 02_indexes/entity_indexes.sql
psql -d your_database -f 02_indexes/integration_indexes.sql
psql -d your_database -f 02_indexes/catalog_indexes.sql
```

### 4. Functions (03_functions/)
```bash
psql -d your_database -f 03_functions/workspace_functions.sql
psql -d your_database -f 03_functions/user_functions.sql
psql -d your_database -f 03_functions/entity_functions.sql
psql -d your_database -f 03_functions/auth_functions.sql
```

### 5. Constraints (04_constraints/)
```bash
psql -d your_database -f 04_constraints/workspace_constraints.sql
psql -d your_database -f 04_constraints/block_constraints.sql
```

### 6. Row Level Security (05_rls/)
```bash
psql -d your_database -f 05_rls/workspace_rls.sql
psql -d your_database -f 05_rls/block_rls.sql
psql -d your_database -f 05_rls/entity_rls.sql
```

### 7. Triggers (08_triggers/)
Must be executed after functions are created:
```bash
psql -d your_database -f 08_triggers/workspace_triggers.sql
psql -d your_database -f 08_triggers/user_triggers.sql
psql -d your_database -f 08_triggers/entity_triggers.sql
```

### 8. Grants (09_grants/)
```bash
psql -d your_database -f 09_grants/auth_grants.sql
```

## Quick Setup Script

You can execute all files in the correct order using this bash script:

```bash
#!/bin/bash
DB_NAME="your_database"
SCHEMA_DIR="./db/schema"

# Arrays of files in execution order
EXTENSIONS=("extensions.sql")
TABLES=("workspace.sql" "user.sql" "activity.sql" "blocks.sql" "entities.sql" "integrations.sql" "catalog.sql" "integrations_stale_migration.sql")
INDEXES=("workspace_indexes.sql" "user_indexes.sql" "activity_indexes.sql" "block_indexes.sql" "entity_indexes.sql" "integration_indexes.sql" "catalog_indexes.sql")
FUNCTIONS=("workspace_functions.sql" "user_functions.sql" "entity_functions.sql" "auth_functions.sql")
CONSTRAINTS=("workspace_constraints.sql" "block_constraints.sql")
RLS=("workspace_rls.sql" "block_rls.sql" "entity_rls.sql")
TRIGGERS=("workspace_triggers.sql" "user_triggers.sql" "entity_triggers.sql")
GRANTS=("auth_grants.sql")

echo "Setting up Riven database schema..."

# Execute extensions
echo "1. Creating extensions..."
for file in "${EXTENSIONS[@]}"; do
  psql -d $DB_NAME -f "$SCHEMA_DIR/00_extensions/$file"
done

# Execute tables
echo "2. Creating tables..."
for file in "${TABLES[@]}"; do
  psql -d $DB_NAME -f "$SCHEMA_DIR/01_tables/$file"
done

# Execute indexes
echo "3. Creating indexes..."
for file in "${INDEXES[@]}"; do
  psql -d $DB_NAME -f "$SCHEMA_DIR/02_indexes/$file"
done

# Execute functions
echo "4. Creating functions..."
for file in "${FUNCTIONS[@]}"; do
  psql -d $DB_NAME -f "$SCHEMA_DIR/03_functions/$file"
done

# Execute constraints
echo "5. Creating constraints..."
for file in "${CONSTRAINTS[@]}"; do
  psql -d $DB_NAME -f "$SCHEMA_DIR/04_constraints/$file"
done

# Execute RLS policies
echo "6. Creating RLS policies..."
for file in "${RLS[@]}"; do
  psql -d $DB_NAME -f "$SCHEMA_DIR/05_rls/$file"
done

# Execute triggers
echo "7. Creating triggers..."
for file in "${TRIGGERS[@]}"; do
  psql -d $DB_NAME -f "$SCHEMA_DIR/08_triggers/$file"
done

# Execute grants
echo "8. Setting up grants..."
for file in "${GRANTS[@]}"; do
  psql -d $DB_NAME -f "$SCHEMA_DIR/09_grants/$file"
done

echo "Database schema setup complete!"
```

## Schema Components

### Workspaces
- **Tables**: workspaces, workspace_members, workspace_invites
- **Functions**: update_org_member_count()
- **Triggers**: trg_update_workspace_member_count
- **RLS**: Multi-tenant access control

### Users
- **Tables**: users
- **Functions**: handle_new_user(), handle_phone_confirmation()
- **Triggers**: on_auth_user_created
- **Integration**: Supabase Auth

### Blocks
- **Tables**: block_types, blocks, block_children, block_tree_layouts
- **Constraints**: System vs workspace blocks, nesting rules
- **RLS**: Workspace-scoped access
- **Pattern**: Immutable versioning for block_types

### Entities
- **Tables**: entity_types, entities, entities_unique_values, relationship_definitions, relationship_target_rules, entity_relationships
- **Functions**: sync_entity_identifier_key(), update_entity_type_count()
- **Triggers**: trg_sync_entity_identifier_key, trg_update_entity_type_count
- **RLS**: Workspace-scoped access
- **Pattern**: Mutable schema for entity_types

### Integrations
- **Tables**: integration_definitions, integration_connections
- **Indexes**: Category, stale status, connection status
- **Pattern**: Global catalog (no workspace scoping) for definitions; workspace-scoped connections

### Manifest Catalog
- **Tables**: manifest_catalog, catalog_entity_types, catalog_relationships, catalog_relationship_target_rules, catalog_field_mappings, catalog_semantic_metadata
- **Indexes**: Manifest type, stale status, entity type lookups
- **Pattern**: Global catalog loaded from classpath manifests on startup. Stale flag for reconciliation.
- **Key relationships**: manifest_catalog -> catalog_entity_types/catalog_relationships/catalog_field_mappings (via manifest_id), catalog_relationships -> catalog_relationship_target_rules (via id), catalog_entity_types -> catalog_semantic_metadata (via id)

### Activity Logs
- **Tables**: activity_logs
- **Purpose**: Audit trail for all operations

### Authentication
- **Functions**: custom_access_token_hook()
- **Grants**: Supabase Auth admin permissions
- **Purpose**: JWT token enrichment with workspace roles

## Important Notes

### Foreign Key Dependencies
The schema has several circular dependencies that require careful ordering:
- `users` can reference `workspaces` (default_workspace_id)
- `workspace_members` references both `users` and `workspaces`
- `block_tree_layouts` references `entities`

### Row Level Security (RLS)
All main tables have RLS enabled with policies that enforce workspace-level multi-tenancy:
- Users can only access data from workspaces they belong to
- System resources (NULL workspace_id) are globally accessible
- Auth admin has special permissions for JWT token generation

### Mutable vs Immutable Patterns
- **Entity Types**: Mutable (single row per type, updated in-place)
- **Block Types**: Immutable (copy-on-write, new version creates new row)

### Triggers and Denormalization
Several triggers maintain denormalized counts:
- `workspaces.member_count` updated by workspace_members changes
- `entity_types.count` updated by entities changes
- `entities.identifier_key` synced from entity_types

## Maintenance

When making schema changes:
1. Update the appropriate file in the correct directory
2. Consider dependencies (functions before triggers, tables before constraints)
3. Test in a development environment first
4. Update this README if adding new files or changing execution order
5. Document any breaking changes

## Migration from Monolithic Schema

The original `schema.sql` file has been decomposed into this modular structure. If you need to compare or migrate from the old schema:
1. The original file is preserved at `../../schema.sql`
2. All functionality has been migrated to the new structure
3. No schema changes were made, only reorganization

## Seed Data

For seed data (initial system block types, entity types, etc.), see:
- `../seed/` directory (if applicable)
