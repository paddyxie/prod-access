package io.prodaccess.control.ownership;

import io.prodaccess.control.web.ApiException;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Who may call which tools: a user may call the tools of a CMDB role when one of their teams owns that role.
 * Checked when a run is submitted and again just before it executes, since ownership can change in between.
 */
@Service
public class OwnershipService {
    private final Cmdb cmdb;

    OwnershipService(Cmdb cmdb) {
        this.cmdb = cmdb;
    }

    public Set<String> teamsOf(String user) {
        return cmdb.teamsOf(user);
    }

    public Set<String> ownedRoles(String user) {
        return cmdb.rolesOwnedBy(cmdb.teamsOf(user));
    }

    public boolean owns(String user, String role) {
        Set<String> owners = cmdb.owningTeams(role);
        return cmdb.teamsOf(user).stream().anyMatch(owners::contains);
    }

    /** The roles among {@code roles} that none of the user's teams owns; empty when the user may proceed. */
    public Set<String> notOwned(String user, Collection<String> roles) {
        Set<String> missing = new TreeSet<>();
        for (String role : roles) if (!owns(user, role)) missing.add(role);
        return missing;
    }

    public void requireOwner(String user, Collection<String> roles) {
        Set<String> missing = notOwned(user, roles);
        if (!missing.isEmpty()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "none of your teams owns " + (missing.size() == 1 ? "role " : "roles ")
                    + String.join(", ", missing));
        }
    }
}
