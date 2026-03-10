package com.pfe.Repository;

import com.pfe.Entity.AppUser;
import com.pfe.Entity.UserRole;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends MongoRepository<AppUser, String> {
    Optional<AppUser> findByEmail(String email);
    Optional<AppUser> findByfullName(String username);
    List<AppUser> findByRole(UserRole role);

}
