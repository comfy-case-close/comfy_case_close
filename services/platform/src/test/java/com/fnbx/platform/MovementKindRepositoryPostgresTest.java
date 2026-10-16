package com.fnbx.platform;

import com.fnbx.platform.entity.MovementKind;
import com.fnbx.platform.repository.MovementKindRepository;
import com.fnbx.shared.enums.EffectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Run with a disposable PostgreSQL database populated by the current changelog. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=none")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = MovementKindRepositoryPostgresTest.Persistence.class)
@EnabledIfEnvironmentVariable(named = "FNB_PLATFORM_TEST_DB_URL", matches = ".+")
class MovementKindRepositoryPostgresTest {
    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = MovementKind.class)
    @EnableJpaRepositories(basePackageClasses = MovementKindRepository.class)
    static class Persistence { }

    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("FNB_PLATFORM_TEST_DB_URL"));
        properties.add("spring.datasource.username", () -> "postgres");
        properties.add("spring.datasource.password", () -> "local-test-only");
    }

    @Autowired MovementKindRepository kinds;
    @Autowired TestEntityManager entities;
    @Autowired JdbcTemplate jdbc;
    private UUID business;
    private UUID otherBusiness;
    private long baseline;
    private MovementKind global, current, historical, future, foreign;

    @BeforeEach void fixture() {
        business = UUID.randomUUID();
        otherBusiness = UUID.randomUUID();
        for (UUID id : new UUID[]{business, otherBusiness}) {
            jdbc.update("INSERT INTO identity.business(business_id,business_code,business_name) VALUES (?,?,'Catalogue test')",
                    id, id.toString().toUpperCase());
        }
        baseline = jdbc.queryForObject("SELECT count(*) FROM platform.movement_kind WHERE business_id IS NULL", Long.class);
        String code = "TEST_" + UUID.randomUUID();
        global = kind(null, code, "2026-01-01", null);
        current = kind(business, code, "2026-01-01", null);
        historical = kind(business, code, "2025-01-01", "2026-01-01");
        future = kind(business, code + "_FUTURE", "2030-01-01", null);
        foreign = kind(otherBusiness, code + "_FOREIGN", "2026-01-01", null);
        entities.flush();
        entities.clear();
    }

    @Test void includesGlobalAndAllTenantVersionsButNeverAnotherTenant() {
        var page = kinds.findAllVisible(business, PageRequest.of(0, 200));
        assertThat(page.getTotalElements()).isEqualTo(baseline + 4);
        assertThat(page.getContent()).extracting(MovementKind::getKindSk)
                .contains(global.getKindSk(), current.getKindSk(), historical.getKindSk(), future.getKindSk())
                .doesNotContain(foreign.getKindSk());
        assertThat(page.getContent()).allSatisfy(kind ->
                assertThat(kind.getBusinessId()).isIn(null, business));
        assertThat(page.getContent()).isSortedAccordingTo(Comparator.comparing(MovementKind::getKindCode)
                .thenComparing(MovementKind::getValidFrom, Comparator.reverseOrder())
                .thenComparing(MovementKind::getKindSk));
    }

    @Test void databasePaginationHasConsistentCountsAndNoRepeatedRows() {
        var first = kinds.findAllVisible(business, PageRequest.of(0, 2));
        var second = kinds.findAllVisible(business, PageRequest.of(1, 2));
        int lastIndex = (int) ((baseline + 3) / 2);
        var last = kinds.findAllVisible(business, PageRequest.of(lastIndex, 2));
        assertThat(first.getTotalElements()).isEqualTo(baseline + 4);
        assertThat(second.getTotalElements()).isEqualTo(first.getTotalElements());
        assertThat(first.getContent()).hasSize(2);
        assertThat(second.getContent()).extracting(MovementKind::getKindSk)
                .doesNotContainAnyElementsOf(first.getContent().stream().map(MovementKind::getKindSk).toList());
        assertThat(last.isLast()).isTrue();
        assertThat(last.getTotalPages()).isEqualTo(lastIndex + 1);
    }

    private MovementKind kind(UUID tenant, String code, String from, String to) {
        MovementKind kind = new MovementKind();
        kind.setBusinessId(tenant);
        kind.setKindCode(code);
        kind.setValidFrom(LocalDate.parse(from));
        kind.setValidTo(to == null ? null : LocalDate.parse(to));
        kind.setDisplayName(code);
        kind.setEffectType(EffectType.CASH_OUT);
        entities.persist(kind);
        return kind;
    }
}
