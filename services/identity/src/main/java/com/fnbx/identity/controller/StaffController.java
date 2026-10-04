package com.fnbx.identity.controller;

import com.fnbx.identity.dto.response.AuthUserResponse;
import com.fnbx.identity.service.StaffDirectoryService;
import com.fnbx.shared.utils.PagedResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Validated
public class StaffController {
    private final StaffDirectoryService directory;

    public StaffController(StaffDirectoryService directory) {
        this.directory = directory;
    }

    @GetMapping("/staff")
    public PagedResponse<AuthUserResponse> list(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return directory.list(branchId, page, size);
    }

    @GetMapping("/staff/{staffId}")
    public AuthUserResponse get(
            @PathVariable UUID staffId,
            @RequestParam(required = false) UUID branchId
    ) {
        return directory.get(staffId, branchId);
    }
}
