package com.fnbx.hrm.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fnbx.hrm.dto.request.ScheduleRequests;
import com.fnbx.hrm.dto.response.AutoFillResponses.Gap;
import com.fnbx.hrm.dto.response.AutoFillResponses.Run;
import com.fnbx.hrm.entity.ScheduleGenerationRun;
import com.fnbx.hrm.entity.ShiftAssignment;
import com.fnbx.hrm.entity.ShiftSchedule;
import com.fnbx.hrm.entity.ShiftSlot;
import com.fnbx.hrm.enums.AssignmentSource;
import com.fnbx.hrm.enums.RunStatus;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.repository.ScheduleGenerationRunRepository;
import com.fnbx.hrm.repository.ShiftAssignmentRepository;
import com.fnbx.hrm.service.ScheduleAutoFillService;
import com.fnbx.hrm.service.scheduling.AssignmentGuard;
import com.fnbx.hrm.service.scheduling.AssignmentStore;
import com.fnbx.hrm.service.scheduling.AssignmentStore.NewAssignment;
import com.fnbx.hrm.service.scheduling.ScheduleAccess;
import com.fnbx.hrm.service.scheduling.ScheduleContext;
import com.fnbx.hrm.service.scheduling.ScheduleContextLoader;
import com.fnbx.hrm.service.scheduling.ScheduleLoader;
import com.fnbx.hrm.service.scheduling.ScheduleStateRules;
import com.fnbx.hrm.service.scheduling.autofill.AutoFillEngine;
import com.fnbx.hrm.service.scheduling.autofill.AutoFillInputLoader;
import com.fnbx.hrm.service.scheduling.autofill.AutoFillPlan;
import com.fnbx.hrm.service.scheduling.autofill.Placed;
import com.fnbx.hrm.service.scheduling.autofill.UnfilledGap;
import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.tenant.TenantContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ScheduleAutoFillServiceImpl implements ScheduleAutoFillService {

    private static final String AUTO_FILL_REASON = "Auto-fill";
    private static final String UNDO_REASON = "Auto-fill undone";

    private final ScheduleLoader scheduleLoader;
    private final ScheduleContextLoader contextLoader;
    private final AutoFillInputLoader inputLoader;
    private final AutoFillEngine engine;
    private final AssignmentGuard guard;
    private final AssignmentStore store;
    private final ScheduleGenerationRunRepository runRepository;
    private final ShiftAssignmentRepository assignmentRepository;
    private final ScheduleAccess access;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public Run autoFill(UUID scheduleId, ScheduleRequests.AutoFill request) {
        ShiftSchedule schedule = scheduleLoader.require(scheduleId);
        access.requireEdit(schedule.getBranchId());
        ScheduleStateRules.requireArrangeable(schedule.getStatus(), access.canApprove(schedule.getBranchId()));
        ScheduleContext context = contextLoader.load(schedule);
        AutoFillPlan plan = engine.plan(inputLoader.load(context, request.keepLastWeek()));

        ScheduleGenerationRun run = startRun(schedule, request);
        int created = 0;
        for (Placed proposal : plan.proposals()) {
            created += place(context, run, proposal) ? 1 : 0;
        }
        return finish(run, created, plan.proposals().size() - created, plan.gaps());
    }

    @Override
    @Transactional(readOnly = true)
    public Run run(UUID runId) {
        ScheduleGenerationRun run = requireRun(runId);
        access.requireRead(scheduleLoader.require(run.getShiftScheduleId()).getBranchId());
        return toResponse(run);
    }

    @Override
    @Transactional
    public Run undo(UUID runId) {
        ScheduleGenerationRun run = requireRun(runId);
        ShiftSchedule schedule = scheduleLoader.require(run.getShiftScheduleId());
        access.requireEdit(schedule.getBranchId());
        ScheduleStateRules.requireTrimmable(schedule.getStatus(), access.canApprove(schedule.getBranchId()));
        if (run.getUndoneAt() == null) {
            assignmentRepository.findByGenerationRunId(runId).stream()
                    .filter(assignment -> assignment.getVersion() == 0)
                    .forEach(assignment -> store.remove(assignment, UNDO_REASON));
            run.setUndoneAt(Instant.now());
            runRepository.saveAndFlush(run);
        }
        return toResponse(run);
    }

    private boolean place(ScheduleContext context, ScheduleGenerationRun run, Placed proposal) {
        ShiftSlot slot = context.slots().byId(proposal.shiftSlotId());
        try {
            guard.requireAssignable(context, proposal.staffId(), proposal.date(), slot, proposal.positionId(), proposal.window(),
                    null, AUTO_FILL_REASON);
        } catch (AppException ex) {
            return false;
        }
        store.add(context.schedule(), new NewAssignment(proposal.staffId(), proposal.date(), slot, proposal.positionId(),
                proposal.window(), AssignmentSource.AUTO, run.getScheduleGenerationRunId(), null), AUTO_FILL_REASON);
        return true;
    }

    private ScheduleGenerationRun startRun(ShiftSchedule schedule, ScheduleRequests.AutoFill request) {
        ScheduleGenerationRun run = new ScheduleGenerationRun();
        run.setScheduleGenerationRunId(UUID.randomUUID());
        run.setBusinessId(schedule.getBusinessId());
        run.setShiftScheduleId(schedule.getShiftScheduleId());
        run.setParams(json(Map.of("keepLastWeek", request.keepLastWeek())));
        run.setStatus(RunStatus.RUNNING);
        run.setCreatedBy(TenantContext.current().userId());
        run.setStartedAt(Instant.now());
        return runRepository.saveAndFlush(run);
    }

    private Run finish(ScheduleGenerationRun run, int created, int skipped, List<UnfilledGap> gaps) {
        List<Map<String, Object>> summaryGaps = new ArrayList<>();
        gaps.forEach(gap -> summaryGaps.add(Map.of("date", gap.date().toString(), "period", gap.period().name(),
                "start", gap.window().start().toString(), "end", gap.window().end().toString(),
                "positionId", gap.positionId().toString(), "reason", gap.reason().name())));
        run.setSummary(json(Map.of("created", created, "skipped", skipped, "gaps", summaryGaps)));
        run.setStatus(RunStatus.SUCCEEDED);
        run.setFinishedAt(Instant.now());
        runRepository.saveAndFlush(run);
        return new Run(run.getScheduleGenerationRunId(), run.getStatus().name(), created, skipped,
                gaps.stream().map(this::toGap).toList(), run.getStartedAt(), run.getUndoneAt());
    }

    private Run toResponse(ScheduleGenerationRun run) {
        Map<String, Object> summary = read(run.getSummary());
        List<Gap> gaps = ((List<Map<String, String>>) summary.getOrDefault("gaps", List.of())).stream()
                .map(gap -> new Gap(java.time.LocalDate.parse(gap.get("date")), gap.get("period"),
                        java.time.LocalTime.parse(gap.get("start")), java.time.LocalTime.parse(gap.get("end")),
                        UUID.fromString(gap.get("positionId")), gap.get("reason")))
                .toList();
        return new Run(run.getScheduleGenerationRunId(), run.getStatus().name(), ((Number) summary.getOrDefault("created", 0)).intValue(),
                ((Number) summary.getOrDefault("skipped", 0)).intValue(), gaps, run.getStartedAt(), run.getUndoneAt());
    }

    private Gap toGap(UnfilledGap gap) {
        return new Gap(gap.date(), gap.period().name(), gap.window().start(), gap.window().end(), gap.positionId(), gap.reason().name());
    }

    private ScheduleGenerationRun requireRun(UUID runId) {
        return runRepository.findById(runId).orElseThrow(PayrollExceptions::generationRunNotFound);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize the auto-fill run", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> read(String json) {
        try {
            return json == null ? Map.of() : objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot read the auto-fill run", ex);
        }
    }
}
