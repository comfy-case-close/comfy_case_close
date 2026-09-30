package com.fnbx.cashclose.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/** Reads the active branch > business > global policy without caching stale values. */
@Component
@RequiredArgsConstructor
public class EffectiveConfig {
    private final EntityManager entityManager;

    public boolean bool(UUID branchId, String key, boolean fallback) {
        Object value = entityManager.createNativeQuery("SELECT platform.fn_config_bool(:branch,:key)")
                .setParameter("branch", branchId).setParameter("key", key).getSingleResult();
        return value == null ? fallback : Boolean.parseBoolean(value.toString());
    }

    public BigDecimal number(UUID branchId, String key, BigDecimal fallback) {
        Object value = entityManager.createNativeQuery("SELECT platform.fn_config_num(:branch,:key)")
                .setParameter("branch", branchId).setParameter("key", key).getSingleResult();
        return value == null ? fallback : new BigDecimal(value.toString());
    }
}
