package io.prodaccess.control.catalog;

public record CmdbRole(String id, String serviceId, String serviceCode, String code, String name, String systemType,
                       String description, String createdBy, long createdAt) {}
