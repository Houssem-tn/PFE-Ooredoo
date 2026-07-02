package com.pfe.case_management.messaging;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CaseMessageRepository extends MongoRepository<CaseMessage, String> {

    List<CaseMessage> findByCaseIdOrderByCreatedAtAsc(String caseId);

    List<CaseMessage> findByCaseIdAndReceiverEmailAndReadFalse(String caseId, String receiverEmail);

    List<CaseMessage> findByReceiverEmailAndReadFalse(String receiverEmail);

    long countByCaseIdAndReceiverEmailAndReadFalse(String caseId, String receiverEmail);
}
