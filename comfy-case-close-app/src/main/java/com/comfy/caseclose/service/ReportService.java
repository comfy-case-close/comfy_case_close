package com.comfy.caseclose.service;

import com.comfy.caseclose.dto.response.BranchReportDTO;
import com.comfy.caseclose.dto.response.DateReportDTO;
import com.comfy.caseclose.dto.response.DetailItemDTO;
import com.comfy.caseclose.dto.response.EmployeeReportDTO;
import com.comfy.caseclose.dto.response.IssueReportDTO;
import com.comfy.caseclose.dto.response.KpiReportDTO;
import com.comfy.caseclose.dto.response.RiskBreakdownDTO;
import com.comfy.caseclose.dto.response.ShiftTypeReportDTO;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public interface ReportService {

    KpiReportDTO kpi(Long branchId, LocalDate fromDate, LocalDate toDate);

    List<BranchReportDTO> byBranch(Long branchId, LocalDate fromDate, LocalDate toDate);

    List<DateReportDTO> byDate(Long branchId, LocalDate fromDate, LocalDate toDate);

    List<ShiftTypeReportDTO> byShiftType(Long branchId, LocalDate fromDate, LocalDate toDate);

    List<EmployeeReportDTO> byEmployee(Long branchId, LocalDate fromDate, LocalDate toDate);

    List<RiskBreakdownDTO> riskBreakdown(Long branchId, LocalDate fromDate, LocalDate toDate);

    List<IssueReportDTO> issues(Long branchId, LocalDate fromDate, LocalDate toDate, int limit);

    List<DetailItemDTO> details(Long branchId, LocalDate fromDate, LocalDate toDate, String category);

    KpiReportDTO monthlyExport(Long branchId, YearMonth month);
}
