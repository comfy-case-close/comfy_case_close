package com.fnbx.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fnbx.platform.service.ConfigService;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.security.access.AccessDeniedException;

import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConfigEntriesTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final BranchAccessGuard access = mock(BranchAccessGuard.class);
    private final ConfigService service = new ConfigService(jdbc, new ObjectMapper(), access);
    private final UUID business = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();
    private final UUID branch = UUID.randomUUID();
    private final OffsetDateTime time = OffsetDateTime.parse("2026-10-11T14:00:00Z");

    @AfterEach void clear() { TenantContext.clear(); }

    @Test void branchMetadataUsesWinningRowAndIsTenantScoped() throws Exception {
        TenantContext.set(TenantContext.of(business, actor));
        when(access.effective(branch)).thenReturn(Set.of(Permission.CLOSE_READ));
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(branch), eq(business))).thenReturn(true);
        doAnswer(call -> {
            RowCallbackHandler callback = call.getArgument(1);
            callback.processRow(row("DIFF_ALERT_ABS", "150000", "BRANCH", "Branch alert", actor));
            callback.processRow(row("DIFF_ALERT_ABS", "100000", "BUSINESS", "Business alert", null));
            callback.processRow(row("REQUIRE_APPROVAL_OTHER", "true", "GLOBAL", null, null));
            callback.processRow(row("UNSUPPORTED", "1", "GLOBAL", null, null));
            return null;
        }).when(jdbc).query(anyString(), any(RowCallbackHandler.class), eq(branch), eq(business));
        var rows = service.entries(branch);
        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst().configKey()).isEqualTo("DIFF_ALERT_ABS");
        assertThat(rows.getFirst().configValue()).isEqualTo(150000);
        assertThat(rows.getFirst().scope()).isEqualTo("BRANCH");
        assertThat(rows.getFirst().description()).isEqualTo("Branch alert");
        assertThat(rows.getFirst().updatedBy()).isEqualTo(actor);
        assertThat(rows.getFirst().updatedAt()).isEqualTo(time);
        assertThat(rows.get(1).configValue()).isEqualTo(true);
        assertThat(rows.get(1).updatedBy()).isNull();
        verify(jdbc).query(argThat(sql -> sql.contains("c.business_id=b.business_id") && sql.contains("ORDER BY c.config_key") && sql.contains("WHEN 'BRANCH' THEN 1")), any(RowCallbackHandler.class), eq(branch), eq(business));
    }

    @Test void businessMetadataRequiresBusinessUpdateBeforeReading() {
        TenantContext.set(TenantContext.of(business, actor));
        doThrow(new AccessDeniedException("Business permission denied")).when(access).requireBusiness(Permission.BUSINESS_UPDATE);
        assertThatThrownBy(service::businessEntries).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(jdbc);
    }

    @Test void businessMetadataExcludesBranchOverrides() throws Exception {
        TenantContext.set(TenantContext.of(business, actor));
        doAnswer(call -> {
            RowCallbackHandler callback = call.getArgument(1);
            callback.processRow(row("DIFF_ALERT_ABS", "100000", "BUSINESS", null, actor));
            callback.processRow(row("DIFF_ALERT_ABS", "50000", "GLOBAL", "Global alert", null));
            return null;
        }).when(jdbc).query(anyString(), any(RowCallbackHandler.class), eq(business));
        assertThat(service.businessEntries()).singleElement().satisfies(row -> {
            assertThat(row.scope()).isEqualTo("BUSINESS");
            assertThat(row.configValue()).isEqualTo(100000);
            assertThat(row.description()).isNull();
        });
        verify(access).requireBusiness(Permission.BUSINESS_UPDATE);
        verify(jdbc).query(argThat(sql -> !sql.contains("c.scope='BRANCH'") && sql.contains("c.business_id=?")), any(RowCallbackHandler.class), eq(business));
    }

    private ResultSet row(String key, String value, String scope, String description, UUID by) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getString(1)).thenReturn(key);
        when(row.getString(2)).thenReturn(value);
        when(row.getString(3)).thenReturn(scope);
        when(row.getString(4)).thenReturn(description);
        when(row.getObject(5, UUID.class)).thenReturn(by);
        when(row.getObject(6, OffsetDateTime.class)).thenReturn(time);
        return row;
    }
}
