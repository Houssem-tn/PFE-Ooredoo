package com.pfe.Security;

import com.pfe.MainPackage.Entity.AccountStatus;
import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository repo;

    public UserDetailsServiceImpl(UserRepository repo) {
        this.repo = repo;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AppUser u = repo.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        boolean approved = u.getStatus() == AccountStatus.APPROVED;
        boolean enabled = u.isEnabled() && approved;

        List<SimpleGrantedAuthority> authorities =
                (approved && u.getRole() != null)
                        ? List.of(new SimpleGrantedAuthority(u.getRole().name()))
                        : Collections.emptyList();

        return new org.springframework.security.core.userdetails.User(
                u.getEmail(),
                u.getPassword(),
                enabled,
                true,
                true,
                true,
                authorities
        );
    }
}