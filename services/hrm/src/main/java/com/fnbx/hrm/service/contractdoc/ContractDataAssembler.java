package com.fnbx.hrm.service.contractdoc;

import com.fnbx.hrm.config.HrmContractProperties;
import com.fnbx.hrm.entity.BranchSetting;
import com.fnbx.hrm.entity.EmployeeAllowance;
import com.fnbx.hrm.entity.EmployeeProfile;
import com.fnbx.hrm.entity.EmploymentAssignment;
import com.fnbx.hrm.entity.Province;
import com.fnbx.hrm.enums.ContractKind;
import com.fnbx.hrm.enums.EmploymentType;
import com.fnbx.hrm.enums.Gender;
import com.fnbx.hrm.repository.BranchSettingRepository;
import com.fnbx.hrm.repository.EmployeeAllowanceRepository;
import com.fnbx.hrm.repository.EmployeeProfileRepository;
import com.fnbx.hrm.repository.ProvinceRepository;
import com.fnbx.identity.entity.Branch;
import com.fnbx.identity.entity.Staff;
import com.fnbx.identity.entity.StaffPosition;
import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContractDataAssembler {

    private static final String OPEN_ENDED_TEXT = "Không xác định thời hạn";
    private static final String INDEFINITE_TERM_TEXT = "không xác định thời hạn";
    private static final String FIXED_TERM_TEXT = "xác định thời hạn";
    private static final String UNINSURED_TEXT = "Không đóng bảo hiểm";

    private final EmployeeProfileRepository profileRepository;
    private final ProvinceRepository provinceRepository;
    private final BranchSettingRepository branchSettingRepository;
    private final EmployeeAllowanceRepository allowanceRepository;
    private final HrmContractProperties contractProperties;
    private final EntityManager entityManager;

    public Map<ContractPlaceholder, String> assemble(EmploymentAssignment assignment, String contractNo) {
        Staff staff = entityManager.find(Staff.class, assignment.getStaffId());
        EmployeeProfile profile = profileRepository.findById(assignment.getStaffId())
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_PROFILE_INCOMPLETE));
        Branch branch = entityManager.find(Branch.class, assignment.getDefaultBranchId());
        String address = addressOf(profile);
        String workplaceAddress = workplaceAddressOf(branch);
        requirePrintable(profile, assignment, branch, address, workplaceAddress);

        Map<ContractPlaceholder, String> values = new EnumMap<>(ContractPlaceholder.class);
        putPerson(values, staff, profile, address);
        putWorkplace(values, assignment, branch, workplaceAddress);
        putPay(values, assignment);
        putTerm(values, assignment);
        putIssuance(values, contractNo);
        return values;
    }

    private void putPerson(Map<ContractPlaceholder, String> values, Staff staff, EmployeeProfile profile, String address) {
        String fullName = staff.getFirstName() + " " + staff.getLastName();
        values.put(ContractPlaceholder.FULL_NAME, fullName);
        values.put(ContractPlaceholder.FULL_NAME_UPPER, fullName.toUpperCase(ContractFormats.VIETNAM));
        values.put(ContractPlaceholder.HONORIFIC, honorific(profile.getGender()));
        values.put(ContractPlaceholder.GENDER_TEXT, genderText(profile.getGender()));
        values.put(ContractPlaceholder.DATE_OF_BIRTH, ContractFormats.date(profile.getDateOfBirth()));
        values.put(ContractPlaceholder.NATIONAL_ID_NO, profile.getNationalIdNo());
        values.put(ContractPlaceholder.NATIONAL_ID_ISSUED_ON, ContractFormats.date(profile.getNationalIdIssuedOn()));
        values.put(ContractPlaceholder.NATIONAL_ID_ISSUED_PLACE, profile.getNationalIdIssuedPlace());
        values.put(ContractPlaceholder.ADDRESS, address);
        values.put(ContractPlaceholder.PHONE, staff.getPhone());
    }

    private void putWorkplace(Map<ContractPlaceholder, String> values, EmploymentAssignment assignment, Branch branch, String workplaceAddress) {
        values.put(ContractPlaceholder.POSITION, entityManager.find(StaffPosition.class, assignment.getPositionId()).getPositionName());
        values.put(ContractPlaceholder.BRANCH, branch.getBranchName());
        values.put(ContractPlaceholder.BRANCH_ADDRESS, workplaceAddress);
        values.put(ContractPlaceholder.JOB_LEVEL, assignment.getJobLevel() == null ? "" : assignment.getJobLevel().name());
        values.put(ContractPlaceholder.EMPLOYMENT_TYPE, assignment.getEmploymentType() == EmploymentType.FULLTIME ? "Toàn thời gian" : "Bán thời gian");
        values.put(ContractPlaceholder.CONTRACT_KIND, contractKindText(assignment.getContractKind()));
    }

    private void putPay(Map<ContractPlaceholder, String> values, EmploymentAssignment assignment) {
        BigDecimal base = ContractFormats.orZero(assignment.getMonthlyBaseSalary());
        BigDecimal supplement = ContractFormats.orZero(assignment.getSupplementAllowance());
        BigDecimal responsibility = ContractFormats.orZero(assignment.getResponsibilityAllowance());
        EmployeeAllowance personal = personalAllowanceOf(assignment);
        BigDecimal lunch = ContractFormats.orZero(personal.getLunchAllowance());
        BigDecimal housing = ContractFormats.orZero(personal.getHousingAllowance());
        BigDecimal phone = ContractFormats.orZero(personal.getPhoneAllowance());
        BigDecimal fuel = ContractFormats.orZero(personal.getFuelAllowance());

        values.put(ContractPlaceholder.BASE_SALARY, ContractFormats.money(base));
        values.put(ContractPlaceholder.BASE_SALARY_WORDS, VietnameseNumberWords.money(base));
        putAllowance(values, ContractPlaceholder.LUNCH_ALLOWANCE, ContractPlaceholder.LUNCH_ALLOWANCE_WORDS, lunch, true);
        putAllowance(values, ContractPlaceholder.HOUSING_ALLOWANCE, ContractPlaceholder.HOUSING_ALLOWANCE_WORDS, housing, false);
        putAllowance(values, ContractPlaceholder.PHONE_ALLOWANCE, ContractPlaceholder.PHONE_ALLOWANCE_WORDS, phone, false);
        putAllowance(values, ContractPlaceholder.FUEL_ALLOWANCE, ContractPlaceholder.FUEL_ALLOWANCE_WORDS, fuel, false);
        putAllowance(values, ContractPlaceholder.SUPPLEMENT_ALLOWANCE, ContractPlaceholder.SUPPLEMENT_ALLOWANCE_WORDS, supplement, false);
        putAllowance(values, ContractPlaceholder.RESPONSIBILITY_ALLOWANCE, ContractPlaceholder.RESPONSIBILITY_ALLOWANCE_WORDS, responsibility, false);
        BigDecimal fixedTotal = base.add(lunch).add(housing).add(phone).add(fuel).add(supplement).add(responsibility);
        values.put(ContractPlaceholder.FIXED_TOTAL, ContractFormats.money(fixedTotal));
        values.put(ContractPlaceholder.HOURLY_RATE, assignment.getHourlyBaseRate() == null ? "" : ContractFormats.money(assignment.getHourlyBaseRate()));
        values.put(ContractPlaceholder.INSURANCE_SALARY_BASE, insuranceBaseText(assignment, base, responsibility));
    }

    private void putAllowance(Map<ContractPlaceholder, String> values, ContractPlaceholder amount,
            ContractPlaceholder words, BigDecimal value, boolean wordsEvenWhenZero) {
        values.put(amount, ContractFormats.money(value));
        boolean omitted = value.signum() == 0 && !wordsEvenWhenZero;
        values.put(words, omitted ? "" : VietnameseNumberWords.money(value));
    }

    private EmployeeAllowance personalAllowanceOf(EmploymentAssignment assignment) {
        LocalDate asOf = assignment.getEffectiveFrom().isAfter(LocalDate.now()) ? assignment.getEffectiveFrom() : LocalDate.now();
        return allowanceRepository.findEffective(assignment.getStaffId(), asOf).orElseGet(EmployeeAllowance::new);
    }

    private void putTerm(Map<ContractPlaceholder, String> values, EmploymentAssignment assignment) {
        LocalDate from = assignment.getEffectiveFrom();
        LocalDate to = assignment.getEffectiveTo();
        values.put(ContractPlaceholder.EFFECTIVE_FROM, ContractFormats.date(from));
        values.put(ContractPlaceholder.EFFECTIVE_TO, to == null ? OPEN_ENDED_TEXT : ContractFormats.date(to));
        values.put(ContractPlaceholder.CONTRACT_PERIOD, periodText(from, to));
        values.put(ContractPlaceholder.CONTRACT_TERM_TYPE, termTypeText(assignment));
        if (to != null) {
            values.put(ContractPlaceholder.PROBATION_DAYS, VietnameseNumberWords.countWithWords(ChronoUnit.DAYS.between(from, to) + 1));
            values.put(ContractPlaceholder.LABOR_CONTRACT_START, ContractFormats.date(to.plusDays(1)));
        }
    }

    private void putIssuance(Map<ContractPlaceholder, String> values, String contractNo) {
        String today = ContractFormats.date(LocalDate.now());
        values.put(ContractPlaceholder.CONTRACT_NO, contractNo);
        values.put(ContractPlaceholder.TODAY, today);
        values.put(ContractPlaceholder.SIGN_DATE, today);
    }

    private String periodText(LocalDate from, LocalDate to) {
        String start = "Từ ngày " + ContractFormats.date(from);
        return to == null ? start : start + " đến hết ngày " + ContractFormats.date(to);
    }

    private String termTypeText(EmploymentAssignment assignment) {
        ContractKind kind = assignment.getContractKind();
        if (!isFixedTerm(assignment)) {
            return kind == null || kind == ContractKind.INDEFINITE_FT ? INDEFINITE_TERM_TEXT : "";
        }
        Period span = Period.between(assignment.getEffectiveFrom(), assignment.getEffectiveTo().plusDays(1));
        boolean wholeMonths = span.getDays() == 0 && span.toTotalMonths() > 0;
        return wholeMonths
                ? FIXED_TERM_TEXT + " " + VietnameseNumberWords.countWithWords(span.toTotalMonths()) + " tháng"
                : FIXED_TERM_TEXT;
    }

    private boolean isFixedTerm(EmploymentAssignment assignment) {
        ContractKind kind = assignment.getContractKind();
        return kind == null ? assignment.getEffectiveTo() != null : kind == ContractKind.FIXED_TERM_FT;
    }

    private String insuranceBaseText(EmploymentAssignment assignment, BigDecimal base, BigDecimal responsibility) {
        if (!assignment.isInsured()) {
            return UNINSURED_TEXT;
        }
        return ContractFormats.money(base.add(ContractFormats.orZero(assignment.getKpiAllowance())).add(responsibility));
    }

    private void requirePrintable(EmployeeProfile profile, EmploymentAssignment assignment, Branch branch, String address, String workplaceAddress) {
        List<String> missing = new ArrayList<>();
        addIf(missing, profile.getDateOfBirth() == null, "dateOfBirth");
        addIf(missing, profile.getGender() == null, "gender");
        addIf(missing, ContractFormats.isBlank(profile.getNationalIdNo()), "nationalIdNo");
        addIf(missing, profile.getNationalIdIssuedOn() == null, "nationalIdIssuedOn");
        addIf(missing, ContractFormats.isBlank(profile.getNationalIdIssuedPlace()), "nationalIdIssuedPlace");
        addIf(missing, address.isBlank(), "address");
        addIf(missing, ContractFormats.isBlank(workplaceAddress), "branchAddress(" + branch.getBranchName() + ")");
        addIf(missing, assignment.getMonthlyBaseSalary() == null, "monthlyBaseSalary");
        addIf(missing, needsEndDate(assignment) && assignment.getEffectiveTo() == null, "effectiveTo");
        if (!missing.isEmpty()) {
            throw new AppException(ErrorCode.CONTRACT_PROFILE_INCOMPLETE, "The contract lacks: " + String.join(", ", missing));
        }
    }

    private void addIf(List<String> missing, boolean isMissing, String field) {
        if (isMissing) {
            missing.add(field);
        }
    }

    private boolean needsEndDate(EmploymentAssignment assignment) {
        return assignment.getContractKind() == ContractKind.PROBATION || assignment.getContractKind() == ContractKind.FIXED_TERM_FT;
    }

    private String workplaceAddressOf(Branch branch) {
        if (!ContractFormats.isBlank(branch.getAddress())) {
            return branch.getAddress();
        }
        boolean sharedPool = branchSettingRepository.findById(branch.getBranchId()).map(BranchSetting::isSharedPool).orElse(false);
        return sharedPool ? contractProperties.headOfficeAddress() : null;
    }

    private String addressOf(EmployeeProfile profile) {
        String province = profile.getAddressProvinceCode() == null ? null
                : provinceRepository.findById(profile.getAddressProvinceCode()).map(Province::getProvinceName).orElse(null);
        return Stream.of(profile.getAddressStreet(), profile.getAddressWard(), province)
                .filter(Objects::nonNull)
                .filter(part -> !part.isBlank())
                .collect(Collectors.joining(", "));
    }

    private String honorific(Gender gender) {
        return switch (gender) {
            case MALE -> "Ông";
            case FEMALE -> "Bà";
            case OTHER -> "Ông/Bà";
        };
    }

    private String genderText(Gender gender) {
        return switch (gender) {
            case MALE -> "Nam";
            case FEMALE -> "Nữ";
            case OTHER -> "Khác";
        };
    }

    private String contractKindText(ContractKind kind) {
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
}
