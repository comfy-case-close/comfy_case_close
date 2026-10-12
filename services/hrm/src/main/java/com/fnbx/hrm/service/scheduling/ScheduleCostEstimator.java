package com.fnbx.hrm.service.scheduling;

import com.fnbx.hrm.entity.EmploymentAssignment;
import com.fnbx.hrm.entity.PayrollConfig;
import com.fnbx.hrm.enums.EmploymentType;
import com.fnbx.hrm.repository.EmploymentAssignmentRepository;
import com.fnbx.hrm.repository.PayrollConfigRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Hourly cost of a person from their contract, used only for the planning estimate of a schedule. */
@Component
@RequiredArgsConstructor
public class ScheduleCostEstimator {

    private final EmploymentAssignmentRepository assignmentRepository;
    private final PayrollConfigRepository configRepository;

    public Optional<BigDecimal> hourlyCost(UUID staffId, LocalDate date) {
        return assignmentRepository.findEffectiveForStaff(List.of(staffId), date).stream()
                .min(Comparator.comparing(EmploymentAssignment::getEffectiveFrom))
                .flatMap(contract -> hourly(contract, date));
    }

    private Optional<BigDecimal> hourly(EmploymentAssignment contract, LocalDate date) {
        if (contract.getEmploymentType() == EmploymentType.PARTTIME) {
            return Optional.ofNullable(contract.getHourlyBaseRate());
        }
        return configRepository.findCurrent(date).map(config -> fullTimeHourly(contract, config));
    }

    private BigDecimal fullTimeHourly(EmploymentAssignment contract, PayrollConfig config) {
        BigDecimal monthly = contract.getMonthlyBaseSalary().add(contract.getSupplementAllowance());
        BigDecimal hoursPerMonth = config.getStandardDaysPerMonth().multiply(config.getStandardHoursPerDay());
        return monthly.divide(hoursPerMonth, 0, RoundingMode.HALF_UP);
    }
}
