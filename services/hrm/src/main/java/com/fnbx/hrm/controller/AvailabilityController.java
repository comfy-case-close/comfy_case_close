package com.fnbx.hrm.controller;

import com.fnbx.hrm.dto.request.SaveAvailabilityRequest;
import com.fnbx.hrm.dto.response.AvailabilityResponses;
import com.fnbx.hrm.service.AvailabilityService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Weekly registration: employees mark the shifts they are busy, the store manager opens and closes the window. */
@RestController
@RequiredArgsConstructor
public class AvailabilityController {

    private static final String DATE = "yyyy-MM-dd";

    private final AvailabilityService availabilityService;

    @PostMapping("/registration-windows/{branchId}/{weekStart}/open")
    public ResponseEntity<AvailabilityResponses.Window> open(@PathVariable UUID branchId,
            @PathVariable @DateTimeFormat(pattern = DATE) LocalDate weekStart) {
        return ResponseEntity.ok(availabilityService.openWindow(branchId, weekStart));
    }

    @PostMapping("/registration-windows/{branchId}/{weekStart}/close")
    public ResponseEntity<AvailabilityResponses.Window> close(@PathVariable UUID branchId,
            @PathVariable @DateTimeFormat(pattern = DATE) LocalDate weekStart) {
        return ResponseEntity.ok(availabilityService.closeWindow(branchId, weekStart));
    }

    @GetMapping("/own/availability")
    public ResponseEntity<AvailabilityResponses.OwnForm> own(@RequestParam @DateTimeFormat(pattern = DATE) LocalDate weekStart) {
        return ResponseEntity.ok(availabilityService.own(weekStart));
    }

    @PutMapping("/own/availability/{weekStart}")
    public ResponseEntity<AvailabilityResponses.OwnForm> saveOwn(@PathVariable @DateTimeFormat(pattern = DATE) LocalDate weekStart,
            @Valid @RequestBody SaveAvailabilityRequest request) {
        return ResponseEntity.ok(availabilityService.saveOwn(weekStart, request));
    }

    @PostMapping("/own/availability/{weekStart}/submit")
    public ResponseEntity<AvailabilityResponses.OwnForm> submitOwn(@PathVariable @DateTimeFormat(pattern = DATE) LocalDate weekStart) {
        return ResponseEntity.ok(availabilityService.submitOwn(weekStart));
    }

    @PostMapping("/own/availability/{weekStart}/copy-from/{otherWeek}")
    public ResponseEntity<AvailabilityResponses.OwnForm> copyOwn(@PathVariable @DateTimeFormat(pattern = DATE) LocalDate weekStart,
            @PathVariable @DateTimeFormat(pattern = DATE) LocalDate otherWeek) {
        return ResponseEntity.ok(availabilityService.copyOwn(weekStart, otherWeek));
    }

    @GetMapping("/availability")
    public ResponseEntity<AvailabilityResponses.Overview> overview(@RequestParam UUID branchId,
            @RequestParam @DateTimeFormat(pattern = DATE) LocalDate weekStart, @RequestParam(required = false) String search) {
        return ResponseEntity.ok(availabilityService.overview(branchId, weekStart, search));
    }

    @PutMapping("/availability/{staffId}/{weekStart}")
    public ResponseEntity<AvailabilityResponses.OwnForm> enterFor(@PathVariable UUID staffId,
            @PathVariable @DateTimeFormat(pattern = DATE) LocalDate weekStart, @Valid @RequestBody SaveAvailabilityRequest request) {
        return ResponseEntity.ok(availabilityService.enterFor(staffId, weekStart, request));
    }

    @PostMapping("/availability/reminders")
    public ResponseEntity<Integer> remind(@RequestParam UUID branchId, @RequestParam @DateTimeFormat(pattern = DATE) LocalDate weekStart) {
        return ResponseEntity.ok(availabilityService.remind(branchId, weekStart));
    }
}
