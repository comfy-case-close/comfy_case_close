package com.comfy.caseclose.controller;

import com.comfy.caseclose.dto.response.BranchReportDTO;
import com.comfy.caseclose.dto.response.DateReportDTO;
import com.comfy.caseclose.dto.response.DetailItemDTO;
import com.comfy.caseclose.dto.response.EmployeeReportDTO;
import com.comfy.caseclose.dto.response.IssueReportDTO;
import com.comfy.caseclose.dto.response.KpiReportDTO;
import com.comfy.caseclose.dto.response.RiskBreakdownDTO;
import com.comfy.caseclose.dto.response.ShiftTypeReportDTO;
import com.comfy.caseclose.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/kpi")
    public ResponseEntity<KpiReportDTO> kpi(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(reportService.kpi(branchId, fromDate, toDate));
    }

    @GetMapping("/by-branch")
    public ResponseEntity<List<BranchReportDTO>> byBranch(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(reportService.byBranch(branchId, fromDate, toDate));
    }

    @GetMapping("/by-date")
    public ResponseEntity<List<DateReportDTO>> byDate(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(reportService.byDate(branchId, fromDate, toDate));
    }

    @GetMapping("/by-shift-type")
    public ResponseEntity<List<ShiftTypeReportDTO>> byShiftType(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(reportService.byShiftType(branchId, fromDate, toDate));
    }

    @GetMapping("/by-employee")
    public ResponseEntity<List<EmployeeReportDTO>> byEmployee(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(reportService.byEmployee(branchId, fromDate, toDate));
    }

    @GetMapping("/risk-breakdown")
    public ResponseEntity<List<RiskBreakdownDTO>> riskBreakdown(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(reportService.riskBreakdown(branchId, fromDate, toDate));
    }

    @GetMapping("/issues")
    public ResponseEntity<List<IssueReportDTO>> issues(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(reportService.issues(branchId, fromDate, toDate, limit));
    }

    @GetMapping("/details")
    public ResponseEntity<List<DetailItemDTO>> details(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam String category) {
        return ResponseEntity.ok(reportService.details(branchId, fromDate, toDate, category));
    }

    @PostMapping("/monthly-export")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'ACCOUNTANT')")
    public ResponseEntity<KpiReportDTO> monthlyExport(
            @RequestParam(required = false) Long branchId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return ResponseEntity.ok(reportService.monthlyExport(branchId, month));
    }
}
