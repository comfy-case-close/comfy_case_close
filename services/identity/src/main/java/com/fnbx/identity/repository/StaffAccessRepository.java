package com.fnbx.identity.repository;

import com.fnbx.identity.entity.BranchMember;
import com.fnbx.shared.security.Permission;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Append-only assignment/grant writes owned by identity-service. */
@Repository
public class StaffAccessRepository {
 private final JdbcTemplate jdbc;
 public StaffAccessRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
 public Set<UUID> branchesFor(UUID staffId) {
  return Set.copyOf(jdbc.queryForList("""
   SELECT b.branch_id FROM identity.branch b WHERE b.is_active AND EXISTS(
    SELECT 1 FROM identity.staff_branch_position a
    JOIN identity.position p ON p.position_id=a.position_id AND p.business_id=a.business_id AND p.is_active
    WHERE a.branch_id=b.branch_id AND a.business_id=b.business_id AND a.staff_id=?
      AND a.revoked_at IS NULL AND a.assigned_at<=clock_timestamp())
   """,UUID.class,staffId));
 }
 @Transactional
 public void assignPosition(UUID staffId, UUID branchId, UUID businessId, UUID positionId) {
  lockStaff(staffId);
  jdbc.update("""
   INSERT INTO identity.staff_branch_position(staff_id,branch_id,business_id,position_id)
    VALUES(?,?,?,?) ON CONFLICT(staff_id,branch_id,position_id) WHERE revoked_at IS NULL DO NOTHING
   """,staffId,branchId,businessId,positionId);
 }
 @Transactional
 public void replacePositions(UUID staffId,UUID branchId,UUID businessId,Set<UUID> positions) {
  lockStaff(staffId);
  for(UUID current:jdbc.queryForList("SELECT position_id FROM identity.staff_branch_position WHERE staff_id=? AND branch_id=? AND revoked_at IS NULL",UUID.class,staffId,branchId))
   if(!positions.contains(current)) revokePosition(staffId,branchId,current);
  for(UUID position:positions) assignPosition(staffId,branchId,businessId,position);
 }
 public void revokePosition(UUID staffId,UUID branchId,UUID positionId) {
  jdbc.update("UPDATE identity.staff_branch_position SET revoked_at=greatest(clock_timestamp(),assigned_at) WHERE staff_id=? AND branch_id=? AND position_id=? AND revoked_at IS NULL",staffId,branchId,positionId);
 }
 @Transactional
 public boolean revokePositions(UUID staffId,UUID branchId) {
  lockStaff(staffId);
  return jdbc.update("UPDATE identity.staff_branch_position SET revoked_at=greatest(clock_timestamp(),assigned_at) WHERE staff_id=? AND branch_id=? AND revoked_at IS NULL",staffId,branchId)>0;
 }
 public void lockStaff(UUID staffId) {
  jdbc.queryForObject("SELECT staff_id FROM identity.staff WHERE staff_id=? FOR UPDATE",UUID.class,staffId);
 }
 @Transactional
 public void grantOwnerPosition(UUID staffId,UUID branchId,UUID businessId) {
  UUID positionId=UUID.randomUUID();
  jdbc.update("INSERT INTO identity.position(position_id,business_id,position_code,position_name) VALUES (?,?,'ADMIN','Administrator')",positionId,businessId);
  for(Permission permission:Permission.values())
   jdbc.update("INSERT INTO identity.position_permission(position_id,business_id,permission_code,scope) VALUES (?,?,?,?)",positionId,businessId,permission.name(),permission.scope().name());
  assignPosition(staffId,branchId,businessId,positionId);
 }
 public void lockBusiness(UUID businessId) {
  jdbc.queryForObject("SELECT business_id FROM identity.business WHERE business_id=? FOR UPDATE",UUID.class,businessId);
 }
 private static final String MEMBERS = """
   SELECT a.*,s.employee_code,s.first_name,s.last_name,s.email,s.is_active
   FROM identity.staff_branch_position a JOIN identity.staff s ON s.staff_id=a.staff_id
   WHERE a.branch_id=? AND (? OR a.revoked_at IS NULL)
   ORDER BY s.employee_code,a.assigned_at DESC,a.assignment_id
   """;
 public List<BranchMember> membersOf(UUID branchId,boolean history) { return membersOf(branchId,history,Integer.MAX_VALUE,0); }
 public List<BranchMember> membersOf(UUID branchId,boolean history,int limit,long offset) {
  return jdbc.query(MEMBERS+" LIMIT ? OFFSET ?",(rs,n)->new BranchMember(
   rs.getObject("staff_id",UUID.class),rs.getObject("branch_id",UUID.class),
   rs.getString("employee_code"),rs.getString("first_name"),rs.getString("last_name"),rs.getString("email"),
   rs.getBoolean("is_active"),rs.getObject("position_id",UUID.class),
   rs.getTimestamp("assigned_at").toInstant(),rs.getTimestamp("revoked_at")==null?null:rs.getTimestamp("revoked_at").toInstant()),branchId,history,limit,offset);
 }
 public long countMembersOf(UUID branchId,boolean history) {
  return jdbc.queryForObject("SELECT count(*) FROM identity.staff_branch_position WHERE branch_id=? AND (? OR revoked_at IS NULL)",Long.class,branchId,history);
 }
 public boolean activePosition(UUID positionId) {
  return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM identity.position WHERE position_id=? AND is_active)",Boolean.class,positionId));
 }
 public Set<Permission> businessPermissions(UUID positionId) {
  Set<Permission> permissions=new HashSet<>();
  for(String code:jdbc.queryForList("SELECT permission_code FROM identity.position_permission WHERE position_id=? AND scope='BUSINESS' AND revoked_at IS NULL AND granted_at<=clock_timestamp()",String.class,positionId))
   permissions.add(Permission.valueOf(code));
  return Set.copyOf(permissions);
 }
}
