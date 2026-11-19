package io.prodaccess.control.security;

import io.prodaccess.control.ownership.OwnershipService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeController {
    private final OwnershipService ownership;

    MeController(OwnershipService ownership) {
        this.ownership = ownership;
    }

    @GetMapping("/api/me")
    Map<String, Object> me() {
        String user = CurrentUser.get();
        return Map.of("user", user, "teams", ownership.teamsOf(user), "ownedRoles", ownership.ownedRoles(user));
    }
}
