package com.pfe.MainPackage.Repository;

import com.pfe.MainPackage.Entity.Notification;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface NotificationRepository extends MongoRepository<Notification, String> {

    List<Notification> findByTargetRoleOrderByCreatedAtDesc(String targetRole);

    List<Notification> findByRecipientEmailOrderByCreatedAtDesc(String recipientEmail);

    long countByTargetRoleAndReadFalse(String targetRole);

    long countByRecipientEmailAndReadFalse(String recipientEmail);

    /** Deduplication: check if an unread alert of the same type already exists for this recipient+entity. */
    boolean existsByRecipientEmailAndEventTypeAndEntityIdAndReadFalse(
            String recipientEmail, String eventType, String entityId);

    /** Deduplication for role-based notifications. */
    boolean existsByTargetRoleAndEventTypeAndEntityIdAndReadFalse(
            String targetRole, String eventType, String entityId);
}