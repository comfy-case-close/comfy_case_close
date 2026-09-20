package com.fnbx.identity.controller;

import java.util.UUID;
import com.fnbx.identity.dto.request.ApproveJoinRequest;
import com.fnbx.identity.dto.request.RejectJoinRequest;
import com.fnbx.identity.dto.response.JoinRequestResponse;
import com.fnbx.identity.dto.response.StaffResponse;
import com.fnbx.identity.enums.JoinRequestStatus;
import com.fnbx.identity.service.JoinRequestService;
import com.fnbx.shared.utils.PagedResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * The queue an ADMIN or HR works through: people asking to join this business.
 *
 * <p>Its own controller rather than a corner of {@code BusinessController}, because
 * a join request is a resource with its own lifecycle - it is created by an
 * anonymous applicant through {@code /auth/signup}, read by a reviewer, and decided
 * exactly once.
 *
 * <p>There is no create route here. Applications arrive through signup, where the
 * email OTP proves the applicant owns the address; an authenticated creation would
 * let anyone already inside the business file requests in other people's names.
 */
@RestController
@RequestMapping("/join-requests")
@Validated
public class JoinRequestController {

    private final JoinRequestService joinRequests;

    public JoinRequestController(JoinRequestService joinRequests) { this.joinRequests = joinRequests; }

    /** Oldest first. Omit {@code status} to see decided requests as well. */
    @GetMapping
    public ResponseEntity<PagedResponse<JoinRequestResponse>> list(
            @RequestParam(required = false) JoinRequestStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(joinRequests.list(status, page, size));
    }

    @GetMapping("/{joinRequestId}")
    public ResponseEntity<JoinRequestResponse> get(@PathVariable UUID joinRequestId) {
        return ResponseEntity.ok(joinRequests.get(joinRequestId));
    }

    /**
     * Creates the staff account and assigns it in one act. 201, because the meaningful
     * result of approving is a person who did not exist before.
     */
    @PostMapping("/{joinRequestId}/approve")
    public ResponseEntity<StaffResponse> approve(@PathVariable UUID joinRequestId,
            @Valid @RequestBody ApproveJoinRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(joinRequests.approve(joinRequestId, request));
    }

    /** The reason is required, and the database agrees - see {@link RejectJoinRequest}. */
    @PostMapping("/{joinRequestId}/reject")
    public ResponseEntity<JoinRequestResponse> reject(@PathVariable UUID joinRequestId,
            @Valid @RequestBody RejectJoinRequest request) {
        return ResponseEntity.ok(joinRequests.reject(joinRequestId, request));
    }
}
