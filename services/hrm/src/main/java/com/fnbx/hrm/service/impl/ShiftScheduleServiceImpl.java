package com.fnbx.hrm.service.impl;

import com.fnbx.hrm.dto.request.ScheduleRequests;
import com.fnbx.hrm.dto.response.AssignmentChangeResponse;
import com.fnbx.hrm.dto.response.CandidateResponse;
import com.fnbx.hrm.dto.response.ScheduleIssueResponse;
import com.fnbx.hrm.dto.response.ScheduleSummaryResponse;
import com.fnbx.hrm.dto.response.ScheduleSummaryTotalsResponse;
import com.fnbx.hrm.dto.response.ScheduleSummaryTotalsResponse.PersonTotal;
import com.fnbx.hrm.dto.response.ScheduleSummaryTotalsResponse.PositionTotal;
import com.fnbx.hrm.dto.response.ShiftScheduleResponse;
import com.fnbx.hrm.entity.ShiftAssignment;
import com.fnbx.hrm.entity.ShiftAssignmentEvent;
import com.fnbx.hrm.entity.ShiftSchedule;
import com.fnbx.hrm.enums.ScheduleStatus;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.repository.ShiftAssignmentEventRepository;
import com.fnbx.hrm.repository.ShiftScheduleRepository;
import com.fnbx.hrm.service.ShiftScheduleService;
import com.fnbx.hrm.service.scheduling.CandidateFinder;
import com.fnbx.hrm.service.scheduling.ScheduleAccess;
import com.fnbx.hrm.service.scheduling.ScheduleContext;
import com.fnbx.hrm.service.scheduling.ScheduleContextLoader;
import com.fnbx.hrm.service.scheduling.ScheduleCopier;
import com.fnbx.hrm.service.scheduling.ScheduleCostEstimator;
import com.fnbx.hrm.service.scheduling.ScheduleIssueFinder;
import com.fnbx.hrm.service.scheduling.ScheduleLoader;
import com.fnbx.hrm.service.scheduling.ScheduleStateRules;
import com.fnbx.hrm.service.scheduling.ScheduleViewAssembler;
import com.fnbx.hrm.service.scheduling.SchedulingLimitsProvider;
import com.fnbx.hrm.service.scheduling.SchedulingStaff;
import com.fnbx.hrm.service.scheduling.SchedulingStaffLoader;
import com.fnbx.hrm.service.scheduling.TimeWindow;
import com.fnbx.hrm.service.scheduling.WeekCalendar;
import com.fnbx.hrm.service.scheduling.WeeklyHours;
import com.fnbx.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShiftScheduleServiceImpl implements ShiftScheduleService {

    private static final int MINUTES_PER_HOUR = 60;

    private final ShiftScheduleRepository scheduleRepository;
    private final ShiftAssignmentEventRepository eventRepository;
    private final ScheduleLoader scheduleLoader;
    private final ScheduleContextLoader contextLoader;
    private final ScheduleViewAssembler viewAssembler;
    private final ScheduleIssueFinder issueFinder;
    private final CandidateFinder candidateFinder;
    private final ScheduleCopier copier;
    private final ScheduleCostEstimator costEstimator;
    private final SchedulingLimitsProvider limitsProvider;
    private final SchedulingStaffLoader staffLoader;
    private final WeeklyHours weeklyHours;
    private final ScheduleAccess access;

    @Override
    @Transactional
    public ShiftScheduleResponse create(ScheduleRequests.Create request) {
        access.requireEdit(request.branchId());
        LocalDate weekStart = WeekCalendar.requireMonday(request.weekStart());
        if (scheduleRepository.findByBranchIdAndWeekStart(request.branchId(), weekStart).isPresent()) {
            throw PayrollExceptions.resourceConflict("A schedule already exists for this branch and week");
        }
        ShiftSchedule schedule = new ShiftSchedule();
        schedule.setShiftScheduleId(UUID.randomUUID());
        schedule.setBusinessId(TenantContext.current().businessId());
        schedule.setBranchId(request.branchId());
        schedule.setWeekStart(weekStart);
        schedule.setStatus(ScheduleStatus.DRAFT);
        scheduleRepository.saveAndFlush(schedule);
        if (request.copyFromWeekStart() != null) {
            copier.copy(schedule, WeekCalendar.requireMonday(request.copyFromWeekStart()));
        }
        return viewAssembler.assemble(contextLoader.load(schedule));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScheduleSummaryResponse> list(UUID branchId, LocalDate weekStart, ScheduleStatus status) {
        Set<UUID> branches = branchId == null ? access.readableBranches() : Set.of(branchId);
        if (branchId != null) {
            access.requireRead(branchId);
        }
        if (branches.isEmpty()) {
            return List.of();
        }
        return scheduleRepository.search(branches, weekStart, status == null ? null : status.name()).stream()
                .map(this::toSummary).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ShiftScheduleResponse get(UUID scheduleId) {
        ShiftSchedule schedule = scheduleLoader.require(scheduleId);
        access.requireRead(schedule.getBranchId());
        return viewAssembler.assemble(contextLoader.load(schedule));
    }

    @Override
    @Transactional
    public ShiftScheduleResponse submit(UUID scheduleId, ScheduleRequests.Submit request) {
        ShiftSchedule schedule = scheduleLoader.require(scheduleId);
        access.requireEdit(schedule.getBranchId());
        scheduleLoader.requireVersion(schedule, request.expectedVersion());
        ScheduleStateRules.requireStatus(schedule.getStatus(), ScheduleStatus.DRAFT, "be submitted");
        schedule.setStatus(ScheduleStatus.PENDING_APPROVAL);
        schedule.setSubmittedBy(TenantContext.current().userId());
        schedule.setSubmittedAt(Instant.now());
        schedule.setSubmitNote(request.note());
        schedule.setReturnReason(null);
        return saveAndAssemble(schedule);
    }

    @Override
    @Transactional
    public ShiftScheduleResponse approve(UUID scheduleId, ScheduleRequests.Approve request) {
        ShiftSchedule schedule = scheduleLoader.require(scheduleId);
        access.requireApprove(schedule.getBranchId());
        scheduleLoader.requireVersion(schedule, request.expectedVersion());
        ScheduleStateRules.requireStatus(schedule.getStatus(), ScheduleStatus.PENDING_APPROVAL, "be approved");
        schedule.setStatus(ScheduleStatus.PUBLISHED);
        schedule.setApprovedBy(TenantContext.current().userId());
        schedule.setApprovedAt(Instant.now());
        return saveAndAssemble(schedule);
    }

    @Override
    @Transactional
    public ShiftScheduleResponse returnToDraft(UUID scheduleId, ScheduleRequests.Return request) {
        ShiftSchedule schedule = scheduleLoader.require(scheduleId);
        access.requireApprove(schedule.getBranchId());
        scheduleLoader.requireVersion(schedule, request.expectedVersion());
        ScheduleStateRules.requireStatus(schedule.getStatus(), ScheduleStatus.PENDING_APPROVAL, "be returned");
        schedule.setStatus(ScheduleStatus.DRAFT);
        schedule.setReturnReason(request.reason());
        return saveAndAssemble(schedule);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScheduleIssueResponse> issues(UUID scheduleId) {
        ShiftSchedule schedule = scheduleLoader.require(scheduleId);
        access.requireRead(schedule.getBranchId());
        return issueFinder.find(contextLoader.load(schedule));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CandidateResponse> candidates(UUID scheduleId, UUID shiftSlotId, LocalDate date, UUID positionId, String search) {
        ShiftSchedule schedule = scheduleLoader.require(scheduleId);
        access.requireEdit(schedule.getBranchId());
        ScheduleContext context = contextLoader.load(schedule);
        return candidateFinder.find(context, context.slots().byId(shiftSlotId), date, positionId, search);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentChangeResponse> changes(UUID scheduleId, boolean sinceSubmitted) {
        ShiftSchedule schedule = scheduleLoader.require(scheduleId);
        access.requireRead(schedule.getBranchId());
        List<ShiftAssignmentEvent> events = sinceSubmitted && schedule.getSubmittedAt() != null
                ? eventRepository.findByShiftScheduleIdAndOccurredAtAfterOrderByOccurredAt(scheduleId, schedule.getSubmittedAt())
                : eventRepository.findByShiftScheduleIdOrderByOccurredAt(scheduleId);
        Map<UUID, SchedulingStaff> staff = staffLoader.byIds(events.stream().map(ShiftAssignmentEvent::getStaffId).toList(), schedule.getWeekStart());
        return events.stream().map(event -> new AssignmentChangeResponse(event.getShiftAssignmentId(), event.getStaffId(),
                staff.containsKey(event.getStaffId()) ? staff.get(event.getStaffId()).displayName() : null, event.getWorkDate(),
                event.getEventType().name(), event.getActorId(), event.getBeforeValue(), event.getAfterValue(), event.getReason(),
                event.getOccurredAt())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ScheduleSummaryTotalsResponse summary(UUID scheduleId) {
        ShiftSchedule schedule = scheduleLoader.require(scheduleId);
        access.requireRead(schedule.getBranchId());
        ScheduleContext context = contextLoader.load(schedule);
        boolean withCost = access.canSeeCost(schedule.getBranchId());
        Map<UUID, Integer> minimum = limitsProvider.minMinutes(context.limits(), context.staff(), schedule.getWeekStart());
        List<PersonTotal> people = context.assignments().stream().collect(Collectors.groupingBy(ShiftAssignment::getStaffId)).entrySet().stream()
                .map(entry -> personTotal(context, entry.getKey(), entry.getValue(), minimum, withCost))
                .sorted(Comparator.comparing(PersonTotal::hours).reversed()).toList();
        return new ScheduleSummaryTotalsResponse(people, positionTotals(context, withCost));
    }

    private PersonTotal personTotal(ScheduleContext context, UUID staffId, List<ShiftAssignment> shifts,
            Map<UUID, Integer> minimum, boolean withCost) {
        SchedulingStaff person = context.person(staffId);
        int minutes = weeklyHours.minutesByStaff(shifts).get(staffId);
        BigDecimal cost = withCost ? cost(staffId, shifts) : null;
        return new PersonTotal(staffId, person == null ? null : person.displayName(),
                person == null ? null : person.typeOn(context.schedule().getWeekStart()).map(Enum::name).orElse(null), shifts.size(),
                hours(minutes), hours(minimum.getOrDefault(staffId, 0)), cost);
    }

    private List<PositionTotal> positionTotals(ScheduleContext context, boolean withCost) {
        Map<UUID, List<ShiftAssignment>> byPosition = context.assignments().stream().collect(Collectors.groupingBy(ShiftAssignment::getPositionId));
        Map<UUID, String> names = staffLoader.positionNames(byPosition.keySet());
        return byPosition.entrySet().stream().map(entry -> new PositionTotal(entry.getKey(), names.get(entry.getKey()), entry.getValue().size(),
                hours(entry.getValue().stream().mapToInt(shift -> TimeWindow.of(shift).minutes()).sum()),
                withCost ? entry.getValue().stream().map(shift -> cost(shift.getStaffId(), List.of(shift))).reduce(BigDecimal.ZERO, BigDecimal::add) : null))
                .toList();
    }

    private BigDecimal cost(UUID staffId, List<ShiftAssignment> shifts) {
        return shifts.stream().map(shift -> costEstimator.hourlyCost(staffId, shift.getWorkDate())
                        .map(rate -> rate.multiply(TimeWindow.of(shift).hours())).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(0, RoundingMode.HALF_UP);
    }

    private BigDecimal hours(int minutes) {
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(MINUTES_PER_HOUR), 2, RoundingMode.HALF_UP);
    }

    private ShiftScheduleResponse saveAndAssemble(ShiftSchedule schedule) {
        schedule.setVersion(schedule.getVersion() + 1);
        scheduleRepository.saveAndFlush(schedule);
        return viewAssembler.assemble(contextLoader.load(schedule));
    }

    private ScheduleSummaryResponse toSummary(ShiftSchedule schedule) {
        return new ScheduleSummaryResponse(schedule.getShiftScheduleId(), schedule.getBranchId(), schedule.getWeekStart(),
                schedule.getStatus().name(), schedule.getVersion(), schedule.getSubmittedAt(), schedule.getApprovedAt());
    }
}
