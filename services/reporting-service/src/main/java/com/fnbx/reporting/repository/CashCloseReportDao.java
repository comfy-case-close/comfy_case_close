package com.fnbx.reporting.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import com.fnbx.shared.security.AccessPrincipal;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.exception.ErrorCode;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Truy van bao cao — SQL viet tay, JOIN xuyen schema.
 *
 * <h2>Vi sao KHONG co WHERE business_id o day</h2>
 * RLS loc o tang database. {@code svc_reporting} chi co {@code SELECT} tren moi
 * schema va KHONG co {@code BYPASSRLS}, nen moi truy van tu dong bi gioi han
 * trong tenant hien tai.
 *
 * <p>Them {@code WHERE business_id = ?} khong sai, nhung tao AO GIAC rang cach
 * ly do ung dung dam bao. Neu mot ngay ai do viet truy van moi ma quen dieu
 * kien do, RLS van chan.
 *
 * <h2>⚠️ Canh bao khi doi schema</h2>
 * SQL o day la NATIVE — compiler khong kiem tra, Spring khoi dong cung khong
 * kiem tra. Doi ten cot ma quen file nay thi loi chi lo ra KHI NGUOI DUNG BAM
 * VAO BAO CAO, tren production.
 *
 * <p>Truoc khi CONTRACT (xoa cot cu) trong quy trinh expand-contract, BAT BUOC:
 * <pre>grep -rn "ten_cot_cu" libs/ services/ --include="*.java" --include="*.sql"</pre>
 * Do la mot trong nhung ly do repo nay con la monorepo. ADR-0003 muc 12.9.
 */
@Repository
@Transactional(readOnly = true)
public class CashCloseReportDao {

    @org.springframework.beans.factory.annotation.Autowired
    private com.fnbx.shared.security.BranchAccessGuard permissions;

    private final NamedParameterJdbcTemplate jdbc;

    public CashCloseReportDao(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Tong hop theo chi nhanh trong khoang ngay. */
    public List<Map<String, Object>> summaryByBranch(LocalDate from, LocalDate to) {
        requireReportDates(from, to);
        String sql = """
            SELECT br.branch_code,
                   br.branch_name,
                   count(*)                                   AS close_count,
                   count(*) FILTER (WHERE cc.is_late)          AS late_count,
                   count(*) FILTER (WHERE cc.expected_cash_source = 'MANUAL')
                                                              AS manual_expected_count,
                   sum(cc.pos_expected_cash)                   AS expected_cash,
                   sum(k.counted_cash)                         AS counted_cash,
                   sum(k.cash_difference)                      AS cash_difference,
                   sum(k.unexplained_difference)               AS unexplained_difference,
                   sum(k.expense_total)                        AS total_expense,
                   sum(k.tips_total)                           AS tips_total,
                   sum(k.tips_total - k.tips_in_drawer_total)  AS tips_separate_total,
                   sum(k.tips_in_drawer_total)                 AS tips_in_drawer_total,
                   sum(k.cash_remaining)                       AS cash_remaining
            FROM cashclose.cash_close cc
            JOIN cashclose.v_close_calc k ON k.cash_close_id = cc.cash_close_id
            JOIN identity.branch br ON br.branch_id = cc.branch_id
            WHERE cc.branch_id IN (:authBranches) AND cc.status = 'APPROVED'
              AND cc.business_date BETWEEN :from AND :to
            GROUP BY br.branch_code, br.branch_name
            ORDER BY sum(abs(k.unexplained_difference)) DESC
            """;
        return jdbc.queryForList(sql,
                authorizedBranches().addValue("from", from).addValue("to", to));
    }

    /**
     * Nhan vien co lech chua giai trinh lap lai.
     * JOIN cashclose x identity — hai schema, mot truy van, khong goi mang.
     */
    public List<Map<String, Object>> staffRisk(LocalDate from, LocalDate to) {
        requireReportDates(from, to);
        String sql = """
            SELECT u.employee_code,
                   btrim(u.first_name || ' ' || u.last_name) AS full_name,
                   count(*)                                  AS close_count,
                   count(*) FILTER (WHERE abs(k.unexplained_difference)
                                          > cc.applied_diff_allowed_abs)
                                                             AS over_threshold_count,
                   sum(abs(k.unexplained_difference))         AS total_unexplained,
                   max(cc.business_date)                      AS last_close_date
            FROM cashclose.cash_close cc
            JOIN cashclose.v_close_calc k ON k.cash_close_id = cc.cash_close_id
            JOIN identity.staff u ON u.staff_id = cc.submitted_by
            WHERE cc.branch_id IN (:authBranches) AND cc.status = 'APPROVED'
              AND cc.business_date BETWEEN :from AND :to
            GROUP BY u.employee_code, u.first_name, u.last_name
            HAVING count(*) FILTER (WHERE abs(k.unexplained_difference)
                                          > cc.applied_diff_allowed_abs) > 0
            ORDER BY sum(abs(k.unexplained_difference)) DESC
            """;
        return jdbc.queryForList(sql,
                authorizedBranches().addValue("from", from).addValue("to", to));
    }

    /**
     * Ty le phieu dung doanh thu ky vong NHAP TAY, theo chi nhanh.
     *
     * <p>Chi so kiem soat noi bo quan trong: chi nhanh nao lien tuc "POS hong"
     * la chi nhanh dang bao dong. Xem javadoc {@code ExpectedCashSource}.
     */
    public List<Map<String, Object>> manualExpectedCashRatio(LocalDate from, LocalDate to) {
        requireReportDates(from, to);
        String sql = """
            SELECT br.branch_code,
                   br.branch_name,
                   count(*)                                              AS total_closes,
                   count(*) FILTER (WHERE cc.expected_cash_source = 'MANUAL') AS manual_closes,
                   round(100.0 * count(*) FILTER (WHERE cc.expected_cash_source = 'MANUAL')
                         / nullif(count(*), 0), 1)                        AS manual_pct
            FROM cashclose.cash_close cc
            JOIN identity.branch br ON br.branch_id = cc.branch_id
            WHERE cc.branch_id IN (:authBranches) AND cc.status <> 'VOIDED'
              AND cc.business_date BETWEEN :from AND :to
            GROUP BY br.branch_code, br.branch_name
            ORDER BY manual_pct DESC NULLS LAST
            """;
        return jdbc.queryForList(sql,
                authorizedBranches().addValue("from", from).addValue("to", to));
    }

    /** Canh bao HIGH/CRITICAL chua ai xac nhan — doc qua view co security_barrier. */
    public List<Map<String, Object>> unacknowledgedAlerts() {
        return jdbc.queryForList(
                """
                SELECT v.* FROM analytics.alert_unacknowledged v
                JOIN notify.alert a ON a.alert_id = v.alert_id
                WHERE a.branch_id IN (:authBranches) ORDER BY v.created_at
                """,
                authorizedBranches());
    }

    private MapSqlParameterSource authorizedBranches() {
        var branches = permissions.branches(Permission.REPORT_READ);
        if (branches.isEmpty()) throw new AccessDeniedException("Reporting access denied");
        return new MapSqlParameterSource("authBranches", branches);
    }

    private void requireReportDates(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new AppException(ErrorCode.DATE_RANGE_INVALID);
        }
        String timezone = jdbc.getJdbcTemplate().queryForObject(
                "SELECT timezone FROM identity.business WHERE business_id = shared.current_business_id()",
                String.class);
        LocalDate today = LocalDate.now(ZoneId.of(timezone));
        if (from.isAfter(today) || to.isAfter(today)) {
            throw new AppException(ErrorCode.INVALID_FILTER, "Report dates cannot be in the future");
        }
    }

}
