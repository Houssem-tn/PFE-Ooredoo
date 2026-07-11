package com.pfe.stock_management.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the REAL security chain (SecurityConfig URL matchers +
 * @PreAuthorize method security + JwtAuthFilter) end-to-end via MockMvc,
 * rather than relying only on service-level ensure* checks. Covers the
 * `/api/v1/stock/equipment/**` rules: GET requires only authentication,
 * mutating verbs require ROLE_ADMIN.
 */
@SpringBootTest
class EquipmentControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void getEquipment_withoutAuthentication_shouldReturn401() throws Exception {
        mockMvc().perform(get("/api/v1/stock/equipment"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "tech@example.com", roles = "TECH")
    void getEquipment_withAnyAuthenticatedRole_shouldReturn200() throws Exception {
        mockMvc().perform(get("/api/v1/stock/equipment"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "stt@example.com", roles = "STT")
    void deactivateEquipment_withNonAdminRole_shouldReturn403() throws Exception {
        // @PreAuthorize runs before the method body / repository lookup, so a
        // non-existent id is fine here — the point is the role check itself.
        mockMvc().perform(delete("/api/v1/stock/equipment/does-not-matter"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void deactivateEquipment_withAdminRoleButUnknownId_shouldPassAuthorizationAndReach404() throws Exception {
        // Confirms ADMIN clears the @PreAuthorize gate (as opposed to also
        // being blocked) — the 404 comes from the service layer, not security.
        mockMvc().perform(delete("/api/v1/stock/equipment/does-not-exist"))
                .andExpect(status().isNotFound());
    }
}
