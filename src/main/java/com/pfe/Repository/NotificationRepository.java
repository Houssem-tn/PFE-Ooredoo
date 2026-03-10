package com.pfe.Repository;

import com.pfe.Entity.Notification;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface NotificationRepository extends MongoRepository<Notification, String> {

    List<Notification> findByTargetRoleOrderByCreatedAtDesc(String targetRole);

    List<Notification> findByRecipientEmailOrderByCreatedAtDesc(String recipientEmail);

    long countByTargetRoleAndReadFalse(String targetRole);

    long countByRecipientEmailAndReadFalse(String recipientEmail);
}