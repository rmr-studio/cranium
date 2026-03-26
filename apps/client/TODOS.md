# TODOS

Deferred work tracked from plan reviews and implementation decisions.

## Notes — Deferred Items (Updated 2026-03-19 — CEO Review)

### @Mention Autocomplete Picker

**Priority:** P2 | **Effort:** L (human) / M (CC) | **Depends on:** Notes feature shipped

Build the autocomplete UI for entity cross-references within notes. When typing `@` in the note editor, show a dropdown that searches entities across the workspace. Selecting inserts an `EntityMention` inline content type (already defined in `components/ui/block-editor/entity-mention.tsx`) that renders as a clickable chip.

**Why:** Cross-referencing entities from notes creates a soft knowledge graph. Meeting notes about a client can reference their entity record. This is the bridge between unstructured notes and structured entity data.

**Backend extension:** Add a `note_mentions` join table (`note_id`, `entity_id`) populated on note save by scanning for EntityMention inline content. Enables indexed queries: "find all notes mentioning entity X."

**How to apply:** Build a BlockNote suggestion menu plugin that intercepts `@` keystrokes, an entity search popover (reuse from `EntityRelationshipPicker`), and wire mention data into the join table on save.

---

### Note Pinning

**Priority:** P3 | **Effort:** S (human) / S (CC) | **Depends on:** Notes feature shipped

Pin important notes to the top of the timeline. Add a `pinned` boolean column to the notes table. Pinned notes render above the chronological timeline with a pin indicator.

---

### Recent Notes Dashboard Widget

**Priority:** P3 | **Effort:** M (human) / S (CC) | **Depends on:** Notes feature shipped

Dashboard component showing recently updated notes across all entities in the workspace. Query: `SELECT * FROM notes WHERE workspace_id = ? ORDER BY updated_at DESC LIMIT 10`.

---

### Note Templates

**Priority:** P3 | **Effort:** M (human) / S (CC) | **Depends on:** Notes feature shipped

Pre-filled BlockNote content for common note types (Meeting Notes, Action Items, Follow-up). The "New Note" button becomes a split button: click for blank, dropdown for templates. Start with hardcoded templates, add user-created template management later.

---

### Note Hover Tooltip Preview

**Priority:** P3 | **Effort:** S (human) / S (CC) | **Depends on:** Notes feature shipped

When hovering the notes badge in the data table, show a tooltip with the titles of the 3 most recent notes. Gives instant context without opening the drawer.

---

### Quick-Add Note from Anywhere

**Priority:** P3 | **Effort:** M (human) / S (CC) | **Depends on:** Notes feature shipped

Global keyboard shortcut to create a note on any entity without navigating to its row. Entity picker then note editor. Useful for power users.

---

### Mobile Notes Access

**Priority:** P2 | **Effort:** M (human) / S (CC) | **Depends on:** Mobile navigation component exists

Add Notes entry to mobile navigation menu. Descoped from the Workspace Notes Hub PR because no mobile nav component exists yet — building one is an orthogonal concern. Once a mobile nav is built (hamburger menu or bottom tabs), add Notes as an entry pointing to `/dashboard/workspace/[workspaceId]/notes`.

**Why:** Notes hub currently ships desktop/tablet only. Mobile users have no way to reach the workspace notes list without typing the URL.

**How to apply:** When the mobile nav component is created, add a Notes entry with `StickyNote` icon. The notes list page should already handle responsive layout (card view on mobile per the design spec).

---

### DataTable Row Grouping Pattern

**Priority:** P3 | **Effort:** M (human) / S (CC) | **Depends on:** Workspace Notes Hub shipped

Extract the date-grouped visual header pattern from the notes list DataTable into a reusable grouping utility. The notes list introduces bespoke group header rows (e.g., "Created today (2)", "Yesterday (1)") rendered between flat DataTable rows. This same pattern will be needed for entity cluster visualization.

**Why:** Avoids duplicating the grouping logic when entity clusters need the same visual treatment. The notes list is the first consumer; entity clusters will be the second.

**How to apply:** Extract the group-by utility function and the group header row renderer from the notes list page into `components/ui/data-table/` as a composable pattern. Keep TanStack Table's `getGroupedRowModel()` out of the shared DataTable — the grouping should be a pre-processing step that inserts visual separator rows.
