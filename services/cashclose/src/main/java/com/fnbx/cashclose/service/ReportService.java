package com.fnbx.cashclose.service;

import com.fnbx.cashclose.dto.response.BranchReportDTO;
import com.fnbx.cashclose.dto.response.DateReportDTO;
import com.fnbx.cashclose.dto.response.DetailItemDTO;
import com.fnbx.cashclose.dto.response.EmployeeReportDTO;
import com.fnbx.cashclose.dto.response.IssueReportDTO;
import com.fnbx.cashclose.dto.response.KpiReportDTO;
import com.fnbx.cashclose.dto.response.RiskBreakdownDTO;
import com.fnbx.cashclose.dto.response.ShiftTypeReportDTO;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Dashboard reports over cash closes, ported from {@code dev}.
 *
 * <p>{@code branchId == null} means every branch where the caller holds
 * {@code REPORT_READ}; a non-null branch narrows that set and never widens it.
 */
public interface ReportService {

    KpiReportDTO kpi(UUID branchId, LocalDate fromDate, LocalDate toDate);

    List<BranchReportDTO> byBranch(UUID branchId, LocalDate fromDate, LocalDate toDate);

    List<DateReportDTO> byDate(UUID branchId, LocalDate fromDate, LocalDate toDate);

    List<ShiftTypeReportDTO> byShiftType(UUID branchId, LocalDate fromDate, LocalDate toDate);

    List<EmployeeReportDTO> byEmployee(UUID branchId, LocalDate fromDate, LocalDate toDate);

    List<RiskBreakdownDTO> riskBreakdown(UUID branchId, LocalDate fromDate, LocalDate toDate);

    List<IssueReportDTO> issues(UUID branchId, LocalDate fromDate, LocalDate toDate, int limit);

    List<DetailItemDTO> details(UUID branchId, LocalDate fromDate, LocalDate toDate, String category);

    KpiReportDTO monthlyExport(UUID branchId, YearMonth month);
}
