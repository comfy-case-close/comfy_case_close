package com.fnbx.cashclose.controller;

import com.fnbx.cashclose.dto.response.BranchReportDTO;
import com.fnbx.cashclose.dto.response.DateReportDTO;
import com.fnbx.cashclose.dto.response.DetailItemDTO;
import com.fnbx.cashclose.dto.response.EmployeeReportDTO;
import com.fnbx.cashclose.dto.response.IssueReportDTO;
import com.fnbx.cashclose.dto.response.KpiReportDTO;
import com.fnbx.cashclose.dto.response.RiskBreakdownDTO;
import com.fnbx.cashclose.dto.response.ShiftTypeReportDTO;
import com.fnbx.cashclose.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Dashboard reports, ported from {@code dev}. Served at {@code /api/v1/reports/*}
 * ({@code WebConfig} adds the {@code /api/v1} prefix); the gateway sends exactly
 * these nine paths here and every other {@code /api/v1/reports/**} path to
 * reporting-service.
 *
 * <p>No {@code X-Branch-Id} header: a report may span every branch the caller can
 * read. The optional {@code branchId} narrows that set. Authorisation
 * ({@code REPORT_READ}, per branch) is checked in the service - dev's role list
 * on monthly-export has no equivalent in the permission model.
 */
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/kpi")
    public ResponseEntity<KpiReportDTO> kpi(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(reportService.kpi(branchId, fromDate, toDate));
    }

    @GetMapping("/by-branch")
    public ResponseEntity<List<BranchReportDTO>> byBranch(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(reportService.byBranch(branchId, fromDate, toDate));
    }

    @GetMapping("/by-date")
    public ResponseEntity<List<DateReportDTO>> byDate(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(reportService.byDate(branchId, fromDate, toDate));
    }

    @GetMapping("/by-shift-type")
    public ResponseEntity<List<ShiftTypeReportDTO>> byShiftType(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(reportService.byShiftType(branchId, fromDate, toDate));
    }

    @GetMapping("/by-employee")
    public ResponseEntity<List<EmployeeReportDTO>> byEmployee(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(reportService.byEmployee(branchId, fromDate, toDate));
    }

    @GetMapping("/risk-breakdown")
    public ResponseEntity<List<RiskBreakdownDTO>> riskBreakdown(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(reportService.riskBreakdown(branchId, fromDate, toDate));
    }

    @GetMapping("/issues")
    public ResponseEntity<List<IssueReportDTO>> issues(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(reportService.issues(branchId, fromDate, toDate, limit));
    }

    @GetMapping("/details")
    public ResponseEntity<List<DetailItemDTO>> details(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam String category) {
        return ResponseEntity.ok(reportService.details(branchId, fromDate, toDate, category));
    }

    @PostMapping("/monthly-export")
    public ResponseEntity<KpiReportDTO> monthlyExport(
            @RequestParam(required = false) UUID branchId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return ResponseEntity.ok(reportService.monthlyExport(branchId, month));
    }
}
