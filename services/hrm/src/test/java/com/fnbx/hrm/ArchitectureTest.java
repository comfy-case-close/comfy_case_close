package com.fnbx.hrm;

import com.fnbx.archtest.LayeredArchitectureRules;
import com.fnbx.archtest.ServiceBoundaryRules;
import org.junit.jupiter.api.Test;

/** Luat kien truc chay moi build. ADR-0003 muc 11.5 va 12.11. */
class ArchitectureTest {

    private final LayeredArchitectureRules layered =
            LayeredArchitectureRules.forService("com.fnbx.hrm");

    @Test void controllerKhongGoiThangRepository() { layered.controllerMustNotTouchRepository(); }
    @Test void repositoryKhongGoiNguocLenService() { layered.repositoryMustNotDependOnService(); }
    @Test void controllerChiNoiChuyenBangDto()     { layered.controllerMustNotExposeEntity(); }

    @Test void khongPhuThuocHanhViServiceKhac() {
        ServiceBoundaryRules.serviceMustNotImportAnotherServiceBehaviour("hrm");
    }
    @Test void khongGhiVaoSchemaServiceKhac() {
        ServiceBoundaryRules.mustNotWriteToForeignSchema("hrm");
    }
}
