package io.prodaccess.control.ownership;

import java.util.Collection;
import java.util.Set;

/** The CMDB facts access control needs. Replace {@link ConfigCmdb} with a client of the real CMDB. */
public interface Cmdb {
    /** Teams that own a role; empty when the role is unknown. */
    Set<String> owningTeams(String role);

    /** Roles owned by any of these teams. */
    Set<String> rolesOwnedBy(Collection<String> teams);

    /** Teams a user belongs to. */
    Set<String> teamsOf(String user);
}
