package com.pfe.Repository;

import com.pfe.Entity.TechInvitation;
import com.pfe.Entity.TechInvitationStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TechInvitationRepository extends MongoRepository<TechInvitation, String> {

    Optional<TechInvitation> findByToken(String token);

    Optional<TechInvitation> findFirstByEmailAndUsedFalseAndExpiresAtAfter(String email, Instant now);

    Optional<TechInvitation> findFirstByRegisteredTechIdOrderByCreatedAtDesc(String registeredTechId);

    Optional<TechInvitation> findFirstByEmailOrderByCreatedAtDesc(String email);

    List<TechInvitation> findByZmIdAndStatus(String zmId, TechInvitationStatus status);

    List<TechInvitation> findByZmIdAndStatusOrderByCreatedAtDesc(String zmId, TechInvitationStatus status);

    List<TechInvitation> findBySttId(String sttId);

    List<TechInvitation> findBySttIdOrderByCreatedAtDesc(String sttId);

    boolean existsByEmailAndUsedFalseAndExpiresAtAfter(String email, Instant now);
}
