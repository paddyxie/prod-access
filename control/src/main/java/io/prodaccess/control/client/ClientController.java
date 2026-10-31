package io.prodaccess.control.client;

import io.prodaccess.control.client.ClientService.ClientInput;
import io.prodaccess.control.security.CurrentUser;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clients")
class ClientController {
    private final ClientService clients;

    ClientController(ClientService clients) {
        this.clients = clients;
    }

    @GetMapping
    List<Client> list(@RequestParam(required = false) String q) {
        return clients.list(q);
    }

    @GetMapping("/{id}")
    Client get(@PathVariable String id) {
        return clients.get(id);
    }

    @PostMapping
    Client create(@RequestBody ClientInput in) {
        return clients.create(in, CurrentUser.get());
    }

    @PutMapping("/{id}")
    Client update(@PathVariable String id, @RequestBody ClientInput in) {
        return clients.update(id, in, CurrentUser.get());
    }
}
