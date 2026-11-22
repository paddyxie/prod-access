package io.prodaccess.control.run;

public record ExecutionAction(String id, String executionId, String parentActionId, String name, String status,
                              long startedAt, Long finishedAt, String inputJson, String resultJson, String error) {}
