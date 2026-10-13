package com.fnbx.hrm.service.scheduling;

import com.fnbx.hrm.dto.response.CandidateResponse;
import com.fnbx.hrm.entity.ShiftAssignment;
import com.fnbx.hrm.entity.ShiftSlot;
import com.fnbx.hrm.repository.ShiftAssignmentRepository;
import com.fnbx.identity.entity.Branch;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CandidateFinder {

    private static final int MINUTES_PER_HOUR = 60;

    private final SchedulingStaffLoader staffLoader;
    private final SchedulingLimitsProvider limitsProvider;
    private final ShiftAssignmentRepository assignmentRepository;
    private final WeeklyHours weeklyHours;
    private final EntityManager entityManager;

    public enum Status { FREE, BUSY, NO_SUBMISSION, OTHER_HOME, OTHER_BRANCH, ALREADY_SCHEDULED, NOT_QUALIFIED, ON_LEAVE }

    public List<CandidateResponse> find(ScheduleContext context, ShiftSlot slot, LocalDate date, UUID positionId, String search) {
        Map<UUID, SchedulingStaff> everyone = staffLoader.forBusiness(context.schedule().getBranchId(), context.schedule().getWeekStart());
        Map<UUID, Integer> minimum = limitsProvider.minMinutes(context.limits(), everyone, context.schedule().getWeekStart());
        Map<UUID, Integer> worked = weeklyHours.minutesByStaff(context.assignments());
        TimeWindow window = TimeWindow.of(slot);
        Map<UUID, List<ShiftAssignment>> sameDay = assignmentRepository.findByStaffIdInAndWorkDateBetween(everyone.keySet(), date, date).stream()
                .collect(Collectors.groupingBy(ShiftAssignment::getStaffId));
        return everyone.values().stream()
                .filter(person -> matches(person, search))
                .map(person -> candidate(context, person, slot, date, positionId, window, minimum, worked,
                        sameDay.getOrDefault(person.staffId(), List.of())))
                .sorted(Comparator.comparing((CandidateResponse c) -> order(c.status()))
                        .thenComparing(CandidateResponse::missingHours, Comparator.reverseOrder())
                        .thenComparing(CandidateResponse::nickname))
                .toList();
    }

    private CandidateResponse candidate(ScheduleContext context, SchedulingStaff person, ShiftSlot slot, LocalDate date,
            UUID positionId, TimeWindow window, Map<UUID, Integer> minimum, Map<UUID, Integer> worked,
            List<ShiftAssignment> assignmentsThatDay) {
        int assigned = worked.getOrDefault(person.staffId(), 0);
        BigDecimal missing = hours(Math.max(0, minimum.getOrDefault(person.staffId(), 0) - assigned));
        boolean qualified = person.canFill(positionId) && person.typeOn(date).filter(type -> type == slot.getEmploymentType()).isPresent();
        Optional<ShiftAssignment> workingElsewhere = assignmentsThatDay.stream()
                .filter(other -> !other.getBranchId().equals(context.schedule().getBranchId())).findFirst();
        Status status = statusOf(context, person, date, window, qualified, workingElsewhere.isPresent(), assignmentsThatDay);
        String note = status == Status.OTHER_BRANCH ? branchName(workingElsewhere.get()) : null;
        return new CandidateResponse(person.staffId(), person.displayName(), person.fullName(),
                person.typeOn(date).map(Enum::name).orElse(null), status.name(), isSelectable(status), note, hours(assigned), missing);
    }

    private Status statusOf(ScheduleContext context, SchedulingStaff person, LocalDate date, TimeWindow window, boolean qualified,
            boolean workingAtOtherBranch, List<ShiftAssignment> assignmentsThatDay) {
        if (!qualified) {
            return Status.NOT_QUALIFIED;
        }
        if (workingAtOtherBranch) {
            return Status.OTHER_BRANCH;
        }
        if (assignmentsThatDay.stream().anyMatch(other -> TimeWindow.of(other).overlaps(window))) {
            return Status.ALREADY_SCHEDULED;
        }
        Status registration = Status.valueOf(context.availability().statusOf(person.staffId(), date, window).name());
        boolean freeButElsewhereByDefault = registration == Status.FREE && !person.belongsTo(context.schedule().getBranchId());
        return freeButElsewhereByDefault ? Status.OTHER_HOME : registration;
    }

    private boolean isSelectable(Status status) {
        return switch (status) {
            case OTHER_BRANCH, ALREADY_SCHEDULED, NOT_QUALIFIED, ON_LEAVE -> false;
            default -> true;
        };
    }

    private String branchName(ShiftAssignment assignment) {
        return entityManager.find(Branch.class, assignment.getBranchId()).getBranchName();
    }

    private int order(String status) {
        return switch (Status.valueOf(status)) {
            case FREE -> 0;
            case OTHER_HOME -> 1;
            case BUSY, NO_SUBMISSION -> 2;
            default -> 3;
        };
    }

    private boolean matches(SchedulingStaff person, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String needle = search.toLowerCase(Locale.ROOT);
        return person.fullName().toLowerCase(Locale.ROOT).contains(needle) || person.displayName().toLowerCase(Locale.ROOT).contains(needle);
    }

    private BigDecimal hours(int minutes) {
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(MINUTES_PER_HOUR), 2, RoundingMode.HALF_UP);
    }
}
