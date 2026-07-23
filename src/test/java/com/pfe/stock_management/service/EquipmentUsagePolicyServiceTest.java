package com.pfe.stock_management.service;

import com.pfe.MainPackage.Entity.CaseModuleType;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.enums.CaseType;
import com.pfe.stock_management.entity.Equipment;
import com.pfe.stock_management.repository.EquipmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

/**
 * {@link EquipmentUsagePolicyService} is the single source of truth for what a field workflow may
 * consume/recover per case context (ACT/PLT/RES). These tests focus on {@code assertRecoverable}'s
 * widened PLT gate (this feature's change) without touching {@code assertConsumable}'s existing,
 * already-covered-elsewhere RES-blocks-consumption behaviour.
 */
@ExtendWith(MockitoExtension.class)
class EquipmentUsagePolicyServiceTest {

    @Mock
    private EquipmentRepository equipmentRepository;

    private EquipmentUsagePolicyService newService() {
        return new EquipmentUsagePolicyService(equipmentRepository);
    }

    // ── resolveContext ─────────────────────────────────────────────────────────

    @Test
    void resolveContext_activationIsAct() {
        CaseEntity c = CaseEntity.builder().type(CaseType.ACTIVATION).build();
        assertThat(newService().resolveContext(c)).isEqualTo(EquipmentUsagePolicyService.UsageContext.ACT);
    }

    @Test
    void resolveContext_plainteWithoutResModuleIsPlt() {
        CaseEntity c = CaseEntity.builder().type(CaseType.PLAINTE).build();
        assertThat(newService().resolveContext(c)).isEqualTo(EquipmentUsagePolicyService.UsageContext.PLT);
    }

    @Test
    void resolveContext_plainteWithResModuleIsRes() {
        CaseEntity c = CaseEntity.builder().type(CaseType.PLAINTE).caseModule(CaseModuleType.RES).build();
        assertThat(newService().resolveContext(c)).isEqualTo(EquipmentUsagePolicyService.UsageContext.RES);
    }

    @Test
    void resolveContext_resiliationTypeIsRes() {
        CaseEntity c = CaseEntity.builder().type(CaseType.RESILIATION).build();
        assertThat(newService().resolveContext(c)).isEqualTo(EquipmentUsagePolicyService.UsageContext.RES);
    }

    // ── assertRecoverable ──────────────────────────────────────────────────────

    @Test
    void assertRecoverable_rejectsActivationContext() {
        CaseEntity c = CaseEntity.builder().type(CaseType.ACTIVATION).build();

        assertThatThrownBy(() -> newService().assertRecoverable(c, List.of("eq-1")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("complaint (PLT) or résiliation (RES)");
    }

    @Test
    void assertRecoverable_acceptsPltContextForRecoverableEquipment() {
        CaseEntity c = CaseEntity.builder().type(CaseType.PLAINTE).build();
        Equipment modem = Equipment.builder().id("eq-1").name("Modem").recoverableFromClient(true).build();
        when(equipmentRepository.findAllById(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(modem));

        assertThatCode(() -> newService().assertRecoverable(c, List.of("eq-1"))).doesNotThrowAnyException();
    }

    @Test
    void assertRecoverable_acceptsResContextForRecoverableEquipment() {
        CaseEntity c = CaseEntity.builder().type(CaseType.PLAINTE).caseModule(CaseModuleType.RES).build();
        Equipment modem = Equipment.builder().id("eq-1").name("Modem").recoverableFromClient(true).build();
        when(equipmentRepository.findAllById(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(modem));

        assertThatCode(() -> newService().assertRecoverable(c, List.of("eq-1"))).doesNotThrowAnyException();
    }

    @Test
    void assertRecoverable_rejectsEquipmentNotFlaggedRecoverable() {
        CaseEntity c = CaseEntity.builder().type(CaseType.PLAINTE).build();
        Equipment cable = Equipment.builder().id("eq-1").name("Cable").recoverableFromClient(false).build();
        when(equipmentRepository.findAllById(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(cable));

        assertThatThrownBy(() -> newService().assertRecoverable(c, List.of("eq-1")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cannot be recovered from the client");
    }

    @Test
    void assertRecoverable_rejectsEmptyItemList() {
        CaseEntity c = CaseEntity.builder().type(CaseType.PLAINTE).build();

        assertThatThrownBy(() -> newService().assertRecoverable(c, List.of()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Declare at least one recovered item");
    }
}
