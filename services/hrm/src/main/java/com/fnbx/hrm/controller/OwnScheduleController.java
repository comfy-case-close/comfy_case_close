package com.fnbx.hrm.controller;

import com.fnbx.hrm.dto.response.OwnScheduleResponses;
import com.fnbx.hrm.service.OwnScheduleService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** An employee's approved schedule: the whole branch week, and their own shifts in lines. */
@RestController
@RequestMapping("/own")
@RequiredArgsConstructor
public class OwnScheduleController {

    private static final String DATE = "yyyy-MM-dd";

    private final OwnScheduleService ownScheduleService;

    /** Lets the screens show the personal pages only to people who have a contract. */
    @GetMapping("/employment")
    public ResponseEntity<OwnScheduleResponses.Employment> employment() {
        return ResponseEntity.ok(ownScheduleService.employment());
    }

    @GetMapping("/shifts")
    public ResponseEntity<List<OwnScheduleResponses.MyShift>> myShifts(@RequestParam @DateTimeFormat(pattern = DATE) LocalDate from,
            @RequestParam @DateTimeFormat(pattern = DATE) LocalDate to) {
        return ResponseEntity.ok(ownScheduleService.myShifts(from, to));
    }

    @GetMapping("/schedule")
    public ResponseEntity<OwnScheduleResponses.Week> week(@RequestParam @DateTimeFormat(pattern = DATE) LocalDate weekStart) {
        return ResponseEntity.ok(ownScheduleService.week(weekStart));
    }
}
