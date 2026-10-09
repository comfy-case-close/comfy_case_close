package com.fnbx.hrm.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class AutoFillResponses {

    private AutoFillResponses() {}

    public record Gap(LocalDate date, String period, LocalTime startTime, LocalTime endTime, UUID positionId, String reason) {}

    public record Run(UUID runId, String status, int created, int skipped, List<Gap> gaps, Instant startedAt, Instant undoneAt) {}
}
