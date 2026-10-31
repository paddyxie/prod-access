package io.prodaccess.control.apicatalog;

import io.prodaccess.control.apicatalog.ApiCatalogService.ApiInput;
import io.prodaccess.control.security.CurrentUser;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/apis")
class ApiCatalogController {
    private final ApiCatalogService catalog;

    ApiCatalogController(ApiCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    List<ApiEntry> list(@RequestParam(required = false) String q, @RequestParam(required = false) String access) {
        return catalog.list(q, access);
    }

    @GetMapping("/{id}")
    ApiEntry get(@PathVariable String id) {
        return catalog.get(id);
    }

    @PostMapping
    ApiEntry upsert(@RequestBody ApiInput in) {
        return catalog.upsert(in, CurrentUser.get());
    }

    @DeleteMapping("/{id}")
    void delete(@PathVariable String id) {
        catalog.delete(id, CurrentUser.get());
    }
}
