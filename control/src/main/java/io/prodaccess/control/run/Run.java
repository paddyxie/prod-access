package io.prodaccess.control.run;

/** Execution instance, joined with its immutable definition and input/output evidence. */
public record Run(String id, RunKind kind, String workflowVersionId, String toolVersionId,
                  String workflowId, String workflowName, Integer ver, String toolKey, String rolesJson,
                  String paramsJson, String submittedBy, RunStatus status, long createdAt, Long startedAt, Long finishedAt,
                  Long heartbeatAt, String node, boolean cancelRequested, String resultJson, String error) {}
