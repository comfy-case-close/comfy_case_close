package com.fnbx.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fnbx.platform.service.ConfigService;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConfigBusinessScopeTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final BranchAccessGuard access = mock(BranchAccessGuard.class);
    private final ConfigService service = new ConfigService(jdbc, new ObjectMapper(), access);
    private final UUID business = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();

    @AfterEach void clear() { TenantContext.clear(); }

    @Test void branchPermissionAloneCannotWriteBusinessDefaults() {
        TenantContext.set(TenantContext.of(business, actor));
        doThrow(new AccessDeniedException("Business permission denied"))
                .when(access).requireBusiness(Permission.BUSINESS_UPDATE);

        assertThatThrownBy(() -> service.updateBusiness(Map.of(
                "REQUIRE_APPROVAL_REFUND", new ObjectMapper().getNodeFactory().booleanNode(true))))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(jdbc);
    }

    @Test void businessUpdateUsesTenantScopeAndPreservesBranchOverrides() {
        TenantContext.set(TenantContext.of(business, actor));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0, 1);

        service.updateBusiness(Map.of("REQUIRE_APPROVAL_REFUND",
                new ObjectMapper().getNodeFactory().booleanNode(true)));

        verify(access, atLeastOnce()).requireBusiness(Permission.BUSINESS_UPDATE);
        verify(jdbc).update(argThat(sql -> sql.contains("UPDATE platform.app_config")
                && sql.contains("scope='BUSINESS'")), eq("true"), eq(actor), eq(business),
                eq("REQUIRE_APPROVAL_REFUND"));
        verify(jdbc).update(argThat(sql -> sql.contains("INSERT INTO platform.app_config")
                && sql.contains("VALUES ('BUSINESS'")), eq(business), eq("REQUIRE_APPROVAL_REFUND"),
                eq("true"), eq(actor));
        verify(jdbc).query(argThat(sql -> sql.contains("c.scope='BUSINESS'")
                && !sql.contains("c.scope='BRANCH'")), any(org.springframework.jdbc.core.RowCallbackHandler.class),
                eq(business));
    }
}
