package com.fnbx.cashclose;

import com.fnbx.archtest.LayeredArchitectureRules;
import com.fnbx.archtest.ServiceBoundaryRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Luat kien truc chay MOI BUILD.
 *
 * <p>Day khong phai quy uoc trong tai lieu — la test. Vi pham thi CI do,
 * khong merge duoc. ADR-0003 muc 11.5 va 12.11.
 */
class ArchitectureTest {

    private static final String PKG = "com.fnbx.cashclose";

    private final LayeredArchitectureRules layered = LayeredArchitectureRules.forService(PKG);

    @Test
    @DisplayName("Controller khong duoc goi thang repository")
    void controllerKhongGoiThangRepository() {
        layered.controllerMustNotTouchRepository();
    }

    @Test
    @DisplayName("Repository khong duoc goi nguoc len service")
    void repositoryKhongGoiNguocLenService() {
        layered.repositoryMustNotDependOnService();
    }

    @Test
    @DisplayName("Controller chi noi chuyen bang DTO, khong lo entity ra API")
    void controllerChiNoiChuyenBangDto() {
        layered.controllerMustNotExposeEntity();
    }

    @Test
    @DisplayName("cashclose khong duoc phu thuoc HANH VI cua service khac")
    void khongPhuThuocHanhViServiceKhac() {
        // Doc ENTITY cua identity/platform/files/integration thi duoc — do la
        // thu vien federated. Nhung khong duoc import service/controller/
        // repository cua chung. Phu thuoc cheo lam chi phi tach repo tang tu
        // 2 ngay len 2 thang.
        ServiceBoundaryRules.serviceMustNotImportAnotherServiceBehaviour("cashclose");
    }

    @Test
    @DisplayName("cashclose khong duoc ghi vao schema cua service khac")
    void khongGhiVaoSchemaServiceKhac() {
        ServiceBoundaryRules.mustNotWriteToForeignSchema("cashclose");
    }
}
