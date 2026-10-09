package com.fnbx.hrm.controller;

import com.fnbx.hrm.dto.request.CreateEmployeeRequest;
import com.fnbx.hrm.dto.request.EmployeeListFilter;
import com.fnbx.hrm.dto.request.TerminateEmployeeRequest;
import com.fnbx.hrm.dto.request.UpdateEmployeeRequest;
import com.fnbx.hrm.dto.response.EmployeeOverviewResponse;
import com.fnbx.hrm.dto.response.EmployeeResponse;
import com.fnbx.hrm.dto.response.EmployeeRestrictedResponse;
import com.fnbx.hrm.dto.response.EmployeeSummaryResponse;
import com.fnbx.hrm.dto.response.PayrollHistoryEntryResponse;
import com.fnbx.hrm.service.EmployeeService;
import com.fnbx.shared.security.BranchHeader;
import com.fnbx.shared.utils.PagedResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** The payroll profile of a person who already exists in identity: bank details, hire and termination dates. */
@RestController
@RequestMapping("/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping
    public ResponseEntity<PagedResponse<EmployeeSummaryResponse>> listEmployees(
            @Valid @ModelAttribute EmployeeListFilter filter,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(employeeService.listEmployees(filter, PageRequest.of(page, size)));
    }

    @GetMapping("/overview")
    public ResponseEntity<EmployeeOverviewResponse> overview() {
        return ResponseEntity.ok(employeeService.overview());
    }

    @PostMapping
    public ResponseEntity<EmployeeResponse> createEmployee(@Valid @RequestBody CreateEmployeeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeeService.createEmployee(request));
    }

    @GetMapping("/{staffId}")
    public ResponseEntity<EmployeeResponse> getEmployee(@PathVariable UUID staffId) {
        return ResponseEntity.ok(employeeService.getEmployee(staffId));
    }

    /** Branch-manager view: carries no bank detail and no money field. */
    @GetMapping("/{staffId}/restricted")
    public ResponseEntity<EmployeeRestrictedResponse> getEmployeeRestricted(
            @RequestHeader(BranchHeader.NAME) UUID branchId, @PathVariable UUID staffId) {
        return ResponseEntity.ok(employeeService.getEmployeeRestricted(branchId, staffId));
    }

    @PutMapping("/{staffId}")
    public ResponseEntity<EmployeeResponse> updateEmployee(@PathVariable UUID staffId,
            @Valid @RequestBody UpdateEmployeeRequest request) {
        return ResponseEntity.ok(employeeService.updateEmployee(staffId, request));
    }

    @PostMapping("/{staffId}/termination")
    public ResponseEntity<EmployeeResponse> terminateEmployee(@PathVariable UUID staffId,
            @Valid @RequestBody TerminateEmployeeRequest request) {
        return ResponseEntity.ok(employeeService.terminateEmployee(staffId, request));
    }

    @GetMapping("/{staffId}/payroll-history")
    public ResponseEntity<List<PayrollHistoryEntryResponse>> getPayrollHistory(@PathVariable UUID staffId) {
        return ResponseEntity.ok(employeeService.getPayrollHistory(staffId));
    }
}
