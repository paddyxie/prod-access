package io.prodaccess.common.protocol;

public enum Access {
    READ, WRITE;

    /** Default classification by HTTP method; users may override per API. */
    public static Access defaultFor(String httpMethod) {
        return switch (httpMethod.toUpperCase()) {
            case "GET", "HEAD" -> READ;
            default -> WRITE;
        };
    }
}
