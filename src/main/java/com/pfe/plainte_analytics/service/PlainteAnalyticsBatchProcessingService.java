package com.pfe.plainte_analytics.service;

import com.pfe.case_management.enums.CaseType;
import com.pfe.plainte_analytics.entity.PlainteAnalyticsImportBatchEntity;
import com.pfe.plainte_analytics.entity.PlainteAnalyticsRowEntity;
import com.pfe.plainte_analytics.model.PlainteImportSharedResult;
import com.pfe.plainte_analytics.model.PlainteImportSharedRow;
import com.pfe.plainte_analytics.repository.PlainteAnalyticsImportBatchRepository;
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
public class PlainteAnalyticsBatchProcessingService {

    private static final int INSERT_CHUNK_SIZE = 5_000;

    private final PlainteAnalyticsImportBatchRepository batchRepository;
    private final MongoTemplate mongoTemplate;

    public StoredAnalyticsBatch store(PlainteImportSharedResult sharedResult, String uploadedBy) {
        List<PlainteImportSharedRow> rowsToStore = sharedResult.analyticsRowsToStore();
        if (rowsToStore.isEmpty()) {
            return new StoredAnalyticsBatch(null, 0, null);
        }

        String batchId = UUID.randomUUID().toString();
        Instant uploadedAt = Instant.now();
        List<PlainteAnalyticsRowEntity> entities = new ArrayList<>(rowsToStore.size());
        for (PlainteImportSharedRow row : rowsToStore) {
            entities.add(toEntity(row, sharedResult.sourceFileName(), batchId, uploadedBy, uploadedAt));
        }

        insertRowsInBatches(entities);

        PlainteAnalyticsImportBatchEntity batch = PlainteAnalyticsImportBatchEntity.builder()
                .batchId(batchId)
                .uploadedBy(uploadedBy)
                .uploadedAt(uploadedAt)
                .caseType(CaseType.PLAINTE)
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

    private void insertRowsInBatches(List<PlainteAnalyticsRowEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return;
        }

        for (int start = 0; start < entities.size(); start += INSERT_CHUNK_SIZE) {
            int end = Math.min(start + INSERT_CHUNK_SIZE, entities.size());
            mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, PlainteAnalyticsRowEntity.class)
                    .insert(entities.subList(start, end))
                    .execute();
        }
    }

    private PlainteAnalyticsRowEntity toEntity(
            PlainteImportSharedRow row,
            String sourceFileName,
            String batchId,
            String uploadedBy,
            Instant uploadedAt
    ) {
        return PlainteAnalyticsRowEntity.builder()
                .batchId(batchId)
                .uploadedBy(uploadedBy)
                .uploadedAt(uploadedAt)
                .caseType(CaseType.PLAINTE)
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
                .contactCl2(row.contactCl2())
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
                .notifResolution(row.notifResolution())
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
                .openDateActivation(row.openDateActivation())
                .openDateActivationDate(row.openDateActivationDate())
                .assignmentBusinessDays(row.assignmentBusinessDays())
                .rdvBusinessDays(row.rdvBusinessDays())
                .executionBusinessDays(row.executionBusinessDays())
                .endToEndBusinessDays(row.endToEndBusinessDays())
                .completed(PlainteAnalyticsSupport.isCompleted(
                        row.statut(),
                        row.repTravauxStt(),
                        row.dateFinTrvDate()
                ))
                .rdvSuccess(PlainteAnalyticsSupport.isRdvSuccess(
                        row.repRdv(),
                        row.datePriseRdvDate()
                ))
                .injoignable(PlainteAnalyticsSupport.isInjoignable(
                        row.causeInjoignabilite(),
                        row.repRdv(),
                        row.statut(),
                        row.detail()
                ))
                .abandon(PlainteAnalyticsSupport.isAbandon(
                        row.causeAbandon(),
                        row.repRdv(),
                        row.statut(),
                        row.detail()
                ))
                .blocked(PlainteAnalyticsSupport.isBlocked(
                        row.statut(),
                        row.detail(),
                        row.commentaire(),
                        row.cmtZm(),
                        row.cmtTrvStt()
                ))
                .delayed(PlainteAnalyticsSupport.isDelayed(
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
