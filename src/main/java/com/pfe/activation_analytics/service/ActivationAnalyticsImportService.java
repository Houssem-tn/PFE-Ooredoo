package com.pfe.activation_analytics.service;

import com.pfe.activation_analytics.dto.AdminActivationAnalyticsImportResponse;
import com.pfe.activation_analytics.dto.AdminActivationAnalyticsInvalidReasonResponse;
import com.pfe.activation_analytics.dto.AdminActivationAnalyticsPreviewResponse;
import com.pfe.activation_analytics.dto.AdminActivationAnalyticsPreviewRowResponse;
import com.pfe.activation_analytics.entity.ActivationAnalyticsImportBatchEntity;
import com.pfe.activation_analytics.entity.ActivationAnalyticsRowEntity;
import com.pfe.activation_analytics.repository.ActivationAnalyticsImportBatchRepository;
import com.pfe.activation_analytics.repository.ActivationAnalyticsRowRepository;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

    private final ActivationAnalyticsRowRepository rowRepository;
    private final ActivationAnalyticsImportBatchRepository batchRepository;

    public AdminActivationAnalyticsPreviewResponse preview(MultipartFile file, String uploadedBy) {
        ParsedImport parsedImport = parseWorkbook(file, uploadedBy);

        List<ParsedRow> validRows = parsedImport.validRowsToStore();
        List<ParsedRow> invalidRows = parsedImport.invalidRowsOnly();

        return new AdminActivationAnalyticsPreviewResponse(
                parsedImport.sourceFileName(),
                parsedImport.totalRows(),
                parsedImport.validNewRows(),
                parsedImport.duplicateRows(),
                parsedImport.invalidRows(),
                parsedImport.distinctStts(),
                parsedImport.distinctGovernorates(),
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
    }

    public AdminActivationAnalyticsImportResponse importFile(MultipartFile file, String uploadedBy) {
        ParsedImport parsedImport = parseWorkbook(file, uploadedBy);

        String batchId = UUID.randomUUID().toString();
        Instant uploadedAt = Instant.now();

        List<ActivationAnalyticsRowEntity> entities = parsedImport.validRowsToStore().stream()
                .map(row -> toEntity(row, parsedImport.sourceFileName(), batchId, uploadedBy, uploadedAt))
                .toList();

        if (!entities.isEmpty()) {
            rowRepository.saveAll(entities);
        }

        ActivationAnalyticsImportBatchEntity batch = ActivationAnalyticsImportBatchEntity.builder()
                .batchId(batchId)
                .uploadedBy(uploadedBy)
                .uploadedAt(uploadedAt)
                .fileName(parsedImport.sourceFileName())
                .totalRows(parsedImport.totalRows())
                .insertedRows(entities.size())
                .validRows(parsedImport.validNewRows())
                .invalidRows(parsedImport.invalidRows())
                .duplicateRows(parsedImport.duplicateRows())
                .distinctStts(parsedImport.distinctStts())
                .distinctGovernorates(parsedImport.distinctGovernorates())
                .build();
        batchRepository.save(batch);

        return new AdminActivationAnalyticsImportResponse(
                batchId,
                parsedImport.sourceFileName(),
                parsedImport.totalRows(),
                entities.size(),
                parsedImport.validNewRows(),
                parsedImport.invalidRows(),
                parsedImport.duplicateRows(),
                parsedImport.distinctStts(),
                parsedImport.distinctGovernorates(),
                uploadedAt
        );
    }

    private ParsedImport parseWorkbook(MultipartFile file, String uploadedBy) {
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
        }
    }

    private ParsedImport parseStructuredTextWorkbook(MultipartFile file, String sourceFileName, String uploadedBy) throws Exception {
        byte[] bytes = file.getBytes();
        String content = decodeTextContent(bytes);
        List<String> failures = new ArrayList<>();

        if (looksLikeSpreadsheetXml(content)) {
            try {
                return buildParsedImport(sourceFileName, toSourceRows(parseSpreadsheetXmlMatrix(bytes)), uploadedBy);
            } catch (Exception ex) {
                failures.add(sanitizeErrorMessage(deepestMessage(ex)));
            }
        }

        if (looksLikeHtml(content)) {
            try {
                return buildParsedImport(sourceFileName, toSourceRows(parseHtmlMatrix(content)), uploadedBy);
            } catch (Exception ex) {
                failures.add(sanitizeErrorMessage(deepestMessage(ex)));
            }
        }

        try {
            return buildParsedImport(sourceFileName, toSourceRows(parseDelimitedMatrix(content)), uploadedBy);
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

    private ParsedImport buildParsedImport(String sourceFileName, List<SourceRow> sourceRows, String uploadedBy) {
        Set<String> crmCases = sourceRows.stream()
                .map(row -> ActivationAnalyticsSupport.normalizeLookupValue(row.sourceValues().get("CRM_CASE")))
                .filter(ActivationAnalyticsSupport::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> existingCrmCases = findExistingCrmCases(uploadedBy, crmCases);
        Set<String> seenInFile = new LinkedHashSet<>();
        List<ParsedRow> rows = new ArrayList<>();

        for (SourceRow sourceRow : sourceRows) {
            ParsedRow row = buildParsedRow(sourceRow.rowNumber(), sourceRow.sourceValues());
            List<String> issues = new ArrayList<>(row.issues());

            if (ActivationAnalyticsSupport.hasText(row.crmCaseNormalized())) {
                if (!seenInFile.add(row.crmCaseNormalized())) {
                    issues.add("Duplicate CRM_CASE inside the selected file");
                }
                if (existingCrmCases.contains(row.crmCaseNormalized())) {
                    issues.add("Duplicate CRM_CASE already imported in the admin activation analytics dataset");
                }
            }

            rows.add(row.withIssues(issues));
        }

        List<ParsedRow> validRows = rows.stream()
                .filter(ParsedRow::validNewRow)
                .toList();
        int duplicateRows = (int) rows.stream()
                .filter(ParsedRow::duplicateOnlyRow)
                .count();
        int invalidRows = (int) rows.stream()
                .filter(ParsedRow::invalidOnlyRow)
                .count();

        int distinctStts = (int) validRows.stream()
                .map(ParsedRow::nameStt)
                .filter(ActivationAnalyticsSupport::hasText)
                .distinct()
                .count();
        int distinctGovernorates = (int) validRows.stream()
                .map(ParsedRow::gouvernorat)
                .filter(ActivationAnalyticsSupport::hasText)
                .distinct()
                .count();

        return new ParsedImport(
                sourceFileName,
                sourceRows.size(),
                validRows.size(),
                invalidRows,
                duplicateRows,
                distinctStts,
                distinctGovernorates,
                rows
        );
    }

    private ParsedRow buildParsedRow(int rowNumber, Map<String, String> sourceValues) {
        List<String> issues = new ArrayList<>();

        String crmCase = ActivationAnalyticsSupport.cleanString(sourceValues.get("CRM_CASE"));
        if (!ActivationAnalyticsSupport.hasText(crmCase)) {
            issues.add("CRM_CASE is required");
        }

        String gouvernorat = ActivationAnalyticsSupport.canonicalGovernorate(sourceValues.get("GOUVERNORAT"));
        String nameStt = ActivationAnalyticsSupport.normalizeSttName(sourceValues.get("NAME_STT"));

        String dateCreationCrm = ActivationAnalyticsSupport.cleanString(sourceValues.get("DATE_CREATION_CRM"));
        String openingDateSurTimos = ActivationAnalyticsSupport.cleanString(sourceValues.get("OPENING_DATE_SUR_TIMOS"));
        String dateAffectationStt = ActivationAnalyticsSupport.cleanString(sourceValues.get("DATE_AFFECTATION_STT"));
        String datePriseRdv = ActivationAnalyticsSupport.cleanString(sourceValues.get("DATE_PRISE_RDV"));
        String dateFinTrv = ActivationAnalyticsSupport.cleanString(sourceValues.get("DATE_FIN_TRV"));

        return new ParsedRow(
                rowNumber,
                crmCase,
                ActivationAnalyticsSupport.normalizeLookupValue(crmCase),
                dateCreationCrm,
                ActivationAnalyticsSupport.parseDate(dateCreationCrm),
                openingDateSurTimos,
                ActivationAnalyticsSupport.parseDate(openingDateSurTimos),
                ActivationAnalyticsSupport.cleanString(sourceValues.get("STATUT")),
                ActivationAnalyticsSupport.cleanString(sourceValues.get("CLIENT")),
                ActivationAnalyticsSupport.cleanString(sourceValues.get("CONTACT_CLIENT")),
                ActivationAnalyticsSupport.cleanString(sourceValues.get("COMMENTAIRE")),
                ActivationAnalyticsSupport.cleanString(sourceValues.get("MSISDN")),
                ActivationAnalyticsSupport.cleanString(sourceValues.get("LATITUDE_SITE")),
                ActivationAnalyticsSupport.parseCoordinate(sourceValues.get("LATITUDE_SITE")),
                ActivationAnalyticsSupport.cleanString(sourceValues.get("LONGITUDE_SITE")),
                ActivationAnalyticsSupport.parseCoordinate(sourceValues.get("LONGITUDE_SITE")),
                ActivationAnalyticsSupport.cleanString(sourceValues.get("LOCALITE")),
                ActivationAnalyticsSupport.cleanString(sourceValues.get("DELEGATION")),
                gouvernorat,
                ActivationAnalyticsSupport.normalizeLookupValue(gouvernorat),
                ActivationAnalyticsSupport.cleanString(sourceValues.get("OFFRE")),
                ActivationAnalyticsSupport.cleanString(sourceValues.get("DES_PACK")),
                nameStt,
                ActivationAnalyticsSupport.normalizeLookupValue(nameStt),
                dateAffectationStt,
                ActivationAnalyticsSupport.parseDate(dateAffectationStt),
                ActivationAnalyticsSupport.cleanString(sourceValues.get("REP_RDV")),
                datePriseRdv,
                ActivationAnalyticsSupport.parseDate(datePriseRdv),
                ActivationAnalyticsSupport.cleanString(sourceValues.get("CAUSE_INJOIGNABILITE")),
                ActivationAnalyticsSupport.cleanString(sourceValues.get("CAUSE_ABANDON")),
                ActivationAnalyticsSupport.cleanString(sourceValues.get("DETAIL")),
                ActivationAnalyticsSupport.cleanString(sourceValues.get("CMT_RDV")),
                ActivationAnalyticsSupport.cleanString(sourceValues.get("REP_TRAVAUX_STT")),
                dateFinTrv,
                ActivationAnalyticsSupport.parseDate(dateFinTrv),
                ActivationAnalyticsSupport.cleanString(sourceValues.get("CMT_TRV_STT")),
                ActivationAnalyticsSupport.cleanString(sourceValues.get("REP_ZM")),
                ActivationAnalyticsSupport.cleanString(sourceValues.get("CMT_ZM")),
                ActivationAnalyticsSupport.cleanString(sourceValues.get("METRAGE_CABLE")),
                ActivationAnalyticsSupport.parseDouble(sourceValues.get("METRAGE_CABLE")),
                ActivationAnalyticsSupport.cleanString(sourceValues.get("NOMBRE_DE_PV")),
                ActivationAnalyticsSupport.parseDouble(sourceValues.get("NOMBRE_DE_PV")),
                ActivationAnalyticsSupport.cleanString(sourceValues.get("DEBIT_DL")),
                ActivationAnalyticsSupport.parseDouble(sourceValues.get("DEBIT_DL")),
                ActivationAnalyticsSupport.cleanString(sourceValues.get("DEBIT_UL")),
                ActivationAnalyticsSupport.parseDouble(sourceValues.get("DEBIT_UL")),
                ActivationAnalyticsSupport.cleanString(sourceValues.get("RSRP")),
                ActivationAnalyticsSupport.parseDouble(sourceValues.get("RSRP")),
                ActivationAnalyticsSupport.cleanString(sourceValues.get("RSRQ")),
                ActivationAnalyticsSupport.parseDouble(sourceValues.get("RSRQ")),
                ActivationAnalyticsSupport.cleanString(sourceValues.get("SINR")),
                ActivationAnalyticsSupport.parseDouble(sourceValues.get("SINR")),
                ActivationAnalyticsSupport.cleanString(sourceValues.get("SITESECTEUR")),
                ActivationAnalyticsSupport.cleanString(sourceValues.get("TYPE_OFFRE_SPEED")),
                ActivationAnalyticsSupport.cleanString(sourceValues.get("TYPE_CLIENT")),
                ActivationAnalyticsSupport.cleanString(sourceValues.get("SOURCE")),
                ActivationAnalyticsSupport.cleanString(sourceValues.get("DESCRIPTION")),
                ActivationAnalyticsSupport.calculateBusinessDays(
                        ActivationAnalyticsSupport.parseDate(openingDateSurTimos),
                        ActivationAnalyticsSupport.parseDate(dateAffectationStt)
                ),
                ActivationAnalyticsSupport.calculateBusinessDays(
                        ActivationAnalyticsSupport.parseDate(dateAffectationStt),
                        ActivationAnalyticsSupport.parseDate(datePriseRdv)
                ),
                ActivationAnalyticsSupport.calculateBusinessDays(
                        ActivationAnalyticsSupport.parseDate(datePriseRdv),
                        ActivationAnalyticsSupport.parseDate(dateFinTrv)
                ),
                ActivationAnalyticsSupport.calculateBusinessDays(
                        ActivationAnalyticsSupport.parseDate(openingDateSurTimos),
                        ActivationAnalyticsSupport.parseDate(dateFinTrv)
                ),
                Map.copyOf(sourceValues),
                List.copyOf(issues)
        );
    }

    private Set<String> findExistingCrmCases(String uploadedBy, Set<String> crmCases) {
        if (!ActivationAnalyticsSupport.hasText(uploadedBy) || crmCases.isEmpty()) {
            return Set.of();
        }

        return rowRepository.findByUploadedByAndCrmCaseNormalizedIn(uploadedBy, crmCases).stream()
                .map(ActivationAnalyticsRowEntity::getCrmCaseNormalized)
                .filter(ActivationAnalyticsSupport::hasText)
                .collect(Collectors.toSet());
    }

    private ActivationAnalyticsRowEntity toEntity(
            ParsedRow row,
            String sourceFileName,
            String batchId,
            String uploadedBy,
            Instant uploadedAt
    ) {
        return ActivationAnalyticsRowEntity.builder()
                .batchId(batchId)
                .uploadedBy(uploadedBy)
                .uploadedAt(uploadedAt)
                .sourceFileName(sourceFileName)
                .rowNumber(row.rowNumber())
                .validRow(row.validNewRow())
                .validationIssues(row.issues())
                .crmCase(row.crmCase())
                .crmCaseNormalized(row.crmCaseNormalized())
                .dateCreationCrm(row.dateCreationCrm())
                .dateCreationCrmDate(row.dateCreationCrmDate())
                .openingDateSurTimos(row.openingDateSurTimos())
                .openingDateSurTimosDate(row.openingDateSurTimosDate())
                .statut(row.statut())
                .client(row.client())
                .contactClient(row.contactClient())
                .commentaire(row.commentaire())
                .msisdn(row.msisdn())
                .latitudeSite(row.latitudeSite())
                .latitudeSiteValue(row.latitudeSiteValue())
                .longitudeSite(row.longitudeSite())
                .longitudeSiteValue(row.longitudeSiteValue())
                .localite(row.localite())
                .delegation(row.delegation())
                .gouvernorat(row.gouvernorat())
                .gouvernoratNormalized(row.gouvernoratNormalized())
                .offre(row.offre())
                .desPack(row.desPack())
                .nameStt(row.nameStt())
                .nameSttNormalized(row.nameSttNormalized())
                .dateAffectationStt(row.dateAffectationStt())
                .dateAffectationSttDate(row.dateAffectationSttDate())
                .repRdv(row.repRdv())
                .datePriseRdv(row.datePriseRdv())
                .datePriseRdvDate(row.datePriseRdvDate())
                .causeInjoignabilite(row.causeInjoignabilite())
                .causeAbandon(row.causeAbandon())
                .detail(row.detail())
                .cmtRdv(row.cmtRdv())
                .repTravauxStt(row.repTravauxStt())
                .dateFinTrv(row.dateFinTrv())
                .dateFinTrvDate(row.dateFinTrvDate())
                .cmtTrvStt(row.cmtTrvStt())
                .repZm(row.repZm())
                .cmtZm(row.cmtZm())
                .metrageCable(row.metrageCable())
                .metrageCableValue(row.metrageCableValue())
                .nombreDePv(row.nombreDePv())
                .nombreDePvValue(row.nombreDePvValue())
                .debitDl(row.debitDl())
                .debitDlValue(row.debitDlValue())
                .debitUl(row.debitUl())
                .debitUlValue(row.debitUlValue())
                .rsrp(row.rsrp())
                .rsrpValue(row.rsrpValue())
                .rsrq(row.rsrq())
                .rsrqValue(row.rsrqValue())
                .sinr(row.sinr())
                .sinrValue(row.sinrValue())
                .siteSecteur(row.siteSecteur())
                .typeOffreSpeed(row.typeOffreSpeed())
                .typeClient(row.typeClient())
                .source(row.source())
                .description(row.description())
                .assignmentBusinessDays(row.assignmentBusinessDays())
                .rdvBusinessDays(row.rdvBusinessDays())
                .executionBusinessDays(row.executionBusinessDays())
                .endToEndBusinessDays(row.endToEndBusinessDays())
                .sourceValues(row.sourceValues())
                .build();
    }

    private AdminActivationAnalyticsPreviewRowResponse toPreviewRow(ParsedRow row) {
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
                row.validNewRow(),
                row.category()
        );
    }

    private List<AdminActivationAnalyticsInvalidReasonResponse> buildInvalidReasonSummaries(List<ParsedRow> invalidRows) {
        Map<String, Long> counts = new LinkedHashMap<>();

        for (ParsedRow row : invalidRows) {
            List<String> issues = row.issues() == null || row.issues().isEmpty()
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

    private record ParsedImport(
            String sourceFileName,
            int totalRows,
            int validNewRows,
            int invalidRows,
            int duplicateRows,
            int distinctStts,
            int distinctGovernorates,
            List<ParsedRow> rows
    ) {
        List<ParsedRow> validRowsToStore() {
            return rows.stream()
                    .filter(ParsedRow::validNewRow)
                    .toList();
        }

        List<ParsedRow> invalidRowsOnly() {
            return rows.stream()
                    .filter(ParsedRow::invalidOnlyRow)
                    .toList();
        }
    }

    private record ParsedRow(
            int rowNumber,
            String crmCase,
            String crmCaseNormalized,
            String dateCreationCrm,
            java.time.LocalDate dateCreationCrmDate,
            String openingDateSurTimos,
            java.time.LocalDate openingDateSurTimosDate,
            String statut,
            String client,
            String contactClient,
            String commentaire,
            String msisdn,
            String latitudeSite,
            Double latitudeSiteValue,
            String longitudeSite,
            Double longitudeSiteValue,
            String localite,
            String delegation,
            String gouvernorat,
            String gouvernoratNormalized,
            String offre,
            String desPack,
            String nameStt,
            String nameSttNormalized,
            String dateAffectationStt,
            java.time.LocalDate dateAffectationSttDate,
            String repRdv,
            String datePriseRdv,
            java.time.LocalDate datePriseRdvDate,
            String causeInjoignabilite,
            String causeAbandon,
            String detail,
            String cmtRdv,
            String repTravauxStt,
            String dateFinTrv,
            java.time.LocalDate dateFinTrvDate,
            String cmtTrvStt,
            String repZm,
            String cmtZm,
            String metrageCable,
            Double metrageCableValue,
            String nombreDePv,
            Double nombreDePvValue,
            String debitDl,
            Double debitDlValue,
            String debitUl,
            Double debitUlValue,
            String rsrp,
            Double rsrpValue,
            String rsrq,
            Double rsrqValue,
            String sinr,
            Double sinrValue,
            String siteSecteur,
            String typeOffreSpeed,
            String typeClient,
            String source,
            String description,
            Integer assignmentBusinessDays,
            Integer rdvBusinessDays,
            Integer executionBusinessDays,
            Integer endToEndBusinessDays,
            Map<String, String> sourceValues,
            List<String> issues
    ) {
        boolean validNewRow() {
            return issues == null || issues.isEmpty();
        }

        boolean duplicateOnlyRow() {
            return !validNewRow() && issues.stream().allMatch(ActivationAnalyticsImportService::isDuplicateIssue);
        }

        boolean invalidOnlyRow() {
            return !validNewRow() && !duplicateOnlyRow();
        }

        String category() {
            if (validNewRow()) {
                return "valid_new";
            }
            if (duplicateOnlyRow()) {
                return "duplicate";
            }
            return "invalid";
        }

        ParsedRow withIssues(List<String> newIssues) {
            return new ParsedRow(
                    rowNumber,
                    crmCase,
                    crmCaseNormalized,
                    dateCreationCrm,
                    dateCreationCrmDate,
                    openingDateSurTimos,
                    openingDateSurTimosDate,
                    statut,
                    client,
                    contactClient,
                    commentaire,
                    msisdn,
                    latitudeSite,
                    latitudeSiteValue,
                    longitudeSite,
                    longitudeSiteValue,
                    localite,
                    delegation,
                    gouvernorat,
                    gouvernoratNormalized,
                    offre,
                    desPack,
                    nameStt,
                    nameSttNormalized,
                    dateAffectationStt,
                    dateAffectationSttDate,
                    repRdv,
                    datePriseRdv,
                    datePriseRdvDate,
                    causeInjoignabilite,
                    causeAbandon,
                    detail,
                    cmtRdv,
                    repTravauxStt,
                    dateFinTrv,
                    dateFinTrvDate,
                    cmtTrvStt,
                    repZm,
                    cmtZm,
                    metrageCable,
                    metrageCableValue,
                    nombreDePv,
                    nombreDePvValue,
                    debitDl,
                    debitDlValue,
                    debitUl,
                    debitUlValue,
                    rsrp,
                    rsrpValue,
                    rsrq,
                    rsrqValue,
                    sinr,
                    sinrValue,
                    siteSecteur,
                    typeOffreSpeed,
                    typeClient,
                    source,
                    description,
                    assignmentBusinessDays,
                    rdvBusinessDays,
                    executionBusinessDays,
                    endToEndBusinessDays,
                    sourceValues,
                    List.copyOf(newIssues)
            );
        }
    }

    private static boolean isDuplicateIssue(String issue) {
        if (!ActivationAnalyticsSupport.hasText(issue)) {
            return false;
        }
        return issue.toLowerCase(Locale.ROOT).contains("duplicate");
    }
}
