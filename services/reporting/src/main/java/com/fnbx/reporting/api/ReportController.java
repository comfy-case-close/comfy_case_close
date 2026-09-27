package com.fnbx.reporting.api;

import com.fnbx.reporting.repository.CashCloseReportDao;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Bao cao — controller goi THANG repository.
 *
 * <p>Day la NGOAI LE duy nhat cua luat "controller khong goi thang repository"
 * (ADR-0003 quyet dinh #11). Ly do o javadoc cua {@code ReportingApplication}:
 * tao mot {@code ReportService} chi de chuyen tiep loi goi la Architecture
 * Sinkhole ma sach Ch.10 tr.132 canh bao.
 *
 * <p>Neu mot endpoint bao cao nao do BAT DAU co logic that (tinh toan, quyet
 * dinh, ket hop nhieu nguon theo quy tac nghiep vu) — luc do moi tao tang
 * service, va CHI cho endpoint do.
 */
@RestController
@RequestMapping("/reports")
public class ReportController {

    private final CashCloseReportDao dao;

    public ReportController(CashCloseReportDao dao) { this.dao = dao; }

    @GetMapping("/cash-close/by-branch")
    public List<Map<String, Object>> summaryByBranch(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dao.summaryByBranch(from, to);
    }

    @GetMapping("/cash-close/staff-risk")
    public List<Map<String, Object>> staffRisk(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dao.staffRisk(from, to);
    }

    @GetMapping("/cash-close/manual-expected-ratio")
    public List<Map<String, Object>> manualExpectedRatio(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dao.manualExpectedCashRatio(from, to);
    }

    @GetMapping("/alerts/unacknowledged")
    public List<Map<String, Object>> unacknowledgedAlerts() {
        return dao.unacknowledgedAlerts();
    }
}
