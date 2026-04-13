-- =====================================================
-- CONNECTOR MAPPINGS — FK CONSTRAINTS
-- =====================================================
-- Phase 3 plan 03-01.
--
-- Both mapping tables FK to data_connector_connections(id) with
-- ON DELETE CASCADE. Rationale: mappings are connection-internal state
-- with no historical value once the parent connection is hard-purged.
-- Note: soft-delete on the connection does NOT trigger cascade —
-- @SQLRestriction operates at the JPA query layer, not the SQL layer,
-- so a soft-deleted parent remains physically present in the table.

ALTER TABLE connector_table_mappings
    ADD CONSTRAINT fk_connector_table_mappings_connection
    FOREIGN KEY (connection_id)
    REFERENCES data_connector_connections(id)
    ON DELETE CASCADE;

ALTER TABLE connector_field_mappings
    ADD CONSTRAINT fk_connector_field_mappings_connection
    FOREIGN KEY (connection_id)
    REFERENCES data_connector_connections(id)
    ON DELETE CASCADE;
