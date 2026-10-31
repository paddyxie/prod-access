package io.prodaccess.control.apicatalog;

/** One entry of the hand-maintained API list: service A's operation B, and whether it reads or writes. */
public record ApiEntry(String id, String service, String operation, String method, String pathTemplate, String access,
                       String updatedBy, long updatedAt) {

    public String key() {
        return service + "." + operation;
    }
}
