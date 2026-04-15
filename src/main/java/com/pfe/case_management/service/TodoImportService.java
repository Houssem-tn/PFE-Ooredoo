package com.pfe.case_management.service;

import com.pfe.Entity.AppUser;
import com.pfe.Entity.UserRole;
import com.pfe.Repository.UserRepository;
import com.pfe.case_management.dto.AdminPerformanceImportInjectResponse;
import com.pfe.case_management.dto.AdminPerformanceImportPreviewResponse;
import com.pfe.case_management.dto.AdminPerformanceImportPreviewRowResponse;
import com.pfe.case_management.dto.ImportedTodoListItemResponse;
import com.pfe.case_management.dto.ImportedTodoPageResponse;
import com.pfe.case_management.dto.ImportedTodoPreviewRowResponse;
import com.pfe.case_management.dto.TodoImportInjectResponse;
import com.pfe.case_management.dto.TodoImportPreviewResponse;
import com.pfe.case_management.dto.ZmTodoAnalyticsAlertResponse;
import com.pfe.case_management.dto.ZmTodoAnalyticsGovernorateItemResponse;
import com.pfe.case_management.dto.ZmTodoAnalyticsInvalidReasonResponse;
import com.pfe.case_management.dto.ZmTodoImportProcessResponse;
import com.pfe.case_management.dto.ZmTodoAnalyticsResponse;
import com.pfe.case_management.dto.ZmTodoAnalyticsSttItemResponse;
import com.pfe.case_management.entity.ImportedTodoEntity;
import com.pfe.case_management.entity.ImportedZmTodoEntity;
import com.pfe.case_management.model.ImportedTodoSourceType;
import com.pfe.case_management.repository.ImportedTodoRepository;
import com.pfe.case_management.repository.ImportedZmTodoRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TodoImportService {
    private static final Logger log = LoggerFactory.getLogger(TodoImportService.class);
    private static final int ADMIN_PREVIEW_LIMIT = 50;
    private static final int ZM_DELAYED_SLA_THRESHOLD_DAYS = 3;
    private static final double ZM_HIGH_AVERAGE_SLA_THRESHOLD_DAYS = 3d;
    private static final double ZM_HIGH_DELAYED_RATE_THRESHOLD = 25d;
    private static final double ZM_HIGH_GELE_RATE_THRESHOLD = 15d;
    private static final double ZM_STT_OVERLOAD_THRESHOLD = 50d;
    private static final double ZM_WORST_GOVERNORATE_ALERT_THRESHOLD_DAYS = 4d;
    private static final List<String> REQUIRED_COLUMNS = List.of(
            "CRM_CASE",
            "FX_DATE_CRM",
            "CONTACT1",
            "CONTACT2",
            "MSISDN",
            "GOUVERNORAT",
            "DESCRIPTION",
            "LATITUDE",
            "LONGITUDE",
            "OPEN_DATE_SUR_TIMOS",
            "DATE_AFFECTATION_STT",
            "STT",
            "STATUT",
            "ZONE_MNANAGER"
    );
    private static final List<DateTimeFormatter> SUPPORTED_DATE_FORMATTERS = List.of(
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("M/d/yyyy").toFormatter(Locale.ROOT),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("M/d/yy").toFormatter(Locale.ROOT),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d/M/yyyy").toFormatter(Locale.ROOT),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d/M/yy").toFormatter(Locale.ROOT),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("yyyy-MM-dd").toFormatter(Locale.ROOT),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd-MM-yyyy").toFormatter(Locale.ROOT),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("MM-dd-yyyy").toFormatter(Locale.ROOT),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("M/d/yyyy H:mm").toFormatter(Locale.ROOT),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("M/d/yyyy HH:mm").toFormatter(Locale.ROOT),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("M/d/yyyy h:mm a").toFormatter(Locale.ROOT),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d/M/yyyy H:mm").toFormatter(Locale.ROOT),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d/M/yyyy HH:mm").toFormatter(Locale.ROOT),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d/M/yyyy h:mm a").toFormatter(Locale.ROOT),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("yyyy-MM-dd H:mm").toFormatter(Locale.ROOT),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("yyyy-MM-dd HH:mm").toFormatter(Locale.ROOT),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("yyyy-MM-dd HH:mm:ss").toFormatter(Locale.ROOT)
    );
    private static final Map<String, String> TUNISIA_GOVERNORATE_ALIASES = buildTunisiaGovernorateAliases();

    static {
        // Large enterprise XLSX exports can be highly compressed and trigger
        // POI's default zip-bomb threshold even when the file is legitimate.
        ZipSecureFile.setMinInflateRatio(0.0001d);
    }

    private final ImportedTodoRepository importedTodoRepository;
    private final ImportedZmTodoRepository importedZmTodoRepository;
    private final MongoTemplate mongoTemplate;
    private final UserRepository userRepository;
    private final ZmTodoAnalysisService zmTodoAnalysisService;

    public AdminPerformanceImportPreviewResponse previewAdminPerformanceImport(MultipartFile file, String uploadedBy) {
        ParsedAnalyticsImport parsedImport = parseAdminPerformanceWorkbook(file, uploadedBy);
        return new AdminPerformanceImportPreviewResponse(
                parsedImport.sourceFileName(),
                parsedImport.totalRows(),
                parsedImport.validRows(),
                parsedImport.invalidRows(),
                parsedImport.distinctZoneManagers(),
                parsedImport.distinctStts(),
                parsedImport.distinctGovernorates(),
                parsedImport.previewRows().stream()
                        .limit(ADMIN_PREVIEW_LIMIT)
                        .map(this::toAdminPreviewRowResponse)
                        .toList()
        );
    }

    public AdminPerformanceImportInjectResponse injectAdminPerformanceImport(MultipartFile file, String uploadedBy) {
        ParsedAnalyticsImport parsedImport = parseAdminPerformanceWorkbook(file, uploadedBy);

        String batchId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        List<ImportedTodoEntity> entities = parsedImport.previewRows().stream()
                .map(row -> toAdminEntity(row, parsedImport.sourceFileName(), batchId, uploadedBy, now))
                .toList();

        importedTodoRepository.saveAll(entities);

        return new AdminPerformanceImportInjectResponse(
                batchId,
                parsedImport.sourceFileName(),
                parsedImport.totalRows(),
                parsedImport.validRows(),
                parsedImport.invalidRows(),
                parsedImport.distinctZoneManagers(),
                parsedImport.distinctStts(),
                parsedImport.distinctGovernorates(),
                entities.size()
        );
    }

    public ZmTodoAnalyticsResponse analyzeForZm(MultipartFile file, String zmEmail) {
        ParsedAnalyticsImport parsedImport = parseAdminPerformanceWorkbook(file, null);
        ResolvedZmIdentity zmIdentity = resolveZmIdentity(zmEmail);
        return prepareZmAnalytics(parsedImport, zmIdentity, zmEmail, false).analytics();
    }

    public ZmTodoImportProcessResponse processForZm(MultipartFile file, String zmEmail) {
        ParsedAnalyticsImport parsedImport = parseAdminPerformanceWorkbook(file, null);
        ResolvedZmIdentity zmIdentity = resolveZmIdentity(zmEmail);
        PreparedZmAnalytics prepared = prepareZmAnalytics(parsedImport, zmIdentity, zmEmail, true);

        String batchId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        List<ImportedZmTodoEntity> entities = prepared.matchedRows().stream()
                .map(row -> toZmImportedEntity(row, parsedImport.sourceFileName(), zmIdentity.displayManagerKey(), batchId, zmEmail, now))
                .toList();

        if (!entities.isEmpty()) {
            importedZmTodoRepository.saveAll(entities);
        }

        var savedAnalysis = zmTodoAnalysisService.saveAnalysis(
                zmEmail,
                now,
                parsedImport.sourceFileName(),
                batchId,
                prepared.analytics()
        );

        return new ZmTodoImportProcessResponse(
                savedAnalysis.getId(),
                parsedImport.sourceFileName(),
                now,
                batchId,
                prepared.analytics().totalRows(),
                prepared.analytics().matchedRows(),
                prepared.analytics().ignoredRows(),
                prepared.analytics().validMatchedRows(),
                prepared.analytics().invalidMatchedRows(),
                entities.size()
        );
    }

    private PreparedZmAnalytics prepareZmAnalytics(
            ParsedAnalyticsImport parsedImport,
            ResolvedZmIdentity zmIdentity,
            String uploadedBy,
            boolean annotateDuplicates
    ) {
        List<ParsedAnalyticsRow> matchedRows = parsedImport.previewRows().stream()
                .filter(row -> zmIdentity.matches(row.zoneManagerNormalized()))
                .toList();

        List<ParsedAnalyticsRow> preparedRows = annotateDuplicates
                ? annotateDuplicateAnalyticsRows(uploadedBy, matchedRows)
                : matchedRows;

        List<ParsedAnalyticsRow> validRows = preparedRows.stream()
                .filter(ParsedAnalyticsRow::validRow)
                .toList();

        List<ParsedAnalyticsRow> invalidRows = preparedRows.stream()
                .filter(row -> !row.validRow())
                .toList();

        return new PreparedZmAnalytics(
                preparedRows,
                validRows,
                invalidRows,
                buildZmAnalyticsResponse(parsedImport, zmIdentity, preparedRows, validRows, invalidRows)
        );
    }

    private ZmTodoAnalyticsResponse buildZmAnalyticsResponse(
            ParsedAnalyticsImport parsedImport,
            ResolvedZmIdentity zmIdentity,
            List<ParsedAnalyticsRow> matchedRows,
            List<ParsedAnalyticsRow> validRows,
            List<ParsedAnalyticsRow> invalidRows
    ) {
        List<ZmTodoAnalyticsSttItemResponse> sttBreakdown = buildZmTodoSttBreakdown(validRows);
        List<ZmTodoAnalyticsGovernorateItemResponse> governorateBreakdown = buildZmTodoGovernorateBreakdown(validRows);

        long totalCases = validRows.size();
        List<Integer> slaValues = validRows.stream()
                .map(ParsedAnalyticsRow::slaBusinessDays)
                .filter(value -> value != null)
                .toList();

        long delayedCases = validRows.stream()
                .filter(this::isDelayedZmAnalyticsRow)
                .count();
        double delayedRate = totalCases == 0 ? 0d : round2((delayedCases * 100d) / totalCases);

        long geleCases = validRows.stream()
                .filter(row -> isGeleStatus(row.statut()))
                .count();
        double geleRate = totalCases == 0 ? 0d : round2((geleCases * 100d) / totalCases);

        ZmTodoAnalyticsSttItemResponse topStt = sttBreakdown.isEmpty() ? null : sttBreakdown.get(0);
        ZmTodoAnalyticsGovernorateItemResponse bestGovernorate = governorateBreakdown.stream()
                .filter(item -> item.averageSlaBusinessDays() != null)
                .min(Comparator.comparingDouble(ZmTodoAnalyticsGovernorateItemResponse::averageSlaBusinessDays))
                .orElse(null);
        ZmTodoAnalyticsGovernorateItemResponse worstGovernorate = governorateBreakdown.stream()
                .filter(item -> item.averageSlaBusinessDays() != null)
                .max(Comparator.comparingDouble(ZmTodoAnalyticsGovernorateItemResponse::averageSlaBusinessDays))
                .orElse(null);

        List<ZmTodoAnalyticsInvalidReasonResponse> invalidReasonSummaries = summarizeInvalidReasons(invalidRows);

        return new ZmTodoAnalyticsResponse(
                parsedImport.sourceFileName(),
                zmIdentity.fullName(),
                zmIdentity.displayManagerKey(),
                parsedImport.totalRows(),
                matchedRows.size(),
                Math.max(0, parsedImport.totalRows() - matchedRows.size()),
                validRows.size(),
                invalidRows.size(),
                totalCases,
                slaValues.size(),
                slaValues.isEmpty() ? null : round2(slaValues.stream().mapToInt(Integer::intValue).average().orElse(0d)),
                delayedCases,
                delayedRate,
                geleCases,
                geleRate,
                sttBreakdown.size(),
                governorateBreakdown.size(),
                topStt != null ? topStt.sttName() : null,
                topStt != null ? topStt.caseCount() : 0L,
                topStt != null ? topStt.percentage() : null,
                bestGovernorate != null ? bestGovernorate.gouvernorat() : null,
                bestGovernorate != null ? bestGovernorate.averageSlaBusinessDays() : null,
                worstGovernorate != null ? worstGovernorate.gouvernorat() : null,
                worstGovernorate != null ? worstGovernorate.averageSlaBusinessDays() : null,
                buildZmTodoAlerts(zmIdentity, totalCases, matchedRows.size(), invalidRows.size(), sttBreakdown, governorateBreakdown, delayedRate, geleRate, invalidReasonSummaries, slaValues),
                buildZmTodoInsights(zmIdentity, totalCases, sttBreakdown, governorateBreakdown, delayedRate, geleRate, invalidRows.size(), invalidReasonSummaries, slaValues),
                sttBreakdown,
                governorateBreakdown,
                invalidReasonSummaries
        );
    }

    public ImportedTodoPageResponse listImportedTodos(
            String uploadedBy,
            int page,
            int size,
            String search,
            String statut,
            String rowQuality,
            String gouvernorat,
            LocalDate uploadedFrom,
            LocalDate uploadedTo
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);

        if (uploadedFrom != null && uploadedTo != null && uploadedTo.isBefore(uploadedFrom)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded To date must be on or after Uploaded From date");
        }

        Query query = new Query();
        query.addCriteria(Criteria.where("uploadedBy").is(uploadedBy));

        if (hasText(search)) {
            String containsPattern = ".*" + Pattern.quote(search.trim()) + ".*";
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("crmCase").regex(containsPattern, "i"),
                    Criteria.where("description").regex(containsPattern, "i"),
                    Criteria.where("gouvernorat").regex(containsPattern, "i"),
                    Criteria.where("stt").regex(containsPattern, "i"),
                    Criteria.where("statut").regex(containsPattern, "i"),
                    Criteria.where("validationIssues").regex(containsPattern, "i"),
                    Criteria.where("zoneManager").regex(containsPattern, "i"),
                    Criteria.where("sourceFileName").regex(containsPattern, "i"),
                    Criteria.where("managerFilterUsed").regex(containsPattern, "i"),
                    Criteria.where("importBatchId").regex(containsPattern, "i")
            ));
        }

        if (hasText(statut)) {
            query.addCriteria(Criteria.where("statut").regex("^" + Pattern.quote(statut.trim()) + "$", "i"));
        }

        if (hasText(rowQuality)) {
            String normalizedRowQuality = rowQuality.trim().toLowerCase(Locale.ROOT);

            if ("valid".equals(normalizedRowQuality)) {
                query.addCriteria(Criteria.where("validRow").is(true));
            } else if ("invalid".equals(normalizedRowQuality)) {
                query.addCriteria(Criteria.where("validRow").ne(true));
            }
        }

        if (hasText(gouvernorat)) {
            query.addCriteria(Criteria.where("gouvernorat").regex("^" + Pattern.quote(gouvernorat.trim()) + "$", "i"));
        }

        if (uploadedFrom != null || uploadedTo != null) {
            Criteria uploadedAtCriteria = Criteria.where("uploadedAt");

            if (uploadedFrom != null) {
                uploadedAtCriteria = uploadedAtCriteria.gte(uploadedFrom.atStartOfDay().toInstant(ZoneOffset.UTC));
            }

            if (uploadedTo != null) {
                uploadedAtCriteria = uploadedAtCriteria.lte(uploadedTo.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusNanos(1));
            }

            query.addCriteria(uploadedAtCriteria);
        }

        long totalElements = mongoTemplate.count(query, ImportedZmTodoEntity.class);

        query.with(Sort.by(Sort.Direction.DESC, "uploadedAt", "id"));
        query.skip((long) safePage * safeSize);
        query.limit(safeSize);

        List<ImportedTodoListItemResponse> items = mongoTemplate.find(query, ImportedZmTodoEntity.class).stream()
                .map(this::toListItemResponse)
                .toList();

        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / safeSize);
        return new ImportedTodoPageResponse(items, totalElements, totalPages, safePage, safeSize);
    }

    public TodoImportPreviewResponse preview(MultipartFile file, String managerFilter, String uploadedBy) {
        ParsedImport parsedImport = parseWorkbook(file, managerFilter, uploadedBy);
        return new TodoImportPreviewResponse(
                parsedImport.sourceFileName(),
                parsedImport.managerFilterUsed(),
                parsedImport.totalRows(),
                parsedImport.matchedRows(),
                parsedImport.unmatchedRows(),
                parsedImport.invalidRows(),
                parsedImport.duplicateRows(),
                parsedImport.readyRows(),
                parsedImport.previewRows().stream()
                        .map(this::toPreviewRowResponse)
                        .toList()
        );
    }

    public TodoImportInjectResponse inject(MultipartFile file, String managerFilter, String uploadedBy) {
        ParsedImport parsedImport = parseWorkbook(file, managerFilter, uploadedBy);

        if (parsedImport.readyRows() == 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No valid matched rows are ready to inject"
            );
        }

        String batchId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        List<ImportedZmTodoEntity> entities = parsedImport.previewRows().stream()
                .filter(ParsedPreviewRow::readyToInject)
                .map(row -> toEntity(row, parsedImport, batchId, uploadedBy, now))
                .toList();

        importedZmTodoRepository.saveAll(entities);

        return new TodoImportInjectResponse(
                batchId,
                parsedImport.sourceFileName(),
                parsedImport.managerFilterUsed(),
                parsedImport.totalRows(),
                parsedImport.matchedRows(),
                parsedImport.unmatchedRows(),
                parsedImport.invalidRows(),
                parsedImport.duplicateRows(),
                entities.size()
        );
    }

    private ParsedImport parseWorkbook(MultipartFile file, String managerFilter, String uploadedBy) {
        validateFile(file);

        String normalizedManagerFilter = normalizeLookupValue(managerFilter);
        if (normalizedManagerFilter == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Manager filter is required");
        }

        String sourceFileName = resolveSourceFileName(file);

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded Excel file has no sheets");
            }

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded Excel file has no header row");
            }

            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            Map<String, Integer> headerIndexes = resolveHeaderIndexes(headerRow, formatter, evaluator);
            validateRequiredColumns(headerIndexes);

            List<ParsedPreviewRow> matchedRows = new ArrayList<>();
            int totalRows = 0;
            int unmatchedRows = 0;

            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                Map<String, String> sourceValues = extractSourceValues(row, headerIndexes, formatter, evaluator);

                if (sourceValues.isEmpty() || isRowEmpty(sourceValues.values())) {
                    continue;
                }

                totalRows += 1;

                String zoneManager = trimmedValue(sourceValues.get("ZONE_MNANAGER"));
                if (!normalizedManagerFilter.equals(normalizeLookupValue(zoneManager))) {
                    unmatchedRows += 1;
                    continue;
                }

                matchedRows.add(buildPreviewRow(rowIndex + 1, sourceValues));
            }

            List<ParsedPreviewRow> annotatedRows = annotateDuplicateRows(uploadedBy, matchedRows);
            int invalidRows = (int) annotatedRows.stream().filter(ParsedPreviewRow::hasValidationIssue).count();
            int duplicateRows = (int) annotatedRows.stream().filter(ParsedPreviewRow::hasDuplicateIssue).count();
            int readyRows = (int) annotatedRows.stream().filter(ParsedPreviewRow::readyToInject).count();

            return new ParsedImport(
                    sourceFileName,
                    managerFilter.trim(),
                    totalRows,
                    annotatedRows.size(),
                    unmatchedRows,
                    invalidRows,
                    duplicateRows,
                    readyRows,
                    annotatedRows
            );
        } catch (EncryptedDocumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password-protected Excel files are not supported",
                    ex
            );
        } catch (IOException ex) {
            if (shouldAttemptFallback(sourceFileName, ex)) {
                try {
                    return parseStructuredTextWorkbook(file, managerFilter.trim(), normalizedManagerFilter, sourceFileName, uploadedBy);
                } catch (Exception fallbackEx) {
                    log.warn("Failed to read structured-text spreadsheet fallback for {}", sourceFileName, fallbackEx);
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            resolveFallbackReadError(ex, fallbackEx, sourceFileName),
                            fallbackEx
                    );
                }
            }

            log.warn("Failed to read uploaded Excel file {}", sourceFileName, ex);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, resolveExcelReadError(ex), ex);
        }
    }

    private ParsedAnalyticsImport parseAdminPerformanceWorkbook(MultipartFile file, String uploadedBy) {
        validateFile(file);

        String sourceFileName = resolveSourceFileName(file);

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded Excel file has no sheets");
            }

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded Excel file has no header row");
            }

            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            Map<String, Integer> headerIndexes = resolveHeaderIndexes(headerRow, formatter, evaluator);
            validateRequiredColumns(headerIndexes);

            List<SourceRow> sourceRows = new ArrayList<>();
            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                Map<String, String> sourceValues = extractSourceValues(row, headerIndexes, formatter, evaluator);

                if (sourceValues.isEmpty() || isRowEmpty(sourceValues.values())) {
                    continue;
                }

                sourceRows.add(new SourceRow(rowIndex + 1, sourceValues));
            }

            return buildAnalyticsImport(sourceFileName, sourceRows, uploadedBy);
        } catch (EncryptedDocumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password-protected Excel files are not supported",
                    ex
            );
        } catch (IOException ex) {
            if (shouldAttemptFallback(sourceFileName, ex)) {
                try {
                    return parseAdminStructuredTextWorkbook(file, sourceFileName, uploadedBy);
                } catch (Exception fallbackEx) {
                    log.warn("Failed to read admin analytics spreadsheet fallback for {}", sourceFileName, fallbackEx);
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            resolveFallbackReadError(ex, fallbackEx, sourceFileName),
                            fallbackEx
                    );
                }
            }

            log.warn("Failed to read uploaded admin analytics file {}", sourceFileName, ex);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, resolveExcelReadError(ex), ex);
        }
    }

    private ParsedAnalyticsImport parseAdminStructuredTextWorkbook(
            MultipartFile file,
            String sourceFileName,
            String uploadedBy
    ) throws Exception {
        byte[] bytes = file.getBytes();
        String content = decodeTextContent(bytes);
        List<String> failures = new ArrayList<>();

        if (looksLikeSpreadsheetXml(content)) {
            try {
                return buildAnalyticsImport(sourceFileName, toSourceRows(parseSpreadsheetXmlMatrix(bytes)), uploadedBy);
            } catch (Exception ex) {
                failures.add(sanitizeErrorMessage(deepestMessage(ex)));
            }
        }

        if (looksLikeHtml(content)) {
            try {
                return buildAnalyticsImport(sourceFileName, toSourceRows(parseHtmlMatrix(content)), uploadedBy);
            } catch (Exception ex) {
                failures.add(sanitizeErrorMessage(deepestMessage(ex)));
            }
        }

        try {
            return buildAnalyticsImport(sourceFileName, toSourceRows(parseDelimitedMatrix(content)), uploadedBy);
        } catch (Exception ex) {
            failures.add(sanitizeErrorMessage(deepestMessage(ex)));
        }

        String summary = failures.stream()
                .filter(this::hasText)
                .distinct()
                .collect(Collectors.joining(" | "));

        throw new IOException(hasText(summary)
                ? summary
                : "Unsupported import file content. Open the file in Excel and save it as .xlsx or .csv, then upload it again.");
    }

    private List<SourceRow> toSourceRows(List<List<String>> matrix) {
        if (matrix.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded spreadsheet does not contain any data rows");
        }

        Map<String, Integer> headerIndexes = resolveHeaderIndexes(matrix.get(0));
        validateRequiredColumns(headerIndexes);

        List<SourceRow> sourceRows = new ArrayList<>();
        for (int rowIndex = 1; rowIndex < matrix.size(); rowIndex++) {
            Map<String, String> sourceValues = extractSourceValues(matrix.get(rowIndex), headerIndexes);
            if (sourceValues.isEmpty() || isRowEmpty(sourceValues.values())) {
                continue;
            }

            sourceRows.add(new SourceRow(rowIndex + 1, sourceValues));
        }

        return sourceRows;
    }

    private ParsedAnalyticsImport buildAnalyticsImport(String sourceFileName, List<SourceRow> sourceRows, String uploadedBy) {
        Set<String> crmCases = sourceRows.stream()
                .map(row -> normalizeLookupValue(row.sourceValues().get("CRM_CASE")))
                .filter(this::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> zoneManagers = sourceRows.stream()
                .map(row -> normalizeLookupValue(row.sourceValues().get("ZONE_MNANAGER")))
                .filter(this::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> existingDuplicateKeys = hasText(uploadedBy)
                ? findExistingAdminDuplicateKeys(uploadedBy, crmCases, zoneManagers)
                : Set.of();
        Set<String> seenInFile = new LinkedHashSet<>();
        Set<String> distinctZoneManagers = new LinkedHashSet<>();
        Set<String> distinctStts = new LinkedHashSet<>();
        Set<String> distinctGovernorates = new LinkedHashSet<>();
        List<ParsedAnalyticsRow> previewRows = new ArrayList<>();

        for (SourceRow sourceRow : sourceRows) {
            ParsedAnalyticsRow row = buildAnalyticsPreviewRow(sourceRow.rowNumber(), sourceRow.sourceValues());
            List<String> issues = new ArrayList<>(row.issues());

            if (hasText(row.zoneManagerNormalized())) {
                distinctZoneManagers.add(row.zoneManager());
            }

            if (hasText(row.sttNormalized())) {
                distinctStts.add(row.stt());
            }

            if (hasText(row.gouvernoratNormalized())) {
                distinctGovernorates.add(row.gouvernorat());
            }

            String duplicateKey = analyticsDuplicateKey(row.zoneManagerNormalized(), row.crmCaseNormalized());
            if (duplicateKey != null) {
                if (!seenInFile.add(duplicateKey)) {
                    issues.add("Duplicate CRM_CASE for the same Zone Manager inside the selected file");
                }

                if (existingDuplicateKeys.contains(duplicateKey)) {
                    issues.add("Duplicate CRM_CASE already imported for this Zone Manager in your admin analytics context");
                }
            }

            previewRows.add(row.withIssues(issues));
        }

        int validRows = (int) previewRows.stream().filter(ParsedAnalyticsRow::validRow).count();
        return new ParsedAnalyticsImport(
                sourceFileName,
                sourceRows.size(),
                validRows,
                sourceRows.size() - validRows,
                distinctZoneManagers.size(),
                distinctStts.size(),
                distinctGovernorates.size(),
                previewRows
        );
    }

    private ParsedAnalyticsRow buildAnalyticsPreviewRow(int rowNumber, Map<String, String> sourceValues) {
        List<String> issues = new ArrayList<>();

        String crmCase = trimmedValue(sourceValues.get("CRM_CASE"));
        String zoneManager = trimmedValue(sourceValues.get("ZONE_MNANAGER"));
        String stt = trimmedValue(sourceValues.get("STT"));
        String gouvernoratRaw = trimmedValue(sourceValues.get("GOUVERNORAT"));
        String openDateRaw = trimmedValue(sourceValues.get("OPEN_DATE_SUR_TIMOS"));
        String affectationRaw = trimmedValue(sourceValues.get("DATE_AFFECTATION_STT"));

        if (!hasText(crmCase)) {
            issues.add("CRM_CASE is required");
        }

        if (!hasText(zoneManager)) {
            issues.add("ZONE_MNANAGER is required");
        }

        if (!hasText(stt)) {
            issues.add("STT is required");
        }

        if (!hasText(gouvernoratRaw)) {
            issues.add("GOUVERNORAT is required");
        }

        String gouvernorat = canonicalTunisiaGovernorate(gouvernoratRaw);
        if (hasText(gouvernoratRaw) && !hasText(gouvernorat)) {
            issues.add("Unknown Tunisian governorate");
        }

        LocalDate openDate = parseImportedDate(openDateRaw);
        LocalDate affectationDate = parseImportedDate(affectationRaw);
        Integer slaBusinessDays = calculateBusinessDays(openDate, affectationDate);

        return new ParsedAnalyticsRow(
                rowNumber,
                crmCase,
                normalizeLookupValue(crmCase),
                trimmedValue(sourceValues.get("FX_DATE_CRM")),
                trimmedValue(sourceValues.get("CONTACT1")),
                trimmedValue(sourceValues.get("CONTACT2")),
                trimmedValue(sourceValues.get("MSISDN")),
                gouvernorat,
                normalizeLookupValue(gouvernorat),
                trimmedValue(sourceValues.get("DESCRIPTION")),
                normalizeCoordinate(sourceValues.get("LATITUDE")),
                normalizeCoordinate(sourceValues.get("LONGITUDE")),
                openDateRaw,
                openDate,
                affectationRaw,
                affectationDate,
                slaBusinessDays,
                stt,
                normalizeLookupValue(stt),
                trimmedValue(sourceValues.get("STATUT")),
                zoneManager,
                normalizeLookupValue(zoneManager),
                Map.copyOf(sourceValues),
                List.copyOf(issues)
        );
    }

    private Set<String> findExistingAdminDuplicateKeys(String uploadedBy, Set<String> crmCases, Set<String> zoneManagers) {
        if (crmCases.isEmpty() || zoneManagers.isEmpty()) {
            return Set.of();
        }

        Query query = new Query();
        query.addCriteria(Criteria.where("sourceType").in(
                ImportedTodoSourceType.ADMIN_PERFORMANCE_IMPORT,
                ImportedTodoSourceType.SAVED_ADMIN_PERFORMANCE_IMPORT
        ));
        query.addCriteria(Criteria.where("uploadedBy").is(uploadedBy));
        query.addCriteria(Criteria.where("crmCaseNormalized").in(crmCases));
        query.addCriteria(Criteria.where("zoneManagerNormalized").in(zoneManagers));

        return mongoTemplate.find(query, ImportedTodoEntity.class).stream()
                .map(entity -> analyticsDuplicateKey(entity.getZoneManagerNormalized(), entity.getCrmCaseNormalized()))
                .filter(this::hasText)
                .collect(Collectors.toSet());
    }

    private String analyticsDuplicateKey(String zoneManagerNormalized, String crmCaseNormalized) {
        if (!hasText(zoneManagerNormalized) || !hasText(crmCaseNormalized)) {
            return null;
        }

        return zoneManagerNormalized + "::" + crmCaseNormalized;
    }

    private Map<String, Integer> resolveHeaderIndexes(Row headerRow, DataFormatter formatter, FormulaEvaluator evaluator) {
        Map<String, Integer> indexes = new LinkedHashMap<>();

        for (int cellIndex = 0; cellIndex < headerRow.getLastCellNum(); cellIndex++) {
            String rawHeader = readCellValue(headerRow.getCell(cellIndex), formatter, evaluator);
            if (!hasText(rawHeader)) {
                continue;
            }

            String header = rawHeader.trim();
            indexes.putIfAbsent(normalizeHeader(header), cellIndex);
        }

        return indexes;
    }

    private Map<String, Integer> resolveHeaderIndexes(List<String> headers) {
        Map<String, Integer> indexes = new LinkedHashMap<>();

        for (int index = 0; index < headers.size(); index++) {
            String rawHeader = headers.get(index);
            if (!hasText(rawHeader)) {
                continue;
            }

            indexes.putIfAbsent(normalizeHeader(rawHeader), index);
        }

        return indexes;
    }

    private boolean containsRequiredColumns(Map<String, Integer> headerIndexes) {
        return REQUIRED_COLUMNS.stream()
                .allMatch(column -> headerIndexes.containsKey(normalizeHeader(column)));
    }

    private void validateRequiredColumns(Map<String, Integer> headerIndexes) {
        List<String> missingColumns = REQUIRED_COLUMNS.stream()
                .filter(column -> !headerIndexes.containsKey(normalizeHeader(column)))
                .toList();

        if (!missingColumns.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Missing required Excel columns: " + String.join(", ", missingColumns)
            );
        }
    }

    private Map<String, String> extractSourceValues(
            Row row,
            Map<String, Integer> headerIndexes,
            DataFormatter formatter,
            FormulaEvaluator evaluator
    ) {
        Map<String, String> values = new LinkedHashMap<>();

        for (String column : REQUIRED_COLUMNS) {
            Integer cellIndex = headerIndexes.get(normalizeHeader(column));
            if (cellIndex == null) {
                continue;
            }

            values.put(column, readCellValue(row == null ? null : row.getCell(cellIndex), formatter, evaluator));
        }

        return values;
    }

    private Map<String, String> extractSourceValues(List<String> rowValues, Map<String, Integer> headerIndexes) {
        Map<String, String> values = new LinkedHashMap<>();

        for (String column : REQUIRED_COLUMNS) {
            Integer cellIndex = headerIndexes.get(normalizeHeader(column));
            String value = cellIndex == null || cellIndex >= rowValues.size()
                    ? ""
                    : rowValues.get(cellIndex);
            values.put(column, value);
        }

        return values;
    }

    private ParsedImport parseStructuredTextWorkbook(
            MultipartFile file,
            String managerFilter,
            String normalizedManagerFilter,
            String sourceFileName,
            String uploadedBy
    ) throws Exception {
        byte[] bytes = file.getBytes();
        String content = decodeTextContent(bytes);
        List<String> failures = new ArrayList<>();

        if (looksLikeSpreadsheetXml(content)) {
            try {
                return parseMatrixBasedImport(
                        parseSpreadsheetXmlMatrix(bytes),
                        managerFilter,
                        normalizedManagerFilter,
                        sourceFileName,
                        uploadedBy
                );
            } catch (Exception ex) {
                failures.add(sanitizeErrorMessage(deepestMessage(ex)));
            }
        }

        if (looksLikeHtml(content)) {
            try {
                return parseMatrixBasedImport(
                        parseHtmlMatrix(content),
                        managerFilter,
                        normalizedManagerFilter,
                        sourceFileName,
                        uploadedBy
                );
            } catch (Exception ex) {
                failures.add(sanitizeErrorMessage(deepestMessage(ex)));
            }
        }

        try {
            return parseMatrixBasedImport(
                    parseDelimitedMatrix(content),
                    managerFilter,
                    normalizedManagerFilter,
                    sourceFileName,
                    uploadedBy
            );
        } catch (Exception ex) {
            failures.add(sanitizeErrorMessage(deepestMessage(ex)));
        }

        String summary = failures.stream()
                .filter(this::hasText)
                .distinct()
                .collect(Collectors.joining(" | "));

        throw new IOException(hasText(summary)
                ? summary
                : "Unsupported .xls content. Open the file in Excel and save it as .xlsx, then upload it again.");
    }

    private ParsedImport parseMatrixBasedImport(
            List<List<String>> matrix,
            String managerFilter,
            String normalizedManagerFilter,
            String sourceFileName,
            String uploadedBy
    ) {
        if (matrix.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded spreadsheet does not contain any data rows");
        }

        Map<String, Integer> headerIndexes = resolveHeaderIndexes(matrix.get(0));
        validateRequiredColumns(headerIndexes);

        List<ParsedPreviewRow> matchedRows = new ArrayList<>();
        int totalRows = 0;
        int unmatchedRows = 0;

        for (int rowIndex = 1; rowIndex < matrix.size(); rowIndex++) {
            Map<String, String> sourceValues = extractSourceValues(matrix.get(rowIndex), headerIndexes);

            if (sourceValues.isEmpty() || isRowEmpty(sourceValues.values())) {
                continue;
            }

            totalRows += 1;

            String zoneManager = trimmedValue(sourceValues.get("ZONE_MNANAGER"));
            if (!normalizedManagerFilter.equals(normalizeLookupValue(zoneManager))) {
                unmatchedRows += 1;
                continue;
            }

            matchedRows.add(buildPreviewRow(rowIndex + 1, sourceValues));
        }

        List<ParsedPreviewRow> annotatedRows = annotateDuplicateRows(uploadedBy, matchedRows);
        int invalidRows = (int) annotatedRows.stream().filter(ParsedPreviewRow::hasValidationIssue).count();
        int duplicateRows = (int) annotatedRows.stream().filter(ParsedPreviewRow::hasDuplicateIssue).count();
        int readyRows = (int) annotatedRows.stream().filter(ParsedPreviewRow::readyToInject).count();

        return new ParsedImport(
                sourceFileName,
                managerFilter,
                totalRows,
                annotatedRows.size(),
                unmatchedRows,
                invalidRows,
                duplicateRows,
                readyRows,
                annotatedRows
        );
    }

    private List<List<String>> parseHtmlMatrix(String content) throws IOException {
        org.jsoup.nodes.Document document = Jsoup.parse(content);
        Elements tables = document.select("table");

        for (org.jsoup.nodes.Element table : tables) {
            List<List<String>> matrix = new ArrayList<>();

            for (org.jsoup.nodes.Element row : table.select("tr")) {
                Elements cells = row.select("th,td");
                if (cells.isEmpty()) {
                    continue;
                }

                List<String> values = cells.stream()
                        .map(cell -> cell.text() == null ? "" : cell.text().trim())
                        .toList();
                matrix.add(values);
            }

            if (!matrix.isEmpty() && containsRequiredColumns(resolveHeaderIndexes(matrix.get(0)))) {
                return matrix;
            }
        }

        throw new IOException("No HTML table with the required todo columns was found");
    }

    private List<List<String>> parseSpreadsheetXmlMatrix(byte[] bytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new ByteArrayInputStream(bytes));
        NodeList tableNodes = document.getElementsByTagNameNS("*", "Table");

        for (int tableIndex = 0; tableIndex < tableNodes.getLength(); tableIndex++) {
            Node tableNode = tableNodes.item(tableIndex);
            if (tableNode == null || tableNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            List<List<String>> matrix = new ArrayList<>();
            NodeList rowNodes = tableNode.getChildNodes();

            for (int rowIndex = 0; rowIndex < rowNodes.getLength(); rowIndex++) {
                Node rowNode = rowNodes.item(rowIndex);
                if (!isElementNamed(rowNode, "Row")) {
                    continue;
                }

                List<String> values = new ArrayList<>();
                NodeList cellNodes = rowNode.getChildNodes();
                int currentColumnIndex = 1;

                for (int cellIndex = 0; cellIndex < cellNodes.getLength(); cellIndex++) {
                    Node cellNode = cellNodes.item(cellIndex);
                    if (!isElementNamed(cellNode, "Cell")) {
                        continue;
                    }

                    int targetColumnIndex = resolveIndexedPosition(cellNode, currentColumnIndex);
                    while (values.size() < targetColumnIndex - 1) {
                        values.add("");
                    }

                    values.add(extractSpreadsheetXmlCellValue(cellNode));
                    currentColumnIndex = targetColumnIndex + 1;
                }

                if (!values.isEmpty()) {
                    matrix.add(values);
                }
            }

            if (!matrix.isEmpty() && containsRequiredColumns(resolveHeaderIndexes(matrix.get(0)))) {
                return matrix;
            }
        }

        throw new IOException("No Spreadsheet XML table with the required todo columns was found");
    }

    private List<List<String>> parseDelimitedMatrix(String content) throws IOException {
        List<String> lines = content.lines()
                .map(line -> line == null ? "" : line.replace("\uFEFF", ""))
                .filter(line -> !line.isBlank())
                .toList();

        if (lines.isEmpty()) {
            throw new IOException("The uploaded file does not contain readable rows");
        }

        char[] separators = new char[]{'\t', ';', ',', '|'};
        for (char separator : separators) {
            List<String> headerValues = splitDelimitedLine(lines.get(0), separator);
            if (!containsRequiredColumns(resolveHeaderIndexes(headerValues))) {
                continue;
            }

            List<List<String>> matrix = new ArrayList<>();
            matrix.add(headerValues);

            for (int lineIndex = 1; lineIndex < lines.size(); lineIndex++) {
                matrix.add(splitDelimitedLine(lines.get(lineIndex), separator));
            }

            return matrix;
        }

        throw new IOException("No supported delimited table with the required todo columns was found");
    }

    private List<String> splitDelimitedLine(String line, char separator) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean insideQuotes = false;

        for (int index = 0; index < line.length(); index++) {
            char currentChar = line.charAt(index);

            if (currentChar == '"') {
                if (insideQuotes && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index += 1;
                } else {
                    insideQuotes = !insideQuotes;
                }
                continue;
            }

            if (currentChar == separator && !insideQuotes) {
                values.add(current.toString().trim());
                current.setLength(0);
                continue;
            }

            current.append(currentChar);
        }

        values.add(current.toString().trim());
        return values;
    }

    private ParsedPreviewRow buildPreviewRow(int rowNumber, Map<String, String> sourceValues) {
        List<String> issues = new ArrayList<>();

        String crmCase = trimmedValue(sourceValues.get("CRM_CASE"));
        if (!hasText(crmCase)) {
            issues.add("CRM_CASE is required");
        }

        String gouvernorat = trimmedValue(sourceValues.get("GOUVERNORAT"));
        if (!hasText(gouvernorat)) {
            issues.add("GOUVERNORAT is required");
        }

        String zoneManager = trimmedValue(sourceValues.get("ZONE_MNANAGER"));
        if (!hasText(zoneManager)) {
            issues.add("ZONE_MNANAGER is required");
        }

        return new ParsedPreviewRow(
                rowNumber,
                crmCase,
                normalizeLookupValue(crmCase),
                trimmedValue(sourceValues.get("FX_DATE_CRM")),
                trimmedValue(sourceValues.get("CONTACT1")),
                trimmedValue(sourceValues.get("CONTACT2")),
                trimmedValue(sourceValues.get("MSISDN")),
                gouvernorat,
                trimmedValue(sourceValues.get("DESCRIPTION")),
                normalizeCoordinate(sourceValues.get("LATITUDE")),
                normalizeCoordinate(sourceValues.get("LONGITUDE")),
                trimmedValue(sourceValues.get("OPEN_DATE_SUR_TIMOS")),
                trimmedValue(sourceValues.get("DATE_AFFECTATION_STT")),
                trimmedValue(sourceValues.get("STT")),
                trimmedValue(sourceValues.get("STATUT")),
                zoneManager,
                Map.copyOf(sourceValues),
                List.copyOf(issues)
        );
    }

    private List<ParsedPreviewRow> annotateDuplicateRows(String uploadedBy, List<ParsedPreviewRow> rows) {
        if (rows.isEmpty()) {
            return rows;
        }

        Set<String> crmCases = rows.stream()
                .map(ParsedPreviewRow::crmCaseNormalized)
                .filter(this::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> existingCrmCases = crmCases.isEmpty()
                ? Set.of()
                : importedZmTodoRepository.findByUploadedByAndSourceTypeAndCrmCaseNormalizedIn(
                        uploadedBy,
                        ImportedTodoSourceType.ZM_TODO_INJECTION,
                        crmCases
                )
                .stream()
                .map(ImportedZmTodoEntity::getCrmCaseNormalized)
                .filter(this::hasText)
                .collect(Collectors.toSet());

        Set<String> seenInWorkbook = new LinkedHashSet<>();
        List<ParsedPreviewRow> annotatedRows = new ArrayList<>();

        for (ParsedPreviewRow row : rows) {
            List<String> issues = new ArrayList<>(row.issues());
            String crmCaseNormalized = row.crmCaseNormalized();

            if (hasText(crmCaseNormalized)) {
                if (!seenInWorkbook.add(crmCaseNormalized)) {
                    issues.add("Duplicate CRM_CASE inside the selected file");
                }

                if (existingCrmCases.contains(crmCaseNormalized)) {
                    issues.add("Duplicate CRM_CASE already imported in your ZM todo context");
                }
            }

            annotatedRows.add(row.withIssues(issues));
        }

        return annotatedRows;
    }

    private List<ParsedAnalyticsRow> annotateDuplicateAnalyticsRows(String uploadedBy, List<ParsedAnalyticsRow> rows) {
        if (rows.isEmpty()) {
            return rows;
        }

        Set<String> crmCases = rows.stream()
                .map(ParsedAnalyticsRow::crmCaseNormalized)
                .filter(this::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> existingCrmCases = crmCases.isEmpty()
                ? Set.of()
                : importedZmTodoRepository.findByUploadedByAndSourceTypeAndCrmCaseNormalizedIn(
                        uploadedBy,
                        ImportedTodoSourceType.ZM_TODO_INJECTION,
                        crmCases
                ).stream()
                .map(ImportedZmTodoEntity::getCrmCaseNormalized)
                .filter(this::hasText)
                .collect(Collectors.toSet());

        Set<String> seenInWorkbook = new LinkedHashSet<>();
        List<ParsedAnalyticsRow> annotatedRows = new ArrayList<>();

        for (ParsedAnalyticsRow row : rows) {
            List<String> issues = new ArrayList<>(row.issues());
            String crmCaseNormalized = row.crmCaseNormalized();

            if (hasText(crmCaseNormalized)) {
                if (!seenInWorkbook.add(crmCaseNormalized)) {
                    issues.add("Duplicate CRM_CASE inside the selected file");
                }

                if (existingCrmCases.contains(crmCaseNormalized)) {
                    issues.add("Duplicate CRM_CASE already imported in your ZM todo context");
                }
            }

            annotatedRows.add(row.withIssues(issues));
        }

        return annotatedRows;
    }

    private ImportedZmTodoEntity toEntity(
            ParsedPreviewRow row,
            ParsedImport parsedImport,
            String batchId,
            String uploadedBy,
            Instant uploadedAt
    ) {
        return ImportedZmTodoEntity.builder()
                .importBatchId(batchId)
                .sourceType(ImportedTodoSourceType.ZM_TODO_INJECTION)
                .uploadedBy(uploadedBy)
                .uploadedAt(uploadedAt)
                .sourceFileName(parsedImport.sourceFileName())
                .managerFilterUsed(parsedImport.managerFilterUsed())
                .crmCase(row.crmCase())
                .crmCaseNormalized(row.crmCaseNormalized())
                .rowNumber(row.rowNumber())
                .validRow(row.readyToInject())
                .validationIssues(row.issues())
                .fxDateCrm(row.fxDateCrm())
                .contact1(row.contact1())
                .contact2(row.contact2())
                .msisdn(row.msisdn())
                .gouvernorat(row.gouvernorat())
                .gouvernoratNormalized(normalizeLookupValue(row.gouvernorat()))
                .description(row.description())
                .latitude(row.latitude())
                .longitude(row.longitude())
                .openDateSurTimos(row.openDateSurTimos())
                .openDateSurTimosDate(parseImportedDate(row.openDateSurTimos()))
                .dateAffectationStt(row.dateAffectationStt())
                .dateAffectationSttDate(parseImportedDate(row.dateAffectationStt()))
                .slaBusinessDays(calculateBusinessDays(parseImportedDate(row.openDateSurTimos()), parseImportedDate(row.dateAffectationStt())))
                .stt(row.stt())
                .sttNormalized(normalizeLookupValue(row.stt()))
                .statut(row.statut())
                .zoneManager(row.zoneManager())
                .zoneManagerNormalized(normalizeLookupValue(row.zoneManager()))
                .sourceValues(row.sourceValues())
                .build();
    }

    private ImportedZmTodoEntity toZmImportedEntity(
            ParsedAnalyticsRow row,
            String sourceFileName,
            String managerFilterUsed,
            String batchId,
            String uploadedBy,
            Instant uploadedAt
    ) {
        return ImportedZmTodoEntity.builder()
                .importBatchId(batchId)
                .sourceType(ImportedTodoSourceType.ZM_TODO_INJECTION)
                .uploadedBy(uploadedBy)
                .uploadedAt(uploadedAt)
                .sourceFileName(sourceFileName)
                .managerFilterUsed(managerFilterUsed)
                .crmCase(row.crmCase())
                .crmCaseNormalized(row.crmCaseNormalized())
                .rowNumber(row.rowNumber())
                .validRow(row.validRow())
                .validationIssues(row.issues())
                .fxDateCrm(row.fxDateCrm())
                .contact1(row.contact1())
                .contact2(row.contact2())
                .msisdn(row.msisdn())
                .gouvernorat(row.gouvernorat())
                .gouvernoratNormalized(row.gouvernoratNormalized())
                .description(row.description())
                .latitude(row.latitude())
                .longitude(row.longitude())
                .openDateSurTimos(row.openDateSurTimos())
                .openDateSurTimosDate(row.openDateSurTimosDate())
                .dateAffectationStt(row.dateAffectationStt())
                .dateAffectationSttDate(row.dateAffectationSttDate())
                .slaBusinessDays(row.slaBusinessDays())
                .stt(row.stt())
                .sttNormalized(row.sttNormalized())
                .statut(row.statut())
                .zoneManager(row.zoneManager())
                .zoneManagerNormalized(row.zoneManagerNormalized())
                .sourceValues(row.sourceValues())
                .build();
    }

    private ImportedTodoPreviewRowResponse toPreviewRowResponse(ParsedPreviewRow row) {
        return new ImportedTodoPreviewRowResponse(
                row.rowNumber(),
                row.crmCase(),
                row.fxDateCrm(),
                row.contact1(),
                row.contact2(),
                row.msisdn(),
                row.gouvernorat(),
                row.description(),
                row.latitude(),
                row.longitude(),
                row.openDateSurTimos(),
                row.dateAffectationStt(),
                row.stt(),
                row.statut(),
                row.zoneManager(),
                row.issues(),
                row.readyToInject()
        );
    }

    private ImportedTodoListItemResponse toListItemResponse(ImportedZmTodoEntity entity) {
        return new ImportedTodoListItemResponse(
                entity.getId(),
                entity.getImportBatchId(),
                entity.getSourceFileName(),
                entity.getManagerFilterUsed(),
                entity.getRowNumber(),
                entity.getValidRow(),
                entity.getValidationIssues() == null ? List.of() : List.copyOf(entity.getValidationIssues()),
                entity.getCrmCase(),
                entity.getFxDateCrm(),
                entity.getGouvernorat(),
                entity.getDescription(),
                entity.getStt(),
                entity.getStatut(),
                entity.getZoneManager(),
                entity.getUploadedAt()
        );
    }

    private AdminPerformanceImportPreviewRowResponse toAdminPreviewRowResponse(ParsedAnalyticsRow row) {
        return new AdminPerformanceImportPreviewRowResponse(
                row.rowNumber(),
                row.crmCase(),
                row.zoneManager(),
                row.gouvernorat(),
                row.stt(),
                row.statut(),
                row.openDateSurTimos(),
                row.dateAffectationStt(),
                row.slaBusinessDays(),
                row.issues(),
                row.validRow()
        );
    }

    private ImportedTodoEntity toAdminEntity(
            ParsedAnalyticsRow row,
            String sourceFileName,
            String batchId,
            String uploadedBy,
            Instant uploadedAt
    ) {
        return ImportedTodoEntity.builder()
                .importBatchId(batchId)
                .sourceType(ImportedTodoSourceType.ADMIN_PERFORMANCE_IMPORT)
                .uploadedBy(uploadedBy)
                .uploadedAt(uploadedAt)
                .sourceFileName(sourceFileName)
                .crmCase(row.crmCase())
                .crmCaseNormalized(row.crmCaseNormalized())
                .rowNumber(row.rowNumber())
                .validRow(row.validRow())
                .validationIssues(row.issues())
                .fxDateCrm(row.fxDateCrm())
                .contact1(row.contact1())
                .contact2(row.contact2())
                .msisdn(row.msisdn())
                .gouvernorat(row.gouvernorat())
                .gouvernoratNormalized(row.gouvernoratNormalized())
                .description(row.description())
                .latitude(row.latitude())
                .longitude(row.longitude())
                .openDateSurTimos(row.openDateSurTimos())
                .openDateSurTimosDate(row.openDateSurTimosDate())
                .dateAffectationStt(row.dateAffectationStt())
                .dateAffectationSttDate(row.dateAffectationSttDate())
                .slaBusinessDays(row.slaBusinessDays())
                .stt(row.stt())
                .sttNormalized(row.sttNormalized())
                .statut(row.statut())
                .zoneManager(row.zoneManager())
                .zoneManagerNormalized(row.zoneManagerNormalized())
                .sourceValues(row.sourceValues())
                .build();
    }

    private List<ZmTodoAnalyticsSttItemResponse> buildZmTodoSttBreakdown(List<ParsedAnalyticsRow> rows) {
        long totalCases = rows.size();
        Map<String, List<ParsedAnalyticsRow>> grouped = groupAnalyticsRows(
                rows,
                ParsedAnalyticsRow::sttNormalized
        );

        List<ZmTodoAnalyticsSttItemResponse> items = new ArrayList<>();
        for (List<ParsedAnalyticsRow> group : grouped.values()) {
            List<Integer> slaValues = group.stream()
                    .map(ParsedAnalyticsRow::slaBusinessDays)
                    .filter(value -> value != null)
                    .toList();
            long delayedCases = group.stream().filter(this::isDelayedZmAnalyticsRow).count();
            long geleCases = group.stream().filter(row -> isGeleStatus(row.statut())).count();

            items.add(new ZmTodoAnalyticsSttItemResponse(
                    displayAnalyticsValue(group, ParsedAnalyticsRow::stt),
                    group.size(),
                    totalCases == 0 ? 0d : round2((group.size() * 100d) / totalCases),
                    slaValues.isEmpty() ? null : round2(slaValues.stream().mapToInt(Integer::intValue).average().orElse(0d)),
                    delayedCases,
                    group.isEmpty() ? 0d : round2((delayedCases * 100d) / group.size()),
                    geleCases,
                    group.isEmpty() ? 0d : round2((geleCases * 100d) / group.size())
            ));
        }

        items.sort(Comparator
                .comparingLong(ZmTodoAnalyticsSttItemResponse::caseCount).reversed()
                .thenComparing(item -> item.sttName() == null ? "" : item.sttName(), String.CASE_INSENSITIVE_ORDER));
        return items;
    }

    private List<ZmTodoAnalyticsGovernorateItemResponse> buildZmTodoGovernorateBreakdown(List<ParsedAnalyticsRow> rows) {
        long totalCases = rows.size();
        Map<String, List<ParsedAnalyticsRow>> grouped = groupAnalyticsRows(
                rows,
                ParsedAnalyticsRow::gouvernoratNormalized
        );

        List<ZmTodoAnalyticsGovernorateItemResponse> items = new ArrayList<>();
        for (List<ParsedAnalyticsRow> group : grouped.values()) {
            List<Integer> slaValues = group.stream()
                    .map(ParsedAnalyticsRow::slaBusinessDays)
                    .filter(value -> value != null)
                    .toList();
            long delayedCases = group.stream().filter(this::isDelayedZmAnalyticsRow).count();
            long geleCases = group.stream().filter(row -> isGeleStatus(row.statut())).count();

            Map<String, List<ParsedAnalyticsRow>> byStt = groupAnalyticsRows(
                    group,
                    ParsedAnalyticsRow::sttNormalized
            );
            ZmTodoAnalyticsSttItemResponse topStt = byStt.values().stream()
                    .map(sttGroup -> new ZmTodoAnalyticsSttItemResponse(
                            displayAnalyticsValue(sttGroup, ParsedAnalyticsRow::stt),
                            sttGroup.size(),
                            group.isEmpty() ? 0d : round2((sttGroup.size() * 100d) / group.size()),
                            null,
                            0L,
                            0d,
                            0L,
                            0d
                    ))
                    .max(Comparator
                            .comparingLong(ZmTodoAnalyticsSttItemResponse::caseCount)
                            .thenComparing(item -> item.sttName() == null ? "" : item.sttName(), String.CASE_INSENSITIVE_ORDER))
                    .orElse(null);

            items.add(new ZmTodoAnalyticsGovernorateItemResponse(
                    displayAnalyticsValue(group, ParsedAnalyticsRow::gouvernorat),
                    group.size(),
                    totalCases == 0 ? 0d : round2((group.size() * 100d) / totalCases),
                    slaValues.isEmpty() ? null : round2(slaValues.stream().mapToInt(Integer::intValue).average().orElse(0d)),
                    delayedCases,
                    group.isEmpty() ? 0d : round2((delayedCases * 100d) / group.size()),
                    geleCases,
                    group.isEmpty() ? 0d : round2((geleCases * 100d) / group.size()),
                    topStt != null ? topStt.sttName() : null,
                    topStt != null ? topStt.caseCount() : 0L
            ));
        }

        items.sort(Comparator
                .comparingLong(ZmTodoAnalyticsGovernorateItemResponse::caseCount).reversed()
                .thenComparing(item -> item.gouvernorat() == null ? "" : item.gouvernorat(), String.CASE_INSENSITIVE_ORDER));
        return items;
    }

    private List<ZmTodoAnalyticsInvalidReasonResponse> summarizeInvalidReasons(List<ParsedAnalyticsRow> invalidRows) {
        Map<String, Long> summary = new LinkedHashMap<>();
        Map<String, LinkedHashSet<String>> crmCasesByReason = new LinkedHashMap<>();

        for (ParsedAnalyticsRow row : invalidRows) {
            List<String> issues = row.issues().isEmpty() ? List.of("Unknown validation issue") : row.issues();
            for (String issue : issues) {
                summary.merge(issue, 1L, Long::sum);
                crmCasesByReason
                        .computeIfAbsent(issue, ignored -> new LinkedHashSet<>())
                        .add(hasText(row.crmCase()) ? row.crmCase().trim() : "Row #" + row.rowNumber());
            }
        }

        return summary.entrySet().stream()
                .map(entry -> new ZmTodoAnalyticsInvalidReasonResponse(
                        entry.getKey(),
                        entry.getValue(),
                        new ArrayList<>(crmCasesByReason.getOrDefault(entry.getKey(), new LinkedHashSet<>()))
                ))
                .sorted(Comparator
                        .comparingLong(ZmTodoAnalyticsInvalidReasonResponse::count).reversed()
                        .thenComparing(ZmTodoAnalyticsInvalidReasonResponse::reason, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<ZmTodoAnalyticsAlertResponse> buildZmTodoAlerts(
            ResolvedZmIdentity zmIdentity,
            long totalCases,
            int matchedRows,
            int invalidMatchedRows,
            List<ZmTodoAnalyticsSttItemResponse> sttBreakdown,
            List<ZmTodoAnalyticsGovernorateItemResponse> governorateBreakdown,
            double delayedRate,
            double geleRate,
            List<ZmTodoAnalyticsInvalidReasonResponse> invalidReasonSummaries,
            List<Integer> slaValues
    ) {
        List<ZmTodoAnalyticsAlertResponse> alerts = new ArrayList<>();

        if (matchedRows == 0) {
            alerts.add(new ZmTodoAnalyticsAlertResponse(
                    "info",
                    "No matching rows",
                    "No Excel rows matched your Zone Manager identity using the key " + zmIdentity.displayManagerKey() + "."
            ));
            return alerts;
        }

        if (totalCases == 0) {
            alerts.add(new ZmTodoAnalyticsAlertResponse(
                    "warning",
                    "No analyzable rows",
                    "Matching rows were found for " + zmIdentity.fullName() + ", but all of them were excluded by validation checks."
            ));
        }

        if (!slaValues.isEmpty()) {
            double averageSla = round2(slaValues.stream().mapToInt(Integer::intValue).average().orElse(0d));
            if (averageSla > ZM_HIGH_AVERAGE_SLA_THRESHOLD_DAYS) {
                alerts.add(new ZmTodoAnalyticsAlertResponse(
                        "warning",
                        "High SLA",
                        "Your average SLA is high at " + averageSla + " business day(s)."
                ));
            }
        }

        if (delayedRate >= ZM_HIGH_DELAYED_RATE_THRESHOLD) {
            alerts.add(new ZmTodoAnalyticsAlertResponse(
                    delayedRate >= 40d ? "critical" : "warning",
                    "High delayed rate",
                    round2(delayedRate) + "% of your analyzed cases are delayed based on the SLA threshold of " + ZM_DELAYED_SLA_THRESHOLD_DAYS + " business day(s)."
            ));
        }

        if (geleRate >= ZM_HIGH_GELE_RATE_THRESHOLD) {
            alerts.add(new ZmTodoAnalyticsAlertResponse(
                    geleRate >= 25d ? "critical" : "warning",
                    "High Gelé rate",
                    round2(geleRate) + "% of your analyzed cases are currently marked as Gelé."
            ));
        }

        ZmTodoAnalyticsSttItemResponse topStt = sttBreakdown.isEmpty() ? null : sttBreakdown.get(0);
        if (topStt != null && topStt.percentage() >= ZM_STT_OVERLOAD_THRESHOLD) {
            alerts.add(new ZmTodoAnalyticsAlertResponse(
                    "warning",
                    "STT overload",
                    "STT " + topStt.sttName() + " handles " + topStt.percentage() + "% of your workload."
            ));
        }

        ZmTodoAnalyticsGovernorateItemResponse worstGovernorate = governorateBreakdown.stream()
                .filter(item -> item.averageSlaBusinessDays() != null)
                .max(Comparator.comparingDouble(ZmTodoAnalyticsGovernorateItemResponse::averageSlaBusinessDays))
                .orElse(null);
        if (worstGovernorate != null && worstGovernorate.averageSlaBusinessDays() != null
                && worstGovernorate.averageSlaBusinessDays() >= ZM_WORST_GOVERNORATE_ALERT_THRESHOLD_DAYS) {
            alerts.add(new ZmTodoAnalyticsAlertResponse(
                    "warning",
                    "Worst governorate SLA",
                    worstGovernorate.gouvernorat() + " has your weakest SLA at " + worstGovernorate.averageSlaBusinessDays() + " business day(s)."
            ));
        }

        if (invalidMatchedRows > 0 && !invalidReasonSummaries.isEmpty()) {
            ZmTodoAnalyticsInvalidReasonResponse topReason = invalidReasonSummaries.get(0);
            alerts.add(new ZmTodoAnalyticsAlertResponse(
                    "info",
                    "Rows excluded from analysis",
                    invalidMatchedRows + " matched row(s) were excluded. Most common reason: " + topReason.reason() + "."
            ));
        }

        return alerts;
    }

    private List<String> buildZmTodoInsights(
            ResolvedZmIdentity zmIdentity,
            long totalCases,
            List<ZmTodoAnalyticsSttItemResponse> sttBreakdown,
            List<ZmTodoAnalyticsGovernorateItemResponse> governorateBreakdown,
            double delayedRate,
            double geleRate,
            int invalidMatchedRows,
            List<ZmTodoAnalyticsInvalidReasonResponse> invalidReasonSummaries,
            List<Integer> slaValues
    ) {
        List<String> insights = new ArrayList<>();

        if (totalCases == 0) {
            insights.add("No valid analyzable rows were found for " + zmIdentity.fullName() + " in the uploaded file.");
            if (invalidMatchedRows > 0 && !invalidReasonSummaries.isEmpty()) {
                insights.add("Matched rows exist, but they were excluded mainly because " + invalidReasonSummaries.get(0).reason().toLowerCase(Locale.ROOT) + ".");
            }
            return insights;
        }

        insights.add("You currently have " + totalCases + " valid case(s) across " + governorateBreakdown.size() + " governorate(s) in this uploaded file.");

        ZmTodoAnalyticsSttItemResponse topStt = sttBreakdown.isEmpty() ? null : sttBreakdown.get(0);
        if (topStt != null) {
            insights.add("Most of your workload is assigned to STT " + topStt.sttName() + " with " + topStt.caseCount() + " case(s), representing " + topStt.percentage() + "% of your total volume.");
        }

        ZmTodoAnalyticsGovernorateItemResponse bestGovernorate = governorateBreakdown.stream()
                .filter(item -> item.averageSlaBusinessDays() != null)
                .min(Comparator.comparingDouble(ZmTodoAnalyticsGovernorateItemResponse::averageSlaBusinessDays))
                .orElse(null);
        if (bestGovernorate != null && bestGovernorate.averageSlaBusinessDays() != null) {
            insights.add(bestGovernorate.gouvernorat() + " is your best-performing governorate with an average SLA of " + bestGovernorate.averageSlaBusinessDays() + " business day(s).");
        }

        ZmTodoAnalyticsGovernorateItemResponse worstGovernorate = governorateBreakdown.stream()
                .filter(item -> item.averageSlaBusinessDays() != null)
                .max(Comparator.comparingDouble(ZmTodoAnalyticsGovernorateItemResponse::averageSlaBusinessDays))
                .orElse(null);
        if (worstGovernorate != null && worstGovernorate.averageSlaBusinessDays() != null) {
            insights.add(worstGovernorate.gouvernorat() + " is your weakest governorate on SLA with an average of " + worstGovernorate.averageSlaBusinessDays() + " business day(s).");
        }

        if (topStt != null && topStt.percentage() >= ZM_STT_OVERLOAD_THRESHOLD) {
            insights.add("Your workload is highly concentrated on one STT. Consider balancing assignments away from " + topStt.sttName() + ".");
        }

        if (!slaValues.isEmpty() && round2(slaValues.stream().mapToInt(Integer::intValue).average().orElse(0d)) <= 2d) {
            insights.add("Your overall SLA is under control, with an average at or below 2 business day(s).");
        }

        if (delayedRate >= ZM_HIGH_DELAYED_RATE_THRESHOLD) {
            insights.add("Delayed cases need attention because " + round2(delayedRate) + "% of your analyzed workload exceeded the SLA threshold.");
        }

        if (geleRate >= ZM_HIGH_GELE_RATE_THRESHOLD) {
            insights.add("The Gelé status is materially affecting your flow, with " + round2(geleRate) + "% of cases frozen.");
        }

        if (invalidMatchedRows > 0 && !invalidReasonSummaries.isEmpty()) {
            insights.add(invalidMatchedRows + " matched row(s) were left out of the study. The leading issue is " + invalidReasonSummaries.get(0).reason().toLowerCase(Locale.ROOT) + ".");
        }

        return insights;
    }

    private Map<String, List<ParsedAnalyticsRow>> groupAnalyticsRows(
            List<ParsedAnalyticsRow> rows,
            AnalyticsRowValueExtractor normalizedExtractor
    ) {
        Map<String, List<ParsedAnalyticsRow>> grouped = new LinkedHashMap<>();

        for (ParsedAnalyticsRow row : rows) {
            String normalized = normalizedExtractor.extract(row);
            if (!hasText(normalized)) {
                continue;
            }

            grouped.computeIfAbsent(normalized, ignored -> new ArrayList<>()).add(row);
        }

        return grouped;
    }

    private String displayAnalyticsValue(List<ParsedAnalyticsRow> rows, AnalyticsRowValueExtractor extractor) {
        for (ParsedAnalyticsRow row : rows) {
            String value = extractor.extract(row);
            if (hasText(value)) {
                return value.trim();
            }
        }

        return null;
    }

    private boolean isDelayedZmAnalyticsRow(ParsedAnalyticsRow row) {
        return row != null && row.slaBusinessDays() != null && row.slaBusinessDays() > ZM_DELAYED_SLA_THRESHOLD_DAYS;
    }

    private boolean isGeleStatus(String statut) {
        String normalized = normalizeSearchToken(statut);
        return normalized != null && normalized.contains("gele");
    }

    private String normalizeSearchToken(String value) {
        if (!hasText(value)) {
            return null;
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");

        return normalized.isBlank() ? null : normalized;
    }

    private ResolvedZmIdentity resolveZmIdentity(String zmEmail) {
        if (!hasText(zmEmail)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Authenticated Zone Manager email is required");
        }

        AppUser user = userRepository.findByEmail(zmEmail.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Connected Zone Manager was not found"));

        if (user.getRole() != UserRole.ROLE_ZM) {
            throw new AccessDeniedException("Current user is not a Zone Manager");
        }

        String fullName = hasText(user.getFullName()) ? user.getFullName().trim() : user.getEmail().trim();
        String excelKey = buildZoneManagerExcelKey(fullName);

        LinkedHashSet<String> normalizedCandidates = new LinkedHashSet<>();
        if (hasText(excelKey)) {
            normalizedCandidates.add(normalizeLookupValue(excelKey));
        }
        if (hasText(fullName)) {
            normalizedCandidates.add(normalizeLookupValue(fullName));
        }

        return new ResolvedZmIdentity(
                fullName,
                excelKey,
                List.copyOf(normalizedCandidates)
        );
    }

    private String buildZoneManagerExcelKey(String fullName) {
        if (!hasText(fullName)) {
            return null;
        }

        List<String> nameParts = Arrays.stream(fullName.trim().split("\\s+"))
                .filter(this::hasText)
                .toList();

        if (nameParts.isEmpty()) {
            return null;
        }

        if (nameParts.size() == 1) {
            return nameParts.get(0);
        }

        String firstName = nameParts.get(0);
        String lastName = String.join(" ", nameParts.subList(1, nameParts.size()));
        return (lastName + " " + firstName).trim();
    }

    private double round2(double value) {
        return Math.round(value * 100d) / 100d;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Excel file is required");
        }

        String sourceFileName = resolveSourceFileName(file).toLowerCase(Locale.ROOT);
        if (!sourceFileName.endsWith(".xls") && !sourceFileName.endsWith(".xlsx") && !sourceFileName.endsWith(".csv")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only .csv, .xls and .xlsx files are supported");
        }
    }

    private String resolveSourceFileName(MultipartFile file) {
        if (file == null || !hasText(file.getOriginalFilename())) {
            return "todo-import.xlsx";
        }

        return file.getOriginalFilename().trim();
    }

    private boolean isRowEmpty(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return true;
        }

        return values.stream().noneMatch(this::hasText);
    }

    private String readCellValue(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }

        return formatter.formatCellValue(cell, evaluator);
    }

    private String decodeTextContent(byte[] bytes) {
        String utf8 = new String(bytes, StandardCharsets.UTF_8).replace("\uFEFF", "");
        if (looksLikeSpreadsheetXml(utf8) || looksLikeHtml(utf8) || looksLikeDelimitedText(utf8)) {
            return utf8;
        }

        return new String(bytes, StandardCharsets.ISO_8859_1).replace("\uFEFF", "");
    }

    private boolean shouldAttemptFallback(String sourceFileName, IOException ex) {
        String lowerCaseName = sourceFileName.toLowerCase(Locale.ROOT);
        if (lowerCaseName.endsWith(".xls") || lowerCaseName.endsWith(".csv")) {
            return true;
        }

        String message = deepestMessage(ex).toLowerCase(Locale.ROOT);
        return message.contains("unsupported file type")
                || message.contains("unknown")
                || message.contains("not an ooxml")
                || message.contains("neither an ole2");
    }

    private boolean looksLikeHtml(String content) {
        String normalized = content.toLowerCase(Locale.ROOT);
        return normalized.contains("<html")
                || normalized.contains("<table")
                || normalized.contains("<tr")
                || normalized.contains("<td");
    }

    private boolean looksLikeSpreadsheetXml(String content) {
        String normalized = content.toLowerCase(Locale.ROOT);
        return normalized.contains("urn:schemas-microsoft-com:office:spreadsheet")
                || normalized.contains("<workbook")
                || normalized.contains(":workbook");
    }

    private boolean looksLikeDelimitedText(String content) {
        return content.contains("\t") || content.contains(";") || content.contains(",") || content.contains("|");
    }

    private String normalizeHeader(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeLookupValue(String value) {
        if (!hasText(value)) {
            return null;
        }

        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String canonicalTunisiaGovernorate(String rawValue) {
        String normalized = normalizeGovernorateAlias(rawValue);
        if (!hasText(normalized)) {
            return null;
        }

        String exactMatch = TUNISIA_GOVERNORATE_ALIASES.get(normalized);
        if (exactMatch != null) {
            return exactMatch;
        }

        return TUNISIA_GOVERNORATE_ALIASES.entrySet().stream()
                .filter(entry -> normalized.contains(entry.getKey()))
                .sorted((left, right) -> Integer.compare(right.getKey().length(), left.getKey().length()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private String normalizeGovernorateAlias(String value) {
        if (!hasText(value)) {
            return null;
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");

        return hasText(normalized) ? normalized : null;
    }

    private String normalizeCoordinate(String rawValue) {
        if (!hasText(rawValue)) {
            return null;
        }

        String cleaned = rawValue.trim()
                .replace("\u00B0", "")
                .replace("°", "")
                .replace(" ", "")
                .replace(',', '.');

        if (cleaned.endsWith("N") || cleaned.endsWith("n") || cleaned.endsWith("E") || cleaned.endsWith("e")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        } else if (cleaned.endsWith("S") || cleaned.endsWith("s") || cleaned.endsWith("W") || cleaned.endsWith("w")) {
            cleaned = "-" + cleaned.substring(0, cleaned.length() - 1);
        }

        return hasText(cleaned) ? cleaned : null;
    }

    private LocalDate parseImportedDate(String rawValue) {
        if (!hasText(rawValue)) {
            return null;
        }

        String trimmed = rawValue.trim();
        for (DateTimeFormatter formatter : SUPPORTED_DATE_FORMATTERS) {
            try {
                return LocalDate.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
                // Keep trying the known telecom export formats.
            }
        }

        String strippedTime = trimmed
                .replace('T', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        int firstSpaceIndex = strippedTime.indexOf(' ');
        if (firstSpaceIndex > 0) {
            String datePortion = strippedTime.substring(0, firstSpaceIndex);
            for (DateTimeFormatter formatter : SUPPORTED_DATE_FORMATTERS) {
                try {
                    return LocalDate.parse(datePortion, formatter);
                } catch (DateTimeParseException ignored) {
                    // Keep trying compact date-only fallbacks.
                }
            }
        }

        return null;
    }

    private static Map<String, String> buildTunisiaGovernorateAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        registerGovernorateAlias(aliases, "Ariana", "Ariana");
        registerGovernorateAlias(aliases, "Beja", "Beja", "Béja");
        registerGovernorateAlias(aliases, "Ben Arous", "Ben Arous");
        registerGovernorateAlias(aliases, "Bizerte", "Bizerte");
        registerGovernorateAlias(aliases, "Gabes", "Gabes", "Gabès");
        registerGovernorateAlias(aliases, "Gafsa", "Gafsa");
        registerGovernorateAlias(aliases, "Jendouba", "Jendouba");
        registerGovernorateAlias(aliases, "Kairouan", "Kairouan");
        registerGovernorateAlias(aliases, "Kasserine", "Kasserine");
        registerGovernorateAlias(aliases, "Kebili", "Kebili", "Kébili");
        registerGovernorateAlias(aliases, "Kef", "Kef", "Le Kef");
        registerGovernorateAlias(aliases, "Mahdia", "Mahdia");
        registerGovernorateAlias(aliases, "Manouba", "Manouba", "Mannouba");
        registerGovernorateAlias(aliases, "Medenine", "Medenine", "Médenine");
        registerGovernorateAlias(aliases, "Monastir", "Monastir");
        registerGovernorateAlias(aliases, "Nabeul", "Nabeul");
        registerGovernorateAlias(aliases, "Sfax", "Sfax");
        registerGovernorateAlias(aliases, "Sidi Bouzid", "Sidi Bouzid");
        registerGovernorateAlias(aliases, "Siliana", "Siliana");
        registerGovernorateAlias(aliases, "Sousse", "Sousse");
        registerGovernorateAlias(aliases, "Tataouine", "Tataouine");
        registerGovernorateAlias(aliases, "Tozeur", "Tozeur");
        registerGovernorateAlias(aliases, "Tunis", "Tunis");
        registerGovernorateAlias(aliases, "Zaghouan", "Zaghouan");
        return Map.copyOf(aliases);
    }

    private static void registerGovernorateAlias(Map<String, String> aliases, String canonical, String... variants) {
        for (String variant : variants) {
            String normalized = normalizeGovernorateAliasStatic(variant);
            if (normalized != null) {
                aliases.put(normalized, canonical);
            }
        }
    }

    private static String normalizeGovernorateAliasStatic(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");

        return normalized.isBlank() ? null : normalized;
    }

    private Integer calculateBusinessDays(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            return null;
        }

        // We count working days between the two dates, excluding Saturday and Sunday.
        // When both dates are the same working day, SLA is 0 by design for consistency.
        int businessDays = 0;
        LocalDate cursor = startDate;
        while (cursor.isBefore(endDate)) {
            cursor = cursor.plusDays(1);
            if (cursor.isAfter(endDate)) {
                break;
            }

            if (isWorkingDay(cursor)) {
                businessDays += 1;
            }
        }

        return businessDays;
    }

    private boolean isWorkingDay(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }

    private String trimmedValue(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String resolveFallbackReadError(IOException primaryEx, Exception fallbackEx, String sourceFileName) {
        String fallbackMessage = deepestMessage(fallbackEx);
        if (hasText(fallbackMessage)) {
            return sanitizeErrorMessage(fallbackMessage);
        }

        String primaryMessage = deepestMessage(primaryEx).toLowerCase(Locale.ROOT);
        if (sourceFileName.toLowerCase(Locale.ROOT).endsWith(".xls")
                && primaryMessage.contains("unsupported file type")) {
            return "The uploaded .xls file is not a native Excel workbook and no compatible HTML, Spreadsheet XML, or delimited table was detected. Open it in Excel and save it as .xlsx, then upload it again.";
        }

        return resolveExcelReadError(primaryEx);
    }

    private String resolveExcelReadError(IOException ex) {
        String detailedMessage = deepestMessage(ex);
        String normalized = hasText(detailedMessage)
                ? detailedMessage.toLowerCase(Locale.ROOT)
                : "";

        if (normalized.contains("zip bomb")) {
            return "The uploaded Excel file is too compressed for safe parsing. Re-save it as a fresh .xlsx file or split it into smaller workbooks.";
        }

        if (normalized.contains("neither an ole2 stream")
                || normalized.contains("not an ooxml stream")
                || normalized.contains("invalid header signature")
                || normalized.contains("unsupported file format")) {
            return "The uploaded file is not a valid .csv, .xls, or .xlsx import file";
        }

        if (normalized.contains("truncated")
                || normalized.contains("unexpected end")
                || normalized.contains("premature end")) {
            return "The uploaded Excel file appears incomplete or corrupted";
        }

        if (hasText(detailedMessage)) {
            return "Unable to read the uploaded Excel file: " + sanitizeErrorMessage(detailedMessage);
        }

        return "Unable to read the uploaded Excel file";
    }

    private String deepestMessage(Throwable throwable) {
        Throwable current = throwable;
        String lastMessage = null;

        while (current != null) {
            if (hasText(current.getMessage())) {
                lastMessage = current.getMessage();
            }
            current = current.getCause();
        }

        return lastMessage == null ? "" : lastMessage;
    }

    private String sanitizeErrorMessage(String message) {
        return message == null ? "" : message.replaceAll("[\\r\\n]+", " ").trim();
    }

    private boolean isElementNamed(Node node, String localName) {
        if (node == null || node.getNodeType() != Node.ELEMENT_NODE) {
            return false;
        }

        if (localName.equals(node.getLocalName())) {
            return true;
        }

        String nodeName = node.getNodeName();
        return nodeName != null && (nodeName.equals(localName) || nodeName.endsWith(":" + localName));
    }

    private int resolveIndexedPosition(Node node, int defaultPosition) {
        NamedNodeMap attributes = node.getAttributes();
        if (attributes == null) {
            return defaultPosition;
        }

        for (int index = 0; index < attributes.getLength(); index++) {
            Node attribute = attributes.item(index);
            String attributeName = attribute.getLocalName() != null
                    ? attribute.getLocalName()
                    : attribute.getNodeName();

            if (!"Index".equals(attributeName)
                    && (attribute.getNodeName() == null || !attribute.getNodeName().endsWith(":Index"))) {
                continue;
            }

            try {
                return Integer.parseInt(attribute.getNodeValue());
            } catch (NumberFormatException ignored) {
                return defaultPosition;
            }
        }

        return defaultPosition;
    }

    private String extractSpreadsheetXmlCellValue(Node cellNode) {
        NodeList children = cellNode.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (isElementNamed(child, "Data")) {
                return child.getTextContent() == null ? "" : child.getTextContent().trim();
            }
        }

        return cellNode.getTextContent() == null ? "" : cellNode.getTextContent().trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record SourceRow(
            int rowNumber,
            Map<String, String> sourceValues
    ) {
    }

    private record ParsedAnalyticsImport(
            String sourceFileName,
            int totalRows,
            int validRows,
            int invalidRows,
            int distinctZoneManagers,
            int distinctStts,
            int distinctGovernorates,
            List<ParsedAnalyticsRow> previewRows
    ) {
    }

    private record ParsedAnalyticsRow(
            int rowNumber,
            String crmCase,
            String crmCaseNormalized,
            String fxDateCrm,
            String contact1,
            String contact2,
            String msisdn,
            String gouvernorat,
            String gouvernoratNormalized,
            String description,
            String latitude,
            String longitude,
            String openDateSurTimos,
            LocalDate openDateSurTimosDate,
            String dateAffectationStt,
            LocalDate dateAffectationSttDate,
            Integer slaBusinessDays,
            String stt,
            String sttNormalized,
            String statut,
            String zoneManager,
            String zoneManagerNormalized,
            Map<String, String> sourceValues,
            List<String> issues
    ) {
        private ParsedAnalyticsRow withIssues(List<String> updatedIssues) {
            return new ParsedAnalyticsRow(
                    rowNumber,
                    crmCase,
                    crmCaseNormalized,
                    fxDateCrm,
                    contact1,
                    contact2,
                    msisdn,
                    gouvernorat,
                    gouvernoratNormalized,
                    description,
                    latitude,
                    longitude,
                    openDateSurTimos,
                    openDateSurTimosDate,
                    dateAffectationStt,
                    dateAffectationSttDate,
                    slaBusinessDays,
                    stt,
                    sttNormalized,
                    statut,
                    zoneManager,
                    zoneManagerNormalized,
                    sourceValues,
                    List.copyOf(updatedIssues)
            );
        }

        private boolean validRow() {
            return issues.isEmpty();
        }
    }

    private record ParsedImport(
            String sourceFileName,
            String managerFilterUsed,
            int totalRows,
            int matchedRows,
            int unmatchedRows,
            int invalidRows,
            int duplicateRows,
            int readyRows,
            List<ParsedPreviewRow> previewRows
    ) {
    }

    private record ParsedPreviewRow(
            int rowNumber,
            String crmCase,
            String crmCaseNormalized,
            String fxDateCrm,
            String contact1,
            String contact2,
            String msisdn,
            String gouvernorat,
            String description,
            String latitude,
            String longitude,
            String openDateSurTimos,
            String dateAffectationStt,
            String stt,
            String statut,
            String zoneManager,
            Map<String, String> sourceValues,
            List<String> issues
    ) {
        private ParsedPreviewRow withIssues(List<String> updatedIssues) {
            return new ParsedPreviewRow(
                    rowNumber,
                    crmCase,
                    crmCaseNormalized,
                    fxDateCrm,
                    contact1,
                    contact2,
                    msisdn,
                    gouvernorat,
                    description,
                    latitude,
                    longitude,
                    openDateSurTimos,
                    dateAffectationStt,
                    stt,
                    statut,
                    zoneManager,
                    sourceValues,
                    List.copyOf(updatedIssues)
            );
        }

        private boolean hasDuplicateIssue() {
            return issues.stream().anyMatch(issue -> issue.toLowerCase(Locale.ROOT).contains("duplicate"));
        }

        private boolean hasValidationIssue() {
            return !issues.isEmpty() && !issues.stream().allMatch(issue -> issue.toLowerCase(Locale.ROOT).contains("duplicate"));
        }

        private boolean readyToInject() {
            return issues.isEmpty();
        }
    }

    @FunctionalInterface
    private interface AnalyticsRowValueExtractor {
        String extract(ParsedAnalyticsRow row);
    }

    private record ResolvedZmIdentity(
            String fullName,
            String displayManagerKey,
            List<String> normalizedCandidateKeys
    ) {
        private boolean matches(String normalizedZoneManager) {
            return normalizedZoneManager != null && normalizedCandidateKeys.contains(normalizedZoneManager);
        }
    }

    private record PreparedZmAnalytics(
            List<ParsedAnalyticsRow> matchedRows,
            List<ParsedAnalyticsRow> validRows,
            List<ParsedAnalyticsRow> invalidRows,
            ZmTodoAnalyticsResponse analytics
    ) {
    }
}
