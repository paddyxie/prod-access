package io.prodaccess.common.protocol;

/** How a tool reaches its target. Every tool has exactly one type. */
public enum ToolType {
    /** An API published on API Forge (the API Gateway), called as prod-access. */
    API_FORGE,
    /** An HTTP endpoint without an API Forge entry, called directly. */
    HTTP_CLIENT
}
