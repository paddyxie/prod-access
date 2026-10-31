package io.prodaccess.control.client;

/** An app identity whose credentials a run uses at the API Gateway. */
public record Client(String id, String name, String description, String environment, String credentialRef,
                     boolean enabled, String createdBy, long createdAt) {}
