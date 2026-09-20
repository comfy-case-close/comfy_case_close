package com.fnbx.shared.tenant;

import java.sql.*;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TenantAwareDataSourceTest {
    @AfterEach void clear() { TenantContext.clear(); }

    @Test void installsContextAfterTheTransactionStartsAndReinstallsItAfterCommit() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection physical = mock(Connection.class);
        PreparedStatement config = mock(PreparedStatement.class);
        when(source.getConnection()).thenReturn(physical);
        when(physical.prepareStatement(startsWith("SELECT set_config"))).thenReturn(config);
        TenantContext.set(TenantContext.of(UUID.randomUUID(), UUID.randomUUID()));
        Connection wrapped = new TenantAwareDataSource(source).getConnection();
        verify(physical, never()).prepareStatement(anyString());
        wrapped.setAutoCommit(false);
        wrapped.prepareStatement("SELECT 1");
        wrapped.prepareStatement("SELECT 2");
        verify(config, times(1)).execute();
        wrapped.commit();
        wrapped.prepareStatement("SELECT 3");
        verify(config, times(2)).execute();
    }

    @Test void rejectsTenantQueriesWithoutATransaction() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection physical = mock(Connection.class);
        when(source.getConnection()).thenReturn(physical);
        when(physical.getAutoCommit()).thenReturn(true);
        TenantContext.set(TenantContext.of(UUID.randomUUID(), null));
        var wrapped = new TenantAwareDataSource(source).getConnection();
        assertThatThrownBy(() -> wrapped.prepareStatement("SELECT 1")).isInstanceOf(SQLException.class);
    }
}
