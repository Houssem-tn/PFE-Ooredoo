package com.pfe.stock_management.retrieved_stock;

import com.pfe.stock_management.material_return.ReturnCondition;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One physical (or single-quantity-line) piece of equipment retrieved from a customer during a
 * PLT (complaint) or RES (résiliation) intervention, held against the technician who retrieved it
 * until a ZM physically collects it.
 *
 * <p>Deliberately separate from {@link com.pfe.stock_management.material_return.MaterialReturnRequest}:
 * that entity is an STT-identity-centric, schedule-negotiation workflow (STT submits → ZM proposes a
 * handover date → STT accepts/rejects → ZM marks handed over) with no technician dimension. This
 * entity is technician-centric and the ZM action is a single direct "confirm collection" step with
 * no negotiation — see {@code RetrievedStockService.confirmCollection}.
 *
 * <p>One document per declared line (not one per batch): the ZM's unit of action is a single item,
 * potentially selected alongside items from other technicians in one confirm call, and per-document
 * mutation keeps that call safe without multi-document transactions (this deployment has no replica
 * set, so those aren't available regardless).
 *
 * <p>Never feeds {@code TechStock}/{@code SttStock}: retrieved stock can never be consumed,
 * installed, or manually moved into Available Stock. It only ever moves from
 * {@link RetrievedStockItemStatus#PENDING_COLLECTION} to {@link RetrievedStockItemStatus#COLLECTED}.
 */
@Document(collection = "stock_retrieved_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetrievedStockItem {

    @Id
    private String id;

    /** Groups every line declared in one {@code declareClientRecovery} submission, audit only. */
    private String declarationBatchId;

    // ── Case / client context ────────────────────────────────────────────────
    private String caseId;
    private String crmCase;
    /** "PLT" or "RES" — the business context the item was retrieved under. */
    private String caseUsageContext;
    private String clientName;
    private String clientPhone;

    // ── Who retrieved it / territory ─────────────────────────────────────────
    private String technicianId;
    private String technicianName;
    private String technicianEmail;
    private String sttId;
    private String sttName;
    private String sttEmail;
    private String zmId;
    private String zmName;
    private String zmEmail;

    // ── Equipment ─────────────────────────────────────────────────────────────
    private String equipmentId;
    private String equipmentName;
    private String articleCode;
    private String unit;
    /** Always 1 for tracked (serialized) equipment — one serial/IMEI slot per line. */
    private int quantity;
    private String serialNumber;
    private String macAddress;
    private String imei;
    /** Linked tracked-unit id when the retrieved item corresponds to an EquipmentInstance. */
    private String instanceId;

    // ── Disposition / condition ───────────────────────────────────────────────
    private RecoveryDisposition disposition;
    private ReturnCondition condition;
    private String dispositionNote;

    // ── Proof ─────────────────────────────────────────────────────────────────
    private String proofFileId;

    // ── Status / declare ──────────────────────────────────────────────────────
    @Builder.Default
    private RetrievedStockItemStatus status = RetrievedStockItemStatus.PENDING_COLLECTION;
    private Instant retrievedAt;
    private String declaredBy;

    // ── STT collection (correct owner of the physical collection step) ─────────
    private String collectedBySttId;
    private String collectedBySttName;

    // ── Legacy ZM collection fields — kept for historical documents only ───────
    private String collectedByZmId;
    private String collectedByZmName;

    private Instant collectedAt;
    private String collectionBatchId;
    private String collectionNote;

    // ── Audit ─────────────────────────────────────────────────────────────────
    private Instant createdAt;
    private Instant updatedAt;
}
