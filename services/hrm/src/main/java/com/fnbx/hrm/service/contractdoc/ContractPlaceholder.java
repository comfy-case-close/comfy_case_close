package com.fnbx.hrm.service.contractdoc;

import java.util.Arrays;
import java.util.List;

public enum ContractPlaceholder {
    FULL_NAME("fullName", "Họ và tên", "Nguyễn Văn An"),
    DATE_OF_BIRTH("dateOfBirth", "Ngày sinh", "01/01/2000"),
    NATIONAL_ID_NO("nationalIdNo", "Số CCCD", "079200000001"),
    NATIONAL_ID_ISSUED_ON("nationalIdIssuedOn", "Ngày cấp CCCD", "15/03/2021"),
    NATIONAL_ID_ISSUED_PLACE("nationalIdIssuedPlace", "Nơi cấp CCCD", "Cục Cảnh sát QLHC về TTXH"),
    ADDRESS("address", "Địa chỉ", "12 Võ Văn Ngân, Thủ Đức, TP. Hồ Chí Minh"),
    PHONE("phone", "Số điện thoại", "0900000000"),
    POSITION("position", "Chức vụ", "Phục vụ"),
    BRANCH("branch", "Chi nhánh", "Comfy Thủ Đức"),
    JOB_LEVEL("jobLevel", "Cấp bậc", "Junior"),
    EMPLOYMENT_TYPE("employmentType", "Loại công việc", "Toàn thời gian"),
    CONTRACT_KIND("contractKind", "Loại hợp đồng", "Hợp đồng xác định thời hạn"),
    BASE_SALARY("baseSalary", "Lương cơ bản", "5.000.000 đồng"),
    SUPPLEMENT_ALLOWANCE("supplementAllowance", "Phụ cấp bù lương", "3.500.000 đồng"),
    FIXED_TOTAL("fixedTotal", "Tổng thu nhập cố định", "8.500.000 đồng"),
    HOURLY_RATE("hourlyRate", "Đơn giá giờ", "25.000 đồng"),
    INSURANCE_SALARY_BASE("insuranceSalaryBase", "Mức đóng bảo hiểm", "5.000.000 đồng"),
    RESPONSIBILITY_ALLOWANCE("responsibilityAllowance", "Phụ cấp trách nhiệm", "500.000 đồng"),
    EFFECTIVE_FROM("effectiveFrom", "Hiệu lực từ", "01/10/2026"),
    EFFECTIVE_TO("effectiveTo", "Hiệu lực đến", "30/09/2027"),
    CONTRACT_NO("contractNo", "Số hợp đồng", "HD-2026-10-0001"),
    TODAY("today", "Ngày in", "04/10/2026"),
    SIGN_DATE("signDate", "Ngày ký", "04/10/2026"),
    HONORIFIC("honorific", "Ông/Bà", "Ông"),
    FULL_NAME_UPPER("fullNameUpper", "Họ và tên (in hoa)", "NGUYỄN VĂN AN"),
    GENDER_TEXT("genderText", "Giới tính", "Nam"),
    BRANCH_ADDRESS("branchAddress", "Địa chỉ chi nhánh", "12 Võ Văn Ngân, Thủ Đức, TP. Hồ Chí Minh"),
    BASE_SALARY_WORDS("baseSalaryWords", "Lương cơ bản bằng chữ", "Năm triệu đồng"),
    SUPPLEMENT_ALLOWANCE_WORDS("supplementAllowanceWords", "Phụ cấp bù lương bằng chữ", "Ba triệu năm trăm nghìn đồng"),
    CONTRACT_PERIOD("contractPeriod", "Thời hạn hợp đồng", "Từ ngày 01/10/2026 đến hết ngày 30/09/2027"),
    CONTRACT_TERM_TYPE("contractTermType", "Loại thời hạn hợp đồng", "xác định thời hạn 12 (mười hai) tháng"),
    PROBATION_DAYS("probationDays", "Số ngày thử việc", "60 (sáu mươi)"),
    LABOR_CONTRACT_START("laborContractStart", "Ngày bắt đầu hợp đồng lao động sau thử việc", "01/12/2026"),
    LUNCH_ALLOWANCE("lunchAllowance", "Phụ cấp ăn trưa (phụ cấp cá nhân)", "800.000 đồng"),
    LUNCH_ALLOWANCE_WORDS("lunchAllowanceWords", "Phụ cấp ăn trưa bằng chữ", "Tám trăm nghìn đồng"),
    HOUSING_ALLOWANCE("housingAllowance", "Phụ cấp thuê nhà (phụ cấp cá nhân)", "1.000.000 đồng"),
    HOUSING_ALLOWANCE_WORDS("housingAllowanceWords", "Phụ cấp thuê nhà bằng chữ", "Một triệu đồng"),
    PHONE_ALLOWANCE("phoneAllowance", "Phụ cấp điện thoại (phụ cấp cá nhân)", "200.000 đồng"),
    PHONE_ALLOWANCE_WORDS("phoneAllowanceWords", "Phụ cấp điện thoại bằng chữ", "Hai trăm nghìn đồng"),
    FUEL_ALLOWANCE("fuelAllowance", "Phụ cấp xăng xe (phụ cấp cá nhân)", "300.000 đồng"),
    FUEL_ALLOWANCE_WORDS("fuelAllowanceWords", "Phụ cấp xăng xe bằng chữ", "Ba trăm nghìn đồng"),
    RESPONSIBILITY_ALLOWANCE_WORDS("responsibilityAllowanceWords", "Phụ cấp trách nhiệm bằng chữ", "Năm trăm nghìn đồng");

    private final String key;
    private final String description;
    private final String sample;

    ContractPlaceholder(String key, String description, String sample) {
        this.key = key;
        this.description = description;
        this.sample = sample;
    }

    public String key() {
        return key;
    }

    public String description() {
        return description;
    }

    public String sample() {
        return sample;
    }

    public static java.util.Optional<ContractPlaceholder> byKey(String key) {
        return Arrays.stream(values()).filter(placeholder -> placeholder.key.equals(key)).findFirst();
    }

    public static List<ContractPlaceholder> all() {
        return Arrays.asList(values());
    }
}
