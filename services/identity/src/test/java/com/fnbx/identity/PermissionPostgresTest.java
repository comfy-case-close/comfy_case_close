package com.fnbx.identity;

import com.fasterxml.jackson.databind.*;
import com.fnbx.identity.service.*;
import com.fnbx.shared.security.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Permission administration and invalidation with real RLS, triggers and identity-only JWTs. */
@SpringBootTest(classes=IdentityApplication.class,properties={
 "fnb.security.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
 "fnb.security.jwt.revoked-token-cleanup-cron=-",
 "fnb.platform.admin-key=test-platform-admin-key-0123456789abcdef"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="FNB_PERMISSION_TEST_DB_URL",matches=".+")
class PermissionPostgresTest {
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 @Autowired JdbcTemplate jdbc;
 @Autowired TenantTransactions transactions;
 @MockBean OtpMailer mailer;
 @MockBean GoogleTokenVerifier google;
 @MockBean RegistrationMailer registrationMailer;
 UUID business,branch,other,owner,staff,adminPosition;
 String adminToken,staffToken;
 @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
  p.add("spring.datasource.url",()->System.getenv("FNB_PERMISSION_TEST_DB_URL"));
  p.add("spring.datasource.username",()->"svc_identity");
  p.add("spring.datasource.password",()->"fnbx_auth_test_password");
 }
 @BeforeEach void fixture() throws Exception {
  business=UUID.randomUUID();branch=UUID.randomUUID();other=UUID.randomUUID();owner=UUID.randomUUID();staff=UUID.randomUUID();
  sql("INSERT INTO identity.business(business_id,business_code,business_name) VALUES (?,?,'Permissions')",business,business.toString().toUpperCase());
  sql("INSERT INTO identity.branch(branch_id,business_id,branch_code,branch_name) VALUES (?,?,'A','A'),(?,?,'B','B')",branch,business,other,business);
  sql("INSERT INTO identity.staff(staff_id,business_id,employee_code,first_name,last_name,passcode_hash) VALUES (?,?,'OWNER','Owner','Test','unused'),(?,?,'STAFF','Staff','Test','unused')",owner,business,staff,business);
  adminPosition=UUID.randomUUID();
  sql("INSERT INTO identity.position(position_id,business_id,position_code,position_name) VALUES (?,?,'ADMIN','Administrator')",adminPosition,business);
  sql("INSERT INTO identity.position_permission(position_id,business_id,permission_code,scope) SELECT ?,?,permission_code,scope FROM identity.permission",adminPosition,business);
  sql("INSERT INTO identity.staff_branch_position(staff_id,branch_id,position_id,business_id) VALUES (?,?,?,?)",owner,branch,adminPosition,business);
  adminToken=token(owner,business);staffToken=token(staff,business);
 }
 @Test void positionsAloneDetermineBranchPermissionsAndInvalidateEveryServiceInstance() throws Exception {
  UUID first=position("FIRST"),second=position("SECOND");
  configure(first,Set.of("CLOSE_READ","CLOSE_OPEN"));configure(second,Set.of("CLOSE_EDIT","CLOSE_SUBMIT"));
  assign(first,second);
  assertThat(hint()).containsExactlyInAnyOrder("CLOSE_READ","CLOSE_OPEN","CLOSE_EDIT","CLOSE_SUBMIT");
  // A different process would have a separate in-memory cache but the same revision row.
  BranchAccessGuard anotherService=new BranchAccessGuard(jdbc);
  assertThat(transactions.inTenant(business,staff,()->anotherService.effective(branch))).contains(Permission.CLOSE_OPEN);
  configure(first,Set.of("CLOSE_READ"));
  assertThat(hint()).doesNotContain("CLOSE_OPEN");
  assertThatThrownBy(()->transactions.inTenant(business,staff,()->anotherService.require(branch,Permission.CLOSE_OPEN))).isInstanceOf(AccessDeniedException.class);
  configure(first,Set.of("CLOSE_READ","CLOSE_OPEN"));
  assertThat(hint()).contains("CLOSE_OPEN");
  assign(second);
  assertThatThrownBy(()->transactions.inTenant(business,staff,()->anotherService.require(branch,Permission.CLOSE_OPEN))).isInstanceOf(AccessDeniedException.class);
  assertThat(hint()).doesNotContain("CLOSE_OPEN");
  assertThat(request(get("/api/v1/auth/me"),null,staffToken,200).get("branchIds").toString())
      .doesNotContain(other.toString());
 }
 @Test void businessActsFollowPositionsAcrossBranchesAndHrCannotConfigurePermissions() throws Exception {
  UUID position=position("HR_MANAGER");configure(position,Set.of("STAFF_ASSIGN","CLOSE_READ"));
  request(put("/api/v1/branches/"+other+"/staff/"+staff+"/positions"),Map.of("positionIds",Set.of(position)),adminToken,200);
  BranchAccessGuard anotherService=new BranchAccessGuard(jdbc);
  assertThat(transactions.inTenant(business,staff,()->anotherService.effective(null))).contains(Permission.STAFF_ASSIGN);
  JsonNode me=request(get("/api/v1/auth/me"),null,staffToken,200);
  assertThat(me.get("businessPermissions").toString()).contains("STAFF_ASSIGN");
  assertThat(me.get("branchIds").toString()).doesNotContain(branch.toString());
  request(get("/api/v1/positions"),null,staffToken,403);
  request(put("/api/v1/positions/"+position+"/permissions"),Map.of("permissions",Set.of("CLOSE_VOID")),staffToken,403);
  request(put("/api/v1/branches/"+other+"/staff/"+staff+"/positions"),Map.of("positionIds",Set.of(adminPosition)),staffToken,403);
  request(post("/api/v1/branches"),Map.of("branchName","Unauthorized"),staffToken,403);
  configure(position,Set.of("STAFF_ASSIGN","BRANCH_CREATE"));
  request(post("/api/v1/branches"),Map.of("branchName","Allowed"),staffToken,201);
  configure(position,Set.of("STAFF_ASSIGN"));
  assertThat(transactions.inTenant(business,staff,()->anotherService.effective(null))).doesNotContain(Permission.BRANCH_CREATE);
  request(post("/api/v1/branches"),Map.of("branchName","Revoked"),staffToken,403);
  request(delete("/api/v1/branches/"+other+"/staff/"+staff+"/positions"),null,adminToken,200);
  assertThat(transactions.inTenant(business,staff,()->anotherService.effective(null))).doesNotContain(Permission.STAFF_ASSIGN);
 }
 @Test void businessPermissionSurvivesBranchDeactivationWhileAssignmentIsLive() throws Exception {
  UUID position=position("HR_MANAGER");configure(position,Set.of("STAFF_ASSIGN"));
  request(put("/api/v1/branches/"+other+"/staff/"+staff+"/positions"),Map.of("positionIds",Set.of(position)),adminToken,200);
  sql("UPDATE identity.branch SET is_active=false WHERE branch_id=?",other);
  assertThat(transactions.inTenant(business,staff,()->new BranchAccessGuard(jdbc).effective(null))).contains(Permission.STAFF_ASSIGN);
  assertThat(transactions.inTenant(business,staff,()->new BranchAccessGuard(jdbc).effective(other))).isEmpty();
 }
 @Test void dictionaryAndScopeCannotBeChangedByServiceRoleAndHistoriesAreImmutable() throws Exception {
  UUID position=position("BASIC");assign(position);
  for(String table:List.of("position_permission","staff_branch_position")) {
   String start=table.equals("staff_branch_position")?"assigned_at":"granted_at";
   for(String statement:List.of("DELETE FROM identity."+table,"UPDATE identity."+table+" SET "+start+"="+start+"-interval '1 day'"))
    assertThatThrownBy(()->transactions.inTenant(business,owner,()->jdbc.update(statement))).isInstanceOf(org.springframework.dao.DataAccessException.class);
  }
  assertThatThrownBy(()->transactions.inTenant(business,owner,()->jdbc.update("INSERT INTO identity.permission VALUES ('CUSTOM','BRANCH','custom')"))).isInstanceOf(org.springframework.dao.DataAccessException.class);
  // The dictionary's scope is enforced below the API.
  assertThatThrownBy(()->sql("INSERT INTO identity.position_permission(position_id,business_id,permission_code) VALUES (?,?,'PERMISSION_GRANT')",position,business)).isInstanceOf(SQLException.class);
  assertThatThrownBy(()->sql("INSERT INTO identity.position_permission(position_id,business_id,permission_code,scope) VALUES (?,?,'CLOSE_READ','BUSINESS')",position,business)).isInstanceOf(SQLException.class);
  assertThat(transactions.inTenant(business,owner,()->jdbc.queryForObject("SELECT to_regclass('identity.staff_business_permission')::text",String.class))).isNull();
 }
 @Test void foreignTenantIdsAreRejectedAndActiveFlagsInvalidateCachedAccess() throws Exception {
  UUID position=position("BASIC");assign(position);assertThat(hint()).contains("CLOSE_READ");
  UUID foreign=UUID.randomUUID();
  sql("INSERT INTO identity.business(business_id,business_code,business_name) VALUES (?,?,'Other')",foreign,foreign.toString().toUpperCase());
  request(get("/api/v1/staff").param("branchId",branch.toString()),null,token(staff,foreign),403);
  request(put("/api/v1/positions/"+position+"/permissions"),Map.of("permissions",Set.of("CLOSE_VOID")),token(owner,foreign),403);
  sql("UPDATE identity.position SET is_active=false WHERE position_id=?",position);
  assertThat(hint()).isEmpty();
 }
 @Test void meGroupsPositionsAndDeduplicatesPermissionsWithoutRequiringPermissionView() throws Exception {
  UUID first=position("FIRST"),second=position("SECOND");
  configure(first,Set.of("CLOSE_READ","CLOSE_EDIT"));configure(second,Set.of("CLOSE_READ","CLOSE_SUBMIT"));
  assign(first,second);
  JsonNode me=request(get("/api/v1/auth/me"),null,staffToken,200);
  assertThat(me.get("branchAccess").size()).isEqualTo(1);
  JsonNode access=me.get("branchAccess").get(0);
  assertThat(access.get("positions").size()).isEqualTo(2);
  for(JsonNode position:access.get("positions")) assertThat(position.get("permissions").size()).isEqualTo(2);
  assertThat(access.get("permissions").size()).isEqualTo(3);
  assertThat(access.get("permissions").toString()).contains("CLOSE_READ","CLOSE_EDIT","CLOSE_SUBMIT");
  request(get("/api/v1/staff").param("branchId",branch.toString()),null,staffToken,403);
  request(get("/api/v1/staff/"+staff).param("branchId",branch.toString()),null,staffToken,403);
  request(get("/api/v1/branches/"+branch+"/staff/access"),null,adminToken,404);
  request(get("/api/v1/branches/"+branch+"/staff/"+owner+"/access"),null,adminToken,404);
  request(get("/api/v1/branches/"+branch+"/staff/"+owner+"/positions"),null,adminToken,405);
  request(get("/api/v1/branches/"+branch+"/staff/"+owner+"/permissions"),null,adminToken,404);
  request(get("/api/v1/branches/"+branch+"/staff"),null,staffToken,200);
  request(get("/api/v1/branches/"+branch+"/staff").param("includeRevoked","true"),null,staffToken,403);
 }
 @Test void branchRosterPaginatesStaffAndIncludesEveryPositionAssignment() throws Exception {
  UUID first=position("FIRST"),second=position("SECOND");
  assign(first,second);

  String rosterUrl="/api/v1/branches/"+branch+"/staff";
  JsonNode page=request(get(rosterUrl).param("page","1").param("size","1"),null,adminToken,200);
  assertThat(page.get("totalElements").asLong()).isEqualTo(2);
  assertThat(page.get("content").size()).isEqualTo(1);
  JsonNode member=page.get("content").get(0);
  assertThat(member.get("staffId").asText()).isEqualTo(staff.toString());
  assertThat(member.get("positions").size()).isEqualTo(2);
  Set<String> positionIds=new HashSet<>();
  member.get("positions").forEach(assignment->positionIds.add(assignment.get("positionId").asText()));
  assertThat(positionIds).containsExactlyInAnyOrder(first.toString(),second.toString());

  JsonNode updated=request(put(rosterUrl+"/"+staff+"/positions"),
          Map.of("positionIds",Set.of(first)),adminToken,200);
  assertThat(updated.size()).isEqualTo(1);
  assertThat(updated.get(0).get("positions").size()).isEqualTo(1);
  JsonNode current=request(get(rosterUrl).param("page","1").param("size","1"),null,adminToken,200);
  assertThat(current.get("content").get(0).get("positions").size()).isEqualTo(1);

  JsonNode history=request(get(rosterUrl).param("includeRevoked","true")
          .param("page","1").param("size","1"),null,adminToken,200);
  assertThat(history.get("totalElements").asLong()).isEqualTo(2);
  JsonNode assignments=history.get("content").get(0).get("positions");
  assertThat(assignments.size()).isEqualTo(2);
  long revoked=0;
  for(JsonNode assignment:assignments) if(!assignment.get("revokedAt").isNull()) revoked++;
  assertThat(revoked).isEqualTo(1);
 }
 @Test void permissionViewIsEnforcedForEachReturnedBranchAndRevocationTakesEffect() throws Exception {
  UUID viewer=position("VIEWER"),second=position("SECOND");
  configure(viewer,Set.of("CLOSE_READ","PERMISSION_VIEW","STAFF_ASSIGN"));
  configure(second,Set.of("CLOSE_READ","CLOSE_EDIT"));
  assign(viewer,second);
  JsonNode result=request(get("/api/v1/staff").param("branchId",branch.toString()),null,staffToken,200);
  assertThat(result.get("content").get(0).get("branchAccess").get(0).has("permissions")).isTrue();
  assertThat(result.get("content").get(0).get("branchAccess").get(0).get("positions").get(0).has("permissions")).isTrue();
  JsonNode access=request(get("/api/v1/staff/"+staff).param("branchId",branch.toString()),null,staffToken,200)
      .get("branchAccess").get(0);
  assertThat(access.get("positions").size()).isEqualTo(2);
  assertThat(access.get("permissions").size()).isEqualTo(3);
  Map<String,Set<String>> grants=new HashMap<>();
  for(JsonNode position:access.get("positions")) {
   Set<String> codes=new HashSet<>();
   position.get("permissions").forEach(code->codes.add(code.asText()));
   grants.put(position.get("code").asText(),codes);
  }
  assertThat(grants.get("VIEWER")).containsExactlyInAnyOrder("CLOSE_READ","PERMISSION_VIEW","STAFF_ASSIGN");
  assertThat(access.get("permissions").toString()).doesNotContain("STAFF_ASSIGN");
  assertThat(grants.get("SECOND")).containsExactlyInAnyOrder("CLOSE_READ","CLOSE_EDIT");
  request(get("/api/v1/staff"),null,staffToken,403);
  request(get("/api/v1/staff").param("branchId",other.toString()),null,staffToken,403);
  request(put("/api/v1/branches/"+other+"/staff/"+owner+"/positions"),Map.of("positionIds",Set.of(adminPosition)),adminToken,200);
  request(get("/api/v1/staff/"+owner),null,staffToken,403);
  JsonNode target=request(get("/api/v1/staff/"+owner).param("branchId",branch.toString()),null,staffToken,200);
  assertThat(target.get("branchAccess").size()).isEqualTo(1);
  request(get("/api/v1/staff"),null,adminToken,200);
  request(delete("/api/v1/branches/"+branch+"/staff/"+owner+"/positions"),null,adminToken,200);
  request(get("/api/v1/staff/"+owner).param("branchId",branch.toString()),null,staffToken,404);
  configure(viewer,Set.of("CLOSE_READ"));
  request(get("/api/v1/staff/"+owner).param("branchId",branch.toString()),null,staffToken,403);
 }
 @Test void selfProfilePositionEditsRequireStaffAssignAndCannotEscalateOrPartiallySave() throws Exception {
  UUID basic=position("BASIC"),hr=position("HR");configure(hr,Set.of("STAFF_ASSIGN","CLOSE_READ"));assign(basic);
  Map<String,Object> body=new HashMap<>(Map.of("firstName","Changed","lastName","Test",
   "branchPositions",Map.of(branch.toString(),Set.of(hr))));
  request(patch("/api/v1/auth/me"),body,staffToken,403);
  assertThat(request(get("/api/v1/auth/me"),null,staffToken,200).get("firstName").asText()).isEqualTo("Staff");
  assign(hr);
  body.put("branchPositions",Map.of(branch.toString(),Set.of(adminPosition)));
  request(patch("/api/v1/auth/me"),body,staffToken,403);
  body.put("branchPositions",Map.of(branch.toString(),Set.of(hr,basic)));
  JsonNode updated=request(patch("/api/v1/auth/me"),body,staffToken,200);
  assertThat(updated.get("branchAccess").get(0).get("positions").size()).isEqualTo(2);
  assertThat(updated.get("businessPermissions").toString()).contains("STAFF_ASSIGN");
  request(patch("/api/v1/auth/me"),Map.of("firstName","Owner","lastName","Test",
   "branchPositions",Map.of(branch.toString(),Set.of())),adminToken,422);
 }
 @Test void catalogAndIncrementalGrantApisHonorPermissionsAndInvalidateEffectiveAccess() throws Exception {
  UUID basic=position("BASIC");assign(basic);
  JsonNode dictionary=request(get("/api/v1/permissions"),null,staffToken,200);
  boolean foundPermissionView=false;
  for(JsonNode entry:dictionary) {
   if(entry.get("permissionCode").asText().equals("PERMISSION_VIEW")) {
    assertThat(entry.get("scope").asText()).isEqualTo("BRANCH");
    foundPermissionView=true;
   }
  }
  assertThat(foundPermissionView).isTrue();
  request(get("/api/v1/permissions/PERMISSION_VIEW"),null,staffToken,404);
  request(get("/api/v1/positions/"+basic+"/permissions"),null,adminToken,405);
  request(get("/api/v1/positions").param("branchId",branch.toString()),null,staffToken,403);
  request(post("/api/v1/positions/"+basic+"/permissions"),Map.of("permissions",Set.of("PERMISSION_VIEW")),staffToken,403);
  request(post("/api/v1/positions/"+basic+"/permissions"),Map.of("permissions",Set.of("PERMISSION_VIEW","CLOSE_READ")),adminToken,200);
  assertThat(hint()).contains("PERMISSION_VIEW","CLOSE_READ");
  JsonNode detail=request(get("/api/v1/positions/"+basic).param("branchId",branch.toString()),null,staffToken,200);
  assertThat(detail.get("permissions").toString()).contains("PERMISSION_VIEW","CLOSE_READ");
  JsonNode catalogue=request(get("/api/v1/positions").param("branchId",branch.toString()),null,staffToken,200);
  assertThat(catalogue.toString()).contains("PERMISSION_VIEW","CLOSE_READ");
  request(get("/api/v1/positions"),null,staffToken,403);
  request(get("/api/v1/positions/"+basic),null,staffToken,403);
  request(delete("/api/v1/positions/"+basic+"/permissions/PERMISSION_VIEW"),null,adminToken,200);
  assertThat(hint()).doesNotContain("PERMISSION_VIEW");
  request(get("/api/v1/positions/"+basic).param("branchId",branch.toString()),null,staffToken,403);
  request(delete("/api/v1/positions/"+adminPosition+"/permissions/PERMISSION_GRANT"),null,adminToken,422);
 }
 @Test void directoryRejectsForeignIdsAndDisabledViewers() throws Exception {
  UUID basic=position("BASIC");assign(basic);
  request(get("/api/v1/staff/"+UUID.randomUUID()).param("branchId",branch.toString()),null,adminToken,404);
  request(get("/api/v1/staff").param("branchId",UUID.randomUUID().toString()),null,adminToken,404);
  request(get("/api/v1/staff").param("branchId",branch.toString()),null,staffToken,403);
  request(get("/api/v1/staff"),null,token(staff,UUID.randomUUID()),403);
  sql("UPDATE identity.staff SET is_active=false WHERE staff_id=?",staff);
  request(get("/api/v1/staff"),null,staffToken,403);
  request(get("/api/v1/positions"),null,staffToken,403);
 }
 private UUID position(String code) throws Exception {
  return UUID.fromString(request(post("/api/v1/positions"),Map.of("code",code,"name",code),adminToken,201).get("positionId").asText());
 }
 private void configure(UUID position,Set<String> permissions) throws Exception {
  request(put("/api/v1/positions/"+position+"/permissions"),Map.of("permissions",permissions),adminToken,200);
 }
 private void assign(UUID... positions) throws Exception {
  request(put("/api/v1/branches/"+branch+"/staff/"+staff+"/positions"),Map.of("positionIds",positions),adminToken,200);
 }
 private Set<String> hint() throws Exception {
  Set<String> result=new HashSet<>();
  for(JsonNode access:request(get("/api/v1/auth/me"),null,staffToken,200).get("branchAccess")) {
   if(access.get("branchId").asText().equals(branch.toString()))
    access.get("permissions").forEach(permission->result.add(permission.asText()));
  }
  return result;
 }
 private JsonNode request(MockHttpServletRequestBuilder r,Object body,String bearer,int expected) throws Exception {
  r.header("Authorization",bearer);if(body!=null)r.contentType("application/json").content(json.writeValueAsString(body));
  var response=mvc.perform(r).andReturn().getResponse();assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(expected);
  return response.getContentAsString().isEmpty()?json.getNodeFactory().nullNode():json.readTree(response.getContentAsString());
 }
 private String token(UUID user,UUID tenant) throws Exception {
  Instant now=Instant.now();var claims=new JWTClaimsSet.Builder().issuer("fnbx-identity").subject("test")
   .issueTime(java.util.Date.from(now)).expirationTime(java.util.Date.from(now.plusSeconds(600))).jwtID(UUID.randomUUID().toString())
   .claim("uid",user.toString()).claim("business_id",tenant.toString()).claim("type","access")
   .claim("branch_roles",Map.of(other.toString(),"ADMIN")).claim("permissions",List.of("PERMISSION_GRANT")).build();
  var token=new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),claims);token.sign(new MACSigner(new byte[32]));return "Bearer "+token.serialize();
 }
 private static void sql(String statement,Object... args) throws Exception {
  try(var connection=DriverManager.getConnection(System.getenv("FNB_PERMISSION_TEST_DB_URL"),"postgres","local-test-only");var q=connection.prepareStatement(statement)) {
   for(int i=0;i<args.length;i++)q.setObject(i+1,args[i]);q.executeUpdate();
  }
 }
}
