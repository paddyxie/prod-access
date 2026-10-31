package io.prodaccess.control.run;

/** Audit record of one API Gateway call made by a run. */
public record ApiCallRecord(long id, String runId, long ts, String service, String operation, String method,
                           String path, Integer status, Long durationMs, String error) {}
