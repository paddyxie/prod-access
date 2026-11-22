package io.prodaccess.control.catalog;

import io.prodaccess.control.security.CurrentUser;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalog")
class CatalogController {
    private final CatalogService catalog;
    CatalogController(CatalogService catalog) { this.catalog = catalog; }

    @GetMapping("/services") List<ServiceDefinition> services() { return catalog.services(); }
    @PostMapping("/services") ServiceDefinition createService(@RequestBody CatalogService.ServiceInput input) {
        return catalog.createService(input, CurrentUser.get());
    }
    @GetMapping("/roles") List<CmdbRole> roles() { return catalog.roles(); }
    @PostMapping("/roles") CmdbRole createRole(@RequestBody CatalogService.RoleInput input) {
        return catalog.createRole(input, CurrentUser.get());
    }
}
