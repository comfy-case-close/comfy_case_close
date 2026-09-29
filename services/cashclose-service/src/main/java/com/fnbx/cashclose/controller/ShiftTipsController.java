package com.fnbx.cashclose.controller;

import com.fnbx.cashclose.dto.response.ShiftTipsResponse;
import com.fnbx.cashclose.service.ShiftTipsService;
import com.fnbx.shared.security.BranchHeader;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tips declared for the selected branch, business date and shift type. */
@RestController
@RequestMapping("/tips")
@RequiredArgsConstructor
@Validated
public class ShiftTipsController {
    private final ShiftTipsService service;

    @GetMapping
    public ResponseEntity<ShiftTipsResponse> getShiftTips(
            @RequestHeader(BranchHeader.NAME) UUID branchId,
            @RequestParam @NotNull UUID shiftTypeId,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return ResponseEntity.ok(service.getShiftTips(branchId, shiftTypeId, businessDate));
    }
}
