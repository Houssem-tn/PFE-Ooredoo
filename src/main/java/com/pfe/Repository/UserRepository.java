package com.pfe.Repository;

import com.pfe.Entity.AccountStatus;
import com.pfe.Entity.AppUser;
import com.pfe.Entity.UserRole;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<AppUser, String> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    List<AppUser> findByStatus(AccountStatus status);

    List<AppUser> findByStatusAndRoleRequested(AccountStatus status, UserRole roleRequested);

    List<AppUser> findByStatusAndRoleRequestedAndAssignedSttId(
            AccountStatus status,
            UserRole roleRequested,
            String assignedSttId
    );

    List<AppUser> findByStatusAndRoleRequestedAndAssignedZmId(
            AccountStatus status,
            UserRole roleRequested,
            String assignedZmId
    );

    Optional<AppUser> findByIdAndRoleAndStatusAndEnabled(
            String id,
            UserRole role,
            AccountStatus status,
            boolean enabled
    );

    List<AppUser> findByRoleAndStatusAndEnabled(
            UserRole role,
            AccountStatus status,
            boolean enabled
    );

    List<AppUser> findByRoleAndAssignedSttIdOrderByCreatedAtDesc(
            UserRole role,
            String assignedSttId
    );

    List<AppUser> findByRoleAndAssignedZmIdOrderByFullNameAsc(
            UserRole role,
            String assignedZmId
    );

    List<AppUser> findByRoleOrderByFullNameAsc(UserRole role);

    List<AppUser> findByRoleAndAssignedGovernoratesIn(UserRole role, Collection<String> assignedGovernorates);
}
