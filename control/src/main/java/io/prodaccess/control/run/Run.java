package io.prodaccess.control.run;

/**
 * A run, with the name of its task (null for an ad-hoc script or a direct call). {@code rolesJson} lists the
 * provider roles whose ownership was checked; {@code paramsJson} holds the task parameters, or the request of a call.
 */
public record Run(String id, RunKind kind, String taskId, String taskName, Integer ver, String toolKey, String rolesJson,
                  String paramsJson, String submittedBy, RunStatus status, long createdAt, Long startedAt, Long finishedAt,
                  Long heartbeatAt, String node, boolean cancelRequested, String resultJson, String error) {}
