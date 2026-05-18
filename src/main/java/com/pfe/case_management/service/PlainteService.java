package com.pfe.case_management.service;

import com.pfe.MainPackage.Entity.AccountStatus;
import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Entity.UserRole;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.MainPackage.Service.NotificationEventService;
import com.pfe.case_management.dto.ComplaintDiagnosisRequest;
import com.pfe.case_management.dto.ComplaintImportResponse;
import com.pfe.case_management.dto.PlainteDTO;
import com.pfe.case_management.dto.ReopenComplaintRequest;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.enums.CaseStatus;
import com.pfe.case_management.enums.CaseTimelineEventType;
import com.pfe.case_management.enums.CaseType;
import com.pfe.case_management.enums.ResolutionValidationStatus;
import com.pfe.case_management.mapper.PlainteMapper;
import com.pfe.case_management.model.ComplaintResolutionInfo;
import com.pfe.case_management.model.RdvInfo;
import com.pfe.case_management.model.WorkInfo;
import com.pfe.case_management.model.ZmValidation;
import com.pfe.case_management.repository.PlainteRepository;
import com.pfe.plainte_analytics.model.PlainteImportProcessingMode;
import com.pfe.plainte_analytics.service.PlainteAnalyticsImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlainteService {

    private static final List<String> REQUIRED_HEADERS = List.of(
            "CRM_CASE",
            "GOUVERNORAT"
    );

    private final PlainteRepository plainteRepository;
    private final UserRepository userRepository;
    private final CaseService caseService;
    private final CaseTimelineService caseTimelineService;
    private final NotificationEventService notificationEventService;
    private final PlainteMapper plainteMapper;
    private final PlainteAnalyticsImportService plainteAnalyticsImportService;

    public List<PlainteDTO> listComplaints() {
        return caseService.getAllCasesByType(CaseType.PLAINTE).stream()
                .filter(this::isComplaintCase)
                .map(plainteMapper::toDto)
                .toList();
    }

    public PlainteDTO getComplaint(String caseId) {
        CaseEntity caseEntity = caseService.getCaseById(caseId);
        ensureComplaintCase(caseEntity);
        return plainteMapper.toDto(caseEntity);
    }

    public PlainteDTO diagnose(String caseId, ComplaintDiagnosisRequest request) {
        return plainteMapper.toDto(caseService.submitComplaintDiagnosis(caseId, request));
    }

    public PlainteDTO reopen(String caseId, ReopenComplaintRequest request) {
        return plainteMapper.toDto(caseService.reopenComplaintCase(caseId, request));
    }

    public ComplaintImportResponse importCsv(MultipartFile file, String importedBy) {
        var response = plainteAnalyticsImportService.importFile(
                file,
                importedBy,
                PlainteImportProcessingMode.ANALYTICS_AND_DISTRIBUTION
        );

        int createdCases = response.distributionSummary() != null
                ? response.distributionSummary().createdCases()
                : 0;
        int updatedCases = response.distributionSummary() != null
                ? response.distributionSummary().updatedExistingCases()
                : 0;
        int skippedRows = Math.max(0, response.totalRows() - createdCases - updatedCases);

        List<String> warnings = new ArrayList<>();
        if (response.invalidRows() > 0) {
            warnings.add("Skipped " + response.invalidRows() + " invalid complaint row(s).");
        }
        if (response.duplicateRows() > 0) {
            warnings.add("Detected " + response.duplicateRows() + " duplicate CRM case(s) already present in the complaint analytics dataset.");
        }

        return new ComplaintImportResponse(
                response.sourceFileName(),
                response.totalRows(),
                createdCases,
                updatedCases,
                skippedRows,
                List.copyOf(warnings)
        );
    }

    private void createComplaintFromImport(
            ComplaintImportRow row,
            String importedBy,
            List<String> warnings
    ) {
        Instant now = Instant.now();
        String normalizedGovernorate = resolveGovernorate(row.gouvernorat(), row.rowNumber(), warnings);
        AppUser ownerZm = resolveOwnerZm(normalizedGovernorate, row.rowNumber(), warnings);
        AppUser assignedStt = resolveStt(row.nameStt(), normalizedGovernorate, row.rowNumber(), warnings);

        CaseEntity caseEntity = CaseEntity.builder()
                .type(CaseType.PLAINTE)
                .crmCase(row.crmCase())
                .dateCreationCrm(row.dateCreationCrm())
                .openingDateTimos(firstNonNull(row.openingDateSurTimos(), row.dateCreationCrm(), now))
                .status(CaseStatus.CREATED)
                .clientName(row.client())
                .clientPhone(row.contactClient())
                .contactCl2(row.contactCl2())
                .msisdn(row.msisdn())
                .commentaire(row.commentaire())
                .latitude(row.latitudeSite())
                .longitude(row.longitudeSite())
                .localite(row.localite())
                .delegation(row.delegation())
                .gouvernorat(normalizedGovernorate != null ? normalizedGovernorate : row.gouvernorat())
                .offre(row.offre())
                .desPack(row.desPack())
                .typeOffreSpeed(row.typeOffreSpeed())
                .typeClient(row.typeClient())
                .source(row.source())
                .complaintDetail(row.detail())
                .complaintDescription(row.description())
                .openDateActivation(row.openDateActivation())
                .sourceAssignedSttName(row.nameStt())
                .sourceStatut(row.statut())
                .sourceRepTravauxStt(row.repTravauxStt())
                .sourceGovernorate(row.gouvernorat())
                .sourceLastImportedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .createdBy(importedBy)
                .build();

        applyOwnerAndStt(caseEntity, ownerZm, assignedStt, row.dateAffectationStt(), now);
        applyImportedWorkflow(caseEntity, row);

        caseTimelineService.append(
                caseEntity,
                CaseTimelineEventType.CASE_CREATED,
                "Complaint case imported",
                "Complaint data was imported from CSV and transformed into the workflow engine.",
                importedBy,
                null,
                caseEntity.getStatus(),
                timelineData(
                        "crmCase", row.crmCase(),
                        "sourceFileType", "CSV",
                        "importedComplaint", true
                )
        );

        if (ownerZm != null) {
            caseTimelineService.append(
                    caseEntity,
                    CaseTimelineEventType.ASSIGNMENT_UPDATED,
                    "Owner ZM resolved",
                    "Governorate " + safe(caseEntity.getGouvernorat()) + " is owned by ZM " + safe(ownerZm.getFullName()),
                    importedBy,
                    caseEntity.getStatus(),
                    caseEntity.getStatus(),
                    timelineData(
                            "assigneeRole", "ROLE_ZM",
                            "assigneeEmail", ownerZm.getEmail(),
                            "automaticAssignment", true
                    )
            );
        }

        CaseEntity saved = plainteRepository.save(caseEntity);
        if (ownerZm != null) {
            notificationEventService.onCaseAssignedToZm(saved, importedBy);
        }
        if (assignedStt != null) {
            notificationEventService.onCaseAssignedToStt(saved, importedBy);
        }
    }

    private void updateComplaintFromImport(
            CaseEntity caseEntity,
            ComplaintImportRow row,
            String importedBy,
            List<String> warnings
    ) {
        ensureComplaintCase(caseEntity);

        Instant now = Instant.now();
        caseEntity.setUpdatedAt(now);
        caseEntity.setSourceAssignedSttName(firstNonBlank(row.nameStt(), caseEntity.getSourceAssignedSttName()));
        caseEntity.setSourceStatut(firstNonBlank(row.statut(), caseEntity.getSourceStatut()));
        caseEntity.setSourceRepTravauxStt(firstNonBlank(row.repTravauxStt(), caseEntity.getSourceRepTravauxStt()));
        caseEntity.setSourceGovernorate(firstNonBlank(row.gouvernorat(), caseEntity.getSourceGovernorate()));
        caseEntity.setSourceLastImportedAt(now);

        fillIfBlank(caseEntity::getClientName, caseEntity::setClientName, row.client());
        fillIfBlank(caseEntity::getClientPhone, caseEntity::setClientPhone, row.contactClient());
        fillIfBlank(caseEntity::getContactCl2, caseEntity::setContactCl2, row.contactCl2());
        fillIfBlank(caseEntity::getCommentaire, caseEntity::setCommentaire, row.commentaire());
        fillIfBlank(caseEntity::getComplaintDetail, caseEntity::setComplaintDetail, row.detail());
        fillIfBlank(caseEntity::getComplaintDescription, caseEntity::setComplaintDescription, row.description());
        fillIfBlank(caseEntity::getLatitude, caseEntity::setLatitude, row.latitudeSite());
        fillIfBlank(caseEntity::getLongitude, caseEntity::setLongitude, row.longitudeSite());
        fillIfBlank(caseEntity::getDelegation, caseEntity::setDelegation, row.delegation());
        fillIfBlank(caseEntity::getLocalite, caseEntity::setLocalite, row.localite());
        fillIfBlank(caseEntity::getOffre, caseEntity::setOffre, row.offre());
        fillIfBlank(caseEntity::getDesPack, caseEntity::setDesPack, row.desPack());
        fillIfBlank(caseEntity::getTypeOffreSpeed, caseEntity::setTypeOffreSpeed, row.typeOffreSpeed());
        fillIfBlank(caseEntity::getTypeClient, caseEntity::setTypeClient, row.typeClient());
        fillIfBlank(caseEntity::getSource, caseEntity::setSource, row.source());

        if (caseEntity.getOpenDateActivation() == null && row.openDateActivation() != null) {
            caseEntity.setOpenDateActivation(row.openDateActivation());
        }

        if (caseEntity.getStatus() == CaseStatus.CREATED
                || caseEntity.getStatus() == CaseStatus.ASSIGNED_TO_ZM
                || caseEntity.getStatus() == CaseStatus.ASSIGNED_TO_STT
                || caseEntity.getStatus() == CaseStatus.NOT_REACHABLE
                || caseEntity.getStatus() == CaseStatus.DIAGNOSIS_STEP) {
            applyImportedWorkflow(caseEntity, row);
        }

        if (caseEntity.getSttId() == null && firstNonBlank(row.nameStt(), null) != null) {
            AppUser assignedStt = resolveStt(row.nameStt(), caseEntity.getGouvernorat(), row.rowNumber(), warnings);
            if (assignedStt != null) {
                applyOwnerAndStt(caseEntity, null, assignedStt, row.dateAffectationStt(), now);
            }
        }

        caseTimelineService.append(
                caseEntity,
                CaseTimelineEventType.STATUS_CHANGED,
                "Complaint source refreshed",
                "The complaint import refreshed the latest source snapshot without recreating the case.",
                importedBy,
                caseEntity.getStatus(),
                caseEntity.getStatus(),
                timelineData(
                        "crmCase", row.crmCase(),
                        "sourceStatut", row.statut(),
                        "sourceResolution", row.notifResolution()
                )
        );

        plainteRepository.save(caseEntity);
    }

    private void applyOwnerAndStt(
            CaseEntity caseEntity,
            AppUser ownerZm,
            AppUser assignedStt,
            Instant importedSttAssignmentDate,
            Instant fallbackNow
    ) {
        if (ownerZm != null) {
            caseEntity.setOwnerZmId(ownerZm.getId());
            caseEntity.setOwnerZmName(ownerZm.getFullName());
            caseEntity.setZmId(ownerZm.getId());
            caseEntity.setZmEmail(ownerZm.getEmail());
            caseEntity.setZmName(ownerZm.getFullName());
            caseEntity.setDateAffectationZm(fallbackNow);
            caseEntity.setStatus(CaseStatus.ASSIGNED_TO_ZM);
        }

        if (assignedStt != null) {
            caseEntity.setAssignedSttId(assignedStt.getId());
            caseEntity.setSttId(assignedStt.getId());
            caseEntity.setSttEmail(assignedStt.getEmail());
            caseEntity.setSttName(assignedStt.getFullName());
            caseEntity.setDateAffectationStt(firstNonNull(importedSttAssignmentDate, fallbackNow, fallbackNow));
            caseEntity.setStatus(CaseStatus.ASSIGNED_TO_STT);
        }
    }

    private void applyImportedWorkflow(CaseEntity caseEntity, ComplaintImportRow row) {
        ComplaintResolutionInfo resolution = caseEntity.getResolution() != null
                ? caseEntity.getResolution()
                : ComplaintResolutionInfo.builder().build();
        resolution.setResolutionType(firstNonBlank(row.notifResolution(), resolution.getResolutionType()));
        caseEntity.setResolution(resolution);

        Boolean rdvReply = toBooleanFlag(row.repRdv());
        Instant rdvDate = row.datePriseRdv();
        if (rdvReply != null || rdvDate != null || firstNonBlank(row.cmtRdv(), null) != null) {
            RdvInfo rdv = new RdvInfo();
            rdv.setJoignable(rdvReply == null || rdvReply);
            rdv.setDatePriseRdv(rdvDate);
            rdv.setDetail(row.detail());
            rdv.setCmtRdv(row.cmtRdv());
            caseEntity.setRdv(rdv);

            if (Boolean.FALSE.equals(rdvReply)) {
                caseEntity.setStatus(CaseStatus.NOT_REACHABLE);
            } else if (rdvDate != null) {
                caseEntity.setStatus(CaseStatus.DIAGNOSIS_STEP);
            }
        }

        if (firstNonBlank(row.repTravauxStt(), null) != null
                || row.dateFinTrv() != null
                || firstNonBlank(row.cmtTrvStt(), null) != null
                || firstNonBlank(row.notifResolution(), null) != null) {
            WorkInfo work = WorkInfo.builder()
                    .repTravauxStt(row.repTravauxStt())
                    .dateFinTrv(row.dateFinTrv())
                    .cmtTrvStt(row.cmtTrvStt())
                    .updatedAt(Instant.now())
                    .updatedBy("CSV_IMPORT")
                    .build();
            caseEntity.setWork(work);

            resolution.setResolutionComment(firstNonBlank(row.cmtTrvStt(), resolution.getResolutionComment()));
            resolution.setSubmittedAt(firstNonNull(row.dateFinTrv(), Instant.now(), Instant.now()));
            resolution.setSubmittedBy("CSV_IMPORT");
            resolution.setValidationStatus(ResolutionValidationStatus.PENDING);
            caseEntity.setStatus(CaseStatus.RESOLUTION_SUBMITTED);
        }

        Boolean zmReply = toBooleanFlag(row.repZm());
        if (zmReply != null || firstNonBlank(row.cmtZm(), null) != null) {
            ZmValidation validation = ZmValidation.builder()
                    .approved(zmReply)
                    .cmtZm(row.cmtZm())
                    .validatedAt(Instant.now())
                    .validatedBy("CSV_IMPORT")
                    .build();
            caseEntity.setZmValidation(validation);
            resolution.setValidatedAt(Instant.now());
            resolution.setValidatedBy("CSV_IMPORT");

            if (Boolean.TRUE.equals(zmReply)) {
                resolution.setValidationStatus(ResolutionValidationStatus.APPROVED);
                caseEntity.setStatus(CaseStatus.CLOSED);
            } else if (Boolean.FALSE.equals(zmReply)) {
                resolution.setValidationStatus(ResolutionValidationStatus.RETURNED_TO_STT);
                caseEntity.setStatus(CaseStatus.RETURNED_TO_STT);
            }
        }

        CaseStatus derivedFromRawStatus = deriveStatusFromRawStatus(row.statut());
        if (derivedFromRawStatus != null
                && (caseEntity.getStatus() == CaseStatus.ASSIGNED_TO_STT
                || caseEntity.getStatus() == CaseStatus.ASSIGNED_TO_ZM
                || caseEntity.getStatus() == CaseStatus.CREATED)) {
            caseEntity.setStatus(derivedFromRawStatus);
        }
    }

    private ComplaintImportRow toComplaintImportRow(
            Map<String, String> rowValues,
            int rowNumber,
            List<String> warnings
    ) {
        String crmCase = cleaned(rowValues.get("CRM_CASE"));
        if (!TodoAnalyticsSupport.hasText(crmCase)) {
            warnings.add("Row " + rowNumber + ": skipped because CRM_CASE is missing.");
            return null;
        }

        return new ComplaintImportRow(
                rowNumber,
                crmCase,
                parseInstant(rowValues.get("DATE_CREATION_CRM")),
                parseInstant(rowValues.get("OPENING_DATE_SUR_TIMOS")),
                cleaned(rowValues.get("STATUT")),
                cleaned(rowValues.get("CONTACT_CL2")),
                cleaned(rowValues.get("MSISDN")),
                cleaned(rowValues.get("DETAIL")),
                cleaned(rowValues.get("GOUVERNORAT")),
                cleaned(rowValues.get("LATITUDE_SITE")),
                cleaned(rowValues.get("LONGITUDE_SITE")),
                cleaned(rowValues.get("DESCRIPTION")),
                cleaned(rowValues.get("COMMENTAIRE")),
                parseInstant(rowValues.get("DATE_AFFECTATION_STT")),
                cleaned(rowValues.get("NAME_STT")),
                cleaned(rowValues.get("REP_RDV")),
                parseInstant(rowValues.get("DATE_PRISE_RDV")),
                cleaned(rowValues.get("CMT_RDV")),
                cleaned(rowValues.get("REP_TRAVAUX_STT")),
                parseInstant(rowValues.get("DATE_FIN_TRV")),
                cleaned(rowValues.get("NOTIF_RESOLUTION")),
                cleaned(rowValues.get("CMT_TRV_STT")),
                cleaned(rowValues.get("REP_ZM")),
                cleaned(rowValues.get("CMT_ZM")),
                parseInstant(rowValues.get("OPEN_DATE_ACTIVATION")),
                cleaned(rowValues.get("CLIENT")),
                cleaned(rowValues.get("CONTACT_CLIENT")),
                cleaned(rowValues.get("OFFRE")),
                cleaned(rowValues.get("DES_PACK")),
                cleaned(rowValues.get("DELEGATION")),
                cleaned(rowValues.get("LOCALITE")),
                cleaned(rowValues.get("TYPE_OFFRE_SPEED")),
                cleaned(rowValues.get("TYPE_CLIENT")),
                cleaned(rowValues.get("SOURCE"))
        );
    }

    private List<Map<String, String>> readCsvRows(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return List.of();
            }

            List<String> headers = parseCsvLine(stripBom(headerLine)).stream()
                    .map(TodoAnalyticsSupport::normalizeHeader)
                    .toList();

            ensureRequiredHeaders(headers);

            List<Map<String, String>> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!TodoAnalyticsSupport.hasText(line)) {
                    continue;
                }

                List<String> values = parseCsvLine(line);
                Map<String, String> row = new LinkedHashMap<>();
                for (int index = 0; index < headers.size(); index += 1) {
                    String header = headers.get(index);
                    String value = index < values.size() ? values.get(index) : null;
                    row.put(header, value);
                }
                rows.add(row);
            }

            return rows;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read complaint CSV file", ex);
        }
    }

    private void ensureRequiredHeaders(List<String> headers) {
        LinkedHashSet<String> missing = new LinkedHashSet<>();
        for (String requiredHeader : REQUIRED_HEADERS) {
            if (!headers.contains(requiredHeader)) {
                missing.add(requiredHeader);
            }
        }

        if (!missing.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Complaint CSV is missing required columns: " + String.join(", ", missing)
            );
        }
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int index = 0; index < line.length(); index += 1) {
            char currentChar = line.charAt(index);
            if (currentChar == '"') {
                if (inQuotes && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index += 1;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }

            if (currentChar == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
                continue;
            }

            current.append(currentChar);
        }

        values.add(current.toString());
        return values;
    }

    private AppUser resolveOwnerZm(String governorate, int rowNumber, List<String> warnings) {
        if (!TodoAnalyticsSupport.hasText(governorate)) {
            warnings.add("Row " + rowNumber + ": no governorate was provided, so the complaint stays without an owner ZM.");
            return null;
        }

        try {
            return caseService.resolveOwnerZmForGovernorate(governorate);
        } catch (ResponseStatusException ex) {
            warnings.add("Row " + rowNumber + ": " + ex.getReason());
            return null;
        }
    }

    private AppUser resolveStt(String sttName, String governorate, int rowNumber, List<String> warnings) {
        if (!TodoAnalyticsSupport.hasText(sttName)) {
            return null;
        }

        String normalizedWanted = normalizeLookup(sttName);
        List<AppUser> candidates = userRepository.findByRoleAndStatusAndEnabled(
                UserRole.ROLE_STT,
                AccountStatus.APPROVED,
                true
        ).stream()
                .filter(user -> user.getRole() == UserRole.ROLE_STT)
                .filter(user -> normalizeLookup(user.getFullName()).equals(normalizedWanted)
                        || normalizeLookup(user.getEmail()).equals(normalizedWanted))
                .toList();

        if (governorate != null) {
            candidates = candidates.stream()
                    .filter(user -> caseService.sttCoversGovernorateValue(user, governorate))
                    .toList();
        }

        if (candidates.isEmpty()) {
            warnings.add("Row " + rowNumber + ": NAME_STT '" + sttName + "' could not be matched to an active STT user.");
            return null;
        }

        return candidates.get(0);
    }

    private String resolveGovernorate(String rawGovernorate, int rowNumber, List<String> warnings) {
        String canonical = TodoAnalyticsSupport.canonicalGovernorate(rawGovernorate);
        if (!TodoAnalyticsSupport.hasText(canonical)) {
            warnings.add("Row " + rowNumber + ": governorate is missing, so the complaint stays in the unassigned queue.");
            return cleaned(rawGovernorate);
        }

        try {
            return caseService.normalizeGovernorateValue(canonical);
        } catch (ResponseStatusException ex) {
            warnings.add("Row " + rowNumber + ": " + ex.getReason());
            return canonical;
        }
    }

    private Instant parseInstant(String rawValue) {
        String cleanedValue = cleaned(rawValue);
        if (!TodoAnalyticsSupport.hasText(cleanedValue)) {
            return null;
        }

        try {
            return Instant.parse(cleanedValue);
        } catch (DateTimeParseException ignored) {
            // Continue through telecom CSV fallbacks.
        }

        try {
            return OffsetDateTime.parse(cleanedValue).toInstant();
        } catch (DateTimeParseException ignored) {
            // Continue through local date formats.
        }

        for (DateTimeFormatter formatter : TodoAnalyticsSupport.SUPPORTED_DATE_FORMATTERS) {
            try {
                return LocalDateTime.parse(cleanedValue, formatter).toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException ignored) {
                // Try parsing as date-only next.
            }

            try {
                LocalDate date = LocalDate.parse(cleanedValue, formatter);
                return date.atStartOfDay(ZoneOffset.UTC).toInstant();
            } catch (DateTimeParseException ignored) {
                // Continue trying supported formats.
            }
        }

        LocalDate date = TodoAnalyticsSupport.parseDate(cleanedValue);
        return date != null ? date.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
    }

    private Boolean toBooleanFlag(String rawValue) {
        String normalized = normalizeLookup(rawValue);
        if (normalized == null) {
            return null;
        }

        if (normalized.equals("oui")
                || normalized.equals("yes")
                || normalized.equals("ok")
                || normalized.equals("approved")
                || normalized.equals("resolve")
                || normalized.equals("resolved")
                || normalized.equals("true")
                || normalized.equals("1")) {
            return true;
        }

        if (normalized.equals("non")
                || normalized.equals("no")
                || normalized.equals("ko")
                || normalized.equals("reject")
                || normalized.equals("rejected")
                || normalized.equals("false")
                || normalized.equals("0")) {
            return false;
        }

        return null;
    }

    private CaseStatus deriveStatusFromRawStatus(String rawStatus) {
        String normalized = normalizeLookup(rawStatus);
        if (normalized == null) {
            return null;
        }

        return switch (normalized) {
            case "created" -> CaseStatus.CREATED;
            case "assigned to zm", "assigned zm" -> CaseStatus.ASSIGNED_TO_ZM;
            case "assigned to stt", "assigned stt" -> CaseStatus.ASSIGNED_TO_STT;
            case "assigned to tech", "assigned tech" -> CaseStatus.ASSIGNED_TO_TECH;
            case "rdv", "rdv set", "rdv step", "diagnosis", "diagnosis step" -> CaseStatus.DIAGNOSIS_STEP;
            case "resolution submitted" -> CaseStatus.RESOLUTION_SUBMITTED;
            case "escalated", "escalation" -> CaseStatus.ESCALATED;
            case "returned to stt" -> CaseStatus.RETURNED_TO_STT;
            case "reopened" -> CaseStatus.REOPENED;
            case "closed" -> CaseStatus.CLOSED;
            default -> null;
        };
    }

    private String normalizeLookup(String value) {
        return TodoAnalyticsSupport.normalizeLookupValue(value);
    }

    private String cleaned(String value) {
        return TodoAnalyticsSupport.cleanString(value);
    }

    private String stripBom(String value) {
        if (value != null && !value.isEmpty() && value.charAt(0) == '\uFEFF') {
            return value.substring(1);
        }
        return value;
    }

    private <T> T firstNonNull(T primary, T fallback, T defaultValue) {
        if (primary != null) {
            return primary;
        }
        if (fallback != null) {
            return fallback;
        }
        return defaultValue;
    }

    private String firstNonBlank(String primary, String fallback) {
        return TodoAnalyticsSupport.hasText(primary) ? primary : fallback;
    }

    private String safe(String value) {
        return TodoAnalyticsSupport.hasText(value) ? value : "-";
    }

    private Map<String, Object> timelineData(Object... keyValuePairs) {
        Map<String, Object> data = new LinkedHashMap<>();

        if (keyValuePairs == null) {
            return data;
        }

        for (int index = 0; index + 1 < keyValuePairs.length; index += 2) {
            if (keyValuePairs[index] instanceof String key && TodoAnalyticsSupport.hasText(key) && keyValuePairs[index + 1] != null) {
                data.put(key, keyValuePairs[index + 1]);
            }
        }

        return data;
    }

    private void fillIfBlank(ValueReader reader, ValueWriter writer, String importedValue) {
        if (!TodoAnalyticsSupport.hasText(importedValue) || TodoAnalyticsSupport.hasText(reader.read())) {
            return;
        }

        writer.write(importedValue);
    }

    private void ensureComplaintCase(CaseEntity caseEntity) {
        if (!isComplaintCase(caseEntity)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Case is not a complaint");
        }
    }

    private boolean isComplaintCase(CaseEntity caseEntity) {
        return caseEntity != null && caseEntity.getType() == CaseType.PLAINTE;
    }

    @FunctionalInterface
    private interface ValueReader {
        String read();
    }

    @FunctionalInterface
    private interface ValueWriter {
        void write(String value);
    }

    private record ComplaintImportRow(
            int rowNumber,
            String crmCase,
            Instant dateCreationCrm,
            Instant openingDateSurTimos,
            String statut,
            String contactCl2,
            String msisdn,
            String detail,
            String gouvernorat,
            String latitudeSite,
            String longitudeSite,
            String description,
            String commentaire,
            Instant dateAffectationStt,
            String nameStt,
            String repRdv,
            Instant datePriseRdv,
            String cmtRdv,
            String repTravauxStt,
            Instant dateFinTrv,
            String notifResolution,
            String cmtTrvStt,
            String repZm,
            String cmtZm,
            Instant openDateActivation,
            String client,
            String contactClient,
            String offre,
            String desPack,
            String delegation,
            String localite,
            String typeOffreSpeed,
            String typeClient,
            String source
    ) {
    }
}
