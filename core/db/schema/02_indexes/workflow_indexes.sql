CREATE INDEX idx_workflow_definition_workspace_id ON workflow_definitions (workspace_id) where deleted = FALSE AND deleted_at IS NULL;
CREATE INDEX idx_workflow_definition_versions_definition ON workflow_definition_versions (workspace_id, workflow_definition_id, version_number) where deleted = FALSE AND deleted_at IS NULL;

CREATE INDEX idx_workflow_nodes_workspace_id ON workflow_nodes (workspace_id) where deleted = FALSE AND deleted_at IS NULL;
CREATE INDEX idx_workflow_nodes_type ON workflow_nodes (workspace_id, type) where deleted = FALSE AND deleted_at IS NULL;
CREATE INDEX idx_workflow_nodes_source_id ON workflow_nodes (workspace_id, source_id) WHERE source_id IS NOT NULL AND deleted = FALSE AND deleted_at IS NULL;
CREATE INDEX idx_workflow_nodes_key ON workflow_nodes (workspace_id, key) where deleted = FALSE AND deleted_at IS NULL;

CREATE INDEX idx_workflow_edges_source_id ON workflow_edges (workspace_id, source_node_id) where deleted = FALSE AND deleted_at IS NULL;
CREATE INDEX idx_workflow_edges_target_id ON workflow_edges (workspace_id, target_node_id) where deleted = FALSE AND deleted_at IS NULL;

CREATE INDEX idx_execution_queue_workspace ON execution_queue (workspace_id, status);

CREATE UNIQUE INDEX IF NOT EXISTS uq_execution_queue_pending_identity_match
    ON execution_queue (workspace_id, entity_id, job_type)
    WHERE status = 'PENDING' AND entity_id IS NOT NULL;