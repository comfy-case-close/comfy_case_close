package com.fnbx.identity.utils;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DuplicateKeyException;
import static org.assertj.core.api.Assertions.*;

class BusinessCodeUtilsTest {
    @Test void normalizesVietnameseAndHandlesNamesWithoutAsciiLetters() {
        assertThat(BusinessCodeUtils.generate("Cà phê Đà Nẵng"))
                .matches("CA-PHE-DA-NA-[0-9A-HJKMNP-TV-Z]{12}");
        assertThat(BusinessCodeUtils.generate("咖啡"))
                .matches("BIZ-[0-9A-HJKMNP-TV-Z]{12}");
        assertThat(BusinessCodeUtils.generate("x".repeat(200))).hasSize(25);
        assertThat(BusinessCodeUtils.generate("Same name"))
                .isNotEqualTo(BusinessCodeUtils.generate("Same name"));
    }

    @Test void retriesUuidCollisionButDoesNotMisclassifyUnrelatedDuplicate() {
        var attempts = new AtomicInteger();
        String result = BusinessCodeUtils.retry(() -> {
            if (attempts.incrementAndGet() == 1) throw duplicate("business_pkey");
            return "created";
        }, "business_pkey");
        assertThat(result).isEqualTo("created");
        assertThat(attempts.get()).isEqualTo(2);
        var emailCollision = duplicate("uq_registration_pending_email");
        assertThatThrownBy(() -> BusinessCodeUtils.retry(() -> { throw emailCollision; }, "business_pkey"))
                .isSameAs(emailCollision);
    }

    @Test void repeatedCollisionsAreBoundedAndRetainTheirActualConstraint() {
        var attempts = new AtomicInteger();
        var collision = duplicate("business_pkey");
        assertThatThrownBy(() -> BusinessCodeUtils.retry(() -> {
            attempts.incrementAndGet(); throw collision;
        }, "business_pkey")).isSameAs(collision);
        assertThat(attempts.get()).isEqualTo(5);
        assertThat(BusinessCodeUtils.violates(collision, "business_business_code_key")).isFalse();
    }

    private static DuplicateKeyException duplicate(String constraint) {
        return new DuplicateKeyException("duplicate", new PSQLException(new ServerErrorMessage(
                "SERROR\0C23505\0Mduplicate key\0n" + constraint + "\0\0")));
    }
}
