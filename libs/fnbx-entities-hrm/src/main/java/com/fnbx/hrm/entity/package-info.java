/**
 * Payroll entities - schema {@code payroll} (service package stays
 * {@code com.fnbx.hrm}; see db/changelog/payroll/1.0.0.1-tables.xml for why
 * the schema and package names differ).
 *
 * <p>{@code employee}, {@code branch} and {@code app_user} from
 * {@code comfy-payroll-erd.mmd} are NOT entities here - they are
 * {@code identity.staff}, {@code identity.branch} and {@code identity.staff}
 * again (staff already logs in). {@link com.fnbx.hrm.entity.EmployeeProfile},
 * {@link com.fnbx.hrm.entity.BranchSetting} and
 * {@link com.fnbx.hrm.entity.PositionProfile} are thin 1:1 extensions holding
 * only the columns identity's own tables have no reason to carry.
 *
 * <p>Schema, migration and JPA entities only - no repository, service or
 * controller layer yet (spec section 13 build phases).
 */
package com.fnbx.hrm.entity;
