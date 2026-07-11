package com.pfe.activation_analytics.service;

import com.pfe.activation_analytics.dto.AdminActivationAnalyticsImportResponse;
import com.pfe.activation_analytics.dto.AdminActivationAnalyticsInvalidReasonResponse;
import com.pfe.activation_analytics.dto.AdminActivationAnalyticsPreviewResponse;
import com.pfe.activation_analytics.dto.AdminActivationAnalyticsPreviewRowResponse;
import com.pfe.activation_analytics.entity.ActivationAnalyticsRowEntity;
import com.pfe.activation_analytics.model.ActivationImportProcessingMode;
import com.pfe.activation_analytics.model.ActivationImportSharedResult;
import com.pfe.activation_analytics.model.ActivationImportSharedRow;
import com.pfe.case_management.enums.CaseType;
import com.pfe.metrics.BusinessMetrics;
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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ActivationAnalyticsImportService {

    private static final Logger log = LoggerFactory.getLogger(ActivationAnalyticsImportService.class);
    private static final int VALID_PREVIEW_LIMIT = 80;
    private static final int INVALID_ROW_PAGE_SIZE = 50;
    private static final List<String> REQUIRED_COLUMNS = List.of("CRM_CASE");
    private static final Map<String, List<String>> COLUMN_ALIASES = buildColumnAliases();

    static {
        ZipSecureFile.setMinInflateRatio(0.0001d);
    }

    private final MongoTemplate mongoTemplate;
    private final ActivationDistributionEligibilityService eligibilityService;
    private final ActivationAnalyticsBatchProcessingService batchProcessingService;
    private final ActivationDistributionProcessingService distributionProcessingService;
    private final BusinessMetrics businessMetrics;

    public AdminActivationAnalyticsPreviewResponse preview(MultipartFile file, String uploadedBy) {
        long totalStart = System.nanoTime();
        ActivationImportSharedResult sharedResult = parseWorkbook(file, uploadedBy);

        List<ActivationImportSharedRow> validRows = sharedResult.analyticsRowsToStore();
        List<ActivationImportSharedRow> invalidRows = sharedResult.invalidRowsOnly();

        AdminActivationAnalyticsPreviewResponse response = new AdminActivationAnalyticsPreviewResponse(
                sharedResult.sourceFileName(),
                sharedResult.totalRows(),
                sharedResult.processableRows(),
                sharedResult.validNewRows(),
                sharedResult.duplicateRows(),
                sharedResult.invalidRows(),
                sharedResult.distinctStts(),
                sharedResult.distinctGovernorates(),
                sharedResult.enCoursRows(),
                sharedResult.effectueRows(),
                sharedResult.termineRows(),
                sharedResult.actionableRows(),
                INVALID_ROW_PAGE_SIZE,
                buildInvalidReasonSummaries(invalidRows),
                validRows.stream()
                        .limit(VALID_PREVIEW_LIMIT)
                        .map(this::toPreviewRow)
                        .toList(),
                invalidRows.stream()
                        .map(this::toPreviewRow)
                        .toList()
        );

        log.info(
                "activation_import_preview file={} uploadedBy={} totalRows={} processableRows={} validNewRows={} duplicateRows={} invalidRows={} totalMs={}",
                sharedResult.sourceFileName(),
                uploadedBy,
                sharedResult.totalRows(),
                sharedResult.processableRows(),
                sharedResult.validNewRows(),
                sharedResult.duplicateRows(),
                sharedResult.invalidRows(),
                elapsedMs(totalStart)
        );
        return response;
    }

    public AdminActivationAnalyticsImportResponse importFile(
            MultipartFile file,
            String uploadedBy,
            ActivationImportProcessingMode mode
    ) {
        long totalStart = System.nanoTime();
        // See PlainteAnalyticsImportService.importFile's identical wrap for
        // why: observes and re-throws, never swallows — the exact same
        // exceptions propagate as before this was added.
        try {
            ActivationImportSharedResult sharedResult = parseWorkbook(file, uploadedBy);

            ActivationAnalyticsBatchProcessingService.StoredAnalyticsBatch storedBatch = null;
            if (mode.analyticsEnabled()) {
                storedBatch = batchProcessingService.store(sharedResult, uploadedBy);
            }

            var distributionSummary = mode.distributionEnabled()
                    ? distributionProcessingService.process(sharedResult, uploadedBy)
                    : null;

            Instant uploadedAt = storedBatch != null && storedBatch.uploadedAt() != null
                    ? storedBatch.uploadedAt()
                    : Instant.now();

            AdminActivationAnalyticsImportResponse response = new AdminActivationAnalyticsImportResponse(
                    mode,
                    storedBatch != null ? storedBatch.batchId() : null,
                    sharedResult.sourceFileName(),
                    sharedResult.totalRows(),
                    sharedResult.processableRows(),
                    storedBatch != null ? storedBatch.insertedRows() : 0,
                    sharedResult.validNewRows(),
                    sharedResult.invalidRows(),
                    sharedResult.duplicateRows(),
                    sharedResult.distinctStts(),
                    sharedResult.distinctGovernorates(),
                    sharedResult.enCoursRows(),
                    sharedResult.effectueRows(),
                    sharedResult.termineRows(),
                    sharedResult.actionableRows(),
                    distributionSummary,
                    uploadedAt
            );

            log.info(
                    "activation_import_commit file={} uploadedBy={} mode={} batchId={} insertedRows={} processableRows={} actionableRows={} duplicateRows={} invalidRows={} totalMs={}",
                    sharedResult.sourceFileName(),
                    uploadedBy,
                    mode,
                    storedBatch != null ? storedBatch.batchId() : null,
                    storedBatch != null ? storedBatch.insertedRows() : 0,
                    sharedResult.processableRows(),
                    sharedResult.actionableRows(),
                    sharedResult.duplicateRows(),
                    sharedResult.invalidRows(),
                    elapsedMs(totalStart)
            );
            return response;
        } catch (RuntimeException e) {
            businessMetrics.recordExcelImportFailure("activation");
            throw e;
        } finally {
            businessMetrics.recordExcelImportDuration("activation", System.nanoTime() - totalStart);
        }
    }

    private ActivationImportSharedResult parseWorkbook(MultipartFile file, String uploadedBy) {
        validateFile(file);

        String sourceFileName = resolveSourceFileName(file);
        long parseStart = System.nanoTime();

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

            long extractionStart = System.nanoTime();
            List<SourceRow> sourceRows = new ArrayList<>();
            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                Map<String, String> sourceValues = extractSourceValues(row, headerIndexes, formatter, evaluator);
                if (sourceValues.isEmpty() || isRowEmpty(sourceValues.values())) {
                    continue;
                }

                sourceRows.add(new SourceRow(rowIndex + 1, sourceValues));
            }

            long extractionMs = elapsedMs(extractionStart);
            log.info(
                    "activation_import_parse file={} uploadedBy={} parser=poi extractedRows={} extractionMs={}",
                    sourceFileName,
                    uploadedBy,
                    sourceRows.size(),
                    extractionMs
            );
            return buildParsedImport(sourceFileName, sourceRows, uploadedBy);
        } catch (EncryptedDocumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password-protected Excel files are not supported", ex);
        } catch (IOException ex) {
            if (shouldAttemptFallback(sourceFileName, ex)) {
                try {
                    return parseStructuredTextWorkbook(file, sourceFileName, uploadedBy);
                } catch (Exception fallbackEx) {
                    log.warn("Failed to read activation analytics fallback for {}", sourceFileName, fallbackEx);
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            resolveFallbackReadError(ex, fallbackEx, sourceFileName),
                            fallbackEx
                    );
                }
            }

            log.warn("Failed to read activation analytics file {}", sourceFileName, ex);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, resolveExcelReadError(ex), ex);
        } finally {
            log.info(
                    "activation_import_parse_complete file={} uploadedBy={} totalParseMs={}",
                    sourceFileName,
                    uploadedBy,
                    elapsedMs(parseStart)
            );
        }
    }

    private ActivationImportSharedResult parseStructuredTextWorkbook(
            MultipartFile file,
            String sourceFileName,
            String uploadedBy
    ) throws Exception {
        long parseStart = System.nanoTime();
        byte[] bytes = file.getBytes();
        String content = decodeTextContent(bytes);
        List<String> failures = new ArrayList<>();

        if (looksLikeSpreadsheetXml(content)) {
            try {
                List<SourceRow> sourceRows = toSourceRows(parseSpreadsheetXmlMatrix(bytes));
                log.info(
                        "activation_import_parse file={} uploadedBy={} parser=spreadsheet_xml extractedRows={} extractionMs={}",
                        sourceFileName,
                        uploadedBy,
                        sourceRows.size(),
                        elapsedMs(parseStart)
                );
                return buildParsedImport(sourceFileName, sourceRows, uploadedBy);
            } catch (Exception ex) {
                failures.add(sanitizeErrorMessage(deepestMessage(ex)));
            }
        }

        if (looksLikeHtml(content)) {
            try {
                List<SourceRow> sourceRows = toSourceRows(parseHtmlMatrix(content));
                log.info(
                        "activation_import_parse file={} uploadedBy={} parser=html extractedRows={} extractionMs={}",
                        sourceFileName,
                        uploadedBy,
                        sourceRows.size(),
                        elapsedMs(parseStart)
                );
                return buildParsedImport(sourceFileName, sourceRows, uploadedBy);
            } catch (Exception ex) {
                failures.add(sanitizeErrorMessage(deepestMessage(ex)));
            }
        }

        try {
            List<SourceRow> sourceRows = toSourceRows(parseDelimitedMatrix(content));
            log.info(
                    "activation_import_parse file={} uploadedBy={} parser=delimited extractedRows={} extractionMs={}",
                    sourceFileName,
                    uploadedBy,
                    sourceRows.size(),
                    elapsedMs(parseStart)
            );
            return buildParsedImport(sourceFileName, sourceRows, uploadedBy);
        } catch (Exception ex) {
            failures.add(sanitizeErrorMessage(deepestMessage(ex)));
        }

        String summary = failures.stream()
                .filter(ActivationAnalyticsSupport::hasText)
                .distinct()
                .collect(Collectors.joining(" | "));

        throw new IOException(ActivationAnalyticsSupport.hasText(summary)
                ? summary
                : "Unsupported import file content. Open the file in Excel and save it as .xlsx or .csv, then upload it again.");
    }

    private ActivationImportSharedResult buildParsedImport(String sourceFileName, List<SourceRow> sourceRows, String uploadedBy) {
        long duplicateLookupStart = System.nanoTime();
        Set<String> existingCrmCases = findExistingCrmCases(uploadedBy);
        long duplicateLookupMs = elapsedMs(duplicateLookupStart);
        long classificationStart = System.nanoTime();
        ImportValueCache cache = new ImportValueCache();
        Set<String> seenInFile = new LinkedHashSet<>(Math.max(16, sourceRows.size()));
        Set<String> distinctStts = new LinkedHashSet<>();
        Set<String> distinctGovernorates = new LinkedHashSet<>();
        List<ActivationImportSharedRow> rows = new ArrayList<>(sourceRows.size());
        List<ActivationImportSharedRow> analyticsRows = new ArrayList<>(sourceRows.size());
        List<ActivationImportSharedRow> processableRows = new ArrayList<>(sourceRows.size());
        List<ActivationImportSharedRow> invalidRowsOnly = new ArrayList<>();
        int duplicateRows = 0;
        int invalidRows = 0;
        int enCoursRows = 0;
        int effectueRows = 0;
        int termineRows = 0;
        int actionableRows = 0;

        for (SourceRow sourceRow : sourceRows) {
            ActivationImportSharedRow row = buildParsedRow(sourceRow.rowNumber(), sourceRow.sourceValues(), cache);
            boolean duplicateInFile = false;
            boolean duplicateInDataset = false;

            if (ActivationAnalyticsSupport.hasText(row.crmCaseNormalized())) {
                duplicateInFile = !seenInFile.add(row.crmCaseNormalized());
                duplicateInDataset = existingCrmCases.contains(row.crmCaseNormalized());
            }

            ActivationImportSharedRow finalRow = row.withDuplicateFlags(duplicateInFile, duplicateInDataset);
            rows.add(finalRow);

            if (finalRow.analyticsInsertableRow()) {
                analyticsRows.add(finalRow);
            }

            if (finalRow.distributionCandidateRow()) {
                processableRows.add(finalRow);
                if (ActivationAnalyticsSupport.hasText(finalRow.nameStt())) {
                    distinctStts.add(finalRow.nameStt());
                }
                if (ActivationAnalyticsSupport.hasText(finalRow.gouvernorat())) {
                    distinctGovernorates.add(finalRow.gouvernorat());
                }
                if (eligibilityService.isEnCours(finalRow)) {
                    enCoursRows += 1;
                }
                if (eligibilityService.isEffectue(finalRow)) {
                    effectueRows += 1;
                }
                if (eligibilityService.isSourceTerminated(finalRow)) {
                    termineRows += 1;
                }
                if (eligibilityService.isActionable(finalRow)) {
                    actionableRows += 1;
                }
            }

            if (finalRow.duplicateOnlyRow()) {
                duplicateRows += 1;
            } else if (finalRow.invalidOnlyRow()) {
                invalidRows += 1;
                invalidRowsOnly.add(finalRow);
            }
        }

        int distinctSttCount = distinctStts.size();
        int distinctGovernorateCount = distinctGovernorates.size();

        log.info(
                "activation_import_classification file={} uploadedBy={} totalRows={} existingCrmCases={} duplicateLookupMs={} classificationMs={} processableRows={} validNewRows={} duplicateRows={} invalidRows={} actionableRows={} distinctStts={} distinctGovernorates={}",
                sourceFileName,
                uploadedBy,
                sourceRows.size(),
                existingCrmCases.size(),
                duplicateLookupMs,
                elapsedMs(classificationStart),
                processableRows.size(),
                analyticsRows.size(),
                duplicateRows,
                invalidRows,
                actionableRows,
                distinctSttCount,
                distinctGovernorateCount
        );

        return new ActivationImportSharedResult(
                sourceFileName,
                sourceRows.size(),
                processableRows.size(),
                analyticsRows.size(),
                invalidRows,
                duplicateRows,
                distinctSttCount,
                distinctGovernorateCount,
                enCoursRows,
                effectueRows,
                termineRows,
                actionableRows,
                rows,
                analyticsRows,
                processableRows,
                invalidRowsOnly
        );
    }

    private ActivationImportSharedRow buildParsedRow(int rowNumber, Map<String, String> sourceValues, ImportValueCache cache) {
        String crmCase = cache.clean(sourceValues.get("CRM_CASE"));
        if (!ActivationAnalyticsSupport.hasText(crmCase)) {
            return buildParsedRow(rowNumber, sourceValues, cache, crmCase, List.of("CRM_CASE is required"));
        }

        return buildParsedRow(rowNumber, sourceValues, cache, crmCase, List.of());
    }

    private ActivationImportSharedRow buildParsedRow(
            int rowNumber,
            Map<String, String> sourceValues,
            ImportValueCache cache,
            String crmCase,
            List<String> issues
    ) {
        String gouvernorat = cache.canonicalGovernorate(sourceValues.get("GOUVERNORAT"));
        String nameStt = cache.normalizeSttName(sourceValues.get("NAME_STT"));

        String dateCreationCrm = cache.clean(sourceValues.get("DATE_CREATION_CRM"));
        String openingDateSurTimos = cache.clean(sourceValues.get("OPENING_DATE_SUR_TIMOS"));
        String dateAffectationStt = cache.clean(sourceValues.get("DATE_AFFECTATION_STT"));
        String datePriseRdv = cache.clean(sourceValues.get("DATE_PRISE_RDV"));
        String dateFinTrv = cache.clean(sourceValues.get("DATE_FIN_TRV"));
        LocalDate dateCreationCrmDate = cache.parseDate(dateCreationCrm);
        LocalDate openingDateSurTimosDate = cache.parseDate(openingDateSurTimos);
        LocalDate dateAffectationSttDate = cache.parseDate(dateAffectationStt);
        LocalDate datePriseRdvDate = cache.parseDate(datePriseRdv);
        LocalDate dateFinTrvDate = cache.parseDate(dateFinTrv);
        String latitudeSite = cache.clean(sourceValues.get("LATITUDE_SITE"));
        String longitudeSite = cache.clean(sourceValues.get("LONGITUDE_SITE"));
        String metrageCable = cache.clean(sourceValues.get("METRAGE_CABLE"));
        String nombreDePv = cache.clean(sourceValues.get("NOMBRE_DE_PV"));
        String debitDl = cache.clean(sourceValues.get("DEBIT_DL"));
        String debitUl = cache.clean(sourceValues.get("DEBIT_UL"));
        String rsrp = cache.clean(sourceValues.get("RSRP"));
        String rsrq = cache.clean(sourceValues.get("RSRQ"));
        String sinr = cache.clean(sourceValues.get("SINR"));

        return new ActivationImportSharedRow(
                rowNumber,
                crmCase,
                cache.normalizeLookup(crmCase),
                dateCreationCrm,
                dateCreationCrmDate,
                openingDateSurTimos,
                openingDateSurTimosDate,
                cache.clean(sourceValues.get("STATUT")),
                cache.clean(sourceValues.get("CLIENT")),
                cache.clean(sourceValues.get("CONTACT_CLIENT")),
                cache.clean(sourceValues.get("COMMENTAIRE")),
                cache.clean(sourceValues.get("MSISDN")),
                latitudeSite,
                cache.parseCoordinate(latitudeSite),
                longitudeSite,
                cache.parseCoordinate(longitudeSite),
                cache.clean(sourceValues.get("LOCALITE")),
                cache.clean(sourceValues.get("DELEGATION")),
                gouvernorat,
                cache.normalizeLookup(gouvernorat),
                cache.clean(sourceValues.get("OFFRE")),
                cache.clean(sourceValues.get("DES_PACK")),
                nameStt,
                cache.normalizeLookup(nameStt),
                dateAffectationStt,
                dateAffectationSttDate,
                cache.clean(sourceValues.get("REP_RDV")),
                datePriseRdv,
                datePriseRdvDate,
                cache.clean(sourceValues.get("CAUSE_INJOIGNABILITE")),
                cache.clean(sourceValues.get("CAUSE_ABANDON")),
                cache.clean(sourceValues.get("DETAIL")),
                cache.clean(sourceValues.get("CMT_RDV")),
                cache.clean(sourceValues.get("REP_TRAVAUX_STT")),
                dateFinTrv,
                dateFinTrvDate,
                cache.clean(sourceValues.get("CMT_TRV_STT")),
                cache.clean(sourceValues.get("REP_ZM")),
                cache.clean(sourceValues.get("CMT_ZM")),
                metrageCable,
                cache.parseDouble(metrageCable),
                nombreDePv,
                cache.parseDouble(nombreDePv),
                debitDl,
                cache.parseDouble(debitDl),
                debitUl,
                cache.parseDouble(debitUl),
                rsrp,
                cache.parseDouble(rsrp),
                rsrq,
                cache.parseDouble(rsrq),
                sinr,
                cache.parseDouble(sinr),
                cache.clean(sourceValues.get("SITESECTEUR")),
                cache.clean(sourceValues.get("TYPE_OFFRE_SPEED")),
                cache.clean(sourceValues.get("TYPE_CLIENT")),
                cache.clean(sourceValues.get("SOURCE")),
                cache.clean(sourceValues.get("DESCRIPTION")),
                ActivationAnalyticsSupport.calculateBusinessDays(
                        openingDateSurTimosDate,
                        dateAffectationSttDate
                ),
                ActivationAnalyticsSupport.calculateBusinessDays(
                        dateAffectationSttDate,
                        datePriseRdvDate
                ),
                ActivationAnalyticsSupport.calculateBusinessDays(
                        datePriseRdvDate,
                        dateFinTrvDate
                ),
                ActivationAnalyticsSupport.calculateBusinessDays(
                        openingDateSurTimosDate,
                        dateFinTrvDate
                ),
                List.copyOf(issues),
                false,
                false
        );
    }

    private Set<String> findExistingCrmCases(String uploadedBy) {
        if (!ActivationAnalyticsSupport.hasText(uploadedBy)) {
            return Set.of();
        }

        Query query = Query.query(Criteria.where("uploadedBy").is(uploadedBy).and("caseType").is(CaseType.ACTIVATION));
        List<String> existingCrmCases = mongoTemplate.query(ActivationAnalyticsRowEntity.class)
                .distinct("crmCaseNormalized")
                .matching(query)
                .as(String.class)
                .all();

        return existingCrmCases.stream()
                .filter(ActivationAnalyticsSupport::hasText)
                .collect(Collectors.toSet());
    }

    private AdminActivationAnalyticsPreviewRowResponse toPreviewRow(ActivationImportSharedRow row) {
        return new AdminActivationAnalyticsPreviewRowResponse(
                row.rowNumber(),
                row.crmCase(),
                row.gouvernorat(),
                row.nameStt(),
                row.statut(),
                row.assignmentBusinessDays(),
                row.rdvBusinessDays(),
                row.executionBusinessDays(),
                row.endToEndBusinessDays(),
                row.issues(),
                row.analyticsInsertableRow(),
                row.category()
        );
    }

    private List<AdminActivationAnalyticsInvalidReasonResponse> buildInvalidReasonSummaries(
            List<ActivationImportSharedRow> invalidRows
    ) {
        Map<String, Long> counts = new LinkedHashMap<>();

        for (ActivationImportSharedRow row : invalidRows) {
            List<String> issues = row.issues().isEmpty()
                    ? List.of("Unknown validation issue")
                    : row.issues();
            for (String issue : issues) {
                counts.merge(issue, 1L, Long::sum);
            }
        }

        return counts.entrySet().stream()
                .map(entry -> new AdminActivationAnalyticsInvalidReasonResponse(entry.getKey(), entry.getValue()))
                .sorted(Comparator
                        .comparingLong(AdminActivationAnalyticsInvalidReasonResponse::count).reversed()
                        .thenComparing(AdminActivationAnalyticsInvalidReasonResponse::reason, String.CASE_INSENSITIVE_ORDER))
                .toList();
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

    private Map<String, Integer> resolveHeaderIndexes(Row headerRow, DataFormatter formatter, FormulaEvaluator evaluator) {
        Map<String, Integer> rawIndexes = new LinkedHashMap<>();
        for (int cellIndex = 0; cellIndex < headerRow.getLastCellNum(); cellIndex++) {
            String rawHeader = readCellValue(headerRow.getCell(cellIndex), formatter, evaluator);
            if (!ActivationAnalyticsSupport.hasText(rawHeader)) {
                continue;
            }

            rawIndexes.putIfAbsent(ActivationAnalyticsSupport.normalizeHeader(rawHeader), cellIndex);
        }

        return mapAliases(rawIndexes);
    }

    private Map<String, Integer> resolveHeaderIndexes(List<String> headers) {
        Map<String, Integer> rawIndexes = new LinkedHashMap<>();
        for (int index = 0; index < headers.size(); index++) {
            String rawHeader = headers.get(index);
            if (!ActivationAnalyticsSupport.hasText(rawHeader)) {
                continue;
            }

            rawIndexes.putIfAbsent(ActivationAnalyticsSupport.normalizeHeader(rawHeader), index);
        }

        return mapAliases(rawIndexes);
    }

    private Map<String, Integer> mapAliases(Map<String, Integer> rawIndexes) {
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : COLUMN_ALIASES.entrySet()) {
            for (String alias : entry.getValue()) {
                Integer index = rawIndexes.get(ActivationAnalyticsSupport.normalizeHeader(alias));
                if (index != null) {
                    indexes.put(entry.getKey(), index);
                    break;
                }
            }
        }
        return indexes;
    }

    private boolean containsRequiredColumns(Map<String, Integer> headerIndexes) {
        return REQUIRED_COLUMNS.stream().allMatch(headerIndexes::containsKey);
    }

    private void validateRequiredColumns(Map<String, Integer> headerIndexes) {
        List<String> missingColumns = REQUIRED_COLUMNS.stream()
                .filter(column -> !headerIndexes.containsKey(column))
                .toList();

        if (!missingColumns.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Missing required column(s): " + String.join(", ", missingColumns)
            );
        }
    }

    private Map<String, String> extractSourceValues(Row row, Map<String, Integer> headerIndexes, DataFormatter formatter, FormulaEvaluator evaluator) {
        Map<String, String> values = new LinkedHashMap<>();
        if (row == null) {
            return values;
        }

        for (String column : COLUMN_ALIASES.keySet()) {
            Integer cellIndex = headerIndexes.get(column);
            if (cellIndex == null) {
                values.put(column, null);
                continue;
            }

            values.put(column, readCellValue(row.getCell(cellIndex), formatter, evaluator));
        }

        return values;
    }

    private Map<String, String> extractSourceValues(List<String> rowValues, Map<String, Integer> headerIndexes) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String column : COLUMN_ALIASES.keySet()) {
            Integer index = headerIndexes.get(column);
            String value = index != null && index < rowValues.size() ? rowValues.get(index) : null;
            values.put(column, value);
        }
        return values;
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

        throw new IOException("No HTML table with the required activation analytics columns was found");
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

        throw new IOException("No Spreadsheet XML table with the required activation analytics columns was found");
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

        throw new IOException("No supported delimited table with the required activation analytics columns was found");
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
        if (file == null || !ActivationAnalyticsSupport.hasText(file.getOriginalFilename())) {
            return "activation-analytics.xlsx";
        }
        return file.getOriginalFilename().trim();
    }

    private boolean isRowEmpty(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return true;
        }
        return values.stream().noneMatch(ActivationAnalyticsSupport::hasText);
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

    private String resolveFallbackReadError(IOException primaryEx, Exception fallbackEx, String sourceFileName) {
        String fallbackMessage = deepestMessage(fallbackEx);
        if (ActivationAnalyticsSupport.hasText(fallbackMessage)) {
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
        String normalized = ActivationAnalyticsSupport.hasText(detailedMessage)
                ? detailedMessage.toLowerCase(Locale.ROOT)
                : "";

        if (normalized.contains("zip bomb")) {
            return "The uploaded Excel file is compressed in a way that looks unsafe to the parser. Please resave it in Excel as a fresh .xlsx file and upload it again.";
        }

        if (normalized.contains("invalid header signature")) {
            return "The uploaded file is not a valid .xls or .xlsx workbook. Open it in Excel and save it again before importing.";
        }

        if (ActivationAnalyticsSupport.hasText(detailedMessage)) {
            return "Unable to read the uploaded Excel file: " + sanitizeErrorMessage(detailedMessage);
        }

        return "The uploaded file is not a valid .csv, .xls, or .xlsx import file";
    }

    private String deepestMessage(Throwable throwable) {
        Throwable current = throwable;
        String lastMessage = null;
        while (current != null) {
            if (ActivationAnalyticsSupport.hasText(current.getMessage())) {
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

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private static Map<String, List<String>> buildColumnAliases() {
        Map<String, List<String>> aliases = new LinkedHashMap<>();
        aliases.put("CRM_CASE", List.of("CRM_CASE"));
        aliases.put("DATE_CREATION_CRM", List.of("DATE_CREATION_CRM", "FX_DATE_CRM"));
        aliases.put("OPENING_DATE_SUR_TIMOS", List.of("OPENING_DATE_SUR_TIMOS", "OPEN_DATE_SUR_TIMOS"));
        aliases.put("STATUT", List.of("STATUT", "STATUS"));
        aliases.put("CLIENT", List.of("CLIENT"));
        aliases.put("CONTACT_CLIENT", List.of("CONTACT_CLIENT", "CONTACT1"));
        aliases.put("COMMENTAIRE", List.of("COMMENTAIRE", "COMMENT"));
        aliases.put("MSISDN", List.of("MSISDN"));
        aliases.put("LATITUDE_SITE", List.of("LATITUDE_SITE", "LATITUDE"));
        aliases.put("LONGITUDE_SITE", List.of("LONGITUDE_SITE", "LONGITUDE"));
        aliases.put("LOCALITE", List.of("LOCALITE"));
        aliases.put("DELEGATION", List.of("DELEGATION"));
        aliases.put("GOUVERNORAT", List.of("GOUVERNORAT"));
        aliases.put("OFFRE", List.of("OFFRE"));
        aliases.put("DES_PACK", List.of("DES_PACK"));
        aliases.put("NAME_STT", List.of("NAME_STT", "STT"));
        aliases.put("DATE_AFFECTATION_STT", List.of("DATE_AFFECTATION_STT"));
        aliases.put("REP_RDV", List.of("REP_RDV"));
        aliases.put("DATE_PRISE_RDV", List.of("DATE_PRISE_RDV"));
        aliases.put("CAUSE_INJOIGNABILITE", List.of("CAUSE_INJOIGNABILITE"));
        aliases.put("CAUSE_ABANDON", List.of("CAUSE_ABANDON"));
        aliases.put("DETAIL", List.of("DETAIL"));
        aliases.put("CMT_RDV", List.of("CMT_RDV"));
        aliases.put("REP_TRAVAUX_STT", List.of("REP_TRAVAUX_STT"));
        aliases.put("DATE_FIN_TRV", List.of("DATE_FIN_TRV"));
        aliases.put("CMT_TRV_STT", List.of("CMT_TRV_STT"));
        aliases.put("REP_ZM", List.of("REP_ZM"));
        aliases.put("CMT_ZM", List.of("CMT_ZM"));
        aliases.put("METRAGE_CABLE", List.of("METRAGE_CABLE"));
        aliases.put("NOMBRE_DE_PV", List.of("NOMBRE_DE_PV"));
        aliases.put("DEBIT_DL", List.of("DEBIT_DL"));
        aliases.put("DEBIT_UL", List.of("DEBIT_UL"));
        aliases.put("RSRP", List.of("RSRP"));
        aliases.put("RSRQ", List.of("RSRQ"));
        aliases.put("SINR", List.of("SINR"));
        aliases.put("SITESECTEUR", List.of("SITESECTEUR", "SITE_SECTEUR"));
        aliases.put("TYPE_OFFRE_SPEED", List.of("TYPE_OFFRE_SPEED", "TYPE OFFRE SPEED", "TYPEOFFRESPEED", "Type_Offre_Speed"));
        aliases.put("TYPE_CLIENT", List.of("TYPE_CLIENT", "TYPE CLIENT", "Type_Client"));
        aliases.put("SOURCE", List.of("SOURCE", "Source"));
        aliases.put("DESCRIPTION", List.of("DESCRIPTION", "description"));
        return Map.copyOf(aliases);
    }

    private record SourceRow(
            int rowNumber,
            Map<String, String> sourceValues
    ) {
    }

    private static final class ImportValueCache {
        private final Map<String, String> cleanedValues = new HashMap<>();
        private final Map<String, String> normalizedLookupValues = new HashMap<>();
        private final Map<String, String> canonicalGovernorates = new HashMap<>();
        private final Map<String, String> normalizedSttValues = new HashMap<>();
        private final Map<String, LocalDate> parsedDates = new HashMap<>();
        private final Map<String, Double> parsedDoubles = new HashMap<>();
        private final Map<String, Double> parsedCoordinates = new HashMap<>();

        String clean(String rawValue) {
            return cached(cleanedValues, rawValue, ActivationAnalyticsSupport::cleanString);
        }

        String normalizeLookup(String rawValue) {
            return cached(normalizedLookupValues, rawValue, ActivationAnalyticsSupport::normalizeLookupValue);
        }

        String canonicalGovernorate(String rawValue) {
            return cached(canonicalGovernorates, rawValue, ActivationAnalyticsSupport::canonicalGovernorate);
        }

        String normalizeSttName(String rawValue) {
            return cached(normalizedSttValues, rawValue, ActivationAnalyticsSupport::normalizeSttName);
        }

        LocalDate parseDate(String rawValue) {
            return cached(parsedDates, rawValue, ActivationAnalyticsSupport::parseDate);
        }

        Double parseDouble(String rawValue) {
            return cached(parsedDoubles, rawValue, ActivationAnalyticsSupport::parseDouble);
        }

        Double parseCoordinate(String rawValue) {
            return cached(parsedCoordinates, rawValue, ActivationAnalyticsSupport::parseCoordinate);
        }

        private <T> T cached(Map<String, T> cache, String rawValue, Function<String, T> loader) {
            if (cache.containsKey(rawValue)) {
                return cache.get(rawValue);
            }
            T value = loader.apply(rawValue);
            cache.put(rawValue, value);
            return value;
        }
    }
}
