package com.pfe.stock_management.service;

import com.pfe.MainPackage.Entity.CaseModuleType;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.enums.CaseType;
import com.pfe.stock_management.entity.Equipment;
import com.pfe.stock_management.repository.EquipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Single source of truth for which equipment a field workflow may consume or recover,
 * derived from the per-item business classification on {@link Equipment}.
 *
 * <p>Rules enforced (server-side, regardless of what the frontend sends):
 * <ul>
 *   <li><b>ACT</b> (activation) — only {@code usableInAct} items; pack items are always
 *       blocked because the client already receives them in the boutique pack.</li>
 *   <li><b>PLT</b> (complaint) — only {@code usableInPlt} items; tracked as complaint
 *       material usage / swap.</li>
 *   <li><b>RES</b> (résiliation) — no stock consumption at all. Recovered client material
 *       is declared separately and must be {@code recoverableFromClient}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class EquipmentUsagePolicyService {

    /** Source marker used by the RES Excel import (mirrors CaseService.RES_IMPORT_SOURCE). */
    private static final String RES_IMPORT_SOURCE = "RES_TODO_IMPORT";

    private final EquipmentRepository equipmentRepository;

    /** Business context a case belongs to for stock purposes. */
    public enum UsageContext {
        ACT("activation (ACT)"),
        PLT("complaint (PLT)"),
        RES("résiliation (RES)");

        private final String label;

        UsageContext(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /**
     * Resolves the stock business context of a case. RES is detected from the dedicated
     * RESILIATION type, the RES module discriminator, or the RES import source (RES persists
     * as type=PLAINTE in the shared collection). Everything else PLAINTE is PLT; ACTIVATION is ACT.
     */
    public UsageContext resolveContext(CaseEntity caseEntity) {
        if (caseEntity == null) {
            return UsageContext.ACT;
        }
        if (caseEntity.getType() == CaseType.RESILIATION) {
            return UsageContext.RES;
        }
        if (caseEntity.getType() == CaseType.PLAINTE) {
            boolean isRes = caseEntity.getCaseModule() == CaseModuleType.RES
                    || RES_IMPORT_SOURCE.equals(caseEntity.getSource());
            return isRes ? UsageContext.RES : UsageContext.PLT;
        }
        return UsageContext.ACT;
    }

    /** Audit label distinguishing ACT consumption from PLT usage/swap. */
    public String consumptionAuditLabel(UsageContext context) {
        return switch (context) {
            case ACT -> "ACT_CONSUMPTION";
            case PLT -> "PLT_USAGE_SWAP";
            case RES -> "RES_CLIENT_RECOVERY";
        };
    }

    /**
     * Enforces that every requested equipment may be consumed on the given case.
     * Throws a 400 with a clear message otherwise. A no-op when no equipment is requested.
     */
    public void assertConsumable(CaseEntity caseEntity, Collection<String> equipmentIds) {
        UsageContext context = resolveContext(caseEntity);

        Set<String> ids = normalize(equipmentIds);
        if (ids.isEmpty()) {
            // RES with no items is fine (no consumption); ACT/PLT with no items is also fine.
            return;
        }

        if (context == UsageContext.RES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Résiliation (RES) cases do not consume stock. Retrieved client material must be "
                            + "declared as recovered material via the RES recovery declaration, not consumed from TECH stock.");
        }

        Map<String, Equipment> catalog = loadCatalog(ids);
        for (String id : ids) {
            Equipment eq = catalog.get(id);
            if (eq == null) {
                // Not in the catalogue (e.g. deleted) — cannot classify, allow rather than block legacy stock.
                continue;
            }
            String name = displayName(eq, id);

            if (context == UsageContext.ACT) {
                if (eq.isPackItem()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "\"" + name + "\" is a boutique pack item and cannot be consumed during an "
                                    + "activation (ACT). The client already receives it in the pack.");
                }
                if (!eq.isUsableInAct()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "\"" + name + "\" is not allowed for activation (ACT) consumption. "
                                    + "Only equipment marked \"Usable in ACT\" can be used here.");
                }
            } else { // PLT
                if (!eq.isUsableInPlt()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "\"" + name + "\" is not allowed for complaint (PLT) usage/swap. "
                                    + "Only equipment marked \"Usable in PLT\" can be used here.");
                }
            }
        }
    }

    /**
     * Enforces that a client-material recovery declaration only references equipment that is
     * allowed to be recovered from the client. Available for both RES (résiliation — the only
     * allowed operation there) and PLT (complaint — alongside optional consumption/swap). Requires
     * an ACT case to always be rejected, and at least one item.
     */
    public void assertRecoverable(CaseEntity caseEntity, Collection<String> equipmentIds) {
        UsageContext context = resolveContext(caseEntity);
        if (context != UsageContext.RES && context != UsageContext.PLT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Client material recovery is only available for complaint (PLT) or résiliation (RES) cases.");
        }

        Set<String> ids = normalize(equipmentIds);
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Declare at least one recovered item.");
        }

        Map<String, Equipment> catalog = loadCatalog(ids);
        for (String id : ids) {
            Equipment eq = catalog.get(id);
            if (eq == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Recovered equipment not found in the catalogue: " + id);
            }
            if (!eq.isRecoverableFromClient()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "\"" + displayName(eq, id) + "\" cannot be recovered from the client. "
                                + "Only equipment marked \"Recoverable from client\" can be declared here.");
            }
        }
    }

    private Map<String, Equipment> loadCatalog(Collection<String> ids) {
        Map<String, Equipment> map = new HashMap<>();
        equipmentRepository.findAllById(ids).forEach(eq -> map.put(eq.getId(), eq));
        return map;
    }

    private Set<String> normalize(Collection<String> ids) {
        Set<String> normalized = new LinkedHashSet<>();
        if (ids == null) {
            return normalized;
        }
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                normalized.add(id.trim());
            }
        }
        return normalized;
    }

    private String displayName(Equipment eq, String fallbackId) {
        return (eq.getName() != null && !eq.getName().isBlank()) ? eq.getName() : fallbackId;
    }
}
