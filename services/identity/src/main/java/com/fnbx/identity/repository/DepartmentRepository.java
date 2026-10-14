package com.fnbx.identity.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.fnbx.identity.entity.DepartmentProfile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Departments of the current tenant.
 *
 * <p>No query here filters on {@code business_id}: RLS already does, see {@link BranchRepository}.
 * Only identity writes this table; hrm reads it with a join.
 */
@Repository
public class DepartmentRepository {

    private final JdbcTemplate jdbc;

    public DepartmentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final RowMapper<DepartmentProfile> MAPPER = (rs, row) -> new DepartmentProfile(
            rs.getObject("department_id", UUID.class), rs.getObject("business_id", UUID.class),
            rs.getString("department_code"), rs.getString("department_name"),
            rs.getInt("display_order"), rs.getBoolean("is_active"));

    public UUID insert(UUID businessId, String departmentCode, String departmentName, int displayOrder, boolean active) {
        return jdbc.queryForObject("""
            INSERT INTO identity.department (business_id, department_code, department_name, display_order, is_active)
            VALUES (?, ?, ?, ?, ?)
            RETURNING department_id
            """, UUID.class, businessId, departmentCode, departmentName, displayOrder, active);
    }

    public Optional<DepartmentProfile> find(UUID departmentId) {
        List<DepartmentProfile> rows = jdbc.query(
                "SELECT * FROM identity.department WHERE department_id = ?", MAPPER, departmentId);
        return rows.size() == 1 ? Optional.of(rows.getFirst()) : Optional.empty();
    }

    /** Active and inactive, so a screen can show what was switched off. */
    public List<DepartmentProfile> findAll() {
        return jdbc.query("SELECT * FROM identity.department ORDER BY display_order, department_code", MAPPER);
    }

    public void update(UUID departmentId, String departmentCode, String departmentName, int displayOrder, boolean active) {
        jdbc.update("""
            UPDATE identity.department
               SET department_code = ?, department_name = ?, display_order = ?, is_active = ?
             WHERE department_id = ?
            """, departmentCode, departmentName, displayOrder, active, departmentId);
    }

    /** Soft delete: positions and staff keep pointing at the department. */
    public void deactivate(UUID departmentId) {
        jdbc.update("UPDATE identity.department SET is_active = false WHERE department_id = ? AND is_active", departmentId);
    }
}
