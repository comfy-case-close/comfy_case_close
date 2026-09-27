package com.fnbx.identity.controller;

import java.util.UUID;
import com.fnbx.identity.dto.request.UpdateBusinessRequest;
import com.fnbx.identity.dto.response.BusinessResponse;
import com.fnbx.identity.dto.response.MessageResponse;
import com.fnbx.identity.service.BusinessService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** Existing tenant management. Platform-key deactivation; authenticated tenant reads and updates. */
@RestController
@RequestMapping("/businesses")
@Validated
public class BusinessController {

    private final BusinessService businesses;

    public BusinessController(BusinessService businesses) { this.businesses = businesses; }

    // ---- platform -----------------------------------------------------------

    /** Deactivates a tenant. Platform key - deliberately not something the tenant can do to itself. */
    @DeleteMapping("/{businessId}")
    public ResponseEntity<MessageResponse> deactivate(@PathVariable UUID businessId) {
        return ResponseEntity.ok(businesses.deactivate(businessId));
    }

    // ---- the caller's own business ------------------------------------------

    @GetMapping("/me")
    public ResponseEntity<BusinessResponse> current() {
        return ResponseEntity.ok(businesses.current());
    }

    /** ADMIN only. The business code and the active flag are not editable here. */
    @PatchMapping("/me")
    public ResponseEntity<BusinessResponse> update(@Valid @RequestBody UpdateBusinessRequest request) {
        return ResponseEntity.ok(businesses.update(request));
    }
}
