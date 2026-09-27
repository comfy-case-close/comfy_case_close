package com.fnbx.identity.controller;

import com.fnbx.identity.dto.request.*;
import com.fnbx.identity.dto.response.MyPermissionsResponse;
import com.fnbx.identity.service.AccessManagementService;
import com.fnbx.shared.security.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.*;

@RestController
public class PermissionController {
 private final AccessManagementService access;
 public PermissionController(AccessManagementService access) { this.access=access; }
 @GetMapping("/me/permissions")
 public MyPermissionsResponse mine(@RequestParam(required=false) UUID branchId) { return access.mine(branchId); }
 @GetMapping("/permissions")
 public List<AccessManagementService.PermissionView> dictionary() { return access.dictionary(); }
 @GetMapping("/positions")
 public List<AccessManagementService.PositionView> positions() { return access.positions(); }
 @PostMapping("/positions") @ResponseStatus(HttpStatus.CREATED)
 public AccessManagementService.PositionView create(@Valid @RequestBody CreatePositionRequest request) { return access.createPosition(request); }
 @GetMapping("/positions/{id}/permissions")
 public Set<Permission> positionPermissions(@PathVariable UUID id) { return access.positionPermissions(id); }
 @PutMapping("/positions/{id}/permissions")
 public Set<Permission> replace(@PathVariable UUID id,@Valid @RequestBody PositionPermissionsRequest request) { return access.replacePositionPermissions(id,request.permissions()); }
 @PutMapping("/staff/{staffId}/permissions/{permission}") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void grantBranch(@PathVariable UUID staffId,@PathVariable Permission permission,@RequestHeader(BranchHeader.NAME) UUID branchId) {
  access.branchGrant(staffId,branchId,permission,true);
 }
 @DeleteMapping("/staff/{staffId}/permissions/{permission}") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void revokeBranch(@PathVariable UUID staffId,@PathVariable Permission permission,@RequestHeader(BranchHeader.NAME) UUID branchId) {
  access.branchGrant(staffId,branchId,permission,false);
 }
 @PutMapping("/staff/{staffId}/business-permissions/{permission}") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void grantBusiness(@PathVariable UUID staffId,@PathVariable Permission permission) { access.businessGrant(staffId,permission,true); }
 @DeleteMapping("/staff/{staffId}/business-permissions/{permission}") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void revokeBusiness(@PathVariable UUID staffId,@PathVariable Permission permission) { access.businessGrant(staffId,permission,false); }
 @GetMapping("/staff/{staffId}/permissions")
 public List<Map<String,Object>> branchGrants(@PathVariable UUID staffId,@RequestHeader(BranchHeader.NAME) UUID branchId,
    @RequestParam(defaultValue="false") boolean history) { return access.grants(staffId,branchId,history); }
 @GetMapping("/staff/{staffId}/business-permissions")
 public List<Map<String,Object>> businessGrants(@PathVariable UUID staffId,@RequestParam(defaultValue="false") boolean history) { return access.grants(staffId,null,history); }
}
