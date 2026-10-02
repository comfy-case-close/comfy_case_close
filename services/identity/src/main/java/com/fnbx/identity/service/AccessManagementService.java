package com.fnbx.identity.service;

import com.fnbx.identity.dto.request.CreatePositionRequest;
import com.fnbx.identity.dto.response.MyPermissionsResponse;
import com.fnbx.identity.repository.StaffAccessRepository;
import com.fnbx.identity.repository.StaffRepository;
import com.fnbx.identity.exception.OnboardingExceptions;
import com.fnbx.shared.security.*;
import com.fnbx.shared.tenant.TenantContext;
import com.fnbx.shared.exception.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;

@Service
@Transactional
public class AccessManagementService {
 private final BranchAccessGuard guard;
 private final StaffAccessRepository access;
 private final StaffRepository staff;
 private final JdbcTemplate jdbc;
 public AccessManagementService(BranchAccessGuard guard,StaffAccessRepository access,StaffRepository staff,JdbcTemplate jdbc) {
  this.guard=guard;this.access=access;this.staff=staff;this.jdbc=jdbc;
 }
 public record PositionView(UUID positionId,String code,String name,boolean active) {}
 public record PermissionView(String permissionCode,String scope,String description) {}
 @Transactional(readOnly=true)
 public MyPermissionsResponse mine(UUID branchId) {
  UUID staffId=TenantContext.current().userId();
  Set<UUID> branches=access.branchesFor(staffId);
  if(branchId!=null && !branches.contains(branchId)) throw new AccessDeniedException("Branch access denied");
  return new MyPermissionsResponse(branchId,branches,branchId==null?Set.of():guard.effective(branchId),guard.effective(null));
 }
 @Transactional(readOnly=true)
 public List<PermissionView> dictionary() {
  return jdbc.query("SELECT permission_code,scope,description FROM identity.permission ORDER BY scope,permission_code",
   (r,n)->new PermissionView(r.getString(1),r.getString(2),r.getString(3)));
 }
 private void requirePositionManagement() {
  Set<Permission> permissions=guard.effective(null);
  if(!permissions.contains(Permission.STAFF_ASSIGN) && !permissions.contains(Permission.PERMISSION_GRANT))
   throw new AccessDeniedException("Position management denied");
 }
 @Transactional(readOnly=true)
 public List<PositionView> positions() {
  requirePositionManagement();
  return jdbc.query("SELECT position_id,position_code,position_name,is_active FROM identity.position ORDER BY position_code",
   (r,n)->new PositionView(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getBoolean(4)));
 }
 public PositionView createPosition(CreatePositionRequest request) {
  guard.requireBusiness(Permission.PERMISSION_GRANT);
  UUID id=UUID.randomUUID(),business=TenantContext.current().businessId();
  String code=request.code().trim().toUpperCase(Locale.ROOT),name=request.name().trim();
  jdbc.update("INSERT INTO identity.position(position_id,business_id,position_code,position_name) VALUES(?,?,?,?)",id,business,code,name);
  replacePositionPermissions(id,Set.of(Permission.CLOSE_READ,Permission.CLOSE_EDIT,
   Permission.CLOSE_SUBMIT,Permission.DENOMINATION_WRITE,Permission.MOVEMENT_ADD,Permission.WITHDRAWAL_RECORD));
  return new PositionView(id,code,name,true);
 }
 @Transactional(readOnly=true)
 public Set<Permission> positionPermissions(UUID positionId) {
  requirePositionManagement();requirePosition(positionId);
  Set<Permission> result=new HashSet<>();
  for(String code:jdbc.queryForList("SELECT permission_code FROM identity.position_permission WHERE position_id=? AND revoked_at IS NULL",String.class,positionId))
   result.add(Permission.valueOf(code));
  return Set.copyOf(result);
 }
 public Set<Permission> replacePositionPermissions(UUID positionId,Set<Permission> permissions) {
  guard.requireBusiness(Permission.PERMISSION_GRANT);requirePosition(positionId);
  access.lockBusiness(TenantContext.current().businessId());
  jdbc.queryForObject("SELECT position_id FROM identity.position WHERE position_id=? FOR UPDATE",UUID.class,positionId);
  for(String existing:jdbc.queryForList("SELECT permission_code FROM identity.position_permission WHERE position_id=? AND revoked_at IS NULL",String.class,positionId))
   if(!permissions.contains(Permission.valueOf(existing))) jdbc.update("UPDATE identity.position_permission SET revoked_at=greatest(clock_timestamp(),granted_at) WHERE position_id=? AND permission_code=? AND revoked_at IS NULL",positionId,existing);
  for(Permission permission:permissions) jdbc.update("""
   INSERT INTO identity.position_permission(position_id,business_id,permission_code,scope) VALUES(?,?,?,?)
   ON CONFLICT(position_id,permission_code) WHERE revoked_at IS NULL DO NOTHING
   """,positionId,TenantContext.current().businessId(),permission.name(),permission.scope().name());
  if(!staff.hasLiveAdmin()) throw OnboardingExceptions.lastActiveAdmin();
  return Set.copyOf(permissions);
 }
 private void requirePosition(UUID id) {
  if(!access.activePosition(id)) throw new AppException(ErrorCode.VALIDATION_FAILED,"Select an active position in this business");
 }
}
