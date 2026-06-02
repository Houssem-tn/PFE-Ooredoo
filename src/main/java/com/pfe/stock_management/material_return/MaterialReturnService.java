package com.pfe.stock_management.material_return;

import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.MainPackage.Service.NotificationEventService;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.enums.CaseTimelineEventType;
import com.pfe.case_management.repository.CaseRepository;
import com.pfe.case_management.service.CaseTimelineService;
import com.pfe.stock_management.entity.Equipment;
import com.pfe.stock_management.entity.SttStock;
import com.pfe.stock_management.model.SttStockItem;
import com.pfe.stock_management.repository.EquipmentRepository;
import com.pfe.stock_management.repository.SttStockRepository;
import com.pfe.stock_management.service.StockMovementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MaterialReturnService {

    private final MaterialReturnRepository returnRepository;
    private final SttStockRepository sttStockRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;
    private final CaseRepository caseRepository;
    private final CaseTimelineService caseTimelineService;
    private final StockMovementService stockMovementService;
    private final NotificationEventService notificationEventService;

    // ── STT: create return request ──────────────────────────────────────────────

    public MaterialReturnResponseDto createReturn(CreateMaterialReturnDto request) {
        String callerEmail = currentUserEmail();
        AppUser stt = findByEmail(callerEmail);

        // Validate comment required for OTHER reason
        if (request.getReason() == ReturnReason.OTHER
                && (request.getComment() == null || request.getComment().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Comment is required when return reason is OTHER");
        }

        // Validate items not empty with positive quantities
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one item is required");
        }
        for (ReturnedItemDto itemDto : request.getItems()) {
            if (itemDto.getQuantity() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Quantity must be positive for equipment " + itemDto.getEquipmentId());
            }
        }

        // Resolve return source (default to OTHER for robustness)
        ReturnSource returnSource = request.getReturnSource() != null
                ? request.getReturnSource()
                : ReturnSource.OTHER;

        // Always try to load STT stock — used for both validation (STT_STOCK) and name/unit enrichment
        Optional<SttStock> sttStockOpt = sttStockRepository.findBySttId(stt.getId());
        Map<String, SttStockItem> stockIndex = sttStockOpt
                .map(this::buildStockIndex)
                .orElse(new HashMap<>());

        // ── Stock validation: ONLY enforced when returnSource = STT_STOCK ────────
        if (returnSource == ReturnSource.STT_STOCK) {
            if (sttStockOpt.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No stock found for your account. " +
                        "Select a different return source if this material comes from an external source.");
            }
            for (ReturnedItemDto itemDto : request.getItems()) {
                SttStockItem stockItem = stockIndex.get(itemDto.getEquipmentId());
                if (stockItem == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Equipment not found in your stock: " + itemDto.getEquipmentId()
                            + ". Use a different return source for externally recovered material.");
                }
                if (stockItem.getAvailableQuantity() < itemDto.getQuantity()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Insufficient available quantity for " + stockItem.getEquipmentName()
                                    + ": available=" + stockItem.getAvailableQuantity()
                                    + ", requested=" + itemDto.getQuantity());
                }
            }
        }
        // ─────────────────────────────────────────────────────────────────────────

        // Resolve ZM from STT assignment
        if (stt.getAssignedZmId() == null || stt.getAssignedZmId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Your account is not assigned to a Zone Manager. Contact your administrator.");
        }
        AppUser zm = userRepository.findById(stt.getAssignedZmId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Zone Manager not found. Contact your administrator."));

        // Load equipment catalog for name / unit / articleCode enrichment
        List<String> equipmentIds = request.getItems().stream()
                .map(ReturnedItemDto::getEquipmentId).toList();
        Map<String, Equipment> equipmentMap = new HashMap<>();
        equipmentRepository.findAllById(equipmentIds)
                .forEach(e -> equipmentMap.put(e.getId(), e));

        // Build returned items — prefer STT stock data, fall back to catalog
        List<ReturnedItem> returnedItems = request.getItems().stream().map(dto -> {
            SttStockItem si  = stockIndex.get(dto.getEquipmentId());
            Equipment    eq  = equipmentMap.get(dto.getEquipmentId());

            String name        = si != null ? si.getEquipmentName()
                               : (eq != null ? eq.getName() : dto.getEquipmentId());
            String unit        = si != null ? si.getUnit()
                               : (eq != null && eq.getUnit() != null ? eq.getUnit().name() : null);
            String articleCode = eq != null ? eq.getArticleCode() : null;

            return ReturnedItem.builder()
                    .equipmentId(dto.getEquipmentId())
                    .equipmentName(name)
                    .articleCode(articleCode)
                    .unit(unit)
                    .quantity(dto.getQuantity())
                    .serialNumber(dto.getSerialNumber())
                    .macAddress(dto.getMacAddress())
                    .imei(dto.getImei())
                    .condition(dto.getCondition() != null ? dto.getCondition() : request.getCondition())
                    .build();
        }).toList();

        Instant now = Instant.now();
        MaterialReturnRequest returnRequest = MaterialReturnRequest.builder()
                .returnCode(generateReturnCode())
                .sttId(stt.getId())
                .sttName(stt.getFullName())
                .sttEmail(stt.getEmail())
                .zmId(zm.getId())
                .zmName(zm.getFullName())
                .zmEmail(zm.getEmail())
                .relatedCaseId(request.getRelatedCaseId())
                .relatedCaseType(request.getRelatedCaseType())
                .crmCase(request.getCrmCase())
                .returnSource(returnSource)
                .plannedReturnDate(request.getPlannedReturnDate())
                .reason(request.getReason())
                .condition(request.getCondition())
                .destination(request.getDestination())
                .items(returnedItems)
                .comment(request.getComment())
                .proofImageUrls(request.getProofImageUrls() != null ? request.getProofImageUrls() : List.of())
                .status(MaterialReturnStatus.PENDING)
                .createdAt(now)
                .submittedAt(now)
                .build();

        MaterialReturnRequest saved = returnRepository.save(returnRequest);

        // Audit movement: submitted
        stockMovementService.recordMaterialReturn(saved, callerEmail);

        // Case timeline: if linked to a case
        if (saved.getRelatedCaseId() != null && !saved.getRelatedCaseId().isBlank()) {
            appendCaseTimeline(saved, CaseTimelineEventType.MATERIAL_RETURN_SUBMITTED,
                    "Material Return Submitted",
                    "STT " + stt.getFullName() + " submitted return " + saved.getReturnCode()
                            + " (" + saved.getReason() + ", source=" + returnSource + ").",
                    callerEmail);
        }

        // Notify ZM
        notificationEventService.onMaterialReturnSubmitted(saved);

        return toDto(saved);
    }

    // ── STT: list my returns ────────────────────────────────────────────────────

    public List<MaterialReturnResponseDto> getMyReturns() {
        AppUser stt = findByEmail(currentUserEmail());
        return returnRepository.findBySttIdOrderByCreatedAtDesc(stt.getId())
                .stream().map(this::toDto).toList();
    }

    // ── ZM: list pending returns in scope ───────────────────────────────────────

    public List<MaterialReturnResponseDto> getPendingForZm() {
        AppUser zm = findByEmail(currentUserEmail());
        return returnRepository
                .findByZmIdAndStatusOrderByCreatedAtDesc(zm.getId(), MaterialReturnStatus.PENDING)
                .stream().map(this::toDto).toList();
    }

    // ── ZM: list all returns in scope ──────────────────────────────────────────

    public List<MaterialReturnResponseDto> getAllForZm() {
        AppUser zm = findByEmail(currentUserEmail());
        return returnRepository.findByZmIdOrderByCreatedAtDesc(zm.getId())
                .stream().map(this::toDto).toList();
    }

    // ── Any authorized role: get by id ─────────────────────────────────────────

    public MaterialReturnResponseDto getById(String id) {
        MaterialReturnRequest req = findReturnOrThrow(id);
        String callerEmail = currentUserEmail();
        AppUser caller = findByEmail(callerEmail);
        assertReadAccess(req, caller);
        return toDto(req);
    }

    // ── ZM: approve return ─────────────────────────────────────────────────────

    public MaterialReturnResponseDto approveReturn(String id, ReviewMaterialReturnDto body) {
        AppUser zm = findByEmail(currentUserEmail());
        MaterialReturnRequest req = findReturnOrThrow(id);

        assertZmScope(req, zm, "approve");

        if (req.getStatus() != MaterialReturnStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Return " + req.getReturnCode() + " is no longer pending (status=" + req.getStatus() + ")");
        }

        // Decrease STT available stock ONLY when material came from STT stock
        if (req.getReturnSource() == ReturnSource.STT_STOCK) {
            applySttStockDecrement(req);
        }
        // For all other sources (CLIENT_RECOVERY, RESILIATION_RECOVERY, etc.):
        // the material was never part of STT stock, so no decrement is needed.

        Instant now = Instant.now();
        req.setStatus(MaterialReturnStatus.APPROVED);
        req.setReviewedAt(now);
        req.setReviewedByZmId(zm.getId());
        req.setReviewedByZmName(zm.getFullName());
        req.setReviewComment(body != null ? body.getReviewComment() : null);
        req.setClosedAt(now);
        req.setStatus(MaterialReturnStatus.CLOSED);

        MaterialReturnRequest saved = returnRepository.save(req);

        // Audit movements: approved + destination-specific
        stockMovementService.recordMaterialReturnApproved(saved, zm.getEmail());

        // Case timeline
        if (saved.getRelatedCaseId() != null && !saved.getRelatedCaseId().isBlank()) {
            appendCaseTimeline(saved, CaseTimelineEventType.MATERIAL_RETURN_APPROVED,
                    "Material Return Approved",
                    "ZM " + zm.getFullName() + " approved return " + saved.getReturnCode() + ".",
                    zm.getEmail());
        }

        // Notify STT
        notificationEventService.onMaterialReturnApproved(saved);

        return toDto(saved);
    }

    // ── ZM: reject return ──────────────────────────────────────────────────────

    public MaterialReturnResponseDto rejectReturn(String id, ReviewMaterialReturnDto body) {
        AppUser zm = findByEmail(currentUserEmail());
        MaterialReturnRequest req = findReturnOrThrow(id);

        assertZmScope(req, zm, "reject");

        if (req.getStatus() != MaterialReturnStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Return " + req.getReturnCode() + " is no longer pending (status=" + req.getStatus() + ")");
        }

        String rejectionComment = (body != null && body.getReviewComment() != null)
                ? body.getReviewComment().trim() : "";
        if (rejectionComment.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A rejection comment is required when rejecting a material return.");
        }

        Instant now = Instant.now();
        req.setStatus(MaterialReturnStatus.REJECTED);
        req.setReviewedAt(now);
        req.setReviewedByZmId(zm.getId());
        req.setReviewedByZmName(zm.getFullName());
        req.setReviewComment(rejectionComment);

        MaterialReturnRequest saved = returnRepository.save(req);

        // Audit movement: rejected (stock always unchanged on rejection)
        stockMovementService.recordMaterialReturnRejected(saved, zm.getEmail());

        // Case timeline
        if (saved.getRelatedCaseId() != null && !saved.getRelatedCaseId().isBlank()) {
            appendCaseTimeline(saved, CaseTimelineEventType.MATERIAL_RETURN_REJECTED,
                    "Material Return Rejected",
                    "ZM " + zm.getFullName() + " rejected return " + saved.getReturnCode()
                            + ". Reason: " + rejectionComment,
                    zm.getEmail());
        }

        // Notify STT
        notificationEventService.onMaterialReturnRejected(saved);

        return toDto(saved);
    }

    // ── Case-linked returns ────────────────────────────────────────────────────

    public List<MaterialReturnResponseDto> getReturnsByCase(String caseId) {
        return returnRepository.findByRelatedCaseIdOrderByCreatedAtDesc(caseId)
                .stream().map(this::toDto).toList();
    }

    // ── ADMIN: all returns ─────────────────────────────────────────────────────

    public List<MaterialReturnResponseDto> getAllReturns() {
        return returnRepository.findAll().stream().map(this::toDto).toList();
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Decrements STT stock for each returned item.
     * Called ONLY when returnSource == STT_STOCK.
     * Re-validates available quantity at approval time to guard against concurrent depletion.
     */
    private void applySttStockDecrement(MaterialReturnRequest req) {
        SttStock stock = sttStockRepository.findBySttId(req.getSttId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "STT stock not found for sttId=" + req.getSttId()));

        Map<String, SttStockItem> index = buildStockIndex(stock);

        for (ReturnedItem item : req.getItems()) {
            SttStockItem si = index.get(item.getEquipmentId());
            if (si == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Equipment " + item.getEquipmentId() + " no longer exists in STT stock.");
            }
            if (si.getAvailableQuantity() < item.getQuantity()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "STT stock for " + si.getEquipmentName()
                                + " has become insufficient since submission. Available="
                                + si.getAvailableQuantity() + ", return qty=" + item.getQuantity());
            }
            si.setTotalReturned(si.getTotalReturned() + item.getQuantity());
            si.setLastReturnedAt(Instant.now());
        }

        stock.setLastUpdatedAt(Instant.now());
        sttStockRepository.save(stock);
    }

    private void appendCaseTimeline(MaterialReturnRequest req,
                                    CaseTimelineEventType eventType,
                                    String title,
                                    String details,
                                    String actor) {
        Optional<CaseEntity> caseOpt = caseRepository.findById(req.getRelatedCaseId());
        if (caseOpt.isEmpty()) return;

        CaseEntity caseEntity = caseOpt.get();
        Map<String, Object> data = new HashMap<>();
        data.put("returnId", req.getId());
        data.put("returnCode", req.getReturnCode());
        data.put("reason", req.getReason() != null ? req.getReason().name() : null);
        data.put("destination", req.getDestination() != null ? req.getDestination().name() : null);
        data.put("returnSource", req.getReturnSource() != null ? req.getReturnSource().name() : null);
        data.put("status", req.getStatus() != null ? req.getStatus().name() : null);

        caseTimelineService.append(caseEntity, eventType, title, details, actor, null, null, data);
        caseRepository.save(caseEntity);
    }

    private String generateReturnCode() {
        int year = Year.now().getValue();
        long count = returnRepository.count();
        String candidate;
        int attempts = 0;
        do {
            candidate = String.format("RET-%d-%04d", year, count + 1 + attempts);
            attempts++;
        } while (returnRepository.findByReturnCode(candidate).isPresent() && attempts < 100);
        return candidate;
    }

    private Map<String, SttStockItem> buildStockIndex(SttStock stock) {
        Map<String, SttStockItem> index = new HashMap<>();
        if (stock.getItems() != null) {
            stock.getItems().forEach(si -> index.put(si.getEquipmentId(), si));
        }
        return index;
    }

    private void assertZmScope(MaterialReturnRequest req, AppUser zm, String action) {
        if (!zm.getId().equals(req.getZmId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not authorized to " + action + " this return (out of your scope).");
        }
    }

    private void assertReadAccess(MaterialReturnRequest req, AppUser caller) {
        String role = caller.getRole() != null ? caller.getRole().name() : "";
        boolean isAdmin    = role.equals("ROLE_ADMIN");
        boolean isSttOwner = role.equals("ROLE_STT") && caller.getId().equals(req.getSttId());
        boolean isZmOwner  = role.equals("ROLE_ZM")  && caller.getId().equals(req.getZmId());
        if (!isAdmin && !isSttOwner && !isZmOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not authorized to view this return.");
        }
    }

    private MaterialReturnRequest findReturnOrThrow(String id) {
        return returnRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Material return not found: " + id));
    }

    private AppUser findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private String currentUserEmail() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }

    private MaterialReturnResponseDto toDto(MaterialReturnRequest r) {
        return MaterialReturnResponseDto.builder()
                .id(r.getId())
                .returnCode(r.getReturnCode())
                .sttId(r.getSttId())
                .sttName(r.getSttName())
                .sttEmail(r.getSttEmail())
                .zmId(r.getZmId())
                .zmName(r.getZmName())
                .zmEmail(r.getZmEmail())
                .relatedCaseId(r.getRelatedCaseId())
                .relatedCaseType(r.getRelatedCaseType())
                .crmCase(r.getCrmCase())
                .returnSource(r.getReturnSource())
                .plannedReturnDate(r.getPlannedReturnDate())
                .reason(r.getReason())
                .condition(r.getCondition())
                .destination(r.getDestination())
                .items(r.getItems())
                .comment(r.getComment())
                .proofImageUrls(r.getProofImageUrls())
                .status(r.getStatus())
                .createdAt(r.getCreatedAt())
                .submittedAt(r.getSubmittedAt())
                .reviewedAt(r.getReviewedAt())
                .closedAt(r.getClosedAt())
                .reviewedByZmId(r.getReviewedByZmId())
                .reviewedByZmName(r.getReviewedByZmName())
                .reviewComment(r.getReviewComment())
                .build();
    }
}
