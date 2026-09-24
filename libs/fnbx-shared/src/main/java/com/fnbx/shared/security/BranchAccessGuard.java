package com.fnbx.shared.security;

import com.fnbx.shared.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Duration;

/** Live permission checks shared by domain services; never reads JWT authorization claims. */
public class BranchAccessGuard {
 private final JdbcTemplate jdbc;
 private final Map<Key, Entry> cache = new ConcurrentHashMap<>();
 private static final long TTL = Duration.ofSeconds(5).toNanos();
 private record Key(UUID business, UUID staff, UUID branch) {}
 private record Entry(long revision, long expires, Set<Permission> permissions) {}
 public BranchAccessGuard(JdbcTemplate jdbc) { this.jdbc = jdbc; }

 @Transactional(readOnly=true)
 public Permission require(UUID branchId, Permission permission) {
  if (branchId == null || permission.scope() != Permission.Scope.BRANCH
      || !effective(branchId).contains(permission)) throw new AccessDeniedException("Branch permission denied");
  return permission;
 }
 @Transactional(readOnly=true)
 public Permission requireBusiness(Permission permission) {
  if (permission.scope() != Permission.Scope.BUSINESS || !effective(null).contains(permission))
   throw new AccessDeniedException("Business permission denied");
  return permission;
 }
 @Transactional(readOnly=true)
 public Set<Permission> effective(UUID branchId) {
  var tenant=TenantContext.current();
  Key key=new Key(tenant.businessId(),tenant.userId(),branchId);
  List<Long> revisions=jdbc.queryForList("""
   SELECT coalesce(r.revision,0) FROM identity.staff s
   JOIN identity.business b ON b.business_id=s.business_id AND b.is_active
   LEFT JOIN identity.permission_revision r ON r.business_id=b.business_id
   WHERE s.staff_id=? AND s.business_id=? AND s.is_active
   AND (?::uuid IS NULL OR EXISTS(SELECT 1 FROM identity.branch br WHERE br.branch_id=? AND br.business_id=b.business_id AND br.is_active))
   """,Long.class,key.staff(),key.business(),branchId,branchId);
  if(revisions.isEmpty()) { cache.remove(key); return Set.of(); }
  long revision=revisions.getFirst(), now=System.nanoTime();
  Entry hit=cache.get(key);
  if(hit!=null && hit.revision()==revision && now<hit.expires()) return hit.permissions();
  List<String> codes;
  if(branchId==null) {
   codes=jdbc.queryForList("""
    SELECT permission_code FROM identity.staff_business_permission
    WHERE staff_id=? AND business_id=? AND revoked_at IS NULL AND granted_at<=clock_timestamp()
    """,String.class,key.staff(),key.business());
  } else {
   codes=jdbc.queryForList("""
    SELECT permission_code FROM identity.staff_branch_permission
     WHERE staff_id=? AND branch_id=? AND business_id=? AND revoked_at IS NULL AND granted_at<=clock_timestamp()
    UNION
    SELECT p.permission_code FROM identity.staff_branch_position a
     JOIN identity.staff_position pos ON pos.position_id=a.position_id AND pos.business_id=a.business_id AND pos.is_active
     JOIN identity.position_permission p ON p.position_id=a.position_id AND p.business_id=a.business_id
     WHERE a.staff_id=? AND a.branch_id=? AND a.business_id=?
     AND a.revoked_at IS NULL AND a.assigned_at<=clock_timestamp()
     AND p.revoked_at IS NULL AND p.granted_at<=clock_timestamp()
    """,String.class,key.staff(),branchId,key.business(),key.staff(),branchId,key.business());
  }
  Set<Permission> permissions=new HashSet<>();
  for(String code:codes) permissions.add(Permission.valueOf(code));
  Set<Permission> result=Set.copyOf(permissions);
  if(cache.size()>10000) cache.clear();
  cache.put(key,new Entry(revision,now+TTL,result));
  return result;
 }
 /** Filtering by a branch narrows this set; it cannot expand authority. */
 @Transactional(readOnly=true)
 public Set<UUID> branches(Permission permission) {
  if(permission.scope()!=Permission.Scope.BRANCH) throw new IllegalArgumentException("Branch permission required");
  var tenant=TenantContext.current();
  List<UUID> candidates=jdbc.queryForList("""
   SELECT b.branch_id FROM identity.branch b WHERE b.business_id=? AND b.is_active
   AND (EXISTS(SELECT 1 FROM identity.staff_branch_position a WHERE a.branch_id=b.branch_id AND a.staff_id=? AND a.revoked_at IS NULL)
    OR EXISTS(SELECT 1 FROM identity.staff_branch_permission g WHERE g.branch_id=b.branch_id AND g.staff_id=? AND g.revoked_at IS NULL))
   """,UUID.class,tenant.businessId(),tenant.userId(),tenant.userId());
  Set<UUID> result=new HashSet<>();
  for(UUID branch:candidates) if(effective(branch).contains(permission)) result.add(branch);
  return Set.copyOf(result);
 }
}
