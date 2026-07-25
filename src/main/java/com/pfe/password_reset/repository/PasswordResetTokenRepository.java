package com.pfe.password_reset.repository;

import com.pfe.password_reset.entity.PasswordResetToken;
import com.pfe.password_reset.entity.PasswordResetTokenStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends MongoRepository<PasswordResetToken, String> {

    /** Lookup is always by hash — the raw token is never stored. */
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    List<PasswordResetToken> findByUserIdAndStatus(String userId, PasswordResetTokenStatus status);

    long countByUserIdAndStatus(String userId, PasswordResetTokenStatus status);
}
