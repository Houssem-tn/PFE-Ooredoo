package com.pfe.stock_management.controller;

import com.pfe.MainPackage.Entity.AccountStatus;
import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Entity.UserRole;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.MainPackage.Service.UserService;
import com.pfe.stock_management.entity.DpmRequest;
import com.pfe.stock_management.entity.SttStock;
import com.pfe.stock_management.entity.TechStock;
import com.pfe.stock_management.entity.TechStockUsage;
import com.pfe.stock_management.service.DpmService;
import com.pfe.stock_management.service.SttStockService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies that ZM stock scoping is based on governorate territory
 * (same logic as UserService.findMyStts) rather than DPM authorship. Specifically:
 *
 * 1. ZM-A distributes to STT-1 (territory of ZM-B) → ZM-A cannot see STT-1's stock/usages.
 * 2. ZM-B always sees STT-1's stock/usages because STT-1 is in ZM-B's territory.
 * 3. The responsible STT list is resolved via UserService.findResponsibleSttIds, not DPMs.
 */
@ExtendWith(MockitoExtension.class)
class ZmStockScopingTest {

    @Mock private DpmService dpmService;
    @Mock private SttStockService sttStockService;
    @Mock private UserRepository userRepository;
    @Mock private UserService userService;

    private ZmStockController controller;

    // ── Actors ──────────────────────────────────────────────────────────────────
    private static final String ZM_A_ID    = "zm-a";
    private static final String ZM_A_EMAIL = "zm-a@test.com";
    private static final String ZM_B_ID    = "zm-b";
    private static final String ZM_B_EMAIL = "zm-b@test.com";
    private static final String STT_1_ID   = "stt-1";  // in ZM-B's territory
    private static final String STT_2_ID   = "stt-2";  // in ZM-B's territory
    private static final String TECH_1_ID  = "tech-1"; // under STT-1

    @BeforeEach
    void setUp() {
        controller = new ZmStockController(dpmService, sttStockService, userRepository, userService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private void authenticateAs(String email) {
        var auth = new UsernamePasswordAuthenticationToken(email, null, List.of());
        SecurityContextHolder.setContext(new SecurityContextImpl(auth));
    }

    private AppUser zmUser(String id, String email) {
        AppUser u = new AppUser();
        u.setId(id);
        u.setEmail(email);
        u.setRole(UserRole.ROLE_ZM);
        u.setStatus(AccountStatus.APPROVED);
        u.setEnabled(true);
        return u;
    }

    private SttStock sttStock(String sttId) {
        return SttStock.builder().sttId(sttId).items(List.of()).build();
    }

    private TechStock techStock(String techId, String sttId) {
        return TechStock.builder().techId(techId).sttId(sttId).items(List.of()).build();
    }

    private TechStockUsage techUsage(String techId) {
        return TechStockUsage.builder().techId(techId).items(List.of()).build();
    }

    // ── Tests ────────────────────────────────────────────────────────────────────

    /**
     * ZM-B's territory covers STT-1 and STT-2. /my-stts-stocks must return
     * stock for both, resolved via governorate territory — not DPM authorship.
     */
    @Test
    void zmB_getsMySttStocks_forTerritorySttsOnly() {
        AppUser zmB = zmUser(ZM_B_ID, ZM_B_EMAIL);
        authenticateAs(ZM_B_EMAIL);
        when(userRepository.findByEmail(ZM_B_EMAIL)).thenReturn(Optional.of(zmB));
        when(userService.findResponsibleSttIds(zmB)).thenReturn(List.of(STT_1_ID, STT_2_ID));
        when(sttStockService.getStockForStt(STT_1_ID)).thenReturn(sttStock(STT_1_ID));
        when(sttStockService.getStockForStt(STT_2_ID)).thenReturn(sttStock(STT_2_ID));

        var response = controller.getMySttStocks();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<SttStock> stocks = response.getBody();
        assertNotNull(stocks);
        assertEquals(2, stocks.size());
        assertTrue(stocks.stream().anyMatch(s -> STT_1_ID.equals(s.getSttId())));
        assertTrue(stocks.stream().anyMatch(s -> STT_2_ID.equals(s.getSttId())));

        // Must not use DPM authorship to derive the STT list
        verify(dpmService, never()).getDpmsForZm(any());
    }

    /**
     * ZM-A's territory does not include STT-1 (ZM-B's territory).
     * Even though ZM-A created a DPM for STT-1, ZM-A must see nothing.
     */
    @Test
    void zmA_cannotTrackSttInZmBTerritory_evenAfterDistributingThere() {
        AppUser zmA = zmUser(ZM_A_ID, ZM_A_EMAIL);
        authenticateAs(ZM_A_EMAIL);
        when(userRepository.findByEmail(ZM_A_EMAIL)).thenReturn(Optional.of(zmA));
        when(userService.findResponsibleSttIds(zmA)).thenReturn(List.of());

        var response = controller.getMySttStocks();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isEmpty());

        verify(dpmService, never()).getDpmsForZm(any());
        verify(sttStockService, never()).getStockForStt(any());
    }

    /**
     * ZM-B calls /my-techs-usages → gets usages scoped to techs under territory STTs.
     */
    @Test
    void zmB_getsTechUsages_forTechsUnderTerritoryStts() {
        AppUser zmB = zmUser(ZM_B_ID, ZM_B_EMAIL);
        authenticateAs(ZM_B_EMAIL);
        when(userRepository.findByEmail(ZM_B_EMAIL)).thenReturn(Optional.of(zmB));
        when(userService.findResponsibleSttIds(zmB)).thenReturn(List.of(STT_1_ID));
        when(sttStockService.getTechUsagesForStts(List.of(STT_1_ID)))
                .thenReturn(List.of(techUsage(TECH_1_ID)));

        var response = controller.getMyTechsUsages();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<TechStockUsage> usages = response.getBody();
        assertNotNull(usages);
        assertEquals(1, usages.size());
        assertEquals(TECH_1_ID, usages.get(0).getTechId());

        verify(dpmService, never()).getDpmsForZm(any());
    }

    /**
     * ZM-A has no territory overlap with STT-1 → empty usage list.
     */
    @Test
    void zmA_cannotSeeTechUsages_forSttsOutsideTerritory() {
        AppUser zmA = zmUser(ZM_A_ID, ZM_A_EMAIL);
        authenticateAs(ZM_A_EMAIL);
        when(userRepository.findByEmail(ZM_A_EMAIL)).thenReturn(Optional.of(zmA));
        when(userService.findResponsibleSttIds(zmA)).thenReturn(List.of());
        when(sttStockService.getTechUsagesForStts(List.of())).thenReturn(List.of());

        var response = controller.getMyTechsUsages();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isEmpty());
    }

    /**
     * ZM-B calls /my-techs-stocks → gets tech stocks for techs under territory STTs.
     */
    @Test
    void zmB_getsTechStocks_forTechsUnderTerritoryStts() {
        AppUser zmB = zmUser(ZM_B_ID, ZM_B_EMAIL);
        authenticateAs(ZM_B_EMAIL);
        when(userRepository.findByEmail(ZM_B_EMAIL)).thenReturn(Optional.of(zmB));
        when(userService.findResponsibleSttIds(zmB)).thenReturn(List.of(STT_1_ID));
        when(sttStockService.getTechStocksForStt(STT_1_ID))
                .thenReturn(List.of(techStock(TECH_1_ID, STT_1_ID)));

        var response = controller.getMyTechsStocks();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<TechStock> stocks = response.getBody();
        assertNotNull(stocks);
        assertEquals(1, stocks.size());
        assertEquals(TECH_1_ID, stocks.get(0).getTechId());
    }

    /**
     * ZM-B calls /my-stts-dpms → gets ALL DPMs for territory STTs, including
     * those created by ZM-A. This is how ZM-B sees the distributor ZM label.
     */
    @Test
    void zmB_getsMySttsDpms_includesDpmsFromOtherZms() {
        AppUser zmB = zmUser(ZM_B_ID, ZM_B_EMAIL);
        authenticateAs(ZM_B_EMAIL);
        when(userRepository.findByEmail(ZM_B_EMAIL)).thenReturn(Optional.of(zmB));
        when(userService.findResponsibleSttIds(zmB)).thenReturn(List.of(STT_1_ID));

        DpmRequest dpmByZmA = DpmRequest.builder().id("dpm-1").zmId(ZM_A_ID).zmName("Zone Manager A").sttId(STT_1_ID).build();
        DpmRequest dpmByZmB = DpmRequest.builder().id("dpm-2").zmId(ZM_B_ID).zmName("Zone Manager B").sttId(STT_1_ID).build();
        when(dpmService.getAllDpmsForStt(STT_1_ID)).thenReturn(List.of(dpmByZmA, dpmByZmB));

        var response = controller.getMySttsDpms();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<DpmRequest> dpms = response.getBody();
        assertNotNull(dpms);
        assertEquals(2, dpms.size());
        assertTrue(dpms.stream().anyMatch(d -> ZM_A_ID.equals(d.getZmId())));
        assertTrue(dpms.stream().anyMatch(d -> ZM_B_ID.equals(d.getZmId())));
    }

    /**
     * /my-dpms stays scoped to DPMs *created by* this ZM (authorship scope, unchanged).
     */
    @Test
    void getMyDpms_remainsScopedToCreatingZm() {
        AppUser zmA = zmUser(ZM_A_ID, ZM_A_EMAIL);
        authenticateAs(ZM_A_EMAIL);
        when(userRepository.findByEmail(ZM_A_EMAIL)).thenReturn(Optional.of(zmA));
        DpmRequest ownDpm = DpmRequest.builder().id("dpm-1").zmId(ZM_A_ID).sttId(STT_1_ID).build();
        when(dpmService.getDpmsForZm(ZM_A_ID)).thenReturn(List.of(ownDpm));

        var response = controller.getMyDpms();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals(ZM_A_ID, response.getBody().get(0).getZmId());
    }
}
