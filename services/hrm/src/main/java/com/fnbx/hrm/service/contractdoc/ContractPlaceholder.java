package com.fnbx.hrm.service.contractdoc;

import java.util.Arrays;
import java.util.List;

/** The variables a contract template may use, written {@code {{key}}} in the template body. */
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
    TODAY("today", "Ngày in", "04/10/2026");

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

    public static List<ContractPlaceholder> all() {
        return Arrays.asList(values());
    }
}
