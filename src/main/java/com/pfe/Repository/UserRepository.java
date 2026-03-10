package com.pfe.Repository;

import com.pfe.Entity.AccountStatus;
import com.pfe.Entity.AppUser;
import com.pfe.Entity.UserRole;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<AppUser, String> {
    Optional<AppUser> findByEmail(String email);
    boolean existsByEmail(String email);
    List<AppUser> findByStatus(AccountStatus status);

    List<AppUser> findByStatusAndRoleRequested(AccountStatus status, UserRole roleRequested);
}