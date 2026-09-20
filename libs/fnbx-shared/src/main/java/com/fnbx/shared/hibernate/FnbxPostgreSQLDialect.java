package com.fnbx.shared.hibernate;

import java.sql.Types;
import java.util.Set;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.spi.JdbcTypeRegistry;

/**
 * Teaches Hibernate that the PostgreSQL money domains have a numeric base type.
 * PostgreSQL JDBC reports domain columns as {@link Types#DISTINCT}; without this
 * translation, schema validation rejects valid {@code NUMERIC(14,2)} domains.
 *
 * <h2>Lives in fnbx-shared, not in one service</h2>
 * Every service that {@code @EntityScan}s {@code com.fnbx.platform.entity} loads
 * {@code Denomination.face_value shared.d_money_nonneg}, so all of them need this
 * translation, not just cashclose.
 *
 * <h2>How it is applied</h2>
 * NOT by Spring injection - Hibernate instantiates it reflectively from the class
 * name in {@code application.yml}:
 * <pre>
 *   spring.jpa.properties.hibernate.dialect: com.fnbx.shared.hibernate.FnbxPostgreSQLDialect
 * </pre>
 * That is why no Java file ever imports this class.
 *
 * <p>Adding a new {@code CREATE DOMAIN} over a numeric base type means adding its
 * name to {@link #NUMERIC_DOMAINS} here - one place, all services.
 */
public class FnbxPostgreSQLDialect extends PostgreSQLDialect {

    private static final Set<String> NUMERIC_DOMAINS = Set.of("d_money", "d_money_nonneg");

    @Override
    public JdbcType resolveSqlTypeDescriptor(String columnTypeName, int jdbcTypeCode,
            int precision, int scale, JdbcTypeRegistry jdbcTypeRegistry) {
        String unquoted = columnTypeName == null ? "" : columnTypeName.replace("\"", "");
        String unqualified = unquoted.substring(unquoted.lastIndexOf('.') + 1);
        if (jdbcTypeCode == Types.DISTINCT && NUMERIC_DOMAINS.contains(unqualified)) {
            return jdbcTypeRegistry.getDescriptor(Types.NUMERIC);
        }
        return super.resolveSqlTypeDescriptor(
                columnTypeName, jdbcTypeCode, precision, scale, jdbcTypeRegistry);
    }
}
