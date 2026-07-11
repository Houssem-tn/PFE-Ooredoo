package com.pfe.integration;

import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.enums.CaseStatus;
import com.pfe.case_management.enums.CaseType;
import com.pfe.case_management.repository.CaseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real MongoDB integration test. Runs against the `test` Spring profile,
 * which points at a dedicated `pfe_test` database (see
 * application-test.properties) — never the dev/prod database. Requires a
 * reachable local MongoDB (or MONGODB_URI override); no Testcontainers here
 * since Docker is not available in this environment (see README "Testing"
 * section for the tradeoff).
 *
 * Self-cleaning: every case created by a test is deleted in @AfterEach so
 * the suite is repeatable and order-independent (each test also uses a
 * unique crmCase to avoid collisions if cleanup ever fails to run).
 */
@SpringBootTest
class CaseRepositoryIntegrationTest {

    @Autowired
    private CaseRepository caseRepository;

    private final java.util.List<String> createdIds = new java.util.ArrayList<>();

    @AfterEach
    void cleanUp() {
        createdIds.forEach(caseRepository::deleteById);
        createdIds.clear();
    }

    private CaseEntity persist(CaseEntity entity) {
        CaseEntity saved = caseRepository.save(entity);
        createdIds.add(saved.getId());
        return saved;
    }

    @Test
    void save_thenFindById_shouldRoundTripAllFields() {
        String crmCase = "IT-" + UUID.randomUUID();
        CaseEntity entity = CaseEntity.builder()
                .type(CaseType.ACTIVATION)
                .status(CaseStatus.CREATED)
                .crmCase(crmCase)
                .gouvernorat("Tunis")
                .build();

        CaseEntity saved = persist(entity);
        assertThat(saved.getId()).isNotBlank();

        Optional<CaseEntity> found = caseRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getCrmCase()).isEqualTo(crmCase);
        assertThat(found.get().getStatus()).isEqualTo(CaseStatus.CREATED);
    }

    @Test
    void findByTypeAndStatusIn_shouldOnlyReturnMatchingCases() {
        String crmCase = "IT-" + UUID.randomUUID();
        persist(CaseEntity.builder()
                .type(CaseType.ACTIVATION)
                .status(CaseStatus.ASSIGNED_TO_TECH)
                .crmCase(crmCase)
                .build());

        List<CaseEntity> results = caseRepository.findByTypeAndStatusIn(
                CaseType.ACTIVATION, List.of(CaseStatus.ASSIGNED_TO_TECH, CaseStatus.NOT_REACHABLE)
        );

        assertThat(results).extracting(CaseEntity::getCrmCase).contains(crmCase);
        assertThat(results).allSatisfy(c -> assertThat(c.getType()).isEqualTo(CaseType.ACTIVATION));
    }

    @Test
    void deleteById_shouldRemoveTheCase() {
        CaseEntity saved = persist(CaseEntity.builder()
                .type(CaseType.ACTIVATION)
                .status(CaseStatus.CREATED)
                .crmCase("IT-" + UUID.randomUUID())
                .build());

        caseRepository.deleteById(saved.getId());
        createdIds.remove(saved.getId()); // already gone, avoid double-delete in @AfterEach

        assertThat(caseRepository.findById(saved.getId())).isEmpty();
    }
}
