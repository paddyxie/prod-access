package io.prodaccess.control.task;

/**
 * A task with the facts of its latest version (which Client it runs as, whether it writes under the current API
 * list). Unnamed = ad-hoc script.
 */
public record Task(String id, String name, String createdBy, long createdAt, int latestVersion, String clientId,
                   String clientName, boolean hasWrite, String updatedBy, long updatedAt) {}
