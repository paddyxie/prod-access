package io.prodaccess.control.security;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeController {
    @GetMapping("/api/me")
    Map<String, Object> me() {
        return Map.of("user", CurrentUser.get());
    }
}
