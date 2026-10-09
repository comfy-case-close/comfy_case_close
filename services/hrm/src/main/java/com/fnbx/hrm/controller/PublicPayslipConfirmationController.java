package com.fnbx.hrm.controller;

import com.fnbx.hrm.dto.request.DisputePayslipRequest;
import com.fnbx.hrm.dto.response.ConfirmationViewResponse;
import com.fnbx.hrm.service.PublicPayslipConfirmationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The e-mail button opens a web page; only the POSTs below change anything, so link previewers cannot confirm by accident. */
@RestController
@RequestMapping("/public/payslip-confirmations/{token}")
@RequiredArgsConstructor
public class PublicPayslipConfirmationController {

    private final PublicPayslipConfirmationService confirmationService;

    @GetMapping
    public ResponseEntity<ConfirmationViewResponse> view(@PathVariable String token, HttpServletRequest request) {
        return ResponseEntity.ok(confirmationService.view(token, ClientInfoExtractor.from(request)));
    }

    @PostMapping("/confirm")
    public ResponseEntity<ConfirmationViewResponse> confirm(@PathVariable String token, HttpServletRequest request) {
        return ResponseEntity.ok(confirmationService.confirm(token, ClientInfoExtractor.from(request)));
    }

    @PostMapping("/dispute")
    public ResponseEntity<ConfirmationViewResponse> dispute(@PathVariable String token,
            @Valid @RequestBody DisputePayslipRequest body, HttpServletRequest request) {
        return ResponseEntity.ok(confirmationService.dispute(token, body.note(), ClientInfoExtractor.from(request)));
    }
}
