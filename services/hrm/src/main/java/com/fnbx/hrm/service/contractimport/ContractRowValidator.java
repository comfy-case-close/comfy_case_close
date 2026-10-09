package com.fnbx.hrm.service.contractimport;

import static com.fnbx.hrm.service.contractimport.ContractImportColumn.*;

import com.fnbx.hrm.dto.request.EmployeeProfileInput;
import com.fnbx.hrm.dto.request.EmploymentAssignmentRequest;
import com.fnbx.hrm.entity.EmploymentAssignment;
import com.fnbx.hrm.enums.ContractKind;
import com.fnbx.hrm.enums.EducationGrade;
import com.fnbx.hrm.enums.EducationLevel;
import com.fnbx.hrm.enums.EmploymentType;
import com.fnbx.hrm.enums.Gender;
import com.fnbx.hrm.enums.JobLevel;
import com.fnbx.hrm.enums.MaritalStatus;
import com.fnbx.hrm.enums.ProbationResult;
import com.fnbx.hrm.enums.RecruitmentSource;
import com.fnbx.hrm.entity.EmployeeProfile;
import com.fnbx.hrm.repository.EmployeeProfileRepository;
import com.fnbx.hrm.repository.EmploymentAssignmentRepository;
import com.fnbx.hrm.service.contract.ContractPayResolver;
import com.fnbx.identity.entity.Staff;
import com.fnbx.shared.exception.AppException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContractRowValidator {

    private final ImportLookups lookups;
    private final ImportEnumParser enums;
    private final ContractPayResolver payResolver;
    private final EmploymentAssignmentRepository assignmentRepository;
    private final EmployeeProfileRepository profileRepository;
    private final ProfileFieldMerger profileMerger;

    /** {@code earlierRows} are the rows of the same file already validated, so two overlapping rows are caught too. */
    public ValidatedRow validate(ImportSheetRow sheetRow, List<ValidatedRow> earlierRows) {
        List<ImportIssue> issues = new ArrayList<>();
        RowReader reader = new RowReader(sheetRow, enums, issues);

        Optional<Staff> staff = reader.text(EMPLOYEE_CODE).flatMap(lookups::staffByEmployeeCode);
        if (reader.required(EMPLOYEE_CODE) != null && staff.isEmpty()) {
            issues.add(ImportIssue.error("STAFF_NOT_FOUND", "No account has this employee code; create it in identity first"));
        }
        EmployeeProfileInput profile = readProfile(reader, issues);
        staff.flatMap(found -> profileRepository.findById(found.getStaffId()))
                .ifPresent(existing -> warnOnDifferences(existing, profile, issues));
        EmploymentAssignmentRequest assignment = readAssignment(reader, issues);
        staff.ifPresent(found -> checkOverlap(found.getStaffId(), assignment, earlierRows, issues));
        return new ValidatedRow(sheetRow.rowNo(), staff.map(Staff::getStaffId).orElse(null), profile, assignment, issues);
    }

    private void warnOnDifferences(EmployeeProfile existing, EmployeeProfileInput incoming, List<ImportIssue> issues) {
        List<String> differing = profileMerger.merge(existing, incoming).differingFields();
        if (!differing.isEmpty()) {
            issues.add(ImportIssue.warning("PROFILE_FIELD_DIFFERS",
                    "The profile already holds a different value, kept unchanged: " + String.join(", ", differing)));
        }
    }

    private EmployeeProfileInput readProfile(RowReader reader, List<ImportIssue> issues) {
        String bankCode = reader.text(BANK).map(text -> bankCodeOf(text, issues)).orElse(null);
        String provinceCode = reader.text(ADDRESS_PROVINCE).map(text -> provinceCodeOf(text, issues)).orElse(null);
        return new EmployeeProfileInput(
                reader.requiredDate(DATE_OF_BIRTH),
                reader.enumValue(Gender.class, GENDER),
                reader.enumValue(MaritalStatus.class, MARITAL_STATUS),
                reader.smallNumber(CHILDREN_COUNT),
                reader.text(ETHNICITY).orElse(null),
                reader.text(RELIGION).orElse(null),
                reader.text(NATIONALITY).orElse(null),
                reader.required(NATIONAL_ID_NO),
                reader.requiredDate(NATIONAL_ID_ISSUED_ON),
                reader.required(NATIONAL_ID_ISSUED_PLACE),
                reader.text(SOCIAL_INSURANCE_NO).orElse(null),
                reader.text(TAX_CODE).orElse(null),
                reader.enumValue(EducationLevel.class, EDUCATION_LEVEL),
                reader.text(EDUCATION_SCHOOL).orElse(null),
                reader.text(EDUCATION_MAJOR).orElse(null),
                reader.smallNumber(GRADUATION_YEAR),
                reader.enumValue(EducationGrade.class, EDUCATION_GRADE),
                reader.text(PERSONAL_EMAIL).orElse(null),
                reader.text(ADDRESS_STREET).orElse(null),
                reader.text(ADDRESS_WARD).orElse(null),
                provinceCode,
                reader.enumValue(RecruitmentSource.class, RECRUITMENT_SOURCE),
                bankCode,
                reader.text(BANK_ACCOUNT_NO).orElse(null),
                reader.text(BANK_ACCOUNT_NAME).orElse(null),
                reader.date(EFFECTIVE_FROM),
                null);
    }

    private EmploymentAssignmentRequest readAssignment(RowReader reader, List<ImportIssue> issues) {
        EmploymentAssignmentRequest request = new EmploymentAssignmentRequest();
        request.setPositionId(reader.text(POSITION_CODE).flatMap(code -> idOrIssue(lookups.positionByCode(code)
                .map(position -> position.getPositionId()), "POSITION_NOT_FOUND", code, issues)).orElse(null));
        request.setDefaultBranchId(reader.text(BRANCH_CODE).flatMap(code -> idOrIssue(lookups.branchByCode(code)
                .map(branch -> branch.getBranchId()), "BRANCH_NOT_FOUND", code, issues)).orElse(null));
        reader.required(POSITION_CODE);
        reader.required(BRANCH_CODE);
        request.setEmploymentType(reader.requiredEnum(EmploymentType.class, EMPLOYMENT_TYPE));
        request.setContractKind(reader.requiredEnum(ContractKind.class, CONTRACT_KIND));
        request.setJobLevel(reader.enumValue(JobLevel.class, JOB_LEVEL));
        request.setProbationResult(reader.enumValue(ProbationResult.class, PROBATION_RESULT));
        request.setEffectiveFrom(reader.requiredDate(EFFECTIVE_FROM));
        request.setEffectiveTo(reader.date(EFFECTIVE_TO));
        BigDecimal responsibility = reader.money(RESPONSIBILITY_ALLOWANCE);
        request.setResponsibilityAllowance(responsibility == null ? BigDecimal.ZERO : responsibility);
        request.setAgreedMonthlySalary(reader.money(AGREED_MONTHLY_SALARY));
        request.setHourlyBaseRate(reader.money(HOURLY_BASE_RATE));
        request.setInsuranceBase(reader.money(INSURANCE_BASE));
        request.setInsured(request.getInsuranceBase() != null && request.getInsuranceBase().signum() > 0);
        checkDates(request, issues);
        checkProbation(request, issues);
        checkPay(request, issues);
        return request;
    }

    private void checkDates(EmploymentAssignmentRequest request, List<ImportIssue> issues) {
        if (request.getEffectiveFrom() != null && request.getEffectiveTo() != null
                && request.getEffectiveTo().isBefore(request.getEffectiveFrom())) {
            issues.add(ImportIssue.error("INVALID_VALUE", "Hiệu lực đến is before Hiệu lực từ"));
        }
    }

    private void checkProbation(EmploymentAssignmentRequest request, List<ImportIssue> issues) {
        boolean probation = request.getContractKind() == ContractKind.PROBATION;
        if (request.getProbationResult() != null && !probation) {
            issues.add(ImportIssue.error("INVALID_VALUE", "Kết quả thử việc only applies to a probation contract"));
        }
        if (probation && request.getEffectiveTo() == null) {
            issues.add(ImportIssue.warning("PROBATION_END_MISSING", "A probation contract has no end date"));
        }
    }

    private void checkPay(EmploymentAssignmentRequest request, List<ImportIssue> issues) {
        if (request.getEmploymentType() == null) {
            return;
        }
        try {
            payResolver.resolve(request);
        } catch (AppException ex) {
            issues.add(ImportIssue.error(ex.getErrorCode().name(), ex.getMessage()));
        }
    }

    private void checkOverlap(UUID staffId, EmploymentAssignmentRequest request, List<ValidatedRow> earlierRows,
            List<ImportIssue> issues) {
        if (request.getPositionId() == null || request.getEffectiveFrom() == null) {
            return;
        }
        boolean overlapsStored = assignmentRepository.findByStaffIdOrderByEffectiveFromDesc(staffId).stream()
                .anyMatch(existing -> overlaps(existing.getPositionId(), existing.getEffectiveFrom(), existing.getEffectiveTo(), request));
        boolean overlapsEarlierRow = earlierRows.stream()
                .filter(row -> staffId.equals(row.staffId()) && row.committable())
                .anyMatch(row -> overlaps(row.assignment().getPositionId(), row.assignment().getEffectiveFrom(),
                        row.assignment().getEffectiveTo(), request));
        if (overlapsStored || overlapsEarlierRow) {
            issues.add(ImportIssue.error("ASSIGNMENT_OVERLAP", "The contract overlaps another one for the same person and position"));
        }
    }

    private boolean overlaps(UUID positionId, LocalDate from, LocalDate to, EmploymentAssignmentRequest request) {
        LocalDate requestEnd = request.getEffectiveTo() == null ? LocalDate.MAX : request.getEffectiveTo();
        LocalDate otherEnd = to == null ? LocalDate.MAX : to;
        return request.getPositionId().equals(positionId)
                && !request.getEffectiveFrom().isAfter(otherEnd) && !from.isAfter(requestEnd);
    }

    private Optional<UUID> idOrIssue(Optional<UUID> id, String code, String typed, List<ImportIssue> issues) {
        if (id.isEmpty()) {
            issues.add(ImportIssue.error(code, "Unknown code " + typed));
        }
        return id;
    }

    private String bankCodeOf(String text, List<ImportIssue> issues) {
        Optional<String> code = lookups.bankByText(text).map(bank -> bank.getBankCode());
        if (code.isEmpty()) {
            issues.add(ImportIssue.warning("BANK_NOT_MATCHED", "Bank \"" + text + "\" is not in the bank list; pick it on the employee profile"));
        }
        return code.orElse(null);
    }

    private String provinceCodeOf(String text, List<ImportIssue> issues) {
        Optional<String> code = lookups.provinceByText(text).map(province -> province.getProvinceCode());
        if (code.isEmpty()) {
            issues.add(ImportIssue.warning("PROVINCE_NOT_MATCHED", "Province \"" + text + "\" is not in the province list"));
        }
        return code.orElse(null);
    }
}
