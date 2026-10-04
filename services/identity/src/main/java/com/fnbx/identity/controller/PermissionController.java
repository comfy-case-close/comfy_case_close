package com.fnbx.identity.controller;

import com.fnbx.identity.dto.request.CreatePositionRequest;
import com.fnbx.identity.dto.request.PositionPermissionsRequest;
import com.fnbx.identity.dto.response.PermissionView;
import com.fnbx.identity.dto.response.PositionView;
import com.fnbx.identity.service.AccessManagementService;
import com.fnbx.shared.security.Permission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
public class PermissionController {
    private final AccessManagementService access;

    public PermissionController(AccessManagementService access) {
        this.access = access;
    }

    @GetMapping("/permissions")
    public List<PermissionView> dictionary() {
        return access.dictionary();
    }

    @GetMapping("/positions")
    public List<PositionView> positions(@RequestParam(required = false) UUID branchId) {
        return access.positions(branchId);
    }

    @GetMapping("/positions/{id}")
    public PositionView position(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID branchId
    ) {
        return access.position(id, branchId);
    }

    @PostMapping("/positions")
    @ResponseStatus(HttpStatus.CREATED)
    public PositionView create(@Valid @RequestBody CreatePositionRequest request) {
        return access.createPosition(request);
    }

    @PutMapping("/positions/{id}/permissions")
    public Set<Permission> replace(
            @PathVariable UUID id,
            @Valid @RequestBody PositionPermissionsRequest request
    ) {
        return access.replacePositionPermissions(id, request.permissions());
    }

    @PostMapping("/positions/{id}/permissions")
    public Set<Permission> add(
            @PathVariable UUID id,
            @Valid @RequestBody PositionPermissionsRequest request
    ) {
        return access.addPositionPermissions(id, request.permissions());
    }

    @DeleteMapping("/positions/{id}/permissions/{permission}")
    public Set<Permission> remove(@PathVariable UUID id, @PathVariable Permission permission) {
        return access.removePositionPermission(id, permission);
    }
}
