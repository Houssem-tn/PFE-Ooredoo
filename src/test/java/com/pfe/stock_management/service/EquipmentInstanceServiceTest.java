package com.pfe.stock_management.service;

import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.stock_management.dto.UnitTrackingInput;
import com.pfe.stock_management.entity.DpmRequest;
import com.pfe.stock_management.entity.Equipment;
import com.pfe.stock_management.entity.EquipmentInstance;
import com.pfe.stock_management.enums.InstanceOwnerType;
import com.pfe.stock_management.enums.InstanceStatus;
import com.pfe.stock_management.enums.TrackingMode;
import com.pfe.stock_management.material_return.MaterialReturnRequest;
import com.pfe.stock_management.model.InstanceMovementEvent;
import com.pfe.stock_management.repository.EquipmentInstanceRepository;
import com.pfe.stock_management.repository.EquipmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the per-unit IMEI / Serial tracking engine. Covers every validation /
 * lifecycle scenario from the feature spec (Requirement #8).
 */
@ExtendWith(MockitoExtension.class)
class EquipmentInstanceServiceTest {

    @Mock private EquipmentInstanceRepository instanceRepository;
    @Mock private EquipmentRepository equipmentRepository;
    @Mock private MongoTemplate mongoTemplate;

    private EquipmentInstanceService newService() {
        return new EquipmentInstanceService(instanceRepository, equipmentRepository, mongoTemplate);
    }

    // ── Required IMEI / Serial per tracking mode ─────────────────────────────

    @Test
    void serialOnly_rejectsUnitMissingSerial() {
        EquipmentInstanceService service = newService();
        Equipment idu = equipment("idu", "IDU", TrackingMode.SERIAL_ONLY);

        assertThrows(ResponseStatusException.class, () ->
                service.validateUnitsForConfirmation(idu, 1, List.of(unit(null, null))));
        verify(instanceRepository, never()).existsByEquipmentIdAndNormalizedSerialNumber(anyString(), anyString());
    }

    @Test
    void imeiOnly_rejectsUnitMissingImei() {
        EquipmentInstanceService service = newService();
        Equipment modem = equipment("modem", "Modem", TrackingMode.IMEI_ONLY);

        assertThrows(ResponseStatusException.class, () ->
                service.validateUnitsForConfirmation(modem, 1, List.of(unit(null, "SN-1"))));
    }

    @Test
    void serialAndImei_rejectsUnitMissingEither() {
        EquipmentInstanceService service = newService();
        Equipment odu = equipment("odu", "ODU P90", TrackingMode.SERIAL_AND_IMEI);

        // missing imei
        assertThrows(ResponseStatusException.class, () ->
                service.validateUnitsForConfirmation(odu, 1, List.of(unit(null, "SN-1"))));
        // missing serial
        assertThrows(ResponseStatusException.class, () ->
                service.validateUnitsForConfirmation(odu, 1, List.of(unit("IMEI-1", null))));
    }

    @Test
    void serialOrImei_requiresAtLeastOne() {
        EquipmentInstanceService service = newService();
        Equipment modem = equipment("modem", "Modem", TrackingMode.SERIAL_OR_IMEI);

        // neither → rejected
        assertThrows(ResponseStatusException.class, () ->
                service.validateUnitsForConfirmation(modem, 1, List.of(unit(null, null))));

        // one is enough → accepted
        when(instanceRepository.existsByEquipmentIdAndNormalizedImei(eq("modem"), anyString())).thenReturn(false);
        assertDoesNotThrow(() ->
                service.validateUnitsForConfirmation(modem, 1, List.of(unit("IMEI-9", null))));
    }

    // ── Row count must equal confirmed quantity ──────────────────────────────

    @Test
    void quantityThree_requiresExactlyThreeRows() {
        EquipmentInstanceService service = newService();
        Equipment odu = equipment("odu", "ODU P90", TrackingMode.SERIAL_ONLY);

        // too few
        assertThrows(ResponseStatusException.class, () ->
                service.validateUnitsForConfirmation(odu, 3, List.of(unit(null, "SN-1"), unit(null, "SN-2"))));
        // too many
        assertThrows(ResponseStatusException.class, () ->
                service.validateUnitsForConfirmation(odu, 3,
                        List.of(unit(null, "SN-1"), unit(null, "SN-2"), unit(null, "SN-3"), unit(null, "SN-4"))));
        // null list for a tracked item with qty 3
        assertThrows(ResponseStatusException.class, () ->
                service.validateUnitsForConfirmation(odu, 3, null));

        // exactly three, all unique, none in DB → ok
        when(instanceRepository.existsByEquipmentIdAndNormalizedSerialNumber(eq("odu"), anyString())).thenReturn(false);
        assertDoesNotThrow(() ->
                service.validateUnitsForConfirmation(odu, 3,
                        List.of(unit(null, "SN-1"), unit(null, "SN-2"), unit(null, "SN-3"))));
    }

    // ── Duplicate IMEI / Serial blocking ─────────────────────────────────────

    @Test
    void duplicateImei_blockedAgainstExistingStock() {
        EquipmentInstanceService service = newService();
        Equipment modem = equipment("modem", "Modem", TrackingMode.IMEI_ONLY);
        when(instanceRepository.existsByEquipmentIdAndNormalizedImei("modem", "IMEI-DUP")).thenReturn(true);

        assertThrows(ResponseStatusException.class, () ->
                service.validateUnitsForConfirmation(modem, 1, List.of(unit("imei-dup", null))));
    }

    @Test
    void duplicateImei_blockedWithinSameSubmission() {
        EquipmentInstanceService service = newService();
        Equipment modem = equipment("modem", "Modem", TrackingMode.IMEI_ONLY);
        when(instanceRepository.existsByEquipmentIdAndNormalizedImei(eq("modem"), anyString())).thenReturn(false);

        assertThrows(ResponseStatusException.class, () ->
                service.validateUnitsForConfirmation(modem, 2,
                        List.of(unit("IMEI-1", null), unit("imei-1", null)))); // same after normalisation
    }

    @Test
    void duplicateSerial_blockedAgainstExistingStock() {
        EquipmentInstanceService service = newService();
        Equipment idu = equipment("idu", "IDU", TrackingMode.SERIAL_ONLY);
        when(instanceRepository.existsByEquipmentIdAndNormalizedSerialNumber("idu", "SN-DUP")).thenReturn(true);

        assertThrows(ResponseStatusException.class, () ->
                service.validateUnitsForConfirmation(idu, 1, List.of(unit(null, "sn-dup"))));
    }

    @Test
    void duplicateSerial_blockedWithinSameSubmission() {
        EquipmentInstanceService service = newService();
        Equipment idu = equipment("idu", "IDU", TrackingMode.SERIAL_ONLY);
        when(instanceRepository.existsByEquipmentIdAndNormalizedSerialNumber(eq("idu"), anyString())).thenReturn(false);

        assertThrows(ResponseStatusException.class, () ->
                service.validateUnitsForConfirmation(idu, 2,
                        List.of(unit(null, "SN-1"), unit(null, " sn-1 ")))); // same after trim+upper
    }

    @Test
    void sameSerial_allowedAcrossDifferentEquipment() {
        EquipmentInstanceService service = newService();
        Equipment idu = equipment("idu", "IDU", TrackingMode.SERIAL_ONLY);
        // Uniqueness is scoped per equipmentId: serial "SN-9" does not exist for THIS equipment.
        when(instanceRepository.existsByEquipmentIdAndNormalizedSerialNumber("idu", "SN-9")).thenReturn(false);

        assertDoesNotThrow(() ->
                service.validateUnitsForConfirmation(idu, 1, List.of(unit(null, "SN-9"))));
    }

    // ── Non-tracked equipment stays quantity-based ───────────────────────────

    @Test
    void noneTracking_ignoresUnitsAndNeverHitsRepository() {
        EquipmentInstanceService service = newService();
        Equipment cable = equipment("cable", "Cable", TrackingMode.NONE);

        assertDoesNotThrow(() -> service.validateUnitsForConfirmation(cable, 5, null));
        assertDoesNotThrow(() -> service.validateUnitsForConfirmation(cable, 5, List.of()));

        verify(instanceRepository, never()).existsByEquipmentIdAndNormalizedImei(anyString(), anyString());
        verify(instanceRepository, never()).existsByEquipmentIdAndNormalizedSerialNumber(anyString(), anyString());
        // No instances should ever be created for a NONE item.
        DpmRequest dpm = DpmRequest.builder().id("dpm-1").dpmNumber("DPM-1").sttId("stt-1").sttName("STT").build();
        assertEquals(List.of(), service.createForDpm(cable, dpm, List.of(), "actor"));
        verify(instanceRepository, never()).save(any());
    }

    // ── Creation from DPM ─────────────────────────────────────────────────────

    @Test
    void createForDpm_materialisesInstancesInSttStock() {
        EquipmentInstanceService service = newService();
        Equipment odu = equipment("odu", "ODU P90", TrackingMode.SERIAL_AND_IMEI);
        DpmRequest dpm = DpmRequest.builder().id("dpm-1").dpmNumber("DPM-2026-1").sttId("stt-1").sttName("STT One").build();
        when(instanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<EquipmentInstance> created = service.createForDpm(odu, dpm,
                List.of(unit("IMEI-1", "SN-1"), unit("IMEI-2", "SN-2")), "stt@x.com");

        assertEquals(2, created.size());
        EquipmentInstance first = created.get(0);
        assertEquals(InstanceStatus.IN_STT_STOCK, first.getStatus());
        assertEquals(InstanceOwnerType.STT, first.getCurrentOwnerType());
        assertEquals("stt-1", first.getCurrentOwnerId());
        assertEquals("stt-1", first.getOriginSttId());
        assertEquals("dpm-1", first.getSourceDpmId());
        assertEquals("IMEI-1", first.getImei());
        assertEquals("SN-1", first.getNormalizedSerialNumber());
        assertEquals(1, first.getMovements().size());
        assertEquals("RECEIVED_FROM_DPM", first.getMovements().get(0).getType());
    }

    // ── STT → TECH distribution ───────────────────────────────────────────────

    @Test
    void moveStockToTech_transfersOwnershipAndStatus() {
        EquipmentInstanceService service = newService();
        EquipmentInstance inst = sttStockInstance("inst-1", "odu", "stt-1");
        when(instanceRepository.findById("inst-1")).thenReturn(Optional.of(inst));
        when(instanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AppUser stt = user("stt-1", "STT One");
        AppUser tech = user("tech-1", "Tech One");

        List<EquipmentInstance> moved = service.moveStockToTech("odu", List.of("inst-1"), stt, tech, "dist-1", "stt@x.com");

        assertEquals(1, moved.size());
        assertEquals(InstanceStatus.IN_TECH_STOCK, inst.getStatus());
        assertEquals(InstanceOwnerType.TECH, inst.getCurrentOwnerType());
        assertEquals("tech-1", inst.getCurrentOwnerId());
        assertEquals("DISTRIBUTED_TO_TECH", lastMovement(inst).getType());
    }

    @Test
    void moveStockToTech_rejectsInstanceNotOwnedByStt() {
        EquipmentInstanceService service = newService();
        EquipmentInstance inst = sttStockInstance("inst-1", "odu", "other-stt");
        when(instanceRepository.findById("inst-1")).thenReturn(Optional.of(inst));

        AppUser stt = user("stt-1", "STT One");
        AppUser tech = user("tech-1", "Tech One");

        assertThrows(ResponseStatusException.class, () ->
                service.moveStockToTech("odu", List.of("inst-1"), stt, tech, "dist-1", "stt@x.com"));
    }

    // ── Case usage + restoration ──────────────────────────────────────────────

    @Test
    void syncCaseUsageInstances_marksSelectedUnitsUsedOnCase() {
        EquipmentInstanceService service = newService();
        EquipmentInstance inst = techStockInstance("inst-1", "odu", "tech-1");
        when(equipmentRepository.findAllById(any())).thenReturn(List.of(equipment("odu", "ODU P90", TrackingMode.SERIAL_AND_IMEI)));
        when(instanceRepository.findById("inst-1")).thenReturn(Optional.of(inst));
        when(instanceRepository.findByCaseId("case-1")).thenReturn(new ArrayList<>());
        when(instanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CaseEntity caseEntity = CaseEntity.builder().id("case-1").crmCase("CRM-1").build();
        service.syncCaseUsageInstances(caseEntity, InstanceOwnerType.TECH, "tech-1", "Tech One",
                Map.of("odu", 1), Map.of("odu", List.of("inst-1")), "tech@x.com");

        assertEquals(InstanceStatus.USED_ON_CASE, inst.getStatus());
        assertEquals("case-1", inst.getCaseId());
        assertEquals(InstanceOwnerType.CLIENT, inst.getCurrentOwnerType());
    }

    @Test
    void syncCaseUsageInstances_rejectsTrackedItemWithWrongUnitCount() {
        EquipmentInstanceService service = newService();
        when(equipmentRepository.findAllById(any())).thenReturn(List.of(equipment("odu", "ODU P90", TrackingMode.SERIAL_ONLY)));

        CaseEntity caseEntity = CaseEntity.builder().id("case-1").crmCase("CRM-1").build();
        // quantity 2 but only one unit selected
        assertThrows(ResponseStatusException.class, () ->
                service.syncCaseUsageInstances(caseEntity, InstanceOwnerType.TECH, "tech-1", "Tech One",
                        Map.of("odu", 2), Map.of("odu", List.of("inst-1")), "tech@x.com"));
    }

    @Test
    void restoreAllCaseUsage_revertsUsedUnitsToStock() {
        EquipmentInstanceService service = newService();
        EquipmentInstance inst = techStockInstance("inst-1", "odu", "tech-1");
        // simulate it being used on a case
        inst.setStatus(InstanceStatus.USED_ON_CASE);
        inst.setCaseId("case-1");
        inst.setCurrentOwnerType(InstanceOwnerType.CLIENT);
        inst.setCurrentOwnerId("case-1");
        inst.getMovements().add(InstanceMovementEvent.builder()
                .type("USED_ON_CASE")
                .fromOwnerType(InstanceOwnerType.TECH)
                .fromOwnerId("tech-1")
                .fromOwnerName("Tech One")
                .build());
        when(instanceRepository.findByCaseId("case-1")).thenReturn(List.of(inst));
        when(instanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.restoreAllCaseUsage("case-1", "zm@x.com");

        assertEquals(InstanceStatus.IN_TECH_STOCK, inst.getStatus());
        assertEquals(InstanceOwnerType.TECH, inst.getCurrentOwnerType());
        assertEquals("tech-1", inst.getCurrentOwnerId());
        assertNull(inst.getCaseId());
        assertEquals("USAGE_RESTORED", lastMovement(inst).getType());
    }

    // ── RES recovery ────────────────────────────────────────────────────────

    @Test
    void recordClientRecovery_createsRecoveredInstanceLinkedToReturn() {
        EquipmentInstanceService service = newService();
        Equipment odu = equipment("odu", "ODU P90", TrackingMode.SERIAL_AND_IMEI);
        when(instanceRepository.findByEquipmentId("odu")).thenReturn(new ArrayList<>());
        ArgumentCaptor<EquipmentInstance> captor = ArgumentCaptor.forClass(EquipmentInstance.class);
        when(instanceRepository.save(captor.capture())).thenAnswer(i -> {
            EquipmentInstance e = i.getArgument(0);
            e.setId("inst-new");
            return e;
        });

        CaseEntity caseEntity = CaseEntity.builder().id("case-res").crmCase("CRM-RES").build();
        MaterialReturnRequest ret = MaterialReturnRequest.builder()
                .id("ret-1").returnCode("RET-2026-1").sttId("stt-1").build();

        String instanceId = service.recordClientRecovery(odu, caseEntity, "IMEI-R", "SN-R", ret, "tech@x.com");

        assertEquals("inst-new", instanceId);
        EquipmentInstance saved = captor.getValue();
        assertEquals(InstanceStatus.RECOVERED_FROM_CLIENT, saved.getStatus());
        assertEquals(InstanceOwnerType.RETURN, saved.getCurrentOwnerType());
        assertEquals("ret-1", saved.getReturnId());
        assertEquals("case-res", saved.getCaseId());
        assertEquals("stt-1", saved.getOriginSttId());
    }

    @Test
    void recordClientRecovery_noOpForNonTrackedEquipment() {
        EquipmentInstanceService service = newService();
        Equipment cable = equipment("cable", "Cable", TrackingMode.NONE);

        String instanceId = service.recordClientRecovery(cable, null, "X", "Y", null, "tech@x.com");

        assertNull(instanceId);
        verify(instanceRepository, never()).save(any());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static Equipment equipment(String id, String name, TrackingMode mode) {
        return Equipment.builder().id(id).name(name).articleCode("AC-" + id).trackingMode(mode).build();
    }

    private static UnitTrackingInput unit(String imei, String serial) {
        return UnitTrackingInput.builder().imei(imei).serialNumber(serial).build();
    }

    private static EquipmentInstance sttStockInstance(String id, String equipmentId, String sttId) {
        return EquipmentInstance.builder()
                .id(id).equipmentId(equipmentId).equipmentName("ODU P90")
                .trackingMode(TrackingMode.SERIAL_AND_IMEI)
                .currentOwnerType(InstanceOwnerType.STT).currentOwnerId(sttId)
                .status(InstanceStatus.IN_STT_STOCK)
                .movements(new ArrayList<>())
                .build();
    }

    private static EquipmentInstance techStockInstance(String id, String equipmentId, String techId) {
        return EquipmentInstance.builder()
                .id(id).equipmentId(equipmentId).equipmentName("ODU P90")
                .trackingMode(TrackingMode.SERIAL_AND_IMEI)
                .currentOwnerType(InstanceOwnerType.TECH).currentOwnerId(techId)
                .status(InstanceStatus.IN_TECH_STOCK)
                .movements(new ArrayList<>())
                .build();
    }

    private static InstanceMovementEvent lastMovement(EquipmentInstance inst) {
        return inst.getMovements().get(inst.getMovements().size() - 1);
    }

    private static AppUser user(String id, String fullName) {
        return AppUser.builder().id(id).fullName(fullName).email(id + "@x.com").build();
    }
}
