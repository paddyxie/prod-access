package io.prodaccess.control.run;

public record ApiCallRecord(String id, String invocationId, int attempt, long startedAt, Long finishedAt, String method,
                            String url, String requestHeadersJson, String requestBody, boolean requestTruncated,
                            Integer responseStatus, String responseHeadersJson, String responseBody, boolean responseTruncated,
                            String status, String error) {}
