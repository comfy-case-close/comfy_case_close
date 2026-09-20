package com.fnbx.identity.controller;

import java.util.UUID;
import com.fnbx.identity.dto.request.AssignBranchRoleRequest;
import com.fnbx.identity.dto.request.CreateBranchRequest;
import com.fnbx.identity.dto.request.UpdateBranchRequest;
import com.fnbx.identity.dto.response.BranchAssignmentResponse;
import com.fnbx.identity.dto.response.BranchResponse;
import com.fnbx.identity.dto.response.MessageResponse;
import com.fnbx.identity.service.BranchService;
import com.fnbx.shared.utils.PagedResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Branches, and who works at them.
 *
 * <p>Every route here takes its business from the verified JWT; none accepts a
 * {@code businessId}. Authorization is decided in the service against
 * {@code AccessPrincipal}, not here - the controller's job is HTTP.
 *
 * <p>Assignment is a PUT on the logical (branch, staff) pair. Repeating the
 * current role is idempotent; a different role creates a new historical version.
 * DELETE closes the current version without losing history.
 */
@RestController
@RequestMapping("/branches")
@Validated
public class BranchController {

    private final BranchService branches;

    public BranchController(BranchService branches) { this.branches = branches; }

    // ---- branch structure ---------------------------------------------------

    @PostMapping
    public ResponseEntity<BranchResponse> create(@Valid @RequestBody CreateBranchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(branches.create(request));
    }

    @GetMapping
    public ResponseEntity<PagedResponse<BranchResponse>> list(
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(branches.list(includeInactive, page, size));
    }

    @GetMapping("/{branchId}")
    public ResponseEntity<BranchResponse> get(@PathVariable UUID branchId) {
        return ResponseEntity.ok(branches.get(branchId));
    }

    @PatchMapping("/{branchId}")
    public ResponseEntity<BranchResponse> update(@PathVariable UUID branchId,
            @Valid @RequestBody UpdateBranchRequest request) {
        return ResponseEntity.ok(branches.update(branchId, request));
    }

    /** Deactivates rather than deletes, and refuses the business's last active branch. */
    @DeleteMapping("/{branchId}")
    public ResponseEntity<MessageResponse> deactivate(@PathVariable UUID branchId) {
        return ResponseEntity.ok(branches.deactivate(branchId));
    }

    // ---- who works here -----------------------------------------------------

    @GetMapping("/{branchId}/staff")
    public ResponseEntity<PagedResponse<BranchAssignmentResponse>> members(@PathVariable UUID branchId,
            @RequestParam(defaultValue = "false") boolean includeRevoked,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(branches.members(branchId, includeRevoked, page, size));
    }

    /** Assigns or changes a role. Idempotent on {@code (branchId, staffId)}. */
    @PutMapping("/{branchId}/staff/{staffId}")
    public ResponseEntity<BranchAssignmentResponse> assign(@PathVariable UUID branchId, @PathVariable UUID staffId,
            @Valid @RequestBody AssignBranchRoleRequest request) {
        return ResponseEntity.ok(branches.assign(branchId, staffId, request));
    }

    @DeleteMapping("/{branchId}/staff/{staffId}")
    public ResponseEntity<MessageResponse> revoke(@PathVariable UUID branchId, @PathVariable UUID staffId) {
        return ResponseEntity.ok(branches.revoke(branchId, staffId));
    }
}
