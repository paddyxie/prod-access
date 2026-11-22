package io.prodaccess.control.run;

import io.prodaccess.common.protocol.ToolType;

public record ToolCallRecord(String id, String executionId, String actionId, String toolVersionId, String service,
                             String role, String tool, int ver, ToolType type, String actor, long startedAt, Long finishedAt,
                             String status, String inputJson, boolean inputTruncated, String outputJson,
                             boolean outputTruncated, String error) {}
