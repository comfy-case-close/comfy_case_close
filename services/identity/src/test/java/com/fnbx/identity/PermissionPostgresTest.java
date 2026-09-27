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
 UUID business,branch,other,owner,staff;
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
  sql("INSERT INTO identity.staff_business_permission(staff_id,business_id,permission_code) SELECT ?,?,permission_code FROM identity.permission WHERE scope='BUSINESS'",owner,business);
  adminToken=token(owner,business);staffToken=token(staff,business);
 }
 @Test void positionsUnionAndDirectGrantsInvalidateEveryServiceInstance() throws Exception {
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
  request(put("/api/v1/staff/"+staff+"/permissions/CLOSE_OPEN").header("X-Branch-Id",branch),null,adminToken,204);
  assertThat(hint()).contains("CLOSE_OPEN");
  configure(first,Set.of("CLOSE_READ","CLOSE_OPEN"));
  request(delete("/api/v1/staff/"+staff+"/permissions/CLOSE_OPEN").header("X-Branch-Id",branch),null,adminToken,204);
  assertThat(hint()).contains("CLOSE_OPEN"); // Still granted through the first position.
  configure(first,Set.of("CLOSE_READ"));
  assertThat(hint()).doesNotContain("CLOSE_OPEN");
  request(get("/api/v1/me/permissions").param("branchId",other.toString()),null,staffToken,403);
 }
 @Test void businessActsRequireBusinessGrantsAndHrCannotConfigurePermissions() throws Exception {
  UUID position=position("HR");assign(position);
  request(put("/api/v1/staff/"+staff+"/business-permissions/STAFF_ASSIGN"),null,adminToken,204);
  request(get("/api/v1/positions"),null,staffToken,200);
  request(put("/api/v1/positions/"+position+"/permissions"),Map.of("permissions",Set.of("CLOSE_VOID")),staffToken,403);
  request(put("/api/v1/staff/"+staff+"/business-permissions/PERMISSION_GRANT"),null,staffToken,403);
  request(post("/api/v1/branches"),Map.of("branchName","Unauthorized"),staffToken,403);
  request(put("/api/v1/staff/"+staff+"/permissions/PERMISSION_GRANT").header("X-Branch-Id",branch),null,adminToken,400);
  request(put("/api/v1/positions/"+position+"/permissions"),Map.of("permissions",Set.of("PERMISSION_GRANT")),adminToken,400);
  request(put("/api/v1/staff/"+staff+"/business-permissions/BRANCH_CREATE"),null,adminToken,204);
  request(post("/api/v1/branches"),Map.of("branchName","Allowed"),staffToken,201);
  request(delete("/api/v1/staff/"+staff+"/business-permissions/BRANCH_CREATE"),null,adminToken,204);
  request(post("/api/v1/branches"),Map.of("branchName","Revoked"),staffToken,403);
 }
 @Test void dictionaryAndScopeCannotBeChangedByServiceRoleAndHistoriesAreImmutable() throws Exception {
  UUID position=position("BASIC");assign(position);
  request(put("/api/v1/staff/"+staff+"/permissions/CLOSE_READ").header("X-Branch-Id",branch),null,adminToken,204);
  for(String table:List.of("position_permission","staff_branch_permission","staff_business_permission","staff_branch_position")) {
   String start=table.equals("staff_branch_position")?"assigned_at":"granted_at";
   for(String statement:List.of("DELETE FROM identity."+table,"UPDATE identity."+table+" SET "+start+"="+start+"-interval '1 day'"))
    assertThatThrownBy(()->transactions.inTenant(business,owner,()->jdbc.update(statement))).isInstanceOf(org.springframework.dao.DataAccessException.class);
  }
  assertThatThrownBy(()->transactions.inTenant(business,owner,()->jdbc.update("INSERT INTO identity.permission VALUES ('CUSTOM','BRANCH','custom')"))).isInstanceOf(org.springframework.dao.DataAccessException.class);
  // Branch/business scope is also enforced below the API.
  assertThatThrownBy(()->sql("INSERT INTO identity.staff_branch_permission(staff_id,branch_id,business_id,permission_code) VALUES (?,?,?,'PERMISSION_GRANT')",staff,branch,business)).isInstanceOf(SQLException.class);
  assertThatThrownBy(()->sql("INSERT INTO identity.staff_business_permission(staff_id,business_id,permission_code) VALUES (?,?,'CLOSE_READ')",staff,business)).isInstanceOf(SQLException.class);
 }
 @Test void foreignTenantIdsAreRejectedAndActiveFlagsInvalidateCachedAccess() throws Exception {
  UUID position=position("BASIC");assign(position);assertThat(hint()).contains("CLOSE_READ");
  UUID foreign=UUID.randomUUID();
  sql("INSERT INTO identity.business(business_id,business_code,business_name) VALUES (?,?,'Other')",foreign,foreign.toString().toUpperCase());
  request(get("/api/v1/me/permissions").param("branchId",branch.toString()),null,token(staff,foreign),403);
  request(put("/api/v1/positions/"+position+"/permissions"),Map.of("permissions",Set.of("CLOSE_VOID")),token(owner,foreign),403);
  sql("UPDATE identity.staff_position SET is_active=false WHERE position_id=?",position);
  request(get("/api/v1/me/permissions").param("branchId",branch.toString()),null,staffToken,403);
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
  request(get("/api/v1/me/permissions").param("branchId",branch.toString()),null,staffToken,200).get("permissions").forEach(p->result.add(p.asText()));return result;
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
