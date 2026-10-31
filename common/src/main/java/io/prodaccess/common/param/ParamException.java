package io.prodaccess.common.param;

import java.util.Map;

public class ParamException extends RuntimeException {
    private final Map<String, String> errors;

    public ParamException(Map<String, String> errors) {
        super("invalid params: " + errors);
        this.errors = errors;
    }

    public Map<String, String> errors() {
        return errors;
    }
}
