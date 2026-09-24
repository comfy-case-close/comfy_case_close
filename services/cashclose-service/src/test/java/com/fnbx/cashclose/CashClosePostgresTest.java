package com.fnbx.cashclose;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.DriverManager;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Real JWT filter, service-role RLS, JPA, and current DDL; no repository mocks. */
@SpringBootTest(classes = CashCloseApplication.class, properties = {
        "fnb.security.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "FNB_CASHCLOSE_TEST_DB_URL", matches = ".+")
class CashClosePostgresTest {
    @org.springframework.boot.test.mock.mockito.MockBean com.fnbx.mail.MailTransport mailTransport;
    @Autowired com.fnbx.cashclose.repository.CashCloseEmailRecipientsRepository emailRecipients;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    UUID business, branch, otherBranch, staff, shift;
    String token;
    static final String BASE = "/api/v1/cash-closes";

    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", () -> System.getenv("FNB_CASHCLOSE_TEST_DB_URL"));
        p.add("spring.datasource.username", () -> "svc_cashclose");
        p.add("spring.datasource.password", () -> "cashclose-test-only");
    }

    @BeforeEach void fixture() throws Exception {
        business = UUID.randomUUID(); branch = UUID.randomUUID(); otherBranch = UUID.randomUUID();
        staff = UUID.randomUUID(); shift = UUID.randomUUID();
        sql("INSERT INTO identity.business(business_id,business_code,business_name) VALUES (?,?,'API test')", business, business.toString().toUpperCase());
        sql("INSERT INTO identity.branch(branch_id,business_id,branch_code,branch_name) VALUES (?,?,'A','A'), (?,?,'B','B')", branch,business,otherBranch,business);
        sql("INSERT INTO identity.staff(staff_id,business_id,employee_code,first_name,last_name,passcode_hash) VALUES (?,?,'API','API','Test','unused')", staff,business);
        sql("INSERT INTO identity.staff_branch_permission(staff_id,branch_id,business_id,permission_code) SELECT ?,?,?,permission_code FROM identity.permission WHERE scope='BRANCH'",staff,branch,business);
        sql("INSERT INTO identity.staff_branch_permission(staff_id,branch_id,business_id,permission_code) SELECT ?,?,?,permission_code FROM identity.permission WHERE permission_code IN ('CLOSE_READ','CLOSE_OPEN','CLOSE_EDIT','CLOSE_SUBMIT','DENOMINATION_WRITE','MOVEMENT_ADD','WITHDRAWAL_RECORD')",staff,otherBranch,business);
        sql("INSERT INTO identity.shift_type(shift_type_id,business_id,shift_code,shift_name,submit_deadline) VALUES (?,?,'AM','Morning','23:59')", shift,business);
        token = bearer(business, staff, Map.of(branch.toString(),"ADMIN",otherBranch.toString(),"STAFF"));
    }

    @Test void draftCountLedgerDecisionsAndVoidFollowDdl() throws Exception {
        request(get(BASE + "/shift-types"), null, 200);
        request(get(BASE + "/denominations"), null, 200);
        request(get(BASE + "/movement-kinds").param("businessDate","2026-09-21"), null, 200);
        String id = open(branch, 201).get("cashCloseId").asText();
        open(branch, 422);
        request(post(BASE+"/"+id+"/submit"), null, 422);
        request(patch(BASE+"/"+id), "{\"posExpectedCash\":500000,\"withdrawalAmount\":100000}", 200);
        String counts = "{\"counts\":[{\"faceValue\":500000,\"quantity\":1}]}";
        request(put(BASE+"/"+id+"/denominations"), counts, 200);
        assertThat(request(put(BASE+"/"+id+"/denominations"), counts, 200).get("countedCash").decimalValue()).isEqualByComparingTo("500000");
        JsonNode close = request(get(BASE+"/"+id), null, 200);
        assertThat(close.get("cashRemaining").decimalValue()).isEqualByComparingTo("400000");
        String movement = request(post(BASE+"/"+id+"/movements"), "{\"kindCode\":\"POS_ERROR\",\"amount\":10000,\"differenceDirection\":\"OVER\",\"description\":\"POS correction\"}",201).get("movementId").asText();
        assertThat(request(patch(BASE+"/movements/"+movement), "{\"amount\":20000}",200).get("signedAmount").decimalValue()).isEqualByComparingTo("20000");
        request(post(BASE+"/"+id+"/submit"), null, 422);
        request(post(BASE+"/movements/"+movement+"/approve"), null, 200);
        assertThat(request(get(BASE+"/"+id), null, 200).get("explainedDifference").decimalValue()).isEqualByComparingTo("20000");
        request(post(BASE+"/"+id+"/submit"), "{\"note\":\"done\"}",200);
        request(put(BASE+"/"+id+"/denominations"), counts,409);
        request(post(BASE+"/"+id+"/reject"), "{\"note\":\"recount\"}",200);
        request(post(BASE+"/"+id+"/reopen"), "{\"note\":\"recount\"}",200);
        request(post(BASE+"/movements/"+movement+"/reopen"), "{\"note\":\"correction\"}",200);
        request(post(BASE+"/movements/"+movement+"/reject"), "{\"note\":\"not needed\"}",200);
        request(post(BASE+"/"+id+"/submit"), null,200);
        request(post(BASE+"/"+id+"/approve"), null,200);
        request(post(BASE+"/"+id+"/movements"), "{\"kindCode\":\"TIP_DIRECT\",\"amount\":1000}",422);
        assertThat(request(get(BASE+"/"+id+"/history"),null,200).size()).isEqualTo(5);
        assertThat(request(get(BASE+"/"+id+"/movement-history"),null,200).size()).isEqualTo(3);
        assertThat(request(get(BASE+"/"+id+"/day-summary"),null,200).get("closes").size()).isEqualTo(1);
        request(post(BASE+"/"+id+"/void"), "{\"note\":\"redo shift\"}",200);
        String replacement = open(branch,201).get("cashCloseId").asText();
        assertThat(replacement).isNotEqualTo(id);
    }

    @Test void submissionEmailsActualSubmitterAndOnlyLiveStoreManagersAtThisBranch() throws Exception {
        UUID position = UUID.randomUUID(), nameOnly = UUID.randomUUID();
        sql("INSERT INTO identity.staff_position(position_id,business_id,position_code,position_name) VALUES (?,?,'STORE_MANAGER','Renamed title'),(?,?,'OTHER','Store Manager')",
                position,business,nameOnly,business);
        UUID manager = emailStaff("manager@example.test");
        UUID otherManager = emailStaff("other@example.test"), former = emailStaff("former@example.test");
        UUID inactive = emailStaff("inactive@example.test"), onlyName = emailStaff("name-only@example.test");
        UUID submitter = emailStaff("submitter@example.test");
        for (UUID member : java.util.List.of(manager,former,inactive,submitter)) assignPosition(member,branch,position);
        assignPosition(otherManager,otherBranch,position);
        assignPosition(onlyName,branch,nameOnly);
        sql("UPDATE identity.staff_branch_position SET revoked_at=clock_timestamp() WHERE staff_id=?",former);
        sql("UPDATE identity.staff SET is_active=false WHERE staff_id=?",inactive);
        sql("UPDATE identity.staff SET email='creator@example.test' WHERE staff_id=?",staff);
        sql("INSERT INTO identity.staff_branch_permission(staff_id,branch_id,business_id,permission_code) VALUES (?,?,?,'CLOSE_SUBMIT')",submitter,branch,business);
        String id = open(branch,201).get("cashCloseId").asText();
        request(put(BASE+"/"+id+"/denominations"),"{\"counts\":[{\"faceValue\":500000,\"quantity\":1}]}",200);
        token = bearer(business,submitter,Map.of());
        request(post(BASE+"/"+id+"/submit"),null,200);
        var capture = org.mockito.ArgumentCaptor.forClass(com.fnbx.mail.EmailMessage.class);
        org.mockito.Mockito.verify(mailTransport,org.mockito.Mockito.timeout(5000).times(2)).send(capture.capture());
        assertThat(capture.getAllValues()).extracting(m -> m.to().toLowerCase(java.util.Locale.ROOT))
                .containsExactlyInAnyOrder("manager@example.test","submitter@example.test");
        for (var message : capture.getAllValues()) {
            assertThat(message.html()).contains(id);
            assertThat(message.subject()).contains("A", "AM");
            assertThat(message.text()).contains(message.to().equals("submitter@example.test") ? "/history?" : "/approvals?");
        }
        // Service-role RLS still applies even if a caller passes IDs from another tenant.
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        com.fnbx.shared.tenant.TenantContext.runAs(com.fnbx.shared.tenant.TenantContext.of(UUID.randomUUID(),staff), () ->
                transaction.execute(status -> {
                    assertThat(emailRecipients.managerEmails(business,branch)).isEmpty();
                    return null;
                }));
        sql("UPDATE identity.staff_position SET is_active=false WHERE position_id=?",position);
        com.fnbx.shared.tenant.TenantContext.runAs(com.fnbx.shared.tenant.TenantContext.of(business,staff), () ->
                transaction.execute(status -> {
                    assertThat(emailRecipients.managerEmails(business,branch)).isEmpty();
                    return null;
                }));
    }

    private UUID emailStaff(String email) throws Exception {
        UUID id = UUID.randomUUID();
        sql("INSERT INTO identity.staff(staff_id,business_id,employee_code,first_name,last_name,passcode_hash,email) VALUES (?,?,?,'Email','Test','unused',?)",
                id,business,id.toString(),email);
        return id;
    }

    private void assignPosition(UUID member, UUID atBranch, UUID position) throws Exception {
        sql("INSERT INTO identity.staff_branch_position(staff_id,branch_id,position_id,business_id) VALUES (?,?,?,?)",member,atBranch,position,business);
    }

    @Test void branchScopeAndLiveRevocationOverrideStaleToken() throws Exception {
        String id = open(branch,201).get("cashCloseId").asText();
        request(patch(BASE+"/"+id).header("X-Branch-Id",otherBranch),"{\"withdrawalAmount\":1}",403);
        String other = open(otherBranch,201).get("cashCloseId").asText();
        request(post(BASE+"/"+other+"/approve").header("X-Branch-Id",otherBranch),null,403);
        sql("UPDATE identity.staff_branch_permission SET revoked_at=clock_timestamp() WHERE staff_id=? AND branch_id=? AND revoked_at IS NULL",staff,branch);
        request(get(BASE+"/"+id),null,403);
        request(post(BASE+"/"+id+"/submit"),null,403);
        request(get(BASE).param("branchId",branch.toString()),null,403);
    }

    @Test void foreignTenantAndInvalidShiftAreRejected() throws Exception {
        String id = open(branch,201).get("cashCloseId").asText();
        token = bearer(UUID.randomUUID(),staff,Map.of(branch.toString(),"ADMIN"));
        request(get(BASE+"/"+id),null,404);
        token = bearer(business,staff,Map.of(branch.toString(),"ADMIN"));
        request(post(BASE), "{\"shiftTypeId\":\""+UUID.randomUUID()+"\",\"businessDate\":\"2026-09-21\"}",422);
    }

    @Test void receiptReferencesStayWithinTheCloseAndBranch() throws Exception {
        String id = open(branch,201).get("cashCloseId").asText();
        String other = open(otherBranch,201).get("cashCloseId").asText();
        UUID file = UUID.randomUUID();
        sql("INSERT INTO files.stored_file(file_id,business_id,branch_id,file_kind,file_name,content_type,byte_size,sha256,storage_key) VALUES (?,?,?,'RECEIPT','receipt.png','image/png',1,'test','test')",file,business,branch);
        String body = "{\"fileId\":\""+file+"\",\"fileKind\":\"RECEIPT\"}";
        request(post(BASE+"/"+other+"/attachments").header("X-Branch-Id",otherBranch),body,422);
        String attachment = request(post(BASE+"/"+id+"/attachments"),body,201).get("attachmentId").asText();
        assertThat(request(get(BASE+"/"+id+"/attachments"),null,200).get(0).get("fileId").asText()).isEqualTo(file.toString());
        String movement = "{\"kindCode\":\"POS_ERROR\",\"amount\":10000,\"description\":\"test\",\"receiptAttachmentId\":\""+attachment+"\"}";
        request(post(BASE+"/"+other+"/movements").header("X-Branch-Id",otherBranch),movement,422);
        assertThat(request(post(BASE+"/"+id+"/movements"),movement,201).get("signedAmount").decimalValue()).isEqualByComparingTo("-10000");
        request(post(BASE+"/"+id+"/movements"),"{\"kindCode\":\"POS_ERROR\",\"amount\":0.001,\"description\":\"test\"}",400);
    }

    @Test void posSnapshotUsesLatestRevisionAndCannotBeOverwritten() throws Exception {
        sql("INSERT INTO integration.shift_sales(business_id,branch_id,shift_type_id,business_date,cash_sales,source_vendor,revision) VALUES (?,?,?,'2026-09-21',100000,'IPOS',1), (?,?,?,'2026-09-21',200000,'IPOS',2)",business,branch,shift,business,branch,shift);
        JsonNode close = open(branch,201);
        assertThat(close.get("posExpectedCash").decimalValue()).isEqualByComparingTo("200000");
        assertThat(close.get("expectedCashSource").asText()).isEqualTo("POS_SYNC");
        request(patch(BASE+"/"+close.get("cashCloseId").asText()),"{\"posExpectedCash\":1}",422);
        assertThat(request(get(BASE).param("expectedCashSource","POS_SYNC"),null,200).get("content").size()).isEqualTo(1);
    }

    @Test void catalogUsesTenantOverrideAndMovementsExposeItsMeaning() throws Exception {
        sql("INSERT INTO platform.movement_kind(kind_code,display_name,business_id,valid_from,effect_type,affects_difference,affects_remaining,requires_note) VALUES ('POS_ERROR','Tenant reason',?,'2026-01-01','NO_CASH_FLOW',true,false,true)",business);
        JsonNode catalog = request(get(BASE+"/movement-kinds").param("businessDate","2026-09-21"),null,200);
        int matches = 0;
        for (JsonNode kind : catalog) if (kind.get("kindCode").asText().equals("POS_ERROR")) {
            matches++;
            assertThat(kind.get("displayName").asText()).isEqualTo("Tenant reason");
        }
        assertThat(matches).isEqualTo(1);
        String id = open(branch,201).get("cashCloseId").asText();
        request(post(BASE+"/"+id+"/movements"),"{\"kindCode\":\"POS_ERROR\",\"amount\":1}",422);
        JsonNode movement = request(post(BASE+"/"+id+"/movements"),"{\"kindCode\":\"pos_error\",\"amount\":1,\"description\":\"test\"}",201);
        assertThat(movement.get("kindDisplayName").asText()).isEqualTo("Tenant reason");
        assertThat(movement.get("affectsDifference").asBoolean()).isTrue();
        assertThat(request(get(BASE+"/movements").param("kindCode","POS_ERROR"),null,200).get("content").size()).isEqualTo(1);
        request(post(BASE+"/"+id+"/movements"),"{\"kindCode\":\"TIP_IN_DRAWER\",\"amount\":1,\"differenceDirection\":\"OVER\"}",422);
        sql("UPDATE identity.staff_branch_permission SET revoked_at=clock_timestamp() WHERE staff_id=? AND branch_id=? AND revoked_at IS NULL",staff,branch);
        sql("INSERT INTO identity.staff_branch_permission(staff_id,branch_id,business_id,permission_code) VALUES (?,?,?,'CLOSE_READ')",staff,branch,business);
        request(post(BASE+"/movements/"+movement.get("movementId").asText()+"/approve"),null,403);
    }

    private JsonNode open(UUID atBranch, int status) throws Exception {
        return request(post(BASE).header("X-Branch-Id",atBranch),"{\"shiftTypeId\":\""+shift+"\",\"businessDate\":\"2026-09-21\"}",status);
    }
    private JsonNode request(MockHttpServletRequestBuilder r, String body, int status) throws Exception {
        r.header("Authorization",token);
        if (r.buildRequest(new org.springframework.mock.web.MockServletContext()).getHeader("X-Branch-Id") == null) r.header("X-Branch-Id",branch);
        if (body != null) r.contentType("application/json").content(body);
        var response = mvc.perform(r).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(status);
        return json.readTree(response.getContentAsString());
    }
    private static String bearer(UUID business, UUID staff, Map<String,String> roles) throws Exception {
        Instant now = Instant.now();
        var claims = new JWTClaimsSet.Builder().issuer("fnbx-identity").subject("STAFF")
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(600)))
                .jwtID(UUID.randomUUID().toString()).claim("type","access").claim("uid",staff.toString())
                .claim("business_id",business.toString()).claim("branch_roles",roles).build();
        var signed = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),claims);
        signed.sign(new MACSigner(new byte[32])); return "Bearer "+signed.serialize();
    }
    private static void sql(String statement,Object... args) throws Exception {
        try (var c = DriverManager.getConnection(System.getenv("FNB_CASHCLOSE_TEST_DB_URL"),"postgres","local-test-only");
             var q = c.prepareStatement(statement)) {
            for (int i=0;i<args.length;i++) q.setObject(i+1,args[i]);
            q.executeUpdate();
        }
    }
}
