package com.fnbx.hrm.service.contractimport;

import java.util.Arrays;
import java.util.Optional;

/** One column of the bulk contract workbook; {@code required} columns block a row when empty. */
public enum ContractImportColumn {
    EMPLOYEE_CODE("Mã nhân viên", true),
    GENDER("Giới tính", false),
    DATE_OF_BIRTH("Ngày sinh", true),
    NATIONAL_ID_NO("Số CCCD", true),
    NATIONAL_ID_ISSUED_ON("Ngày cấp CCCD", true),
    NATIONAL_ID_ISSUED_PLACE("Nơi cấp CCCD", true),
    ADDRESS_STREET("Số nhà, đường", false),
    ADDRESS_WARD("Phường, xã", false),
    ADDRESS_PROVINCE("Tỉnh, thành phố", false),
    PERSONAL_EMAIL("Email cá nhân", false),
    TAX_CODE("Mã số thuế", false),
    SOCIAL_INSURANCE_NO("Số BHXH", false),
    BANK("Ngân hàng", false),
    BANK_ACCOUNT_NO("Số tài khoản", false),
    BANK_ACCOUNT_NAME("Chủ tài khoản", false),
    RECRUITMENT_SOURCE("Nguồn tuyển dụng", false),
    MARITAL_STATUS("Tình trạng hôn nhân", false),
    CHILDREN_COUNT("Số con", false),
    ETHNICITY("Dân tộc", false),
    RELIGION("Tôn giáo", false),
    NATIONALITY("Quốc tịch", false),
    EDUCATION_LEVEL("Học vấn", false),
    EDUCATION_SCHOOL("Nơi đào tạo", false),
    EDUCATION_MAJOR("Chuyên ngành", false),
    GRADUATION_YEAR("Năm tốt nghiệp", false),
    EDUCATION_GRADE("Xếp loại", false),
    POSITION_CODE("Mã vị trí", true),
    JOB_LEVEL("Cấp bậc", false),
    BRANCH_CODE("Mã chi nhánh", true),
    EMPLOYMENT_TYPE("Loại công việc", true),
    CONTRACT_KIND("Loại hợp đồng", true),
    AGREED_MONTHLY_SALARY("Lương thỏa thuận", false),
    HOURLY_BASE_RATE("Đơn giá giờ", false),
    INSURANCE_BASE("Mức đóng bảo hiểm", false),
    RESPONSIBILITY_ALLOWANCE("Phụ cấp trách nhiệm", false),
    EFFECTIVE_FROM("Hiệu lực từ", true),
    EFFECTIVE_TO("Hiệu lực đến", false),
    PROBATION_RESULT("Kết quả thử việc", false);

    private final String label;
    private final boolean required;

    ContractImportColumn(String label, boolean required) {
        this.label = label;
        this.required = required;
    }

    public String label() {
        return label;
    }

    public boolean required() {
        return required;
    }

    public static Optional<ContractImportColumn> byLabel(String header) {
        String wanted = header == null ? "" : header.trim().replace("*", "").trim();
        return Arrays.stream(values()).filter(column -> column.label.equalsIgnoreCase(wanted)).findFirst();
    }
}
