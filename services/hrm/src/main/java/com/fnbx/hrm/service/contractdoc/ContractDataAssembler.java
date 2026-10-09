package com.fnbx.hrm.service.contractdoc;

import com.fnbx.hrm.entity.EmployeeProfile;
import com.fnbx.hrm.entity.EmploymentAssignment;
import com.fnbx.hrm.entity.Province;
import com.fnbx.hrm.enums.ContractKind;
import com.fnbx.hrm.enums.EmploymentType;
import com.fnbx.hrm.repository.EmployeeProfileRepository;
import com.fnbx.hrm.repository.ProvinceRepository;
import com.fnbx.identity.entity.Branch;
import com.fnbx.identity.entity.Staff;
import com.fnbx.identity.entity.StaffPosition;
import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Collects everything a contract prints; this is the one place the full identity number leaves the system. */
@Component
@RequiredArgsConstructor
public class ContractDataAssembler {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale VIETNAM = Locale.forLanguageTag("vi-VN");

    private final EmployeeProfileRepository profileRepository;
    private final ProvinceRepository provinceRepository;
    private final EntityManager entityManager;

    public Map<ContractPlaceholder, String> assemble(EmploymentAssignment assignment, String contractNo) {
        Staff staff = entityManager.find(Staff.class, assignment.getStaffId());
        EmployeeProfile profile = profileRepository.findById(assignment.getStaffId())
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_PROFILE_INCOMPLETE));
        requirePrintable(profile);
        Map<ContractPlaceholder, String> values = new EnumMap<>(ContractPlaceholder.class);
        values.put(ContractPlaceholder.FULL_NAME, staff.getFirstName() + " " + staff.getLastName());
        values.put(ContractPlaceholder.DATE_OF_BIRTH, date(profile.getDateOfBirth()));
        values.put(ContractPlaceholder.NATIONAL_ID_NO, profile.getNationalIdNo());
        values.put(ContractPlaceholder.NATIONAL_ID_ISSUED_ON, date(profile.getNationalIdIssuedOn()));
        values.put(ContractPlaceholder.NATIONAL_ID_ISSUED_PLACE, profile.getNationalIdIssuedPlace());
        values.put(ContractPlaceholder.ADDRESS, address(profile));
        values.put(ContractPlaceholder.PHONE, staff.getPhone());
        values.put(ContractPlaceholder.POSITION, entityManager.find(StaffPosition.class, assignment.getPositionId()).getPositionName());
        values.put(ContractPlaceholder.BRANCH, entityManager.find(Branch.class, assignment.getDefaultBranchId()).getBranchName());
        values.put(ContractPlaceholder.JOB_LEVEL, assignment.getJobLevel() == null ? "" : assignment.getJobLevel().name());
        values.put(ContractPlaceholder.EMPLOYMENT_TYPE, assignment.getEmploymentType() == EmploymentType.FULLTIME ? "Toàn thời gian" : "Bán thời gian");
        values.put(ContractPlaceholder.CONTRACT_KIND, contractKind(assignment.getContractKind()));
        putMoney(values, assignment);
        values.put(ContractPlaceholder.EFFECTIVE_FROM, date(assignment.getEffectiveFrom()));
        values.put(ContractPlaceholder.EFFECTIVE_TO, assignment.getEffectiveTo() == null ? "Không xác định thời hạn" : date(assignment.getEffectiveTo()));
        values.put(ContractPlaceholder.CONTRACT_NO, contractNo);
        values.put(ContractPlaceholder.TODAY, date(LocalDate.now()));
        return values;
    }

    private void putMoney(Map<ContractPlaceholder, String> values, EmploymentAssignment assignment) {
        BigDecimal base = orZero(assignment.getMonthlyBaseSalary());
        values.put(ContractPlaceholder.BASE_SALARY, money(base));
        values.put(ContractPlaceholder.SUPPLEMENT_ALLOWANCE, money(assignment.getSupplementAllowance()));
        values.put(ContractPlaceholder.FIXED_TOTAL, money(base.add(assignment.getSupplementAllowance())));
        values.put(ContractPlaceholder.HOURLY_RATE, assignment.getHourlyBaseRate() == null ? "" : money(assignment.getHourlyBaseRate()));
        values.put(ContractPlaceholder.RESPONSIBILITY_ALLOWANCE, money(assignment.getResponsibilityAllowance()));
        values.put(ContractPlaceholder.INSURANCE_SALARY_BASE, assignment.isInsured()
                ? money(base.add(assignment.getKpiAllowance()).add(assignment.getResponsibilityAllowance())) : "Không đóng bảo hiểm");
    }

    private void requirePrintable(EmployeeProfile profile) {
        List<String> missing = new ArrayList<>();
        if (profile.getDateOfBirth() == null) {
            missing.add("dateOfBirth");
        }
        if (profile.getNationalIdNo() == null || profile.getNationalIdNo().isBlank()) {
            missing.add("nationalIdNo");
        }
        if (!missing.isEmpty()) {
            throw new AppException(ErrorCode.CONTRACT_PROFILE_INCOMPLETE,
                    "The employee profile lacks: " + String.join(", ", missing));
        }
    }

    private String address(EmployeeProfile profile) {
        String province = profile.getAddressProvinceCode() == null ? null
                : provinceRepository.findById(profile.getAddressProvinceCode()).map(Province::getProvinceName).orElse(null);
        return java.util.stream.Stream.of(profile.getAddressStreet(), profile.getAddressWard(), province)
                .filter(Objects::nonNull).filter(part -> !part.isBlank()).collect(Collectors.joining(", "));
    }

    private String contractKind(ContractKind kind) {
        if (kind == null) {
            return "";
        }
        return switch (kind) {
            case PROBATION -> "Thỏa thuận thử việc";
            case FIXED_TERM_FT -> "Hợp đồng xác định thời hạn";
            case INDEFINITE_FT -> "Hợp đồng không xác định thời hạn";
            case PART_TIME_AGREEMENT -> "Thỏa thuận làm việc bán thời gian";
            case SERVICE -> "Hợp đồng dịch vụ";
        };
    }

    private String date(LocalDate date) {
        return date == null ? "" : date.format(DATE);
    }

    private String money(BigDecimal amount) {
        return NumberFormat.getIntegerInstance(VIETNAM).format(amount) + " đồng";
    }

    private BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
