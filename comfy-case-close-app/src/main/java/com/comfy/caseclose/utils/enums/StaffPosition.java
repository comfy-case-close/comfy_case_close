package com.comfy.caseclose.utils.enums;

import lombok.Getter;

@Getter
public enum StaffPosition {
    HEAD_OF_HR("Head of HR"),
    HEAD_OF_ADMINISTRATION("Head of Administration"),
    HEAD_OF_BRAND_CULTURE_AND_TRAINING("Head of Brand Culture & Training"),
    LEGAL_SUPERVISOR("Legal Supervisor"),
    PASTRY_CHEF("Pastry Chef"),
    HR_MANAGER("HR Manager"),
    C_AND_B_SPECIALIST("C&B Specialist"),
    RECRUITMENT_AND_TRAINING_EXECUTIVE("Recruitment & Training Executive"),
    GUEST_EXPERIENCE_EXECUTIVE("Guest Experience Executive"),
    OPERATIONS_MANAGER("Operations Manager"),
    STORE_MANAGER("Store Manager"),
    SHIFT_LEADER("Shift Leader"),
    CASHIER("Cashier"),
    SERVICE_STAFF("Service Staff"),
    BARISTA("Barista"),
    SHIFT_LEADER_TRAINEE("Shift Leader Trainee"),
    CASHIER_TRAINEE("Cashier Trainee"),
    SERVICE_STAFF_TRAINEE("Service Staff Trainee"),
    BARISTA_TRAINEE("Barista Trainee"),
    HEAD_OF_PRODUCT("Head of Product"),
    R_AND_D_EXECUTIVE("R&D Executive"),
    SUPPLY_CHAIN_MANAGER("Supply Chain Manager"),
    QC_TESTER("QC Tester"),
    WAREHOUSE_CHECKER("Warehouse Checker"),
    HEAD_OF_MARKETING("Head of Marketing"),
    ACCOUNT_EXECUTIVE("Account Executive"),
    HEAD_OF_FINANCE("Head of Finance"),
    ACCOUNTANT("Accountant");

    private final String displayTitle;

    StaffPosition(String displayTitle) {
        this.displayTitle = displayTitle;
    }
}
