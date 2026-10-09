package com.fnbx.hrm.service.scheduling.autofill;

import com.fnbx.hrm.entity.ShiftSlot;
import com.fnbx.hrm.enums.EmploymentType;
import com.fnbx.hrm.enums.ShiftPeriod;
import com.fnbx.hrm.service.scheduling.AvailabilityStatus;
import com.fnbx.hrm.service.scheduling.CoverageCalculator;
import com.fnbx.hrm.service.scheduling.SchedulingStaff;
import com.fnbx.hrm.service.scheduling.TimeWindow;
import com.fnbx.hrm.service.scheduling.WeekCalendar;
import com.fnbx.hrm.service.scheduling.autofill.AutoFillInput.LastWeekKey;
import com.fnbx.hrm.service.scheduling.autofill.UnfilledGap.Reason;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Fills only what is still missing: everything already on the schedule is fixed. The same input always gives the same plan.
 */
@Component
@RequiredArgsConstructor
public class AutoFillEngine {

    private static final long LAST_WEEK_BONUS = 1_000_000L;

    private final CoverageCalculator coverageCalculator;

    public AutoFillPlan plan(AutoFillInput input) {
        return new Run(input).execute();
    }

    private record Gap(LocalDate date, ShiftPeriod period, TimeWindow window, UUID positionId) {}

    private record Candidate(SchedulingStaff staff, ShiftSlot slot, long score) {}

    private final class Run {

        private final AutoFillInput in;
        private final List<Placed> placed;
        private final List<Placed> proposals = new ArrayList<>();

        Run(AutoFillInput in) {
            this.in = in;
            this.placed = new ArrayList<>(in.existing());
        }

        AutoFillPlan execute() {
            fillFullTime();
            fillPartTime();
            topUpMinimumHours();
            return new AutoFillPlan(List.copyOf(proposals), missingGaps().stream().map(this::explain).toList());
        }

        // ---- step 1: full-time people cover a whole shift ---------------------------------------------

        private void fillFullTime() {
            for (LocalDate date : WeekCalendar.days(in.weekStart())) {
                for (ShiftPeriod period : ShiftPeriod.values()) {
                    Optional<ShiftSlot> slot = in.slots().on(date).stream()
                            .filter(candidate -> candidate.getShiftPeriod() == period
                                    && candidate.getEmploymentType() == EmploymentType.FULLTIME)
                            .findFirst();
                    Map<UUID, Integer> required = in.requirements().getOrDefault(date, Map.of()).getOrDefault(period, Map.of());
                    for (UUID positionId : required.keySet().stream().sorted().toList()) {
                        slot.ifPresent(fullTimeSlot -> coverWholeShift(date, period, positionId, fullTimeSlot));
                    }
                }
            }
        }

        private void coverWholeShift(LocalDate date, ShiftPeriod period, UUID positionId, ShiftSlot slot) {
            while (periodHasGap(date, period, positionId)) {
                Optional<Candidate> best = in.staff().values().stream()
                        .filter(person -> eligible(person, date, slot, positionId) && assignable(person, date, slot)
                                && !worksOn(person.staffId(), date))
                        .map(person -> new Candidate(person, slot, score(person, date, slot)))
                        .max(candidateOrder());
                if (best.isEmpty()) {
                    return;
                }
                place(best.get(), date, positionId);
            }
        }

        // ---- step 2: part-time people fill the remaining windows --------------------------------------

        private void fillPartTime() {
            List<Gap> gaps = new ArrayList<>(missingGaps());
            gaps.sort(Comparator.comparingInt(this::options).thenComparing(Gap::date).thenComparing(gap -> gap.window().start()));
            for (Gap gap : gaps) {
                while (stillMissing(gap)) {
                    Optional<Candidate> best = candidatesFor(gap).max(candidateOrder());
                    if (best.isEmpty()) {
                        break;
                    }
                    place(best.get(), gap.date(), gap.positionId());
                }
            }
        }

        private java.util.stream.Stream<Candidate> candidatesFor(Gap gap) {
            return in.slots().on(gap.date()).stream()
                    .filter(slot -> slot.getShiftPeriod() == gap.period() && slot.getEmploymentType() == EmploymentType.PARTTIME
                            && TimeWindow.of(slot).covers(gap.window()))
                    .flatMap(slot -> in.staff().values().stream()
                            .filter(person -> eligible(person, gap.date(), slot, gap.positionId()) && assignable(person, gap.date(), slot))
                            .map(person -> new Candidate(person, slot, score(person, gap.date(), slot))));
        }

        private int options(Gap gap) {
            return (int) candidatesFor(gap).count();
        }

        // ---- step 3: bring everybody up to the weekly minimum -----------------------------------------

        private void topUpMinimumHours() {
            for (SchedulingStaff person : in.staff().values().stream().sorted(Comparator.comparing(SchedulingStaff::staffId)).toList()) {
                for (LocalDate date : WeekCalendar.days(in.weekStart())) {
                    if (minutesWorked(person.staffId()) >= in.minMinutes().getOrDefault(person.staffId(), 0)) {
                        break;
                    }
                    topUpOnDay(person, date);
                }
            }
        }

        private void topUpOnDay(SchedulingStaff person, LocalDate date) {
            Optional<EmploymentType> type = person.typeOn(date);
            if (type.isEmpty()) {
                return;
            }
            for (ShiftSlot slot : in.slots().on(date)) {
                UUID positionId = positionFor(person, date, slot.getShiftPeriod());
                boolean sameDayAllowed = type.get() == EmploymentType.PARTTIME || !worksOn(person.staffId(), date);
                if (positionId != null && slot.getEmploymentType() == type.get() && sameDayAllowed
                        && assignable(person, date, slot)) {
                    place(new Candidate(person, slot, 0), date, positionId);
                    return;
                }
            }
        }

        private UUID positionFor(SchedulingStaff person, LocalDate date, ShiftPeriod period) {
            Set<UUID> needed = in.requirements().getOrDefault(date, Map.of()).getOrDefault(period, Map.of()).keySet();
            return person.positionIds().stream().sorted().filter(needed::contains).findFirst()
                    .orElseGet(() -> person.positionIds().stream().sorted().findFirst().orElse(null));
        }

        // ---- rules ------------------------------------------------------------------------------------

        private boolean eligible(SchedulingStaff person, LocalDate date, ShiftSlot slot, UUID positionId) {
            return person.canFill(positionId) && person.typeOn(date).filter(type -> type == slot.getEmploymentType()).isPresent();
        }

        private boolean assignable(SchedulingStaff person, LocalDate date, ShiftSlot slot) {
            TimeWindow window = TimeWindow.of(slot);
            return in.availability().statusOf(person.staffId(), date, window) == AvailabilityStatus.FREE
                    && !in.otherBranchDays().getOrDefault(person.staffId(), Set.of()).contains(date)
                    && placed.stream().noneMatch(other -> other.staffId().equals(person.staffId())
                            && other.date().equals(date) && other.window().overlaps(window));
        }

        private boolean worksOn(UUID staffId, LocalDate date) {
            return placed.stream().anyMatch(other -> other.staffId().equals(staffId) && other.date().equals(date));
        }

        private long score(SchedulingStaff person, LocalDate date, ShiftSlot slot) {
            long bonus = in.keepLastWeek() && in.lastWeek().contains(
                    new LastWeekKey(person.staffId(), date.getDayOfWeek().getValue() - 1, slot.getName())) ? LAST_WEEK_BONUS : 0;
            long shortfall = Math.max(0, in.minMinutes().getOrDefault(person.staffId(), 0) - minutesWorked(person.staffId()));
            return bonus + shortfall;
        }

        private Comparator<Candidate> candidateOrder() {
            return Comparator.comparingLong(Candidate::score)
                    .thenComparing(candidate -> -TimeWindow.of(candidate.slot()).minutes())
                    .thenComparing(candidate -> candidate.staff().staffId(), Comparator.reverseOrder());
        }

        private int minutesWorked(UUID staffId) {
            return placed.stream().filter(other -> other.staffId().equals(staffId)).mapToInt(other -> other.window().minutes()).sum();
        }

        private void place(Candidate candidate, LocalDate date, UUID positionId) {
            Placed added = new Placed(candidate.staff().staffId(), date, candidate.slot().getShiftSlotId(), candidate.slot().getName(),
                    candidate.slot().getShiftPeriod(), positionId, TimeWindow.of(candidate.slot()));
            placed.add(added);
            proposals.add(added);
        }

        // ---- coverage ---------------------------------------------------------------------------------

        private boolean periodHasGap(LocalDate date, ShiftPeriod period, UUID positionId) {
            return missingGaps().stream().anyMatch(gap -> gap.date().equals(date) && gap.period() == period
                    && gap.positionId().equals(positionId));
        }

        private boolean stillMissing(Gap gap) {
            int required = in.requirements().getOrDefault(gap.date(), Map.of()).getOrDefault(gap.period(), Map.of())
                    .getOrDefault(gap.positionId(), 0);
            return presentIn(gap) < required;
        }

        private int presentIn(Gap gap) {
            return (int) placed.stream()
                    .filter(other -> other.date().equals(gap.date()) && other.period() == gap.period()
                            && other.positionId().equals(gap.positionId()) && other.window().covers(gap.window()))
                    .map(Placed::staffId).distinct().count();
        }

        private List<Gap> missingGaps() {
            List<Gap> gaps = new ArrayList<>();
            for (LocalDate date : WeekCalendar.days(in.weekStart())) {
                for (ShiftPeriod period : ShiftPeriod.values()) {
                    Map<UUID, Integer> required = in.requirements().getOrDefault(date, Map.of()).getOrDefault(period, Map.of());
                    for (TimeWindow window : coverageCalculator.segments(in.slots().on(date), period)) {
                        required.keySet().stream().sorted().map(positionId -> new Gap(date, period, window, positionId))
                                .filter(this::stillMissing).forEach(gaps::add);
                    }
                }
            }
            return gaps;
        }

        // ---- why a gap stays open ---------------------------------------------------------------------

        private UnfilledGap explain(Gap gap) {
            List<SchedulingStaff> qualified = in.staff().values().stream()
                    .filter(person -> person.canFill(gap.positionId()) && person.typeOn(gap.date()).isPresent()).toList();
            Reason reason = qualified.isEmpty() ? Reason.NO_QUALIFIED_STAFF : reasonAmong(qualified, gap);
            return new UnfilledGap(gap.date(), gap.period(), gap.window(), gap.positionId(), reason);
        }

        private Reason reasonAmong(List<SchedulingStaff> qualified, Gap gap) {
            List<SchedulingStaff> atThisBranch = qualified.stream()
                    .filter(person -> !in.otherBranchDays().getOrDefault(person.staffId(), Set.of()).contains(gap.date())).toList();
            if (atThisBranch.isEmpty()) {
                return Reason.OTHER_BRANCH;
            }
            if (atThisBranch.stream().anyMatch(person ->
                    in.availability().statusOf(person.staffId(), gap.date(), gap.window()) == AvailabilityStatus.NO_SUBMISSION)) {
                return Reason.NO_SUBMISSION;
            }
            boolean everyoneBusy = atThisBranch.stream().noneMatch(person ->
                    in.availability().statusOf(person.staffId(), gap.date(), gap.window()) == AvailabilityStatus.FREE);
            return everyoneBusy ? Reason.ALL_BUSY : Reason.NO_FREE_SLOT;
        }
    }
}
