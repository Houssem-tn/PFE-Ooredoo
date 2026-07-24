package com.pfe.stock_management.retrieved_stock;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

public interface RetrievedStockItemRepository extends MongoRepository<RetrievedStockItem, String> {

    List<RetrievedStockItem> findByTechnicianIdAndStatusOrderByRetrievedAtDesc(
            String technicianId, RetrievedStockItemStatus status);

    /** Technician's own pending bucket — status-in so both the new and legacy pending values match. */
    List<RetrievedStockItem> findByTechnicianIdAndStatusInOrderByRetrievedAtDesc(
            String technicianId, Collection<RetrievedStockItemStatus> statuses);

    // ── STT collection (correct owner) ─────────────────────────────────────────

    /** Pending items for a specific STT (both new PENDING_STT_COLLECTION and legacy PENDING_COLLECTION). */
    List<RetrievedStockItem> findBySttIdAndStatusInOrderByRetrievedAtDesc(
            String sttId, Collection<RetrievedStockItemStatus> statuses);

    /** STT Collected Stock — items this STT physically collected, in the given statuses. */
    List<RetrievedStockItem> findByCollectedBySttIdAndStatusInOrderByCollectedAtDesc(
            String collectedBySttId, Collection<RetrievedStockItemStatus> statuses);

    /** Items this STT collected that are still free to be added to a new Material Return Request. */
    List<RetrievedStockItem> findByCollectedBySttIdAndStatusAndMaterialReturnRequestIdIsNullOrderByCollectedAtDesc(
            String collectedBySttId, RetrievedStockItemStatus status);

    /** All items whose responsible STT is this one — read-only history / tracking. */
    List<RetrievedStockItem> findBySttIdOrderByRetrievedAtDesc(String sttId);

    /** All collected-item records currently linked to a given Material Return Request. */
    List<RetrievedStockItem> findByMaterialReturnRequestId(String materialReturnRequestId);

    // ── ZM read-only tracking ──────────────────────────────────────────────────

    List<RetrievedStockItem> findByZmIdAndStatusOrderByRetrievedAtDesc(
            String zmId, RetrievedStockItemStatus status);

    List<RetrievedStockItem> findByZmIdAndStatusInOrderByRetrievedAtDesc(
            String zmId, Collection<RetrievedStockItemStatus> statuses);

    /** All items under a ZM's territory — read-only tracking across the whole flow. */
    List<RetrievedStockItem> findByZmIdOrderByRetrievedAtDesc(String zmId);

    // ── Legacy ─────────────────────────────────────────────────────────────────

    List<RetrievedStockItem> findByCollectedByZmIdAndStatusOrderByCollectedAtDesc(
            String collectedByZmId, RetrievedStockItemStatus status);

    List<RetrievedStockItem> findByCaseIdOrderByRetrievedAtDesc(String caseId);

    List<RetrievedStockItem> findAllByOrderByRetrievedAtDesc();
}
