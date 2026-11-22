-- H2 (PostgreSQL mode) / PostgreSQL. Times are epoch milliseconds. Definitions and execution evidence are separate.
-- CASE-based enum checks remain usable after the connection that initialized the H2 schema closes.
CREATE TABLE IF NOT EXISTS services (
  id VARCHAR(36) PRIMARY KEY,
  code VARCHAR(100) NOT NULL UNIQUE,
  name VARCHAR(200) NOT NULL,
  description TEXT,
  created_by VARCHAR(100) NOT NULL,
  created_at BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS cmdb_roles (
  id VARCHAR(36) PRIMARY KEY,
  service_id VARCHAR(36) NOT NULL REFERENCES services(id),
  code VARCHAR(100) NOT NULL UNIQUE,
  name VARCHAR(200) NOT NULL,
  system_type VARCHAR(20) NOT NULL CHECK (CASE system_type
      WHEN 'WEB' THEN TRUE WHEN 'KAFKA' THEN TRUE WHEN 'BATCH' THEN TRUE
      WHEN 'WORKER' THEN TRUE WHEN 'OTHER' THEN TRUE
      ELSE FALSE END),
  description TEXT,
  created_by VARCHAR(100) NOT NULL,
  created_at BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS tool_definitions (
  id VARCHAR(36) PRIMARY KEY,
  cmdb_role_id VARCHAR(36) NOT NULL REFERENCES cmdb_roles(id),
  name VARCHAR(100) NOT NULL,
  retired BOOLEAN NOT NULL DEFAULT FALSE,
  created_by VARCHAR(100) NOT NULL,
  created_at BIGINT NOT NULL,
  UNIQUE (cmdb_role_id, name)
);
CREATE TABLE IF NOT EXISTS tool_versions (
  id VARCHAR(36) PRIMARY KEY,
  tool_id VARCHAR(36) NOT NULL REFERENCES tool_definitions(id),
  ver INT NOT NULL CHECK (ver > 0),
  type VARCHAR(20) NOT NULL CHECK (CASE type
      WHEN 'API_FORGE' THEN TRUE WHEN 'HTTP_CLIENT' THEN TRUE
      ELSE FALSE END),
  method VARCHAR(10) NOT NULL CHECK (CASE method
      WHEN 'GET' THEN TRUE WHEN 'HEAD' THEN TRUE WHEN 'POST' THEN TRUE
      WHEN 'PUT' THEN TRUE WHEN 'PATCH' THEN TRUE WHEN 'DELETE' THEN TRUE
      ELSE FALSE END),
  path_template VARCHAR(500) NOT NULL,
  base_url VARCHAR(500),
  gateway_api_id VARCHAR(200),
  description TEXT,
  input_schema_json TEXT NOT NULL,
  output_schema_json TEXT NOT NULL,
  created_by VARCHAR(100) NOT NULL,
  created_at BIGINT NOT NULL,
  UNIQUE (tool_id, ver),
  UNIQUE (tool_id, id),
  CHECK ((type = 'API_FORGE' AND base_url IS NULL) OR (type = 'HTTP_CLIENT' AND base_url IS NOT NULL AND gateway_api_id IS NULL))
);
CREATE TABLE IF NOT EXISTS workflows (
  id VARCHAR(36) PRIMARY KEY,
  name VARCHAR(200) UNIQUE,
  created_by VARCHAR(100) NOT NULL,
  created_at BIGINT NOT NULL,
  latest_version INT NOT NULL CHECK (latest_version >= 0)
);
CREATE TABLE IF NOT EXISTS workflow_versions (
  id VARCHAR(36) PRIMARY KEY,
  workflow_id VARCHAR(36) NOT NULL REFERENCES workflows(id),
  ver INT NOT NULL CHECK (ver > 0),
  script TEXT NOT NULL,
  script_hash VARCHAR(64) NOT NULL,
  meta_json TEXT NOT NULL,
  params_json TEXT NOT NULL,
  output_schema_json TEXT NOT NULL,
  created_by VARCHAR(100) NOT NULL,
  created_at BIGINT NOT NULL,
  UNIQUE (workflow_id, ver)
);
CREATE TABLE IF NOT EXISTS workflow_version_tools (
  workflow_version_id VARCHAR(36) NOT NULL REFERENCES workflow_versions(id),
  tool_version_id VARCHAR(36) NOT NULL REFERENCES tool_versions(id),
  tool_id VARCHAR(36) NOT NULL REFERENCES tool_definitions(id),
  PRIMARY KEY (workflow_version_id, tool_id),
  FOREIGN KEY (tool_id, tool_version_id) REFERENCES tool_versions(tool_id, id)
);
CREATE TABLE IF NOT EXISTS executions (
  id VARCHAR(36) PRIMARY KEY,
  kind VARCHAR(10) NOT NULL CHECK (CASE kind
      WHEN 'SCRIPT' THEN TRUE WHEN 'CALL' THEN TRUE
      ELSE FALSE END),
  workflow_version_id VARCHAR(36) REFERENCES workflow_versions(id),
  tool_version_id VARCHAR(36) REFERENCES tool_versions(id),
  roles_json TEXT NOT NULL,
  submitted_by VARCHAR(100) NOT NULL,
  status VARCHAR(20) NOT NULL CHECK (CASE status
      WHEN 'QUEUED' THEN TRUE WHEN 'RUNNING' THEN TRUE WHEN 'SUCCEEDED' THEN TRUE
      WHEN 'FAILED' THEN TRUE WHEN 'CANCELLED' THEN TRUE WHEN 'TIMED_OUT' THEN TRUE
      WHEN 'LOST' THEN TRUE
      ELSE FALSE END),
  created_at BIGINT NOT NULL,
  started_at BIGINT,
  finished_at BIGINT,
  heartbeat_at BIGINT,
  node VARCHAR(100),
  cancel_requested BOOLEAN NOT NULL,
  error TEXT,
  CHECK ((kind = 'SCRIPT' AND workflow_version_id IS NOT NULL AND tool_version_id IS NULL)
      OR (kind = 'CALL' AND tool_version_id IS NOT NULL AND workflow_version_id IS NULL))
);
CREATE INDEX IF NOT EXISTS executions_queue ON executions(status, created_at);
CREATE INDEX IF NOT EXISTS executions_actor ON executions(submitted_by, created_at);
CREATE INDEX IF NOT EXISTS executions_workflow ON executions(workflow_version_id, created_at);
CREATE TABLE IF NOT EXISTS execution_inputs (
  execution_id VARCHAR(36) PRIMARY KEY REFERENCES executions(id),
  submitted_json TEXT NOT NULL,
  resolved_json TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS execution_outputs (
  execution_id VARCHAR(36) PRIMARY KEY REFERENCES executions(id),
  value_json TEXT NOT NULL,
  recorded_at BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS execution_actions (
  id VARCHAR(36) PRIMARY KEY,
  execution_id VARCHAR(36) NOT NULL REFERENCES executions(id),
  parent_action_id VARCHAR(36),
  name VARCHAR(500) NOT NULL,
  status VARCHAR(20) NOT NULL CHECK (CASE status
      WHEN 'RUNNING' THEN TRUE WHEN 'SUCCEEDED' THEN TRUE WHEN 'FAILED' THEN TRUE
      WHEN 'UNKNOWN' THEN TRUE
      ELSE FALSE END),
  started_at BIGINT NOT NULL,
  finished_at BIGINT,
  input_json TEXT,
  result_json TEXT,
  error TEXT,
  UNIQUE (execution_id, id),
  FOREIGN KEY (execution_id, parent_action_id) REFERENCES execution_actions(execution_id, id)
);
CREATE INDEX IF NOT EXISTS execution_actions_execution ON execution_actions(execution_id, started_at);
CREATE TABLE IF NOT EXISTS execution_events (
  id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  execution_id VARCHAR(36) NOT NULL REFERENCES executions(id),
  ts BIGINT NOT NULL,
  kind VARCHAR(20) NOT NULL,
  lvl VARCHAR(10),
  message TEXT
);
CREATE INDEX IF NOT EXISTS execution_events_execution ON execution_events(execution_id, id);
CREATE TABLE IF NOT EXISTS tool_invocations (
  id VARCHAR(36) PRIMARY KEY,
  execution_id VARCHAR(36) NOT NULL REFERENCES executions(id),
  action_id VARCHAR(36),
  tool_version_id VARCHAR(36) NOT NULL REFERENCES tool_versions(id),
  actor VARCHAR(100) NOT NULL,
  started_at BIGINT NOT NULL,
  finished_at BIGINT,
  status VARCHAR(20) NOT NULL CHECK (CASE status
      WHEN 'RUNNING' THEN TRUE WHEN 'SUCCEEDED' THEN TRUE WHEN 'FAILED' THEN TRUE
      WHEN 'UNKNOWN' THEN TRUE
      ELSE FALSE END),
  input_json TEXT NOT NULL,
  input_truncated BOOLEAN NOT NULL,
  output_json TEXT,
  output_truncated BOOLEAN NOT NULL DEFAULT FALSE,
  error TEXT,
  FOREIGN KEY (execution_id, action_id) REFERENCES execution_actions(execution_id, id)
);
CREATE INDEX IF NOT EXISTS tool_invocations_execution ON tool_invocations(execution_id, started_at);
CREATE INDEX IF NOT EXISTS tool_invocations_tool ON tool_invocations(tool_version_id, started_at);
CREATE TABLE IF NOT EXISTS api_calls (
  id VARCHAR(36) PRIMARY KEY,
  invocation_id VARCHAR(36) NOT NULL REFERENCES tool_invocations(id),
  attempt INT NOT NULL CHECK (attempt > 0),
  started_at BIGINT NOT NULL,
  finished_at BIGINT,
  method VARCHAR(10) NOT NULL,
  url TEXT NOT NULL,
  request_headers_json TEXT NOT NULL,
  request_body TEXT,
  request_truncated BOOLEAN NOT NULL,
  response_status INT,
  response_headers_json TEXT,
  response_body TEXT,
  response_truncated BOOLEAN NOT NULL DEFAULT FALSE,
  status VARCHAR(20) NOT NULL CHECK (CASE status
      WHEN 'RUNNING' THEN TRUE WHEN 'SUCCEEDED' THEN TRUE WHEN 'HTTP_ERROR' THEN TRUE
      WHEN 'TRANSPORT_ERROR' THEN TRUE WHEN 'UNKNOWN' THEN TRUE
      ELSE FALSE END),
  error TEXT,
  UNIQUE (invocation_id, attempt)
);
CREATE INDEX IF NOT EXISTS api_calls_invocation ON api_calls(invocation_id, attempt);
CREATE TABLE IF NOT EXISTS audit_log (
  id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  ts BIGINT NOT NULL,
  actor VARCHAR(100) NOT NULL,
  action VARCHAR(50) NOT NULL,
  target VARCHAR(200),
  detail TEXT
);
CREATE TABLE IF NOT EXISTS settings (
  k VARCHAR(50) PRIMARY KEY,
  v VARCHAR(500) NOT NULL
);
-- Legacy records are retained as original evidence. Missing historical requests/contracts are never fabricated.
CREATE TABLE IF NOT EXISTS legacy_archive (
  source_table VARCHAR(50) NOT NULL,
  source_id VARCHAR(100) NOT NULL,
  execution_id VARCHAR(36),
  source_json TEXT NOT NULL,
  archived_at BIGINT NOT NULL,
  PRIMARY KEY (source_table, source_id)
);
CREATE INDEX IF NOT EXISTS legacy_archive_execution ON legacy_archive(execution_id, source_table);
