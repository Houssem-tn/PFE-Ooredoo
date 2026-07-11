package com.pfe.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * A small, focused set of custom business metrics — not one counter per
 * code path. Every tag value here is a bounded enum/category (module,
 * status, channel, endpoint, reason); never a case ID, user ID, email,
 * filename, or other high-cardinality/identifying value — see
 * OBSERVABILITY_BACKUP.md "Sensitive-data protections" for why that line
 * is drawn exactly there (Prometheus itself degrades badly under
 * high-cardinality labels, independent of any privacy concern).
 *
 * <p>Counters/timers are created lazily and cached per tag combination
 * (a fixed, small set in practice — CaseModuleType has 3 values, DpmStatus
 * a handful) rather than declared as a static list, so adding a new enum
 * constant elsewhere in the codebase doesn't require touching this class.
 */
@Component
public class BusinessMetrics {

    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> timers = new ConcurrentHashMap<>();

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    // ---------------------------------------------------------------
    // Cases
    // ---------------------------------------------------------------

    public void recordCaseCreated(String module) {
        counter("cases_created_total", "Cases created, by module.", "module", module).increment();
    }

    public void recordCaseClosed(String module) {
        counter("cases_closed_total", "Cases closed, by module.", "module", module).increment();
    }

    public void recordSlaBreach() {
        counter("cases_sla_breached_total", "SLA breach transitions recorded.").increment();
    }

    public void recordCaseAssignmentFailure(String module) {
        counter("case_assignment_failures_total", "Case/routing assignment attempts that found no eligible assignee.", "module", module).increment();
    }

    // ---------------------------------------------------------------
    // Stock / DPM / material returns
    // ---------------------------------------------------------------

    public void recordDpmConfirmed(String status) {
        counter("dpm_requests_confirmed_total", "DPM requests confirmed (goods received), by resulting status.", "status", status).increment();
    }

    public void recordStockInsufficient() {
        counter("stock_insufficient_total", "Stock operations rejected for insufficient quantity.").increment();
    }

    public void recordMaterialReturnCreated() {
        counter("material_returns_created_total", "Material return requests created.").increment();
    }

    // ---------------------------------------------------------------
    // Excel/DMS import
    // ---------------------------------------------------------------

    public void recordExcelImportDuration(String type, long durationNanos) {
        timer("excel_import_duration_seconds", "Duration of a full Excel/DMS import operation, by import type.", "type", type)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordExcelImportFailure(String type) {
        counter("excel_import_failures_total", "Excel/DMS import operations that failed outright.", "type", type).increment();
    }

    // ---------------------------------------------------------------
    // Notifications
    // ---------------------------------------------------------------

    public void recordNotificationDeliveryFailure(String channel) {
        counter("notification_delivery_failures_total", "Notification deliveries that failed, by channel.", "channel", channel).increment();
    }

    // ---------------------------------------------------------------
    // OCR (backend's view of calls it makes to the OCR service — distinct
    // from the OCR service's own /metrics, which covers its internal
    // pipeline; this covers the network call + backend-side handling).
    // ---------------------------------------------------------------

    public void recordOcrRequest(long durationNanos, boolean success) {
        timer("ocr_request_duration_seconds", "Duration of backend calls to the OCR service.", "outcome", success ? "success" : "failure")
                .record(durationNanos, TimeUnit.NANOSECONDS);
        if (!success) {
            counter("ocr_request_failures_total", "Backend calls to the OCR service that failed.").increment();
        }
    }

    // ---------------------------------------------------------------
    // MinIO
    // ---------------------------------------------------------------

    public void recordMinioUploadFailure() {
        counter("minio_upload_failures_total", "File uploads that fell back to local disk storage after a MinIO error (see FileStorageService.upload).").increment();
    }

    // ---------------------------------------------------------------
    // Helpers — lazy creation, cached by name+tag-value so repeated calls
    // don't re-register a meter (Micrometer treats re-registration of an
    // identical id as a no-op, but the cache avoids the lookup cost and
    // keeps each recordX method a one-liner above).
    // ---------------------------------------------------------------

    private Counter counter(String name, String description) {
        return counters.computeIfAbsent(name, n -> Counter.builder(n).description(description).register(registry));
    }

    private Counter counter(String name, String description, String tagKey, String tagValue) {
        String cacheKey = name + '{' + tagKey + '=' + tagValue + '}';
        return counters.computeIfAbsent(cacheKey, k ->
                Counter.builder(name).description(description).tag(tagKey, safeTag(tagValue)).register(registry));
    }

    private Timer timer(String name, String description, String tagKey, String tagValue) {
        String cacheKey = name + '{' + tagKey + '=' + tagValue + '}';
        return timers.computeIfAbsent(cacheKey, k ->
                Timer.builder(name).description(description).tag(tagKey, safeTag(tagValue)).register(registry));
    }

    private static String safeTag(String value) {
        return value == null ? "unknown" : value;
    }
}
