package io.prodaccess.control.run;

import io.prodaccess.common.protocol.ToolType;

/** Audit record of one tool call made by a run. */
public record ToolCallRecord(long id, String runId, long ts, String role, String tool, ToolType type, String method,
                             String path, Integer status, Long durationMs, String error) {}
