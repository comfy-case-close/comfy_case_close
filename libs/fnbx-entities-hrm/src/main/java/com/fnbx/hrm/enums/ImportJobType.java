package com.fnbx.hrm.enums;

/** What an {@link com.fnbx.hrm.entity.ImportJob} imports or checks. */
public enum ImportJobType {
    /** Sheet {@code Danh sach nhan vien}: employees, contracts, allowances, leave quotas. */
    MASTER,
    /** One period's FULLTIME/PARTTIME grid. */
    TIMESHEET,
    /** Compares app results against a calculated Excel file (spec section 12). */
    PARITY_CHECK,
    /** Bulk contract creation, one row per contract. */
    CONTRACT_BULK
}
