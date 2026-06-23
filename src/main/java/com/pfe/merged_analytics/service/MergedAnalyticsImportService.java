package com.pfe.merged_analytics.service;

import com.pfe.merged_analytics.dto.MergedAnalyticsUploadResponse;
import com.pfe.merged_analytics.entity.MergedAnalyticsInvalidRowEntity;
import com.pfe.merged_analytics.entity.MergedAnalyticsRowEntity;
import com.pfe.merged_analytics.entity.MergedAnalyticsStudyEntity;
import com.pfe.merged_analytics.model.MergedProcessingStatus;
import com.pfe.merged_analytics.model.MergedSourceLabel;
import com.pfe.merged_analytics.repository.MergedAnalyticsStudyRepository;
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
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MergedAnalyticsImportService {

    private static final Logger log = LoggerFactory.getLogger(MergedAnalyticsImportService.class);

    // ── Tuning constants ──────────────────────────────────────────────────────
    /** Rows inserted per bulk write call. Keep between 2000–5000. */
    private static final int BATCH_SIZE = 2_000;
    /** How often (in rows) we emit a progress log. */
    private static final int LOG_PROGRESS_EVERY = 10_000;
    /** How often (in rows) we persist processedRows to MongoDB for the status endpoint. */
    private static final int PROGRESS_PERSIST_EVERY = 5_000;

    private static final List<String> REQUIRED_COLUMNS    = List.of("SOURCE", "ZONE_MANAGER");
    private static final List<String> RECOMMENDED_COLUMNS = List.of(
            "CRM_CASE", "DATE_CREATION_CRM", "OPENING_DATE_SUR_TIMOS", "STATUT", "NAME_STT", "GOUVERNORAT");

    private static final Map<String, List<String>> COLUMN_ALIASES = buildColumnAliases();

    // Most common formats first to minimise failed attempts
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            fmt("M/d/yyyy H:mm"),
            fmt("M/d/yyyy HH:mm"),
            fmt("yyyy-MM-dd HH:mm:ss"),
            fmt("yyyy-MM-dd HH:mm"),
            fmt("M/d/yyyy"),
            fmt("M/d/yy"),
            fmt("d/M/yyyy H:mm"),
            fmt("d/M/yyyy HH:mm"),
            fmt("d/M/yyyy"),
            fmt("d/M/yy"),
            fmt("yyyy-MM-dd"),
            fmt("dd-MM-yyyy"),
            fmt("MM-dd-yyyy"),
            fmt("M/d/yyyy h:mm a"),
            fmt("d/M/yyyy h:mm a"),
            fmt("yyyy-MM-dd H:mm")
    );

    static {
        ZipSecureFile.setMinInflateRatio(0.0001d);
    }

    private final MergedAnalyticsStudyRepository studyRepository;
    private final MergedAnalyticsBatchService batchService;
    private final MongoTemplate mongoTemplate;

    // ──────────────────────────────────────────────────────────────────────────
    //  PUBLIC API
    // ──────────────────────────────────────────────────────────────────────────

    public MergedAnalyticsUploadResponse initiateUpload(MultipartFile file, String uploadedBy) {
        validateFileType(file);

        String studyId  = UUID.randomUUID().toString();
        String fileName = resolveFileName(file);
        String fileType = resolveFileType(fileName);

        MergedAnalyticsStudyEntity study = MergedAnalyticsStudyEntity.builder()
                .studyId(studyId)
                .fileName(fileName)
                .fileType(fileType)
                .uploadedBy(uploadedBy)
                .uploadedAt(Instant.now())
                .processingStatus(MergedProcessingStatus.PENDING)
                .build();
        studyRepository.save(study);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            updateStudyFailed(studyId, "Failed to read uploaded file: " + ex.getMessage());
            return new MergedAnalyticsUploadResponse(studyId, "FAILED", "Failed to read uploaded file.");
        }

        log.info("[MERGED-IMPORT][START] studyId={} file={} size={}KB type={}",
                studyId, fileName, bytes.length / 1024, fileType);

        processAsync(studyId, bytes, fileName, fileType, uploadedBy);
        return new MergedAnalyticsUploadResponse(studyId, "PENDING", "File accepted. Processing started.");
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  ASYNC PROCESSING ENTRY POINT
    // ──────────────────────────────────────────────────────────────────────────

    @Async("mergedAnalyticsExecutor")
    public void processAsync(String studyId, byte[] bytes, String fileName, String fileType, String uploadedBy) {
        long totalStart = System.nanoTime();
        updateStudyProcessing(studyId);

        try {
            if ("CSV".equals(fileType)) {
                // True single-pass: parse + process in one line-by-line pass, no intermediate List<SourceRow>
                processCSVDirect(studyId, bytes, fileName, uploadedBy, totalStart);
            } else {
                ParseResult parsed = parseBytes(bytes, fileName, fileType, uploadedBy);
                buildAndStoreRows(studyId, parsed, uploadedBy, totalStart);
            }
        } catch (ResponseStatusException rse) {
            String msg = rse.getReason() != null ? rse.getReason() : rse.getMessage();
            log.warn("[MERGED-IMPORT][FAILED] studyId={} reason={}", studyId, msg);
            updateStudyFailed(studyId, msg);
        } catch (Exception ex) {
            log.error("[MERGED-IMPORT][FAILED] studyId={} error={}", studyId, ex.getMessage(), ex);
            updateStudyFailed(studyId, "Processing error: " + ex.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  PHASE 0 — CSV SINGLE-PASS DIRECT (no intermediate List<SourceRow>)
    //
    //  For CSV files: read header once, then process each line immediately into
    //  an entity and discard it. Memory at any point = one row's Map + BATCH_SIZE
    //  entities. Eliminates the List<List<String>> matrix AND the List<SourceRow>
    //  that otherwise hold all 110K rows simultaneously.
    // ──────────────────────────────────────────────────────────────────────────

    private void processCSVDirect(String studyId, byte[] bytes, String fileName,
                                   String uploadedBy, long totalStart) {
        long parseStart = System.nanoTime();
        String content = decodeText(bytes);

        // Detect separator from first non-blank line
        String firstLine = content.lines().filter(l -> !l.isBlank()).findFirst().orElse("");
        char sep = detectCsvSeparator(firstLine);
        if (sep == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No supported delimiter with required columns (SOURCE, ZONE_MANAGER) found in CSV header");
        }

        RowProcessingState st = new RowProcessingState(65_536);

        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.StringReader(content))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV file is empty");
            }

            List<String> headers = splitLine(headerLine.replace("﻿", ""), sep);
            Map<String, Integer> idx = resolveHeaderIndexes(headers);
            validateRequiredColumns(idx);
            List<String> missing = detectMissingRecommended(idx);

            log.info("[MERGED-IMPORT][HEADERS] file={} validated in {}ms missingRecommended={}",
                    fileName, elapsedMs(parseStart), missing);
            log.info("[MERGED-IMPORT][PARSE] file={} fast-path=CSV-STREAM sep='{}'", fileName, sep);

            long buildStart = System.nanoTime();
            String line;
            int lineNum = 1;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (line.isBlank()) continue;
                List<String> cols = splitLine(line, sep);
                Map<String, String> v = extractValues(cols, idx);
                if (v.isEmpty() || isRowEmpty(v.values())) continue;
                processOneRow(v, lineNum, studyId, st);
            }

            finalizeStudy(studyId, st, missing, buildStart, totalStart);

        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV read error: " + ex.getMessage());
        }
    }

    private char detectCsvSeparator(String firstLine) {
        for (char sep : new char[]{',', '\t', ';', '|'}) {
            List<String> headers = splitLine(firstLine.replace("﻿", ""), sep);
            if (containsRequiredColumns(resolveHeaderIndexes(headers))) return sep;
        }
        return 0;
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  PHASE 1 — PARSING
    // ──────────────────────────────────────────────────────────────────────────

    private ParseResult parseBytes(byte[] bytes, String fileName, String fileType, String uploadedBy) {
        long parseStart = System.nanoTime();

        // ── Fast-path: CSV → skip POI entirely ───────────────────────────────
        if ("CSV".equals(fileType)) {
            log.info("[MERGED-IMPORT][PARSE] file={} fast-path=CSV", fileName);
            String content = decodeText(bytes);
            try {
                List<SourceRow> rows = fromMatrix(parseDelimited(content));
                long ms = elapsedMs(parseStart);
                log.info("[MERGED-IMPORT][PARSE] file={} parser=delimited rows={} ms={}", fileName, rows.size(), ms);
                return new ParseResult(rows, List.of());
            } catch (Exception ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Failed to parse CSV: " + sanitize(deepestMessage(ex)));
            }
        }

        // ── XLS/XLSX → Apache POI ────────────────────────────────────────────
        try (InputStream stream = new ByteArrayInputStream(bytes);
             Workbook workbook  = WorkbookFactory.create(stream)) {

            if (workbook.getNumberOfSheets() == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file has no sheets");
            }

            Sheet sheet    = workbook.getSheetAt(0);
            Row headerRow  = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file has no header row");
            }

            DataFormatter    formatter = new DataFormatter(Locale.ROOT);
            FormulaEvaluator eval      = workbook.getCreationHelper().createFormulaEvaluator();

            long headerStart = System.nanoTime();
            Map<String, Integer> idx = resolveHeaderIndexes(headerRow, formatter, eval);
            validateRequiredColumns(idx);
            log.info("[MERGED-IMPORT][HEADERS] file={} validated in {}ms missingRecommended={}",
                    fileName, elapsedMs(headerStart), detectMissingRecommended(idx));

            List<SourceRow> rows = new ArrayList<>();
            long extractStart = System.nanoTime();
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                Map<String, String> vals = extractValues(row, idx, formatter, eval);
                if (!vals.isEmpty() && !isRowEmpty(vals.values())) {
                    rows.add(new SourceRow(r + 1, vals));
                }
            }
            log.info("[MERGED-IMPORT][PARSE] file={} parser=poi rows={} extractMs={}",
                    fileName, rows.size(), elapsedMs(extractStart));

            return new ParseResult(rows, detectMissingRecommended(idx));

        } catch (EncryptedDocumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password-protected files are not supported", ex);
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (IOException ex) {
            return fallbackParse(bytes, fileName, uploadedBy, parseStart, ex);
        }
    }

    private ParseResult fallbackParse(byte[] bytes, String fileName, String uploadedBy, long parseStart, IOException primary) {
        String content   = decodeText(bytes);
        List<String> errs = new ArrayList<>();

        if (looksLikeSpreadsheetXml(content)) {
            try {
                List<SourceRow> rows = fromMatrix(parseSpreadsheetXml(bytes));
                log.info("[MERGED-IMPORT][PARSE] file={} parser=spreadsheet_xml rows={} ms={}", fileName, rows.size(), elapsedMs(parseStart));
                return new ParseResult(rows, List.of());
            } catch (Exception ex) { errs.add(sanitize(deepestMessage(ex))); }
        }

        if (looksLikeHtml(content)) {
            try {
                List<SourceRow> rows = fromMatrix(parseHtml(content));
                log.info("[MERGED-IMPORT][PARSE] file={} parser=html rows={} ms={}", fileName, rows.size(), elapsedMs(parseStart));
                return new ParseResult(rows, List.of());
            } catch (Exception ex) { errs.add(sanitize(deepestMessage(ex))); }
        }

        try {
            List<SourceRow> rows = fromMatrix(parseDelimited(content));
            log.info("[MERGED-IMPORT][PARSE] file={} parser=delimited rows={} ms={}", fileName, rows.size(), elapsedMs(parseStart));
            return new ParseResult(rows, List.of());
        } catch (Exception ex) { errs.add(sanitize(deepestMessage(ex))); }

        String summary = String.join(" | ", errs);
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                hasText(summary) ? summary : "Unsupported file content. Open in Excel, save as .xlsx or .csv, then upload again.");
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  PHASE 2 — ROW BUILDING & STREAMING BATCH INSERT
    //
    //  Key optimisations vs first version:
    //   • Streaming batches (BATCH_SIZE=2000): never hold all 110K entities in RAM
    //   • Date parse cache: same string parsed once per import session
    //   • Value de-duplication: clean() called once per field, result reused
    //   • Progress written to DB every PROGRESS_PERSIST_EVERY rows (not every row)
    //   • HashSet for duplicate CRM_CASE detection (O(1) average vs LinkedHashSet)
    // ──────────────────────────────────────────────────────────────────────────

    // ──────────────────────────────────────────────────────────────────────────
    //  PHASE 2 — ROW BUILDING & STREAMING BATCH INSERT  (XLSX / XML / HTML)
    //
    //  Key optimisations:
    //   • Streaming batches (BATCH_SIZE=2000): never hold all 110K entities in RAM
    //   • Date parse cache: same string parsed once per import session
    //   • Value de-duplication: clean() called once per field, result reused
    //   • Progress written to DB every PROGRESS_PERSIST_EVERY rows (not every row)
    //   • HashSet for duplicate CRM_CASE detection (O(1) average vs LinkedHashSet)
    //
    //  For CSV, processCSVDirect() is called instead — it never builds List<SourceRow>.
    // ──────────────────────────────────────────────────────────────────────────

    private void buildAndStoreRows(String studyId, ParseResult parsed, String uploadedBy, long totalStart) {
        long buildStart = System.nanoTime();
        RowProcessingState st = new RowProcessingState(parsed.rows().size());
        for (SourceRow sr : parsed.rows()) {
            processOneRow(sr.values(), sr.rowNumber(), studyId, st);
        }
        finalizeStudy(studyId, st, parsed.missingRecommended(), buildStart, totalStart);
    }

    // ── Shared per-row logic (used by both buildAndStoreRows and processCSVDirect) ──

    private void processOneRow(Map<String, String> v, int rowNum, String studyId, RowProcessingState st) {
        st.rowsProcessed++;

        // ── Required: SOURCE ──────────────────────────────────────────────────
        String sourceRaw   = clean(v.get("SOURCE"));
        String sourceNorm  = null;
        String sourceLabel = null;
        List<String> reasons = new ArrayList<>(2);

        if (!hasText(sourceRaw)) {
            reasons.add("MISSING_SOURCE");
        } else {
            sourceLabel = MergedSourceLabel.resolveLabel(sourceRaw);
            if (sourceLabel == null) {
                reasons.add("INVALID_SOURCE: " + sourceRaw);
            } else {
                sourceNorm = MergedSourceLabel.normalizeSource(sourceRaw);
                st.sourcesFound.add(sourceNorm);
            }
        }

        // ── Required: ZONE_MANAGER ────────────────────────────────────────────
        String zoneManager = clean(v.get("ZONE_MANAGER"));
        if (!hasText(zoneManager)) {
            reasons.add("MISSING_ZONE_MANAGER");
        } else {
            st.zoneManagersFound.add(zoneManager);
        }

        if (!reasons.isEmpty()) {
            st.invalidBatch.add(MergedAnalyticsInvalidRowEntity.builder()
                    .studyId(studyId)
                    .rowNumber(rowNum)
                    .crmCase(clean(v.get("CRM_CASE")))
                    .source(sourceRaw)
                    .zoneManager(zoneManager)
                    .reasons(reasons)
                    .rawPreview(buildRawPreview(v))
                    .build());
            st.invalidTotal++;
            if (st.invalidBatch.size() >= BATCH_SIZE) {
                batchService.insertInvalidRowsInBatches(st.invalidBatch);
                log.info("[MERGED-IMPORT][BATCH_INSERT] studyId={} batchSize={} invalid", studyId, st.invalidBatch.size());
                st.invalidBatch.clear();
            }
            return;
        }

        // ── Clean all fields ONCE per row ─────────────────────────────────────
        String crmCase             = clean(v.get("CRM_CASE"));
        String statut              = clean(v.get("STATUT"));
        String client              = clean(v.get("CLIENT"));
        String contactClient       = clean(v.get("CONTACT_CLIENT"));
        String commentaire         = clean(v.get("COMMENTAIRE"));
        String msisdn              = clean(v.get("MSISDN"));
        String latitudeSite        = clean(v.get("LATITUDE_SITE"));
        String longitudeSite       = clean(v.get("LONGITUDE_SITE"));
        String localite            = clean(v.get("LOCALITE"));
        String delegation          = clean(v.get("DELEGATION"));
        String gouvernorat         = clean(v.get("GOUVERNORAT"));
        String offre               = clean(v.get("OFFRE"));
        String desPack             = clean(v.get("DES_PACK"));
        String nameStt             = clean(v.get("NAME_STT"));
        String dateCreationCrm     = clean(v.get("DATE_CREATION_CRM"));
        String openingDateSurTimos = clean(v.get("OPENING_DATE_SUR_TIMOS"));
        String dateAffectationStt  = clean(v.get("DATE_AFFECTATION_STT"));
        String repRdv              = clean(v.get("REP_RDV"));
        String datePriseRdv        = clean(v.get("DATE_PRISE_RDV"));
        String causeInjoignabilite = clean(v.get("CAUSE_INJOIGNABILITE"));
        String causeAbandon        = clean(v.get("CAUSE_ABANDON"));
        String detail              = clean(v.get("DETAIL"));
        String cmtRdv              = clean(v.get("CMT_RDV"));
        String repTravauxStt       = clean(v.get("REP_TRAVAUX_STT"));
        String dateFinTrv          = clean(v.get("DATE_FIN_TRV"));
        String cmtTrvStt           = clean(v.get("CMT_TRV_STT"));
        String repZm               = clean(v.get("REP_ZM"));
        String cmtZm               = clean(v.get("CMT_ZM"));
        String metrageCable        = clean(v.get("METRAGE_CABLE"));
        String nombreDePv          = clean(v.get("NOMBRE_DE_PV"));
        String debitDl             = clean(v.get("DEBIT_DL"));
        String debitUl             = clean(v.get("DEBIT_UL"));
        String rsrp                = clean(v.get("RSRP"));
        String rsrq                = clean(v.get("RSRQ"));
        String sinr                = clean(v.get("SINR"));
        String siteSecteur         = clean(v.get("SITESECTEUR"));
        String typeOffreSpeed      = clean(v.get("TYPE_OFFRE_SPEED"));
        String typeClient          = clean(v.get("TYPE_CLIENT"));
        String description         = clean(v.get("DESCRIPTION"));
        String contactCl2          = clean(v.get("CONTACT_CL2"));
        String entite              = clean(v.get("ENTITE"));
        String label               = clean(v.get("LABEL"));
        String notifResolution     = clean(v.get("NOTIF_RESOLUTION"));
        String openDateActivation  = clean(v.get("OPEN_DATE_ACTIVATION"));
        String actCmtZm            = clean(v.get("ACT_CMT_ZM"));
        String actRepZm            = clean(v.get("ACT_REP_ZM"));
        String dateTravRes         = clean(v.get("date_travaux_resiliation"));
        String installer           = clean(v.get("INSTALLER"));
        String slaCrm              = clean(v.get("SLA_CRM"));
        String slaTech             = clean(v.get("SLA_TECH"));
        String slaCrmLabel         = clean(v.get("SLA_CRM_LABEL"));
        String slaTechLabel        = clean(v.get("SLA_TECH_LABEL"));

        // ── Collect filter values ─────────────────────────────────────────────
        if (hasText(nameStt))     st.sttsFound.add(nameStt);
        if (hasText(gouvernorat)) st.governoratesFound.add(gouvernorat);
        if (hasText(statut))      st.statusesFound.add(statut);

        // ── Duplicate detection (O(1) HashSet) ────────────────────────────────
        String crmNorm = normalizeLookup(crmCase);
        if (hasText(crmNorm) && !st.seenCrm.add(crmNorm)) {
            st.duplicates++;
        }

        // ── Parse dates via cache (avoids re-trying 16 formatters) ────────────
        LocalDate dateCreationCrmDate     = cachedDate(dateCreationCrm,     st.dateCache);
        LocalDate openingDateSurTimosDate = cachedDate(openingDateSurTimos, st.dateCache);
        LocalDate dateAffectationSttDate  = cachedDate(dateAffectationStt,  st.dateCache);
        LocalDate datePriseRdvDate        = cachedDate(datePriseRdv,        st.dateCache);
        LocalDate dateFinTrvDate          = cachedDate(dateFinTrv,          st.dateCache);
        LocalDate openDateActivationDate  = cachedDate(openDateActivation,  st.dateCache);
        LocalDate dateTravResDate         = cachedDate(dateTravRes,         st.dateCache);

        // Date range tracking
        if (dateCreationCrmDate != null) {
            if (st.dateMin == null || dateCreationCrmDate.isBefore(st.dateMin)) st.dateMin = dateCreationCrmDate;
            if (st.dateMax == null || dateCreationCrmDate.isAfter(st.dateMax))  st.dateMax = dateCreationCrmDate;
        }
        if (hasText(dateCreationCrm) && dateCreationCrmDate == null) st.warnings++;

        // ── Parse numeric values ONCE ─────────────────────────────────────────
        Double latVal  = parseDouble(latitudeSite);
        Double lonVal  = parseDouble(longitudeSite);
        if (latVal != null && (latVal < -90  || latVal > 90))  { st.warnings++; latVal = null; }
        if (lonVal != null && (lonVal < -180 || lonVal > 180)) { st.warnings++; lonVal = null; }

        Double metrageCableValue = parseDouble(metrageCable);
        Double nombreDePvValue   = parseDouble(nombreDePv);
        Double debitDlValue      = parseDouble(debitDl);
        Double debitUlValue      = parseDouble(debitUl);
        Double rsrpValue         = parseDouble(rsrp);
        Double rsrqValue         = parseDouble(rsrq);
        Double sinrValue         = parseDouble(sinr);
        Double slaCrmValue       = parseDouble(slaCrm);
        Double slaTechValue      = parseDouble(slaTech);

        st.validBatch.add(MergedAnalyticsRowEntity.builder()
                .studyId(studyId)
                .rowNumber(rowNum)
                .crmCase(crmCase)
                .dateCreationCrm(dateCreationCrm)
                .dateCreationCrmDate(dateCreationCrmDate)
                .openingDateSurTimos(openingDateSurTimos)
                .openingDateSurTimosDate(openingDateSurTimosDate)
                .statut(statut)
                .client(client)
                .contactClient(contactClient)
                .commentaire(commentaire)
                .msisdn(msisdn)
                .latitudeSite(latitudeSite)
                .latitudeSiteValue(latVal)
                .longitudeSite(longitudeSite)
                .longitudeSiteValue(lonVal)
                .localite(localite)
                .delegation(delegation)
                .gouvernorat(gouvernorat)
                .gouvernoratNormalized(normalizeLookup(gouvernorat))
                .offre(offre)
                .desPack(desPack)
                .nameStt(nameStt)
                .nameSttNormalized(normalizeLookup(nameStt))
                .dateAffectationStt(dateAffectationStt)
                .dateAffectationSttDate(dateAffectationSttDate)
                .repRdv(repRdv)
                .datePriseRdv(datePriseRdv)
                .datePriseRdvDate(datePriseRdvDate)
                .causeInjoignabilite(causeInjoignabilite)
                .causeAbandon(causeAbandon)
                .detail(detail)
                .cmtRdv(cmtRdv)
                .repTravauxStt(repTravauxStt)
                .dateFinTrv(dateFinTrv)
                .dateFinTrvDate(dateFinTrvDate)
                .cmtTrvStt(cmtTrvStt)
                .repZm(repZm)
                .cmtZm(cmtZm)
                .metrageCable(metrageCable)
                .metrageCableValue(metrageCableValue)
                .nombreDePv(nombreDePv)
                .nombreDePvValue(nombreDePvValue)
                .debitDl(debitDl)
                .debitDlValue(debitDlValue)
                .debitUl(debitUl)
                .debitUlValue(debitUlValue)
                .rsrp(rsrp)
                .rsrpValue(rsrpValue)
                .rsrq(rsrq)
                .rsrqValue(rsrqValue)
                .sinr(sinr)
                .sinrValue(sinrValue)
                .siteSecteur(siteSecteur)
                .typeOffreSpeed(typeOffreSpeed)
                .typeClient(typeClient)
                .source(sourceNorm)
                .sourceNormalized(normalizeLookup(sourceNorm))
                .sourceLabel(sourceLabel)
                .description(description)
                .contactCl2(contactCl2)
                .entite(entite)
                .label(label)
                .notifResolution(notifResolution)
                .openDateActivation(openDateActivation)
                .openDateActivationDate(openDateActivationDate)
                .actCmtZm(actCmtZm)
                .actRepZm(actRepZm)
                .dateTravauxResiliation(dateTravRes)
                .dateTravauxResiliationDate(dateTravResDate)
                .installer(installer)
                .slaCrm(slaCrm)
                .slaCrmValue(slaCrmValue)
                .slaTech(slaTech)
                .slaTechValue(slaTechValue)
                .slaCrmLabel(slaCrmLabel)
                .slaTechLabel(slaTechLabel)
                .zoneManager(zoneManager)
                .zoneManagerNormalized(normalizeLookup(zoneManager))
                .createdAt(st.createdAt)
                .build());
        st.validTotal++;

        // ── Flush valid batch when full ────────────────────────────────────────
        if (st.validBatch.size() >= BATCH_SIZE) {
            long insStart = System.nanoTime();
            batchService.insertValidRowsInBatches(st.validBatch);
            long insMs = elapsedMs(insStart);
            st.batchInsertTotalMs += insMs;
            log.info("[MERGED-IMPORT][BATCH_INSERT] studyId={} batchSize={} valid insertedMs={}",
                    studyId, st.validBatch.size(), insMs);
            st.validBatch.clear();
        }

        // ── Progress log ──────────────────────────────────────────────────────
        if (st.rowsProcessed % LOG_PROGRESS_EVERY == 0) {
            log.info("[MERGED-IMPORT][PROGRESS] studyId={} processed={} valid={} invalid={} duplicates={} dateCacheSize={}",
                    studyId, st.rowsProcessed, st.validTotal, st.invalidTotal, st.duplicates, st.dateCache.size());
        }

        // ── Incremental DB progress persist (for status polling) ──────────────
        if (st.rowsProcessed % PROGRESS_PERSIST_EVERY == 0) {
            persistProgress(studyId, st.rowsProcessed, st.validTotal, st.invalidTotal);
        }
    }

    private void finalizeStudy(String studyId, RowProcessingState st, List<String> missing,
                               long buildStart, long totalStart) {
        if (!st.validBatch.isEmpty()) {
            long insStart = System.nanoTime();
            batchService.insertValidRowsInBatches(st.validBatch);
            st.batchInsertTotalMs += elapsedMs(insStart);
            log.info("[MERGED-IMPORT][BATCH_INSERT] studyId={} batchSize={} valid (final flush)",
                    studyId, st.validBatch.size());
        }
        if (!st.invalidBatch.isEmpty()) {
            batchService.insertInvalidRowsInBatches(st.invalidBatch);
            log.info("[MERGED-IMPORT][BATCH_INSERT] studyId={} batchSize={} invalid (final flush)",
                    studyId, st.invalidBatch.size());
        }

        long buildMs = elapsedMs(buildStart);
        long totalMs = elapsedMs(totalStart);

        Instant dMin = st.dateMin != null ? st.dateMin.atStartOfDay().toInstant(java.time.ZoneOffset.UTC) : null;
        Instant dMax = st.dateMax != null ? st.dateMax.atStartOfDay().toInstant(java.time.ZoneOffset.UTC) : null;

        final int fValid      = st.validTotal;
        final int fInvalid    = st.invalidTotal;
        final int fWarnings   = st.warnings;
        final int fDuplicates = st.duplicates;
        final long fBatchMs   = st.batchInsertTotalMs;

        studyRepository.findByStudyId(studyId).ifPresent(study -> {
            study.setTotalRows(fValid + fInvalid);
            study.setProcessedRows(fValid + fInvalid);
            study.setValidRows(fValid);
            study.setInvalidRows(fInvalid);
            study.setWarningRows(fWarnings);
            study.setDuplicateRows(fDuplicates);
            study.setProcessingStatus(MergedProcessingStatus.COMPLETED);
            study.setProcessingDurationMs(totalMs);
            study.setSourcesFound(new ArrayList<>(st.sourcesFound));
            study.setZoneManagersFound(new ArrayList<>(st.zoneManagersFound));
            study.setSttsFound(new ArrayList<>(st.sttsFound));
            study.setGovernoratesFound(new ArrayList<>(st.governoratesFound));
            study.setStatusesFound(new ArrayList<>(st.statusesFound));
            study.setDateMin(dMin);
            study.setDateMax(dMax);
            study.setMissingOptionalColumns(missing);
            studyRepository.save(study);
        });

        log.info("[MERGED-IMPORT][DONE] studyId={} totalRows={} valid={} invalid={} warnings={} duplicates={} "
                + "buildMs={} batchInsertMs={} totalMs={}",
                studyId, fValid + fInvalid, fValid, fInvalid, fWarnings, fDuplicates,
                buildMs, fBatchMs, totalMs);
    }

    /** Writes processedRows to MongoDB without a full save (partial update). */
    private void persistProgress(String studyId, int processed, int valid, int invalid) {
        try {
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("studyId").is(studyId)),
                    new Update().set("processedRows", processed)
                                .set("validRows", valid)
                                .set("invalidRows", invalid),
                    MergedAnalyticsStudyEntity.class
            );
        } catch (Exception ex) {
            log.debug("[MERGED-IMPORT] progress persist failed: {}", ex.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  HEADER & VALUE EXTRACTION
    // ──────────────────────────────────────────────────────────────────────────

    private Map<String, Integer> resolveHeaderIndexes(Row headerRow, DataFormatter formatter, FormulaEvaluator eval) {
        Map<String, Integer> raw = new LinkedHashMap<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            String h = readCell(headerRow.getCell(i), formatter, eval);
            if (hasText(h)) raw.putIfAbsent(normalizeHeader(h), i);
        }
        return mapAliases(raw);
    }

    private Map<String, Integer> resolveHeaderIndexes(List<String> headers) {
        Map<String, Integer> raw = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            if (hasText(headers.get(i))) raw.putIfAbsent(normalizeHeader(headers.get(i)), i);
        }
        return mapAliases(raw);
    }

    private Map<String, Integer> mapAliases(Map<String, Integer> raw) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : COLUMN_ALIASES.entrySet()) {
            for (String alias : entry.getValue()) {
                Integer idx = raw.get(normalizeHeader(alias));
                if (idx != null) { result.put(entry.getKey(), idx); break; }
            }
        }
        return result;
    }

    private void validateRequiredColumns(Map<String, Integer> idx) {
        List<String> missing = REQUIRED_COLUMNS.stream().filter(c -> !idx.containsKey(c)).toList();
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Missing required column(s): " + String.join(", ", missing));
        }
    }

    private List<String> detectMissingRecommended(Map<String, Integer> idx) {
        return RECOMMENDED_COLUMNS.stream().filter(c -> !idx.containsKey(c)).toList();
    }

    private boolean containsRequiredColumns(Map<String, Integer> idx) {
        return REQUIRED_COLUMNS.stream().allMatch(idx::containsKey);
    }

    private Map<String, String> extractValues(Row row, Map<String, Integer> idx, DataFormatter formatter, FormulaEvaluator eval) {
        Map<String, String> values = new LinkedHashMap<>();
        if (row == null) return values;
        for (String col : COLUMN_ALIASES.keySet()) {
            Integer ci = idx.get(col);
            values.put(col, ci != null ? readCell(row.getCell(ci), formatter, eval) : null);
        }
        return values;
    }

    private Map<String, String> extractValues(List<String> rowValues, Map<String, Integer> idx) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String col : COLUMN_ALIASES.keySet()) {
            Integer i = idx.get(col);
            values.put(col, i != null && i < rowValues.size() ? rowValues.get(i) : null);
        }
        return values;
    }

    private List<SourceRow> fromMatrix(List<List<String>> matrix) {
        if (matrix.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File contains no data rows");
        Map<String, Integer> idx = resolveHeaderIndexes(matrix.get(0));
        validateRequiredColumns(idx);
        List<SourceRow> rows = new ArrayList<>(matrix.size());
        for (int r = 1; r < matrix.size(); r++) {
            Map<String, String> vals = extractValues(matrix.get(r), idx);
            if (!vals.isEmpty() && !isRowEmpty(vals.values())) rows.add(new SourceRow(r + 1, vals));
        }
        return rows;
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  FORMAT PARSERS
    // ──────────────────────────────────────────────────────────────────────────

    private List<List<String>> parseHtml(String content) throws IOException {
        org.jsoup.nodes.Document doc = Jsoup.parse(content);
        Elements tables = doc.select("table");
        for (org.jsoup.nodes.Element table : tables) {
            List<List<String>> matrix = new ArrayList<>();
            for (org.jsoup.nodes.Element row : table.select("tr")) {
                Elements cells = row.select("th,td");
                if (cells.isEmpty()) continue;
                matrix.add(cells.stream().map(c -> c.text() == null ? "" : c.text().trim()).toList());
            }
            if (!matrix.isEmpty() && containsRequiredColumns(resolveHeaderIndexes(matrix.get(0)))) return matrix;
        }
        throw new IOException("No HTML table with required merged analytics columns found");
    }

    private List<List<String>> parseSpreadsheetXml(byte[] bytes) throws Exception {
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

        for (int t = 0; t < tableNodes.getLength(); t++) {
            Node tableNode = tableNodes.item(t);
            if (tableNode == null || tableNode.getNodeType() != Node.ELEMENT_NODE) continue;
            List<List<String>> matrix = new ArrayList<>();
            NodeList rowNodes = tableNode.getChildNodes();
            for (int r = 0; r < rowNodes.getLength(); r++) {
                Node rowNode = rowNodes.item(r);
                if (!isElementNamed(rowNode, "Row")) continue;
                List<String> vals = new ArrayList<>();
                NodeList cellNodes = rowNode.getChildNodes();
                int colIdx = 1;
                for (int c = 0; c < cellNodes.getLength(); c++) {
                    Node cellNode = cellNodes.item(c);
                    if (!isElementNamed(cellNode, "Cell")) continue;
                    int target = resolveXmlPos(cellNode, colIdx);
                    while (vals.size() < target - 1) vals.add("");
                    vals.add(xmlCellValue(cellNode));
                    colIdx = target + 1;
                }
                if (!vals.isEmpty()) matrix.add(vals);
            }
            if (!matrix.isEmpty() && containsRequiredColumns(resolveHeaderIndexes(matrix.get(0)))) return matrix;
        }
        throw new IOException("No Spreadsheet XML table with required columns found");
    }

    private List<List<String>> parseDelimited(String content) throws IOException {
        List<String> lines = content.lines()
                .map(l -> l == null ? "" : l.replace("﻿", ""))
                .filter(l -> !l.isBlank()).toList();
        if (lines.isEmpty()) throw new IOException("No readable rows in the file");

        for (char sep : new char[]{',', '\t', ';', '|'}) {
            List<String> headers = splitLine(lines.get(0), sep);
            if (!containsRequiredColumns(resolveHeaderIndexes(headers))) continue;
            List<List<String>> matrix = new ArrayList<>(lines.size());
            matrix.add(headers);
            for (int i = 1; i < lines.size(); i++) matrix.add(splitLine(lines.get(i), sep));
            return matrix;
        }
        throw new IOException("No supported delimited format with required columns found");
    }

    private List<String> splitLine(String line, char sep) {
        List<String> values = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                else inQuotes = !inQuotes;
                continue;
            }
            if (ch == sep && !inQuotes) { values.add(cur.toString().trim()); cur.setLength(0); continue; }
            cur.append(ch);
        }
        values.add(cur.toString().trim());
        return values;
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  STUDY STATUS HELPERS
    // ──────────────────────────────────────────────────────────────────────────

    private void updateStudyProcessing(String studyId) {
        studyRepository.findByStudyId(studyId).ifPresent(s -> {
            s.setProcessingStatus(MergedProcessingStatus.PROCESSING);
            studyRepository.save(s);
        });
    }

    private void updateStudyFailed(String studyId, String message) {
        studyRepository.findByStudyId(studyId).ifPresent(s -> {
            s.setProcessingStatus(MergedProcessingStatus.FAILED);
            s.setErrorMessage(message);
            studyRepository.save(s);
        });
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  VALUE HELPERS
    // ──────────────────────────────────────────────────────────────────────────

    private static String clean(String value) {
        if (value == null) return null;
        String t = value.strip();
        if (t.isEmpty() || t.equalsIgnoreCase("NULL") || t.equalsIgnoreCase("N/A") || t.equals("-")) return null;
        return t;
    }

    private static String normalizeLookup(String value) {
        if (value == null) return null;
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s\\-_]+", " ").trim();
    }

    private static String normalizeHeader(String header) {
        if (header == null) return "";
        return header.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s_]+", "_");
    }

    /**
     * Date parse with per-import-session HashMap cache.
     * Same date string (e.g. "1/2/2025 16:35") is parsed only once across 110K rows.
     */
    private static LocalDate cachedDate(String value, Map<String, LocalDate> cache) {
        if (!hasText(value)) return null;
        if (cache.containsKey(value)) return cache.get(value);
        LocalDate result = parseDate(value);
        cache.put(value, result);   // null entries cached too (avoid retrying on every row)
        return result;
    }

    private static LocalDate parseDate(String value) {
        if (!hasText(value)) return null;
        String v = value.trim();
        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try { return LocalDate.parse(v, fmt); } catch (Exception ignored) {}
        }
        // Excel serial date fallback
        try {
            double serial = Double.parseDouble(v);
            if (serial > 1 && serial < 2_958_466) {
                return LocalDate.of(1899, 12, 30).plusDays((long) serial);
            }
        } catch (NumberFormatException ignored) {}
        return null;
    }

    private static Double parseDouble(String value) {
        if (!hasText(value)) return null;
        try { return Double.parseDouble(value.replace(',', '.')); } catch (NumberFormatException e) { return null; }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isRowEmpty(java.util.Collection<String> values) {
        return values == null || values.isEmpty() || values.stream().noneMatch(MergedAnalyticsImportService::hasText);
    }

    private static String buildRawPreview(Map<String, String> values) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Map.Entry<String, String> e : values.entrySet()) {
            if (hasText(e.getValue()) && count < 8) {
                if (count > 0) sb.append(" | ");
                sb.append(e.getKey()).append(": ").append(e.getValue());
                count++;
            }
        }
        return sb.toString();
    }

    private static void validateFileType(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required");
        String name = resolveFileName(file).toLowerCase(Locale.ROOT);
        if (!name.endsWith(".csv") && !name.endsWith(".xls") && !name.endsWith(".xlsx")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only .csv, .xls, and .xlsx files are supported");
        }
    }

    private static String resolveFileName(MultipartFile file) {
        if (file == null || !hasText(file.getOriginalFilename())) return "merged.xlsx";
        return file.getOriginalFilename().trim();
    }

    private static String resolveFileType(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv"))  return "CSV";
        if (lower.endsWith(".xls"))  return "XLS";
        if (lower.endsWith(".xlsx")) return "XLSX";
        return "UNKNOWN";
    }

    private String decodeText(byte[] bytes) {
        String utf8 = new String(bytes, StandardCharsets.UTF_8).replace("﻿", "");
        if (looksLikeSpreadsheetXml(utf8) || looksLikeHtml(utf8) || utf8.contains(",") || utf8.contains("\t")) return utf8;
        return new String(bytes, StandardCharsets.ISO_8859_1).replace("﻿", "");
    }

    private boolean looksLikeHtml(String content) {
        String lower = content.toLowerCase(Locale.ROOT);
        return lower.contains("<html") || lower.contains("<table") || lower.contains("<tr") || lower.contains("<td");
    }

    private boolean looksLikeSpreadsheetXml(String content) {
        String lower = content.toLowerCase(Locale.ROOT);
        return lower.contains("urn:schemas-microsoft-com:office:spreadsheet") || lower.contains("<workbook") || lower.contains(":workbook");
    }

    private String readCell(Cell cell, DataFormatter formatter, FormulaEvaluator eval) {
        if (cell == null) return "";
        return formatter.formatCellValue(cell, eval);
    }

    private boolean isElementNamed(Node node, String localName) {
        if (node == null || node.getNodeType() != Node.ELEMENT_NODE) return false;
        if (localName.equals(node.getLocalName())) return true;
        String n = node.getNodeName();
        return n != null && (n.equals(localName) || n.endsWith(":" + localName));
    }

    private int resolveXmlPos(Node node, int def) {
        NamedNodeMap attrs = node.getAttributes();
        if (attrs == null) return def;
        for (int i = 0; i < attrs.getLength(); i++) {
            Node a = attrs.item(i);
            String name = a.getLocalName() != null ? a.getLocalName() : a.getNodeName();
            if ("Index".equals(name) || (a.getNodeName() != null && a.getNodeName().endsWith(":Index"))) {
                try { return Integer.parseInt(a.getNodeValue()); } catch (NumberFormatException e) { return def; }
            }
        }
        return def;
    }

    private String xmlCellValue(Node cellNode) {
        NodeList children = cellNode.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (isElementNamed(child, "Data")) return child.getTextContent() == null ? "" : child.getTextContent().trim();
        }
        return cellNode.getTextContent() == null ? "" : cellNode.getTextContent().trim();
    }

    private String deepestMessage(Throwable t) {
        String last = null;
        while (t != null) { if (hasText(t.getMessage())) last = t.getMessage(); t = t.getCause(); }
        return last == null ? "" : last;
    }

    private String sanitize(String msg) { return msg == null ? "" : msg.replaceAll("[\\r\\n]+", " ").trim(); }
    private long elapsedMs(long startNanos) { return (System.nanoTime() - startNanos) / 1_000_000L; }

    // ──────────────────────────────────────────────────────────────────────────
    //  COLUMN ALIASES
    // ──────────────────────────────────────────────────────────────────────────

    private static Map<String, List<String>> buildColumnAliases() {
        Map<String, List<String>> a = new LinkedHashMap<>();
        a.put("CRM_CASE",                 List.of("CRM_CASE"));
        a.put("DATE_CREATION_CRM",        List.of("DATE_CREATION_CRM", "FX_DATE_CRM"));
        a.put("OPENING_DATE_SUR_TIMOS",   List.of("OPENING_DATE_SUR_TIMOS", "OPEN_DATE_SUR_TIMOS"));
        a.put("STATUT",                   List.of("STATUT", "STATUS"));
        a.put("CLIENT",                   List.of("CLIENT"));
        a.put("CONTACT_CLIENT",           List.of("CONTACT_CLIENT", "CONTACT1"));
        a.put("COMMENTAIRE",              List.of("COMMENTAIRE", "COMMENT"));
        a.put("MSISDN",                   List.of("MSISDN"));
        a.put("LATITUDE_SITE",            List.of("LATITUDE_SITE", "LATITUDE"));
        a.put("LONGITUDE_SITE",           List.of("LONGITUDE_SITE", "LONGITUDE"));
        a.put("LOCALITE",                 List.of("LOCALITE"));
        a.put("DELEGATION",               List.of("DELEGATION"));
        a.put("GOUVERNORAT",              List.of("GOUVERNORAT"));
        a.put("OFFRE",                    List.of("OFFRE"));
        a.put("DES_PACK",                 List.of("DES_PACK"));
        a.put("NAME_STT",                 List.of("NAME_STT", "STT"));
        a.put("DATE_AFFECTATION_STT",     List.of("DATE_AFFECTATION_STT"));
        a.put("REP_RDV",                  List.of("REP_RDV"));
        a.put("DATE_PRISE_RDV",           List.of("DATE_PRISE_RDV"));
        a.put("CAUSE_INJOIGNABILITE",     List.of("CAUSE_INJOIGNABILITE"));
        a.put("CAUSE_ABANDON",            List.of("CAUSE_ABANDON"));
        a.put("DETAIL",                   List.of("DETAIL"));
        a.put("CMT_RDV",                  List.of("CMT_RDV"));
        a.put("REP_TRAVAUX_STT",          List.of("REP_TRAVAUX_STT"));
        a.put("DATE_FIN_TRV",             List.of("DATE_FIN_TRV"));
        a.put("CMT_TRV_STT",              List.of("CMT_TRV_STT"));
        a.put("REP_ZM",                   List.of("REP_ZM"));
        a.put("CMT_ZM",                   List.of("CMT_ZM"));
        a.put("METRAGE_CABLE",            List.of("METRAGE_CABLE"));
        a.put("NOMBRE_DE_PV",             List.of("NOMBRE_DE_PV"));
        a.put("DEBIT_DL",                 List.of("DEBIT_DL"));
        a.put("DEBIT_UL",                 List.of("DEBIT_UL"));
        a.put("RSRP",                     List.of("RSRP"));
        a.put("RSRQ",                     List.of("RSRQ"));
        a.put("SINR",                     List.of("SINR"));
        a.put("SITESECTEUR",              List.of("SITESECTEUR", "SITE_SECTEUR"));
        a.put("TYPE_OFFRE_SPEED",         List.of("TYPE_OFFRE_SPEED", "TYPE OFFRE SPEED", "Type_Offre_Speed"));
        a.put("TYPE_CLIENT",              List.of("TYPE_CLIENT", "TYPE CLIENT", "Type_Client"));
        a.put("SOURCE",                   List.of("SOURCE", "Source"));
        a.put("DESCRIPTION",              List.of("DESCRIPTION", "description"));
        a.put("CONTACT_CL2",              List.of("CONTACT_CL2", "Contact_CL2"));
        a.put("ENTITE",                   List.of("ENTITE"));
        a.put("LABEL",                    List.of("LABEL", "Label"));
        a.put("NOTIF_RESOLUTION",         List.of("NOTIF_RESOLUTION"));
        a.put("OPEN_DATE_ACTIVATION",     List.of("OPEN_DATE_ACTIVATION", "OPENING_DATE_ACTIVATION"));
        a.put("ACT_CMT_ZM",              List.of("ACT_CMT_ZM"));
        a.put("ACT_REP_ZM",              List.of("ACT_REP_ZM"));
        a.put("date_travaux_resiliation", List.of("date_travaux_resiliation", "DATE_TRAVAUX_RESILIATION"));
        a.put("INSTALLER",                List.of("INSTALLER", "Installer"));
        a.put("SLA_CRM",                  List.of("SLA_CRM"));
        a.put("SLA_TECH",                 List.of("SLA_TECH"));
        a.put("SLA_CRM_LABEL",            List.of("SLA_CRM_LABEL", "SLA_CRM_Label"));
        a.put("SLA_TECH_LABEL",           List.of("SLA_TECH_LABEL", "SLA_TECH_Label"));
        a.put("ZONE_MANAGER",             List.of("ZONE_MANAGER", "ZONE MANAGER"));
        return Map.copyOf(a);
    }

    private static DateTimeFormatter fmt(String pattern) {
        return new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern).toFormatter(Locale.ROOT);
    }

    private record SourceRow(int rowNumber, Map<String, String> values) {}
    private record ParseResult(List<SourceRow> rows, List<String> missingRecommended) {}

    // ── Mutable accumulator shared between processOneRow and finalizeStudy ────

    private static final class RowProcessingState {
        final Map<String, LocalDate>           dateCache       = new HashMap<>(4096);
        final Set<String>                      sourcesFound    = new LinkedHashSet<>();
        final Set<String>                      zoneManagersFound = new LinkedHashSet<>();
        final Set<String>                      sttsFound       = new LinkedHashSet<>();
        final Set<String>                      governoratesFound = new LinkedHashSet<>();
        final Set<String>                      statusesFound   = new LinkedHashSet<>();
        final Set<String>                      seenCrm;
        final List<MergedAnalyticsRowEntity>         validBatch;
        final List<MergedAnalyticsInvalidRowEntity>  invalidBatch  = new ArrayList<>();
        final Instant                          createdAt       = Instant.now();
        LocalDate dateMin, dateMax;
        int warnings, duplicates, validTotal, invalidTotal, rowsProcessed;
        long batchInsertTotalMs;

        RowProcessingState(int estimatedRows) {
            this.seenCrm   = new HashSet<>(Math.max(16, estimatedRows));
            this.validBatch = new ArrayList<>(BATCH_SIZE);
        }
    }
}
