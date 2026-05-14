package com.pfe.activation_analytics.service;

import com.pfe.activation_analytics.entity.ActivationAnalyticsImportBatchEntity;
import com.pfe.activation_analytics.entity.ActivationAnalyticsRowEntity;
import com.pfe.activation_analytics.model.ActivationImportSharedResult;
import com.pfe.activation_analytics.model.ActivationImportSharedRow;
import com.pfe.activation_analytics.repository.ActivationAnalyticsImportBatchRepository;
import com.pfe.case_management.enums.CaseType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActivationAnalyticsBatchProcessingService {

    private static final int INSERT_CHUNK_SIZE = 5_000;

    private final ActivationAnalyticsImportBatchRepository batchRepository;
    private final MongoTemplate mongoTemplate;

    public StoredAnalyticsBatch store(ActivationImportSharedResult sharedResult, String uploadedBy) {
        List<ActivationImportSharedRow> rowsToStore = sharedResult.analyticsRowsToStore();
        if (rowsToStore.isEmpty()) {
            return new StoredAnalyticsBatch(null, 0, null);
        }

        String batchId = UUID.randomUUID().toString();
        Instant uploadedAt = Instant.now();
        List<ActivationAnalyticsRowEntity> entities = new ArrayList<>(rowsToStore.size());
        for (ActivationImportSharedRow row : rowsToStore) {
            entities.add(toEntity(row, sharedResult.sourceFileName(), batchId, uploadedBy, uploadedAt));
        }

        insertRowsInBatches(entities);

        ActivationAnalyticsImportBatchEntity batch = ActivationAnalyticsImportBatchEntity.builder()
                .batchId(batchId)
                .uploadedBy(uploadedBy)
                .uploadedAt(uploadedAt)
                .caseType(CaseType.ACTIVATION)
                .fileName(sharedResult.sourceFileName())
                .totalRows(sharedResult.totalRows())
                .insertedRows(entities.size())
                .validRows(sharedResult.validNewRows())
                .invalidRows(sharedResult.invalidRows())
                .duplicateRows(sharedResult.duplicateRows())
                .distinctStts(sharedResult.distinctStts())
                .distinctGovernorates(sharedResult.distinctGovernorates())
                .build();
        batchRepository.save(batch);

        return new StoredAnalyticsBatch(batchId, entities.size(), uploadedAt);
    }

    private void insertRowsInBatches(List<ActivationAnalyticsRowEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return;
        }

        for (int start = 0; start < entities.size(); start += INSERT_CHUNK_SIZE) {
            int end = Math.min(start + INSERT_CHUNK_SIZE, entities.size());
            mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, ActivationAnalyticsRowEntity.class)
                    .insert(entities.subList(start, end))
                    .execute();
        }
    }

    private ActivationAnalyticsRowEntity toEntity(
            ActivationImportSharedRow row,
            String sourceFileName,
            String batchId,
            String uploadedBy,
            Instant uploadedAt
    ) {
        return ActivationAnalyticsRowEntity.builder()
                .batchId(batchId)
                .uploadedBy(uploadedBy)
                .uploadedAt(uploadedAt)
                .caseType(CaseType.ACTIVATION)
                .sourceFileName(sourceFileName)
                .rowNumber(row.rowNumber())
                .validRow(row.analyticsInsertableRow())
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
                .completed(ActivationAnalyticsSupport.isCompleted(
                        row.statut(),
                        row.repTravauxStt(),
                        row.dateFinTrvDate()
                ))
                .rdvSuccess(ActivationAnalyticsSupport.isRdvSuccess(
                        row.repRdv(),
                        row.datePriseRdvDate()
                ))
                .injoignable(ActivationAnalyticsSupport.isInjoignable(
                        row.causeInjoignabilite(),
                        row.repRdv(),
                        row.statut(),
                        row.detail()
                ))
                .abandon(ActivationAnalyticsSupport.isAbandon(
                        row.causeAbandon(),
                        row.repRdv(),
                        row.statut(),
                        row.detail()
                ))
                .blocked(ActivationAnalyticsSupport.isBlocked(
                        row.statut(),
                        row.detail(),
                        row.commentaire(),
                        row.cmtZm(),
                        row.cmtTrvStt()
                ))
                .delayed(ActivationAnalyticsSupport.isDelayed(
                        row.endToEndBusinessDays(),
                        row.assignmentBusinessDays(),
                        row.rdvBusinessDays(),
                        row.executionBusinessDays()
                ))
                .build();
    }

    public record StoredAnalyticsBatch(
            String batchId,
            int insertedRows,
            Instant uploadedAt
    ) {
    }
}
