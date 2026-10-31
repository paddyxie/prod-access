package io.prodaccess.control.run;

/** A run, with the names of its task (null for an ad-hoc script) and Client for display. */
public record Run(String id, String taskId, String taskName, int ver, String clientId, String clientName, String paramsJson,
                  String submittedBy, RunStatus status, String approvalHash, boolean requiresApproval,
                  String approvedBy, long createdAt,
                  Long startedAt, Long finishedAt, Long heartbeatAt, String node, boolean cancelRequested,
                  String resultJson, String error) {}
