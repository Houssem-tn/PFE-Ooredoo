package com.pfe.Controller;


import com.pfe.Dto.*;
import com.pfe.Service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    public List<UserDTO> all() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public UserDTO one(@PathVariable String id) {
        return service.findById(id);
    }

    @PostMapping
    public UserDTO create(@Valid @RequestBody UserCreateRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public UserDTO update(@PathVariable String id, @Valid @RequestBody UserUpdateRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        service.delete(id);
    }
}
