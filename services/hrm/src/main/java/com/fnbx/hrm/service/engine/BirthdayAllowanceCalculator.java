package com.fnbx.hrm.service.engine;

import com.fnbx.hrm.entity.EmployeeProfile;
import com.fnbx.hrm.entity.EmploymentAssignment;
import com.fnbx.hrm.entity.PayComponent;
import com.fnbx.hrm.entity.PayrollConfig;
import com.fnbx.hrm.entity.PayrollLine;
import com.fnbx.hrm.entity.PayrollLineItem;
import com.fnbx.hrm.entity.PayrollPeriod;
import com.fnbx.hrm.enums.AppliesTo;
import com.fnbx.hrm.enums.EmploymentType;
import com.fnbx.hrm.repository.EmployeeProfileRepository;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BirthdayAllowanceCalculator {

    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");

    private final EmployeeProfileRepository profileRepository;

    public List<PayrollLineItem> items(PayrollPeriod period, PayrollConfig config, List<PayrollLine> lines,
            Map<UUID, EmploymentAssignment> assignments, PayComponent component) {
        if (component == null || config.getBirthdayAllowanceAmount().signum() == 0) {
            return List.of();
        }
        Map<UUID, List<PayrollLine>> linesByStaff = linesByStaff(lines, assignments);
        return profileRepository.findAllById(linesByStaff.keySet()).stream()
                .filter(profile -> hasBirthdayIn(profile, period))
                .map(profile -> mainLine(linesByStaff.get(profile.getStaffId()), assignments)
                        .filter(line -> appliesTo(config.getBirthdayAllowanceAppliesTo(), line.getEmploymentType()))
                        .map(line -> item(line, component, config, profile)))
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    private boolean hasBirthdayIn(EmployeeProfile profile, PayrollPeriod period) {
        return profile.getDateOfBirth() != null && profile.getDateOfBirth().getMonthValue() == period.getPeriodMonth();
    }

    private boolean appliesTo(AppliesTo appliesTo, EmploymentType type) {
        return appliesTo == AppliesTo.BOTH || appliesTo.name().equals(type.name());
    }

    private Map<UUID, List<PayrollLine>> linesByStaff(List<PayrollLine> lines, Map<UUID, EmploymentAssignment> assignments) {
        return lines.stream()
                .filter(line -> assignments.get(line.getAssignmentId()) != null)
                .collect(Collectors.groupingBy(line -> assignments.get(line.getAssignmentId()).getStaffId()));
    }

    private java.util.Optional<PayrollLine> mainLine(List<PayrollLine> staffLines, Map<UUID, EmploymentAssignment> assignments) {
        return staffLines.stream().min(Comparator
                .comparing((PayrollLine line) -> assignments.get(line.getAssignmentId()).getEffectiveFrom())
                .thenComparing(PayrollLine::getPayrollLineId));
    }

    private PayrollLineItem item(PayrollLine line, PayComponent component, PayrollConfig config, EmployeeProfile profile) {
        PayrollLineItem item = new PayrollLineItem();
        item.setPayrollLineItemId(UUID.randomUUID());
        item.setBusinessId(line.getBusinessId());
        item.setPayrollLineId(line.getPayrollLineId());
        item.setComponentId(component.getPayComponentId());
        item.setAmount(config.getBirthdayAllowanceAmount());
        item.setCalcNote("Sinh nhật " + profile.getDateOfBirth().format(DAY_MONTH));
        return item;
    }
}
