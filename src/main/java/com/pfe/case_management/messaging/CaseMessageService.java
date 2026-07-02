package com.pfe.case_management.messaging;

import com.pfe.MainPackage.Entity.AccountStatus;
import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Entity.FileEntityType;
import com.pfe.MainPackage.Entity.FileMetadata;
import com.pfe.MainPackage.Entity.FileRole;
import com.pfe.MainPackage.Entity.UserRole;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.MainPackage.Service.FileStorageService;
import com.pfe.MainPackage.Service.NotificationEventService;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.repository.CaseRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Case-scoped discussion between the case ZM and the assigned STT.
 *
 * Access rules:
 *  - ZM can read/write only on cases they own (zmId/zmEmail/ownerZmId match).
 *  - STT can read/write only on cases assigned to them (sttId/sttEmail match).
 *  - ADMIN can read every thread (audit) but never writes.
 *
 * The receiver is always derived server-side from the case assignment chain,
 * never trusted from the client.
 */
@Service
@RequiredArgsConstructor
public class CaseMessageService {

    private static final Logger auditLog = LoggerFactory.getLogger("CaseMessageAudit");

    private final CaseMessageRepository caseMessageRepository;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final NotificationEventService notificationEventService;

    public List<CaseMessageResponse> getThread(String caseId) {
        CaseEntity caseEntity = loadCase(caseId);
        AppUser me = currentUser();
        ensureCanViewThread(caseEntity, me);

        return caseMessageRepository.findByCaseIdOrderByCreatedAtAsc(caseId).stream()
                .map(m -> toResponse(m, me))
                .toList();
    }

    public CaseMessageResponse sendMessage(String caseId, String content, MultipartFile attachment) {
        CaseEntity caseEntity = loadCase(caseId);
        AppUser sender = currentUser();

        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty() && (attachment == null || attachment.isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message content is required");
        }
        if (trimmed.length() > 4000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message content must be at most 4000 characters");
        }

        Participant receiver = resolveReceiver(caseEntity, sender);

        CaseMessage message = CaseMessage.builder()
                .caseId(caseEntity.getId())
                .caseReference(caseReference(caseEntity))
                .caseType(caseEntity.getType() != null ? caseEntity.getType().name() : null)
                .caseModule(caseEntity.getCaseModule() != null ? caseEntity.getCaseModule().name() : null)
                .senderId(sender.getId())
                .senderName(sender.getFullName())
                .senderEmail(normalize(sender.getEmail()))
                .senderRole(sender.getRole().name())
                .receiverId(receiver.id())
                .receiverName(receiver.name())
                .receiverEmail(normalize(receiver.email()))
                .receiverRole(receiver.role())
                .content(trimmed.isEmpty() ? null : trimmed)
                .read(false)
                .createdAt(Instant.now())
                .build();

        if (attachment != null && !attachment.isEmpty()) {
            FileMetadata stored = fileStorageService.upload(
                    attachment,
                    FileEntityType.CASE,
                    caseEntity.getId(),
                    FileRole.ATTACHMENT,
                    normalize(sender.getEmail())
            );
            message.setAttachmentFileId(stored.getId());
            message.setAttachmentFilename(stored.getOriginalFilename());
            message.setAttachmentContentType(stored.getContentType());
        }

        CaseMessage saved = caseMessageRepository.save(message);

        auditLog.info("case_message_created messageId={} caseId={} caseRef={} sender={} senderRole={} receiver={} receiverRole={} hasAttachment={} contentLength={}",
                saved.getId(), saved.getCaseId(), saved.getCaseReference(),
                saved.getSenderEmail(), saved.getSenderRole(),
                saved.getReceiverEmail(), saved.getReceiverRole(),
                saved.getAttachmentFileId() != null,
                trimmed.length());

        notificationEventService.onCaseMessageCreated(
                caseEntity,
                saved.getReceiverEmail(),
                saved.getSenderName(),
                saved.getSenderRole()
        );

        return toResponse(saved, sender);
    }

    /** Marks every message addressed to the current user in this case as read. */
    public MarkMessagesReadResponse markThreadRead(String caseId) {
        CaseEntity caseEntity = loadCase(caseId);
        AppUser me = currentUser();
        ensureCanViewThread(caseEntity, me);

        List<CaseMessage> unread =
                caseMessageRepository.findByCaseIdAndReceiverEmailAndReadFalse(caseId, normalize(me.getEmail()));

        if (unread.isEmpty()) {
            return new MarkMessagesReadResponse(caseId, 0);
        }

        Instant now = Instant.now();
        for (CaseMessage message : unread) {
            message.setRead(true);
            message.setReadAt(now);
        }
        caseMessageRepository.saveAll(unread);

        auditLog.info("case_messages_marked_read caseId={} reader={} count={}",
                caseId, normalize(me.getEmail()), unread.size());

        return new MarkMessagesReadResponse(caseId, unread.size());
    }

    /** Unread-badge feed for the current user: total + per-case breakdown. */
    public CaseMessageUnreadSummaryResponse getUnreadSummary() {
        AppUser me = currentUser();
        List<CaseMessage> unread =
                caseMessageRepository.findByReceiverEmailAndReadFalse(normalize(me.getEmail()));

        Map<String, List<CaseMessage>> byCase = new LinkedHashMap<>();
        for (CaseMessage message : unread) {
            byCase.computeIfAbsent(message.getCaseId(), k -> new ArrayList<>()).add(message);
        }

        List<CaseMessageUnreadSummaryResponse.CaseUnread> cases = byCase.entrySet().stream()
                .map(entry -> {
                    List<CaseMessage> messages = entry.getValue();
                    Instant last = messages.stream()
                            .map(CaseMessage::getCreatedAt)
                            .filter(java.util.Objects::nonNull)
                            .max(Comparator.naturalOrder())
                            .orElse(null);
                    return new CaseMessageUnreadSummaryResponse.CaseUnread(
                            entry.getKey(),
                            messages.get(0).getCaseReference(),
                            messages.get(0).getCaseType(),
                            messages.get(0).getCaseModule(),
                            messages.size(),
                            last
                    );
                })
                .sorted(Comparator.comparing(
                        CaseMessageUnreadSummaryResponse.CaseUnread::lastMessageAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        return new CaseMessageUnreadSummaryResponse(unread.size(), cases);
    }

    // ── Access control ─────────────────────────────────────────────────────────

    private void ensureCanViewThread(CaseEntity caseEntity, AppUser user) {
        if (user.getRole() == UserRole.ROLE_ADMIN) {
            return;
        }
        if (user.getRole() == UserRole.ROLE_ZM && isCaseZm(caseEntity, user)) {
            return;
        }
        if (user.getRole() == UserRole.ROLE_STT && isCaseStt(caseEntity, user)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "You are not allowed to access this case discussion");
    }

    private Participant resolveReceiver(CaseEntity caseEntity, AppUser sender) {
        if (sender.getRole() == UserRole.ROLE_ZM) {
            if (!isCaseZm(caseEntity, sender)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "You can only message the STT of your own cases");
            }
            AppUser stt = resolveAssignedStt(caseEntity);
            return new Participant(stt.getId(), stt.getFullName(), stt.getEmail(), UserRole.ROLE_STT.name());
        }

        if (sender.getRole() == UserRole.ROLE_STT) {
            if (!isCaseStt(caseEntity, sender)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "You can only message the ZM of cases assigned to you");
            }
            AppUser zm = resolveCaseZm(caseEntity);
            return new Participant(zm.getId(), zm.getFullName(), zm.getEmail(), UserRole.ROLE_ZM.name());
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Only the case ZM and the assigned STT can write in a case discussion");
    }

    private boolean isCaseZm(CaseEntity c, AppUser user) {
        return equalsSafe(c.getZmId(), user.getId())
                || equalsSafe(c.getOwnerZmId(), user.getId())
                || equalsIgnoreCaseSafe(c.getZmEmail(), user.getEmail());
    }

    private boolean isCaseStt(CaseEntity c, AppUser user) {
        return equalsSafe(c.getSttId(), user.getId())
                || equalsSafe(c.getAssignedSttId(), user.getId())
                || equalsIgnoreCaseSafe(c.getSttEmail(), user.getEmail());
    }

    private AppUser resolveAssignedStt(CaseEntity caseEntity) {
        AppUser stt = null;
        if (!isBlank(caseEntity.getSttId())) {
            stt = userRepository.findById(caseEntity.getSttId()).orElse(null);
        }
        if (stt == null && !isBlank(caseEntity.getAssignedSttId())) {
            stt = userRepository.findById(caseEntity.getAssignedSttId()).orElse(null);
        }
        if (stt == null && !isBlank(caseEntity.getSttEmail())) {
            stt = userRepository.findByEmail(normalize(caseEntity.getSttEmail())).orElse(null);
        }
        if (stt == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No STT is assigned to this case yet");
        }
        return stt;
    }

    private AppUser resolveCaseZm(CaseEntity caseEntity) {
        AppUser zm = null;
        if (!isBlank(caseEntity.getZmId())) {
            zm = userRepository.findById(caseEntity.getZmId()).orElse(null);
        }
        if (zm == null && !isBlank(caseEntity.getOwnerZmId())) {
            zm = userRepository.findById(caseEntity.getOwnerZmId()).orElse(null);
        }
        if (zm == null && !isBlank(caseEntity.getZmEmail())) {
            zm = userRepository.findByEmail(normalize(caseEntity.getZmEmail())).orElse(null);
        }
        if (zm == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No ZM is responsible for this case");
        }
        return zm;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private CaseEntity loadCase(String caseId) {
        return caseRepository.findById(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found"));
    }

    private AppUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }

        AppUser user = userRepository.findByEmail(normalize(authentication.getName()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        if (user.getStatus() != AccountStatus.APPROVED || !user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is not active");
        }
        return user;
    }

    private CaseMessageResponse toResponse(CaseMessage m, AppUser viewer) {
        String viewUrl = m.getAttachmentFileId() != null
                ? fileStorageService.buildViewUrl(m.getAttachmentFileId())
                : null;
        String downloadUrl = m.getAttachmentFileId() != null
                ? fileStorageService.buildDownloadUrl(m.getAttachmentFileId())
                : null;

        return new CaseMessageResponse(
                m.getId(),
                m.getCaseId(),
                m.getCaseReference(),
                m.getSenderId(),
                m.getSenderName(),
                m.getSenderEmail(),
                m.getSenderRole(),
                m.getReceiverId(),
                m.getReceiverName(),
                m.getReceiverRole(),
                m.getContent(),
                m.getAttachmentFilename(),
                m.getAttachmentContentType(),
                viewUrl,
                downloadUrl,
                m.isRead(),
                m.getReadAt(),
                m.getCreatedAt(),
                equalsIgnoreCaseSafe(m.getSenderEmail(), viewer.getEmail())
        );
    }

    private String caseReference(CaseEntity caseEntity) {
        return isBlank(caseEntity.getCrmCase()) ? caseEntity.getId() : caseEntity.getCrmCase();
    }

    private String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private boolean equalsSafe(String a, String b) {
        return a != null && !a.isBlank() && a.equals(b);
    }

    private boolean equalsIgnoreCaseSafe(String a, String b) {
        return a != null && !a.isBlank() && b != null && a.trim().equalsIgnoreCase(b.trim());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record Participant(String id, String name, String email, String role) {
    }
}
