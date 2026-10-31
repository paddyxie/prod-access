package io.prodaccess.common.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

/** One JSON line emitted by the runner on stdout. {@code t} selects which fields are meaningful. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RunEvent(
        /** step | log | api | hb | result | error | inspect */
        String t,
        Long ts,
        String level,
        String msg,
        String name,
        String state,
        String service,
        String operation,
        String method,
        String path,
        Integer status,
        Long ms,
        String err,
        Object value) {

    public static RunEvent of(String t) {
        return new RunEvent(t, System.currentTimeMillis(), null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public static RunEvent log(String level, String msg) {
        return new RunEvent("log", System.currentTimeMillis(), level, msg, null, null, null, null, null, null, null, null, null, null);
    }

    public static RunEvent step(String name, String state) {
        return new RunEvent("step", System.currentTimeMillis(), null, null, name, state, null, null, null, null, null, null, null, null);
    }

    public static RunEvent error(String msg) {
        return new RunEvent("error", System.currentTimeMillis(), null, msg, null, null, null, null, null, null, null, null, null, null);
    }

    public static RunEvent withValue(String t, Object value) {
        return new RunEvent(t, System.currentTimeMillis(), null, null, null, null, null, null, null, null, null, null, null, value);
    }

    public static RunEvent api(String service, String operation, String method, String path, Integer status, long ms, String err) {
        return new RunEvent("api", System.currentTimeMillis(), null, null, null, null, service, operation, method, path, status, ms, err, null);
    }
}
