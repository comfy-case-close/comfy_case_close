package com.fnbx.platform;

import com.fnbx.platform.controller.ConfigController;
import com.fnbx.platform.service.ConfigService;
import com.fnbx.platform.service.MovementKindService;
import com.fnbx.shared.config.WebConfig;
import com.fnbx.shared.security.ServletSecurityConfiguration;
import com.fnbx.shared.tenant.TenantContext;
import com.fnbx.shared.utils.PagedResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(properties = "fnb.security.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@ContextConfiguration(classes = {ConfigController.class, WebConfig.class, ServletSecurityConfiguration.class})
class MovementKindControllerTest {
    private static final String URL = "/api/v1/config/movement-kinds";
    @Autowired MockMvc mvc;
    @MockBean ConfigService config;
    @MockBean MovementKindService kinds;
    @MockBean JwtDecoder decoder;
    private final UUID business = UUID.randomUUID();
    private final UUID staff = UUID.randomUUID();

    @BeforeEach void token() {
        when(decoder.decode("test-token")).thenReturn(Jwt.withTokenValue("test-token")
                .header("alg", "HS256").subject(staff.toString())
                .claim("business_id", business.toString()).claim("uid", staff.toString()).build());
        when(kinds.list(any())).thenAnswer(call -> {
            assertThat(TenantContext.current().businessId()).isEqualTo(business);
            assertThat(TenantContext.current().userId()).isEqualTo(staff);
            var page = call.getArgument(0, org.springframework.data.domain.Pageable.class);
            return new PagedResponse<>(List.of(), page.getPageNumber(), page.getPageSize(), 0, 0, true);
        });
    }

    @Test void requiresAuthentication() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        verifyNoInteractions(kinds);
    }

    @Test void returnsPagedResponseWithoutBranchHeaderAndUsesVerifiedBusiness() throws Exception {
        mvc.perform(get(URL).header("Authorization", "Bearer test-token")
                        .header("X-Business-Id", UUID.randomUUID().toString())
                        .param("businessId", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.last").value(true));
        verify(kinds).list(PageRequest.of(0, 20));
        assertThat(TenantContext.currentOrNull()).isNull();
    }

    @Test void acceptsCustomPageAndSize() throws Exception {
        mvc.perform(get(URL).header("Authorization", "Bearer test-token").param("page", "2").param("size", "5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5));
        verify(kinds).list(PageRequest.of(2, 5));
    }

    @Test void rejectsInvalidPaginationBeforeReading() throws Exception {
        for (String[] params : List.of(new String[]{"-1", "20"}, new String[]{"0", "0"}, new String[]{"0", "101"})) {
            mvc.perform(get(URL).header("Authorization", "Bearer test-token").param("page", params[0]).param("size", params[1]))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(kinds);
    }
}
