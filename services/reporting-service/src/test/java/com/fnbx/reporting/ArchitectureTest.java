package com.fnbx.reporting;

import com.fnbx.archtest.LayeredArchitectureRules;
import com.fnbx.archtest.ServiceBoundaryRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Chu y: dung {@code forReadOnlyService}, khong phai {@code forService}.
 *
 * <p>reporting-service la NGOAI LE da ghi thanh van cua ADR-0003 quyet dinh #11:
 * bo tang {@code service} de tranh Architecture Sinkhole. Luat "controller
 * khong goi thang repository" khong ap dung o day — va viec no khong ap dung
 * la MOT QUYET DINH, khong phai su lo la.
 */
class ArchitectureTest {

    private final LayeredArchitectureRules layered =
            LayeredArchitectureRules.forReadOnlyService("com.fnbx.reporting");

    @Test
    @DisplayName("Controller ĐƯỢC goi thang repository — ngoai le cua reporting")
    void controllerDuocGoiThangRepository() {
        layered.controllerMustNotTouchRepository();   // no-op cho read-only service
    }

    @Test void repositoryKhongGoiNguocLenService() { layered.repositoryMustNotDependOnService(); }

    @Test
    @DisplayName("reporting khong duoc phu thuoc hanh vi cua service khac")
    void khongPhuThuocHanhViServiceKhac() {
        ServiceBoundaryRules.serviceMustNotImportAnotherServiceBehaviour("reporting");
    }
}
