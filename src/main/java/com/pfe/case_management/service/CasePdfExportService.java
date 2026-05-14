package com.pfe.case_management.service;

import com.pfe.case_management.dto.CaseProofFileResponse;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.enums.CaseStatus;
import com.pfe.case_management.model.CaseTimelineEvent;
import com.pfe.case_management.model.RdvInfo;
import com.pfe.case_management.model.SlaTrackingInfo;
import com.pfe.case_management.model.TechnicalMetrics;
import com.pfe.case_management.model.WorkInfo;
import com.pfe.case_management.model.ZmValidation;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;

@Service
public class CasePdfExportService {

    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final int LEFT_MARGIN = 46;
    private static final int TOP_Y = 800;
    private static final int BOTTOM_Y = 52;
    private static final int BODY_WRAP = 88;
    private static final int SMALL_WRAP = 96;
    private static final String OOREEDOO_RED = "0.890 0.024 0.075";
    private static final String DARK_TEXT = "0.149 0.149 0.180";
    private static final String MUTED_TEXT = "0.420 0.420 0.460";

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH)
            .withZone(ZoneId.systemDefault());

    public byte[] generateCasePdf(CaseEntity caseEntity) {
        List<PdfLine> lines = buildDocumentLines(caseEntity);
        List<List<PlacedPdfLine>> pages = paginate(lines);
        return renderPdf(pages);
    }

    public String buildFilename(CaseEntity caseEntity) {
        String crmCase = sanitizeFilenameFragment(caseEntity.getCrmCase());
        if (crmCase.isBlank()) {
            crmCase = sanitizeFilenameFragment(caseEntity.getId());
        }

        if (crmCase.isBlank()) {
            crmCase = "report";
        }

        return "case-" + crmCase + ".pdf";
    }

    private List<PdfLine> buildDocumentLines(CaseEntity c) {
        List<PdfLine> lines = new ArrayList<>();

        addLine(lines, title("Ooredoo Field Service Case Report"));
        addLine(lines, subtitle("CRM Case: " + valueOrDash(c.getCrmCase())));
        addLine(lines, subtitle("Generated on: " + formatInstant(Instant.now())));
        addLine(lines, subtitle("Current status: " + humanizeStatus(c.getStatus())));

        addSection(lines, "Case Information");
        addField(lines, "Case ID", c.getId());
        addField(lines, "Type", humanizeToken(c.getType() != null ? c.getType().name() : null));
        addField(lines, "Status", humanizeStatus(c.getStatus()));
        addField(lines, "Created by", c.getCreatedBy());
        addField(lines, "Created at", formatInstant(c.getCreatedAt()));
        addField(lines, "Updated at", formatInstant(c.getUpdatedAt()));
        addField(lines, "CRM creation date", formatInstant(c.getDateCreationCrm()));
        addField(lines, "TIMOS opening date", formatInstant(c.getOpeningDateTimos()));
        addField(lines, "Comment", c.getCommentaire());

        addSection(lines, "Client");
        addField(lines, "Client name", c.getClientName());
        addField(lines, "Client phone", c.getClientPhone());
        addField(lines, "MSISDN", c.getMsisdn());
        addField(lines, "Offer", c.getOffre());
        addField(lines, "Pack", c.getDesPack());

        addSection(lines, "Location");
        addField(lines, "Address", joinNonBlank(", ", c.getLocalite(), c.getDelegation(), c.getGouvernorat()));
        addField(lines, "Site secteur", c.getSiteSecteur());
        addField(lines, "Coordinates", coordinates(c.getLatitude(), c.getLongitude()));

        addSection(lines, "Assignment Chain");
        addField(lines, "ZM", person(c.getZmName(), c.getZmEmail()));
        addField(lines, "ZM assigned at", formatInstant(c.getDateAffectationZm()));
        addField(lines, "STT", person(c.getSttName(), c.getSttEmail()));
        addField(lines, "STT assigned at", formatInstant(c.getDateAffectationStt()));
        addField(lines, "TECH", person(c.getTechName(), c.getTechEmail()));
        addField(lines, "TECH assigned at", formatInstant(c.getDateAffectationTech()));
        addField(lines, "Executor role", humanizeToken(c.getExecutorRole() != null ? c.getExecutorRole().name() : null));
        addField(lines, "Execution mode", humanizeToken(c.getExecutionMode() != null ? c.getExecutionMode().name() : null));

        addSlaSection(lines, c.getSlaTracking());
        addRdvSection(lines, c.getRdv());
        addWorkSection(lines, c.getWork());
        addMetricsSection(lines, c.getMetrics());
        addValidationSection(lines, c.getZmValidation());
        addProofSection(lines, c.getWorkProofs());
        addTimelineSection(lines, c.getTimeline());

        return lines;
    }

    private void addSlaSection(List<PdfLine> lines, SlaTrackingInfo slaTracking) {
        addSection(lines, "SLA Tracking");

        if (slaTracking == null) {
            addNote(lines, "No SLA tracking data available.");
            return;
        }

        addField(lines, "SLA status", humanizeToken(slaTracking.getStatus() != null ? slaTracking.getStatus().name() : null));
        addField(lines, "Started at", formatInstant(slaTracking.getStartedAt()));
        addField(lines, "Deadline at", formatInstant(slaTracking.getDeadlineAt()));
        addField(lines, "RDV set at", formatInstant(slaTracking.getRdvSetAt()));
        addField(lines, "Warning triggered at", formatInstant(slaTracking.getWarningTriggeredAt()));
        addField(lines, "Breached at", formatInstant(slaTracking.getBreachedAt()));
        addField(lines, "Escalation triggered at", formatInstant(slaTracking.getEscalationTriggeredAt()));
        addField(lines, "Progress", percentage(slaTracking.getProgressPercent()));
        addField(lines, "Remaining time", durationLabel(slaTracking.getRemainingSeconds()));
        addField(lines, "Response duration", durationLabel(slaTracking.getResponseDurationSeconds()));
    }

    private void addRdvSection(List<PdfLine> lines, RdvInfo rdv) {
        addSection(lines, "RDV");

        if (rdv == null) {
            addNote(lines, "No RDV details recorded.");
            return;
        }

        addField(lines, "Joignable", yesNo(rdv.getJoignable()));
        addField(lines, "RDV date", formatInstant(rdv.getDatePriseRdv()));
        addField(lines, "Abandon", yesNo(rdv.getAbandon()));
        addField(lines, "Cause injoignabilite", rdv.getCauseInjoignabilite());
        addField(lines, "Cause abandon", rdv.getCauseAbandon());
        addField(lines, "Detail", rdv.getDetail());
        addField(lines, "Comment", rdv.getCmtRdv());
    }

    private void addWorkSection(List<PdfLine> lines, WorkInfo work) {
        addSection(lines, "Work");

        if (work == null) {
            addNote(lines, "No work report submitted yet.");
            return;
        }

        addField(lines, "Result", work.getRepTravauxStt());
        addField(lines, "Work end date", formatInstant(work.getDateFinTrv()));
        addField(lines, "Comment", work.getCmtTrvStt());
        addField(lines, "Updated at", formatInstant(work.getUpdatedAt()));
        addField(lines, "Updated by", work.getUpdatedBy());
        addField(lines, "Work GPS", coordinates(work.getLocationLatitude(), work.getLocationLongitude()));
        addField(lines, "Location captured at", formatInstant(work.getLocationCapturedAt()));
        addField(lines, "Location accuracy", accuracy(work.getLocationAccuracyMeters()));

        if (work.getUsedEquipment() == null || work.getUsedEquipment().isEmpty()) {
            addField(lines, "Used equipment", "None");
            return;
        }

        int index = 1;
        for (var item : work.getUsedEquipment()) {
            String label = firstNonBlank(item.getEquipmentName(), item.getEquipmentId());
            String value = label + " x" + item.getQuantity()
                    + (item.getUnit() != null && !item.getUnit().isBlank() ? " " + item.getUnit() : "");
            addField(lines, "Used equipment " + index, value);
            index++;
        }
    }

    private void addMetricsSection(List<PdfLine> lines, TechnicalMetrics metrics) {
        addSection(lines, "Technical Metrics");

        if (metrics == null) {
            addNote(lines, "No technical metrics recorded.");
            return;
        }

        addField(lines, "Cable metrage", number(metrics.getMetrageCable()));
        addField(lines, "Nombre PV", number(metrics.getNombrePv()));
        addField(lines, "Debit DL", number(metrics.getDebitDl()));
        addField(lines, "Debit UL", number(metrics.getDebitUl()));
        addField(lines, "RSRP", number(metrics.getRsrp()));
        addField(lines, "RSRQ", number(metrics.getRsrq()));
        addField(lines, "SINR", number(metrics.getSinr()));
    }

    private void addValidationSection(List<PdfLine> lines, ZmValidation validation) {
        addSection(lines, "ZM Validation");

        if (validation == null) {
            addNote(lines, "No ZM validation recorded.");
            return;
        }

        addField(lines, "Approved", yesNo(validation.getApproved()));
        addField(lines, "Comment", validation.getCmtZm());
        addField(lines, "Validated at", formatInstant(validation.getValidatedAt()));
        addField(lines, "Validated by", validation.getValidatedBy());
    }

    private void addProofSection(List<PdfLine> lines, List<CaseProofFileResponse> proofs) {
        addSection(lines, "Work Proof");

        if (proofs == null || proofs.isEmpty()) {
            addNote(lines, "No proof files attached.");
            return;
        }

        int index = 1;
        for (CaseProofFileResponse proof : proofs) {
            addLine(lines, body("Proof " + index + " - " + valueOrDash(proof.originalFilename())));
            addField(lines, "Uploaded by", proof.uploadedBy());
            addField(lines, "Uploaded at", formatInstant(proof.createdAt()));
            addField(lines, "Capture source", proof.captureSource());
            addField(lines, "Proof GPS", coordinates(proof.locationLatitude(), proof.locationLongitude()));
            addField(lines, "Location captured at", formatInstant(proof.locationCapturedAt()));
            addField(lines, "Location accuracy", accuracy(proof.locationAccuracyMeters()));
            addField(lines, "View URL", proof.viewUrl());
            addField(lines, "Download URL", proof.downloadUrl());
            index++;
        }
    }

    private void addTimelineSection(List<PdfLine> lines, List<CaseTimelineEvent> timeline) {
        addSection(lines, "Timeline");

        if (timeline == null || timeline.isEmpty()) {
            addNote(lines, "No timeline events recorded.");
            return;
        }

        int count = 0;
        for (CaseTimelineEvent event : timeline) {
            if (count >= 12) {
                addNote(lines, "Additional events exist but are not included in this PDF export.");
                break;
            }

            String title = valueOrDash(event.getTitle());
            addLine(lines, body(formatInstant(event.getOccurredAt()) + " - " + title));
            addField(lines, "Actor", event.getActor());
            addField(lines, "Details", event.getDetails());
            addField(
                    lines,
                    "State change",
                    humanizeStatus(event.getPreviousStatus()) + " -> " + humanizeStatus(event.getNewStatus())
            );
            count++;
        }
    }

    private void addSection(List<PdfLine> lines, String title) {
        addLine(lines, heading(title));
    }

    private void addNote(List<PdfLine> lines, String note) {
        for (String wrapped : wrap(note, SMALL_WRAP)) {
            addLine(lines, muted(wrapped));
        }
    }

    private void addField(List<PdfLine> lines, String label, Object value) {
        String text = label + ": " + valueOrDash(value);
        List<String> wrapped = wrap(text, BODY_WRAP);

        for (int i = 0; i < wrapped.size(); i++) {
            String chunk = i == 0 ? wrapped.get(i) : "    " + wrapped.get(i);
            addLine(lines, body(chunk));
        }
    }

    private void addLine(List<PdfLine> lines, PdfLine line) {
        lines.add(line);
    }

    private List<List<PlacedPdfLine>> paginate(List<PdfLine> lines) {
        List<List<PlacedPdfLine>> pages = new ArrayList<>();
        List<PlacedPdfLine> currentPage = new ArrayList<>();
        int currentY = TOP_Y;

        for (PdfLine line : lines) {
            int candidateY = currentY - line.gapBefore();
            if (candidateY - line.lineHeight() < BOTTOM_Y) {
                pages.add(currentPage);
                currentPage = new ArrayList<>();
                currentY = TOP_Y;
                candidateY = currentY;
            }

            currentPage.add(new PlacedPdfLine(line, candidateY));
            currentY = candidateY - line.lineHeight();
        }

        if (currentPage.isEmpty()) {
            currentPage.add(new PlacedPdfLine(body("No content available."), TOP_Y));
        }

        pages.add(currentPage);
        return pages;
    }

    private byte[] renderPdf(List<List<PlacedPdfLine>> pages) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        offsets.add(0);

        writeAscii(output, "%PDF-1.4\n");

        int fontObjectNumber = 3;
        int firstPageObjectNumber = 4;

        writeObject(output, offsets, 1, "<< /Type /Catalog /Pages 2 0 R >>");

        StringJoiner kids = new StringJoiner(" ");
        for (int i = 0; i < pages.size(); i++) {
            kids.add((firstPageObjectNumber + (i * 2)) + " 0 R");
        }

        writeObject(
                output,
                offsets,
                2,
                "<< /Type /Pages /Kids [" + kids + "] /Count " + pages.size() + " >>"
        );

        writeObject(
                output,
                offsets,
                fontObjectNumber,
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
        );

        for (int i = 0; i < pages.size(); i++) {
            int pageObjectNumber = firstPageObjectNumber + (i * 2);
            int contentObjectNumber = pageObjectNumber + 1;

            writeObject(
                    output,
                    offsets,
                    pageObjectNumber,
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + PAGE_WIDTH + " " + PAGE_HEIGHT + "] "
                            + "/Resources << /Font << /F1 " + fontObjectNumber + " 0 R >> >> "
                            + "/Contents " + contentObjectNumber + " 0 R >>"
            );

            byte[] stream = renderPageStream(pages.get(i), i + 1, pages.size());
            writeStreamObject(output, offsets, contentObjectNumber, stream);
        }

        int xrefOffset = output.size();
        writeAscii(output, "xref\n0 " + offsets.size() + "\n");
        writeAscii(output, "0000000000 65535 f \n");

        for (int i = 1; i < offsets.size(); i++) {
            writeAscii(output, String.format(Locale.ROOT, "%010d 00000 n \n", offsets.get(i)));
        }

        writeAscii(output, "trailer << /Size " + offsets.size() + " /Root 1 0 R >>\n");
        writeAscii(output, "startxref\n" + xrefOffset + "\n%%EOF");

        return output.toByteArray();
    }

    private byte[] renderPageStream(List<PlacedPdfLine> page, int pageNumber, int pageCount) {
        StringBuilder stream = new StringBuilder();

        for (PlacedPdfLine placed : page) {
            PdfLine line = placed.line();
            stream.append("BT\n")
                    .append("/F1 ").append(line.fontSize()).append(" Tf\n")
                    .append(line.rgb()).append(" rg\n")
                    .append(LEFT_MARGIN).append(' ').append(placed.y()).append(" Td\n")
                    .append('(').append(escapePdfText(line.text())).append(") Tj\n")
                    .append("ET\n");
        }

        stream.append("BT\n")
                .append("/F1 9 Tf\n")
                .append(MUTED_TEXT).append(" rg\n")
                .append(LEFT_MARGIN).append(" 28 Td\n")
                .append("(Page ").append(pageNumber).append(" of ").append(pageCount).append(") Tj\n")
                .append("ET\n");

        return stream.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private void writeObject(ByteArrayOutputStream output, List<Integer> offsets, int objectNumber, String body) {
        offsets.add(output.size());
        writeAscii(output, objectNumber + " 0 obj\n" + body + "\nendobj\n");
    }

    private void writeStreamObject(ByteArrayOutputStream output, List<Integer> offsets, int objectNumber, byte[] stream) {
        offsets.add(output.size());
        writeAscii(output, objectNumber + " 0 obj\n<< /Length " + stream.length + " >>\nstream\n");
        output.writeBytes(stream);
        writeAscii(output, "\nendstream\nendobj\n");
    }

    private void writeAscii(ByteArrayOutputStream output, String value) {
        output.writeBytes(value.getBytes(StandardCharsets.ISO_8859_1));
    }

    private List<String> wrap(String text, int maxLength) {
        String source = toPdfSafeText(valueOrDash(text));
        List<String> lines = new ArrayList<>();

        if (source.length() <= maxLength) {
            lines.add(source);
            return lines;
        }

        String[] words = source.split("\\s+");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            if (word.length() > maxLength) {
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                    current.setLength(0);
                }

                int start = 0;
                while (start < word.length()) {
                    int end = Math.min(start + maxLength, word.length());
                    lines.add(word.substring(start, end));
                    start = end;
                }
                continue;
            }

            if (current.isEmpty()) {
                current.append(word);
                continue;
            }

            if (current.length() + 1 + word.length() <= maxLength) {
                current.append(' ').append(word);
            } else {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
            }
        }

        if (!current.isEmpty()) {
            lines.add(current.toString());
        }

        if (lines.isEmpty()) {
            lines.add("-");
        }

        return lines;
    }

    private PdfLine title(String text) {
        return new PdfLine(toPdfSafeText(text), 18, OOREEDOO_RED, 0, 20);
    }

    private PdfLine subtitle(String text) {
        return new PdfLine(toPdfSafeText(text), 11, MUTED_TEXT, 2, 14);
    }

    private PdfLine heading(String text) {
        return new PdfLine(toPdfSafeText(text), 13, OOREEDOO_RED, 10, 18);
    }

    private PdfLine body(String text) {
        return new PdfLine(toPdfSafeText(text), 11, DARK_TEXT, 2, 14);
    }

    private PdfLine muted(String text) {
        return new PdfLine(toPdfSafeText(text), 10, MUTED_TEXT, 2, 13);
    }

    private String valueOrDash(Object value) {
        if (value == null) {
            return "-";
        }

        String rendered = String.valueOf(value).trim();
        return rendered.isEmpty() ? "-" : rendered;
    }

    private String firstNonBlank(String primary, String fallback) {
        if (!isBlank(primary)) {
            return primary.trim();
        }
        if (!isBlank(fallback)) {
            return fallback.trim();
        }
        return "-";
    }

    private String yesNo(Boolean value) {
        if (value == null) {
            return "-";
        }
        return Boolean.TRUE.equals(value) ? "Yes" : "No";
    }

    private String accuracy(Double meters) {
        if (meters == null) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.1f m", meters);
    }

    private String percentage(Double value) {
        if (value == null) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.1f%%", value);
    }

    private String number(Number value) {
        if (value == null) {
            return "-";
        }
        return String.valueOf(value);
    }

    private String durationLabel(Long seconds) {
        if (seconds == null) {
            return "-";
        }

        long remaining = Math.max(0L, seconds);
        long hours = remaining / 3600;
        long minutes = (remaining % 3600) / 60;
        long secs = remaining % 60;

        if (hours > 0) {
            return hours + "h " + minutes + "m " + secs + "s";
        }

        if (minutes > 0) {
            return minutes + "m " + secs + "s";
        }

        return secs + "s";
    }

    private String person(String name, String email) {
        if (isBlank(name) && isBlank(email)) {
            return "-";
        }
        if (!isBlank(name) && !isBlank(email)) {
            return name.trim() + " (" + email.trim() + ")";
        }
        return !isBlank(name) ? name.trim() : email.trim();
    }

    private String coordinates(Object latitude, Object longitude) {
        String lat = valueOrDash(latitude);
        String lon = valueOrDash(longitude);
        if ("-".equals(lat) && "-".equals(lon)) {
            return "-";
        }
        return lat + ", " + lon;
    }

    private String joinNonBlank(String separator, String... parts) {
        StringJoiner joiner = new StringJoiner(separator);
        for (String part : parts) {
            if (!isBlank(part)) {
                joiner.add(part.trim());
            }
        }
        String joined = joiner.toString();
        return joined.isBlank() ? "-" : joined;
    }

    private String formatInstant(Instant instant) {
        if (instant == null) {
            return "-";
        }
        return DATE_TIME_FORMATTER.format(instant);
    }

    private String humanizeStatus(CaseStatus status) {
        if (status == null) {
            return "-";
        }
        return humanizeToken(status.name());
    }

    private String humanizeToken(String token) {
        if (isBlank(token)) {
            return "-";
        }

        String[] parts = token.trim().toLowerCase(Locale.ROOT).split("_");
        StringJoiner joiner = new StringJoiner(" ");

        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }

            joiner.add(Character.toUpperCase(part.charAt(0)) + part.substring(1));
        }

        String humanized = joiner.toString();
        return humanized.isBlank() ? "-" : humanized;
    }

    private String toPdfSafeText(String value) {
        String normalized = Normalizer.normalize(Objects.toString(value, "-"), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replace('•', '-')
                .replace('→', '>')
                .replace('←', '<')
                .replace('–', '-')
                .replace('—', '-');

        String collapsed = normalized.replaceAll("\\s+", " ").trim();
        if (collapsed.isEmpty()) {
            return "-";
        }

        return collapsed.replaceAll("[^\\x20-\\x7E]", "?");
    }

    private String escapePdfText(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)");
    }

    private String sanitizeFilenameFragment(String value) {
        if (isBlank(value)) {
            return "";
        }

        return value.trim()
                .replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("(^[-.]+|[-.]+$)", "");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record PdfLine(String text, int fontSize, String rgb, int gapBefore, int lineHeight) {
    }

    private record PlacedPdfLine(PdfLine line, int y) {
    }
}
