package com.fnbx.shared.tenant;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

/**
 * Pushes the tenant context down into PostgreSQL so RLS has something to filter on.
 *
 * <h2>Why the third argument must be {@code true}</h2>
 *
 * <pre>
 *   SELECT set_config('app.business_id', ?, true)
 *                                           ^^^^
 *                                           is_local
 * </pre>
 *
 * {@code true} means the value lives only for the CURRENT TRANSACTION.
 *
 * <p>With {@code false} - or with a session-level {@code SET} - the value lives on
 * the CONNECTION. Under PgBouncer in <b>transaction pooling</b> mode the
 * connection returns to the pool after each transaction and is handed to the next
 * request, possibly belonging to a <b>different tenant</b>. The tenant context
 * would leak into that request.
 *
 * <p>This is the most serious hole possible in a shared-database architecture.
 * ADR-0003 sections 3 and 9 (scenario 4).
 *
 * <h2>Fail-closed</h2>
 * Inside a transaction with no context, this class sets empty tenant values.
 * {@code shared.current_business_id()} then returns NULL, no RLS policy matches,
 * and tenant queries return <b>zero rows</b>. A query with tenant context requires
 * an explicit transaction so its transaction-local settings cannot expire early.
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    private static final String SQL = "SELECT set_config('app.business_id', ?, true), "
                                    + "       set_config('app.user_id',     ?, true)";

    public TenantAwareDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return apply(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return apply(super.getConnection(username, password));
    }

    private Connection apply(Connection conn) throws SQLException {
        // Connection checkout precedes Spring's setAutoCommit(false). SET LOCAL at
        // checkout would run in its own autocommit transaction and immediately vanish.
        // Initialize just before the first statement of each transaction instead.
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, new java.lang.reflect.InvocationHandler() {
                    private boolean initialized;

                    @Override
                    public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
                        String name = method.getName();
                        if (name.equals("equals")) return proxy == args[0];
                        if (name.equals("hashCode")) return System.identityHashCode(proxy);
                        if (name.equals("unwrap") && ((Class<?>) args[0]).isInstance(proxy)) return proxy;
                        if (name.equals("isWrapperFor") && ((Class<?>) args[0]).isInstance(proxy)) return true;
                        if (name.equals("prepareStatement") || name.equals("prepareCall") || name.equals("createStatement")) {
                            TenantContext ctx = TenantContext.currentOrNull();
                            if (!initialized) {
                                if (ctx != null && conn.getAutoCommit()) {
                                    throw new SQLException("Tenant queries require an explicit transaction");
                                }
                                if (!conn.getAutoCommit()) {
                                    try (PreparedStatement ps = conn.prepareStatement(SQL)) {
                                        // Empty values fail closed and also neutralize stale session settings.
                                        ps.setString(1, ctx == null || ctx.businessId() == null ? "" : ctx.businessId().toString());
                                        ps.setString(2, ctx == null || ctx.userId() == null ? "" : ctx.userId().toString());
                                        ps.execute();
                                    }
                                    initialized = true;
                                }
                            }
                        }
                        try {
                            Object result = method.invoke(conn, args);
                            if (name.equals("commit") || name.equals("rollback") || name.equals("setAutoCommit")) initialized = false;
                            return result;
                        } catch (InvocationTargetException ex) { throw ex.getCause(); }
                    }
                });
    }
}
