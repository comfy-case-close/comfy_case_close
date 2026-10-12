package com.fnbx.hrm.service.impl;

import com.fnbx.hrm.dto.request.SaveAvailabilityRequest;
import com.fnbx.hrm.dto.request.SaveAvailabilityRequest.BusyShiftInput;
import com.fnbx.hrm.dto.response.AvailabilityResponses.BusyItem;
import com.fnbx.hrm.dto.response.AvailabilityResponses.Overview;
import com.fnbx.hrm.dto.response.AvailabilityResponses.OwnForm;
import com.fnbx.hrm.dto.response.AvailabilityResponses.StaffRegistration;
import com.fnbx.hrm.dto.response.AvailabilityResponses.Window;
import com.fnbx.hrm.entity.AvailabilitySubmission;
import com.fnbx.hrm.entity.BusyShift;
import com.fnbx.hrm.entity.RegistrationWindow;
import com.fnbx.hrm.entity.ShiftSlot;
import com.fnbx.hrm.enums.RegistrationStatus;
import com.fnbx.hrm.enums.SubmissionStatus;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.repository.AvailabilitySubmissionRepository;
import com.fnbx.hrm.repository.BusyShiftRepository;
import com.fnbx.hrm.repository.RegistrationWindowRepository;
import com.fnbx.hrm.repository.ShiftSlotRepository;
import com.fnbx.hrm.service.AvailabilityService;
import com.fnbx.hrm.service.scheduling.AvailabilityFormAssembler;
import com.fnbx.hrm.service.scheduling.AvailabilityWriter;
import com.fnbx.hrm.service.scheduling.EmployeeBranchResolver;
import com.fnbx.hrm.service.scheduling.EmployeeBranchResolver.EmployeeBranch;
import com.fnbx.hrm.service.scheduling.ScheduleAccess;
import com.fnbx.hrm.service.scheduling.SchedulingStaff;
import com.fnbx.hrm.service.scheduling.SchedulingStaffLoader;
import com.fnbx.hrm.service.scheduling.SlotCatalog;
import com.fnbx.hrm.service.scheduling.WeekCalendar;
import com.fnbx.hrm.service.scheduling.WeekSlots;
import com.fnbx.identity.entity.Staff;
import com.fnbx.mail.EmailService;
import com.fnbx.shared.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AvailabilityServiceImpl implements AvailabilityService {

    private final RegistrationWindowRepository windowRepository;
    private final AvailabilitySubmissionRepository submissionRepository;
    private final BusyShiftRepository busyShiftRepository;
    private final ShiftSlotRepository slotRepository;
    private final AvailabilityWriter writer;
    private final AvailabilityFormAssembler formAssembler;
    private final EmployeeBranchResolver branchResolver;
    private final SchedulingStaffLoader staffLoader;
    private final SlotCatalog slotCatalog;
    private final ScheduleAccess access;
    private final EmailService emailService;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public Window openWindow(UUID branchId, LocalDate weekStart) {
        access.requireEdit(branchId);
        RegistrationWindow window = requireWindow(branchId, WeekCalendar.requireMonday(weekStart));
        window.setStatus(RegistrationStatus.OPEN);
        window.setOpenedBy(TenantContext.current().userId());
        window.setOpenedAt(Instant.now());
        window.setClosedBy(null);
        window.setClosedAt(null);
        return toResponse(windowRepository.saveAndFlush(window));
    }

    @Override
    @Transactional
    public Window closeWindow(UUID branchId, LocalDate weekStart) {
        access.requireEdit(branchId);
        RegistrationWindow window = requireWindow(branchId, WeekCalendar.requireMonday(weekStart));
        window.setStatus(RegistrationStatus.CLOSED);
        window.setClosedBy(TenantContext.current().userId());
        window.setClosedAt(Instant.now());
        return toResponse(windowRepository.saveAndFlush(window));
    }

    @Override
    @Transactional(readOnly = true)
    public OwnForm own(LocalDate weekStart) {
        UUID staffId = TenantContext.current().userId();
        return formAssembler.assemble(staffId, WeekCalendar.requireMonday(weekStart), branchResolver.resolve(staffId, weekStart));
    }

    @Override
    @Transactional
    public OwnForm saveOwn(LocalDate weekStart, SaveAvailabilityRequest request) {
        UUID staffId = TenantContext.current().userId();
        EmployeeBranch employee = openEmployeeBranch(staffId, weekStart);
        writer.save(staffId, weekStart, employee, request, SubmissionStatus.DRAFT, null);
        return formAssembler.assemble(staffId, weekStart, employee);
    }

    @Override
    @Transactional
    public OwnForm submitOwn(LocalDate weekStart) {
        UUID staffId = TenantContext.current().userId();
        EmployeeBranch employee = openEmployeeBranch(staffId, weekStart);
        writer.submit(staffId, weekStart);
        return formAssembler.assemble(staffId, weekStart, employee);
    }

    @Override
    @Transactional
    public OwnForm copyOwn(LocalDate weekStart, LocalDate fromWeek) {
        UUID staffId = TenantContext.current().userId();
        EmployeeBranch employee = openEmployeeBranch(staffId, weekStart);
        List<BusyShiftInput> copied = copiedBusyShifts(staffId, employee, weekStart, fromWeek);
        writer.save(staffId, weekStart, employee, new SaveAvailabilityRequest(copied, null, null), SubmissionStatus.DRAFT, null);
        return formAssembler.assemble(staffId, weekStart, employee);
    }

    @Override
    @Transactional(readOnly = true)
    public Overview overview(UUID branchId, LocalDate weekStart, String search) {
        access.requireRead(branchId);
        WeekCalendar.requireMonday(weekStart);
        Map<UUID, SchedulingStaff> staff = schedulable(branchId, weekStart);
        Map<UUID, AvailabilitySubmission> submissions = submissionRepository.findByWeekStartAndStaffIdIn(weekStart, staff.keySet()).stream()
                .collect(Collectors.toMap(AvailabilitySubmission::getStaffId, Function.identity()));
        Map<UUID, List<BusyShift>> busy = busyShiftRepository
                .findByAvailabilitySubmissionIdIn(submissions.values().stream().map(AvailabilitySubmission::getAvailabilitySubmissionId).toList())
                .stream().collect(Collectors.groupingBy(BusyShift::getAvailabilitySubmissionId));
        Map<UUID, ShiftSlot> slots = slotRepository.findAllById(busy.values().stream().flatMap(List::stream)
                .map(BusyShift::getShiftSlotId).distinct().toList()).stream()
                .collect(Collectors.toMap(ShiftSlot::getShiftSlotId, Function.identity()));
        List<StaffRegistration> rows = staff.values().stream()
                .filter(person -> matches(person, search))
                .sorted(Comparator.comparing(SchedulingStaff::displayName))
                .map(person -> registration(person, weekStart, submissions.get(person.staffId()), busy, slots))
                .toList();
        return summarize(branchId, weekStart, rows);
    }

    @Override
    @Transactional
    public OwnForm enterFor(UUID staffId, LocalDate weekStart, SaveAvailabilityRequest request) {
        EmployeeBranch employee = branchResolver.resolve(staffId, WeekCalendar.requireMonday(weekStart));
        access.requireEdit(employee.branchId());
        writer.save(staffId, weekStart, employee, request, SubmissionStatus.SUBMITTED, TenantContext.current().userId());
        return formAssembler.assemble(staffId, weekStart, employee);
    }

    @Override
    @Transactional(readOnly = true)
    public int remind(UUID branchId, LocalDate weekStart) {
        access.requireEdit(branchId);
        Map<UUID, SchedulingStaff> staff = schedulable(branchId, weekStart);
        Map<UUID, AvailabilitySubmission> submissions = submissionRepository.findByWeekStartAndStaffIdIn(weekStart, staff.keySet()).stream()
                .collect(Collectors.toMap(AvailabilitySubmission::getStaffId, Function.identity()));
        List<UUID> pending = staff.keySet().stream()
                .filter(id -> submissions.get(id) == null || submissions.get(id).getStatus() != SubmissionStatus.SUBMITTED).toList();
        pending.forEach(id -> sendReminder(id, weekStart));
        return pending.size();
    }

    /** People of the branch who work shifts that week: someone with no contract has nothing to register. */
    private Map<UUID, SchedulingStaff> schedulable(UUID branchId, LocalDate weekStart) {
        return staffLoader.forBranch(branchId, weekStart).entrySet().stream()
                .filter(entry -> entry.getValue().typeOn(weekStart).isPresent())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private void sendReminder(UUID staffId, LocalDate weekStart) {
        Staff person = entityManager.find(Staff.class, staffId);
        if (person.getEmail() != null && !person.getEmail().isBlank() && emailService.available()) {
            emailService.sendText(person.getEmail(), "Đăng ký ca tuần " + weekStart,
                    "Xin chào " + person.getFirstName() + ", bạn chưa gửi đăng ký ca bận cho tuần bắt đầu " + weekStart + ".");
        }
    }

    private EmployeeBranch openEmployeeBranch(UUID staffId, LocalDate weekStart) {
        EmployeeBranch employee = branchResolver.resolve(staffId, WeekCalendar.requireMonday(weekStart));
        if (!formAssembler.isOpen(employee.branchId(), weekStart)) {
            throw PayrollExceptions.registrationClosed();
        }
        return employee;
    }

    private List<BusyShiftInput> copiedBusyShifts(UUID staffId, EmployeeBranch employee, LocalDate weekStart, LocalDate fromWeek) {
        long shift = java.time.temporal.ChronoUnit.DAYS.between(WeekCalendar.requireMonday(fromWeek), weekStart);
        WeekSlots target = slotCatalog.forWeek(employee.branchId(), weekStart);
        Map<UUID, ShiftSlot> sourceSlots = slotRepository.findAllById(writer.busyOf(staffId, fromWeek).orElse(List.of()).stream()
                .map(BusyShift::getShiftSlotId).toList()).stream().collect(Collectors.toMap(ShiftSlot::getShiftSlotId, Function.identity()));
        return writer.busyOf(staffId, fromWeek).orElse(List.of()).stream()
                .map(source -> sameShiftIn(target, source.getWorkDate().plusDays(shift), sourceSlots.get(source.getShiftSlotId())))
                .flatMap(java.util.Optional::stream).toList();
    }

    private java.util.Optional<BusyShiftInput> sameShiftIn(WeekSlots target, LocalDate date, ShiftSlot sourceSlot) {
        return target.on(date).stream()
                .filter(slot -> slot.getName().equals(sourceSlot.getName()) && slot.getEmploymentType() == sourceSlot.getEmploymentType())
                .findFirst().map(slot -> new BusyShiftInput(date, slot.getShiftSlotId()));
    }

    private StaffRegistration registration(SchedulingStaff person, LocalDate weekStart, AvailabilitySubmission submission,
            Map<UUID, List<BusyShift>> busy, Map<UUID, ShiftSlot> slots) {
        List<BusyItem> items = submission == null ? List.of()
                : busy.getOrDefault(submission.getAvailabilitySubmissionId(), List.of()).stream()
                        .sorted(Comparator.comparing(BusyShift::getWorkDate))
                        .map(shift -> new BusyItem(shift.getWorkDate(), shift.getShiftSlotId(), slots.get(shift.getShiftSlotId()).getName(),
                                slots.get(shift.getShiftSlotId()).getStartTime(), slots.get(shift.getShiftSlotId()).getEndTime()))
                        .toList();
        return new StaffRegistration(person.staffId(), person.displayName(), person.fullName(),
                person.typeOn(weekStart).map(Enum::name).orElse(null),
                submission == null ? "NONE" : submission.getStatus().name(), submission == null ? null : submission.getNote(),
                submission == null ? null : submission.getSubmittedAt(), items);
    }

    private Overview summarize(UUID branchId, LocalDate weekStart, List<StaffRegistration> rows) {
        int submitted = (int) rows.stream().filter(row -> row.status().equals(SubmissionStatus.SUBMITTED.name())).count();
        int draft = (int) rows.stream().filter(row -> row.status().equals(SubmissionStatus.DRAFT.name())).count();
        String windowStatus = windowRepository.findByBranchIdAndWeekStart(branchId, weekStart)
                .map(window -> window.getStatus().name()).orElse(RegistrationStatus.NOT_OPEN.name());
        return new Overview(branchId, weekStart, windowStatus, rows.size(), submitted, draft, rows.size() - submitted - draft,
                rows.stream().mapToInt(row -> row.busyShifts().size()).sum(), rows);
    }

    private boolean matches(SchedulingStaff person, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String needle = search.toLowerCase(Locale.ROOT);
        return person.fullName().toLowerCase(Locale.ROOT).contains(needle) || person.displayName().toLowerCase(Locale.ROOT).contains(needle);
    }

    private RegistrationWindow requireWindow(UUID branchId, LocalDate weekStart) {
        return windowRepository.findByBranchIdAndWeekStart(branchId, weekStart).orElseGet(() -> {
            RegistrationWindow created = new RegistrationWindow();
            created.setRegistrationWindowId(UUID.randomUUID());
            created.setBusinessId(TenantContext.current().businessId());
            created.setBranchId(branchId);
            created.setWeekStart(weekStart);
            created.setStatus(RegistrationStatus.NOT_OPEN);
            return created;
        });
    }

    private Window toResponse(RegistrationWindow window) {
        return new Window(window.getBranchId(), window.getWeekStart(), window.getStatus().name(), window.getOpenedAt(), window.getClosedAt());
    }
}
