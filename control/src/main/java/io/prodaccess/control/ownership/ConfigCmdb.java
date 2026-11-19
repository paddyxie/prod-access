package io.prodaccess.control.ownership;

import io.prodaccess.control.config.ProdAccessProperties;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/** CMDB stand-in read from {@code prodaccess.cmdb}. */
@Component
class ConfigCmdb implements Cmdb {
    private final ProdAccessProperties.Cmdb cmdb;

    ConfigCmdb(ProdAccessProperties props) {
        this.cmdb = props.cmdb();
    }

    @Override
    public Set<String> owningTeams(String role) {
        return Set.copyOf(cmdb.roleOwners().getOrDefault(role, List.of()));
    }

    @Override
    public Set<String> rolesOwnedBy(Collection<String> teams) {
        Set<String> roles = new TreeSet<>();
        cmdb.roleOwners().forEach((role, owners) -> {
            if (owners.stream().anyMatch(teams::contains)) roles.add(role);
        });
        return roles;
    }

    @Override
    public Set<String> teamsOf(String user) {
        Set<String> teams = new TreeSet<>();
        cmdb.teams().forEach((team, members) -> {
            if (members.contains(user)) teams.add(team);
        });
        return teams;
    }
}
