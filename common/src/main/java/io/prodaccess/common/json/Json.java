package io.prodaccess.common.json;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

public final class Json {
    public static final JsonMapper MAPPER = JsonMapper.builder().build();
    /** Map keys sorted, so equal content always serialises to equal text (used for hashing). */
    private static final JsonMapper CANONICAL = MAPPER.rebuild().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();

    private Json() {}

    public static String write(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (JacksonException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static String writeCanonical(Object o) {
        try {
            return CANONICAL.writeValueAsString(o);
        } catch (JacksonException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static <T> T read(String s, Class<T> type) {
        try {
            return MAPPER.readValue(s, type);
        } catch (JacksonException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static Object readTree(String s) {
        return read(s, Object.class);
    }
}
