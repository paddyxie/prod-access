package io.prodaccess.common.protocol;

/** One entry of the hand-maintained API list: "service A's operation B". */
public record ApiDef(String service, String operation, String method, String pathTemplate, Access access) {
    public String key() {
        return service + "." + operation;
    }
}
