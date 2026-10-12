package com.fnbx.hrm.service.contractdoc;

import static org.assertj.core.api.Assertions.assertThat;

import com.fnbx.hrm.entity.EmploymentAssignment;
import com.fnbx.hrm.enums.ContractKind;
import com.fnbx.hrm.enums.EmploymentType;
import org.junit.jupiter.api.Test;

class ContractPrintabilityTest {

    private static EmploymentAssignment contract(EmploymentType type, ContractKind kind) {
        EmploymentAssignment assignment = new EmploymentAssignment();
        assignment.setEmploymentType(type);
        assignment.setContractKind(kind);
        return assignment;
    }

    @Test
    void fullTimeLabourAndProbationContractsHaveAPrintedPaper() {
        assertThat(ContractPrintability.isPrintable(contract(EmploymentType.FULLTIME, ContractKind.FIXED_TERM_FT))).isTrue();
        assertThat(ContractPrintability.isPrintable(contract(EmploymentType.FULLTIME, ContractKind.INDEFINITE_FT))).isTrue();
        assertThat(ContractPrintability.isPrintable(contract(EmploymentType.FULLTIME, ContractKind.PROBATION))).isTrue();
    }

    @Test
    void aContractWithoutAKindIsTreatedAsALabourContract() {
        assertThat(ContractPrintability.isPrintable(contract(EmploymentType.FULLTIME, null))).isTrue();
    }

    @Test
    void partTimeAndServiceContractsHaveNone() {
        assertThat(ContractPrintability.isPrintable(contract(EmploymentType.PARTTIME, ContractKind.FIXED_TERM_FT))).isFalse();
        assertThat(ContractPrintability.isPrintable(contract(EmploymentType.PARTTIME, ContractKind.PART_TIME_AGREEMENT))).isFalse();
        assertThat(ContractPrintability.isPrintable(contract(EmploymentType.FULLTIME, ContractKind.SERVICE))).isFalse();
    }
}
