package com.pfe.stock_management.material_return;

public enum ReturnSource {
    /** Material that currently exists in the STT's available stock */
    STT_STOCK,
    /** Existing collected-item records physically collected by the STT from technicians */
    COLLECTED_ITEM_STOCK,
    /** Equipment recovered from a client site */
    CLIENT_RECOVERY,
    /** Equipment collected after a client résiliation (cancellation) */
    RESILIATION_RECOVERY,
    /** Damaged or broken equipment picked up from the field */
    DAMAGED_FIELD_RECOVERY,
    /** Unused material left over from a field intervention */
    UNUSED_FIELD_MATERIAL,
    /** Any other external / unclassified source */
    OTHER
}
