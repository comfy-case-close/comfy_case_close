package com.fnbx.platform.enums;

/**
 * Matches PostgreSQL enum {@code shared.config_scope}.
 *
 * <p>Resolution order is BRANCH, then BUSINESS, then GLOBAL - implemented by
 * {@code platform.fn_config_num}.
 */
public enum ConfigScope { GLOBAL, BUSINESS, BRANCH }
