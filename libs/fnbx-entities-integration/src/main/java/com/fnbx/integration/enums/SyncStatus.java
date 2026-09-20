package com.fnbx.integration.enums;

/** Matches PostgreSQL enum {@code shared.sync_status}. PARTIAL means some rows landed. */
public enum SyncStatus { SUCCESS, FAILED, PARTIAL }
