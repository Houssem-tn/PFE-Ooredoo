package com.pfe.Service;


import com.pfe.Dto.*;
import com.pfe.Entity.AppUser;
import com.pfe.exception.NotFoundException;
import com.pfe.mapper.UserMapper;
import com.pfe.Repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserRepository repo;
    private final UserMapper mapper;
    private final PasswordEncoder encoder;

    public UserService(UserRepository repo, UserMapper mapper, PasswordEncoder encoder) {
        this.repo = repo;
        this.mapper = mapper;
        this.encoder = encoder;
    }

    public List<UserDTO> findAll() {
        return repo.findAll().stream().map(mapper::toDto).toList();
    }

    public UserDTO findById(String id) {
        return mapper.toDto(getEntity(id));
    }

    public UserDTO create(UserCreateRequest req) {
        String email = req.email().toLowerCase();
        if (repo.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already used");
        }

        AppUser u = AppUser.builder()
                .fullName(req.fullName())
                .email(email)
                .password(encoder.encode(req.password()))
                .role(req.role())
                .enabled(req.enabled() == null || req.enabled())
                .build();

        return mapper.toDto(repo.save(u));
    }

    public UserDTO update(String id, UserUpdateRequest req) {
        AppUser u = getEntity(id);

        if (req.fullName() != null) u.setFullName(req.fullName());
        if (req.email() != null) {
            String newEmail = req.email().toLowerCase();
            if (!newEmail.equals(u.getEmail()) && repo.existsByEmail(newEmail)) {
                throw new IllegalArgumentException("Email already used");
            }
            u.setEmail(newEmail);
        }
        if (req.role() != null) u.setRole(req.role());
        if (req.enabled() != null) u.setEnabled(req.enabled());
        if (req.password() != null && !req.password().isBlank()) u.setPassword(encoder.encode(req.password()));

        return mapper.toDto(repo.save(u));
    }

    public void delete(String id) {
        AppUser u = getEntity(id);
        repo.delete(u);
    }

    private AppUser getEntity(String id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("User not found: " + id));
    }
}
