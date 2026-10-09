package com.fnbx.hrm.controller;

import com.fnbx.hrm.dto.response.BankResponse;
import com.fnbx.hrm.dto.response.ProvinceResponse;
import com.fnbx.hrm.service.ReferenceDataService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lookup lists the HR forms choose from; the same for every business, so any signed-in user may read them. */
@RestController
@RequestMapping
@RequiredArgsConstructor
public class ReferenceDataController {

    private final ReferenceDataService referenceDataService;

    @GetMapping("/banks")
    public ResponseEntity<List<BankResponse>> banks() {
        return ResponseEntity.ok(referenceDataService.banks());
    }

    @GetMapping("/provinces")
    public ResponseEntity<List<ProvinceResponse>> provinces() {
        return ResponseEntity.ok(referenceDataService.provinces());
    }
}
