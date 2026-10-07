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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        sql("INSERT INTO platform.app_config(scope,business_id,branch_id,config_key,config_value) VALUES ('BRANCH',?,?,'REQUIRE_POS_IMAGE','false'),('BRANCH',?,?,'REQUIRE_POS_IMAGE','false')",business,branch,business,otherBranch);
        sql("INSERT INTO identity.staff(staff_id,business_id,employee_code,first_name,last_name,passcode_hash) VALUES (?,?,'API','API','Test','unused')", staff,business);
        UUID fullPosition=UUID.randomUUID(),basicPosition=UUID.randomUUID();
        sql("INSERT INTO identity.position(position_id,business_id,position_code,position_name) VALUES (?,?,'TEST_FULL','Full access'),(?,?,'TEST_BASIC','Basic access')",fullPosition,business,basicPosition,business);
        sql("INSERT INTO identity.position_permission(position_id,business_id,permission_code) SELECT ?,?,permission_code FROM identity.permission WHERE scope='BRANCH'",fullPosition,business);
        sql("INSERT INTO identity.position_permission(position_id,business_id,permission_code) SELECT ?,?,permission_code FROM identity.permission WHERE permission_code IN ('CLOSE_READ','CLOSE_EDIT','CLOSE_SUBMIT','DENOMINATION_WRITE','MOVEMENT_ADD','WITHDRAWAL_RECORD')",basicPosition,business);
        assignPosition(staff,branch,fullPosition);
        assignPosition(staff,otherBranch,basicPosition);
        sql("INSERT INTO identity.shift_type(shift_type_id,business_id,shift_code,shift_name,submit_deadline) VALUES (?,?,'AM','Morning','23:59')", shift,business);
        token = bearer(business, staff, Map.of(branch.toString(),"ADMIN",otherBranch.toString(),"STAFF"));
    }

    @Test void submissionCorrectionApprovalAndVoidFollowDdl() throws Exception {
        JsonNode close = submitClose(branch, 201);
        String id = close.get("cashCloseId").asText();
        assertThat(close.get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(close.get("countedCash").decimalValue()).isEqualByComparingTo("500000");
        submitClose(branch, 422);
        JsonNode corrected = request(post(BASE+"/"+id+"/corrections"),
                "{\"editReason\":\"recount\",\"denominations\":{\"counts\":[{\"faceValue\":500000,\"quantity\":2}]}}",200);
        assertThat(corrected.get("status").asText()).isEqualTo("PENDING_REVIEW");
        assertThat(corrected.get("countedCash").decimalValue()).isEqualByComparingTo("1000000");
        assertThat(request(get(BASE+"/"+id+"/history"),null,200).size()).isEqualTo(2);
        request(post(BASE+"/"+id+"/approve"),null,200);
        request(post(BASE+"/"+id+"/void"),"{\"note\":\"redo shift\"}",200);
        assertThat(submitClose(branch,201).get("cashCloseId").asText()).isNotEqualTo(id);
    }

    @Test void submissionEmailsActualSubmitterAndOnlyLiveStoreManagersAtThisBranch() throws Exception {
        UUID position = UUID.randomUUID(), nameOnly = UUID.randomUUID();
        sql("INSERT INTO identity.position(position_id,business_id,position_code,position_name) VALUES (?,?,'STORE_MANAGER','Renamed title'),(?,?,'OTHER','Store Manager')",
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
        sql("INSERT INTO identity.position_permission(position_id,business_id,permission_code) VALUES (?,?,'CLOSE_SUBMIT')",position,business);
        token = bearer(business,submitter,Map.of());
        String id = submitClose(branch,201).get("cashCloseId").asText();
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
        sql("UPDATE identity.position SET is_active=false WHERE position_id=?",position);
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
        String id = submitClose(branch,201).get("cashCloseId").asText();
        request(post(BASE+"/"+id+"/corrections").header("X-Branch-Id",otherBranch),"{\"editReason\":\"wrong branch\",\"note\":\"x\"}",403);
        String other = submitClose(otherBranch,201).get("cashCloseId").asText();
        request(post(BASE+"/"+other+"/approve").header("X-Branch-Id",otherBranch),null,403);
        sql("UPDATE identity.staff_branch_position SET revoked_at=greatest(clock_timestamp(),assigned_at) WHERE staff_id=? AND branch_id=? AND revoked_at IS NULL",staff,branch);
        request(get(BASE+"/"+id),null,403);
        submitClose(branch,403);
        request(get(BASE).param("branchId",branch.toString()),null,403);
    }

    @Test void foreignTenantAndInvalidShiftAreRejected() throws Exception {
        String id = submitClose(branch,201).get("cashCloseId").asText();
        token = bearer(UUID.randomUUID(),staff,Map.of(branch.toString(),"ADMIN"));
        request(get(BASE+"/"+id),null,404);
        token = bearer(business,staff,Map.of(branch.toString(),"ADMIN"));
        request(post(BASE), "{\"shiftTypeId\":\""+UUID.randomUUID()+"\",\"businessDate\":\"2026-09-21\",\"denominations\":{\"counts\":[{\"faceValue\":500000,\"quantity\":1}]}}",422);
    }

    @Test void receiptReferencesStayWithinTheCloseAndBranch() throws Exception {
        String id = submitClose(branch,201).get("cashCloseId").asText();
        String other = submitClose(otherBranch,201).get("cashCloseId").asText();
        UUID file = UUID.randomUUID();
        sql("INSERT INTO files.stored_file(file_id,business_id,branch_id,file_kind,file_name,content_type,byte_size,sha256,storage_key) VALUES (?,?,?,'RECEIPT','receipt.png','image/png',1,'test','test')",file,business,branch);
        UUID closeFile = UUID.randomUUID();
        sql("INSERT INTO files.stored_file(file_id,business_id,branch_id,file_kind,file_name,content_type,byte_size,sha256,storage_key,public_url) VALUES (?,?,?,'POS_REPORT','pos.png','image/png',1,'test','pos','https://cdn/pos.png')",closeFile,business,branch);
        sql("UPDATE files.stored_file SET public_url='https://cdn/receipt.png' WHERE file_id=?",file);
        String body = "{\"fileId\":\""+closeFile+"\"}";
        request(post(BASE+"/"+other+"/attachments").header("X-Branch-Id",otherBranch),body,422);
        JsonNode attached = request(post(BASE+"/"+id+"/attachments"),body,201);
        assertThat(attached.get("fileKind").asText()).isEqualTo("POS_REPORT");
        String movement = "{\"kindCode\":\"POS_ERROR\",\"amount\":10000,\"description\":\"test\",\"receiptFileId\":\""+file+"\"}";
        request(post(BASE+"/"+other+"/movements").header("X-Branch-Id",otherBranch),movement,422);
        request(post(BASE+"/"+id+"/movements"),movement.replace(file.toString(),closeFile.toString()),422);
        JsonNode added = request(post(BASE+"/"+id+"/movements"),movement,201);
        assertThat(added.get("signedAmount").decimalValue()).isEqualByComparingTo("-10000");
        assertThat(added.get("receiptPublicUrl").asText()).isEqualTo("https://cdn/receipt.png");
        assertThat(added.get("fileKind").asText()).isEqualTo("RECEIPT");
        assertThat(added.get("contentType").asText()).isEqualTo("image/png");
        JsonNode files = request(get(BASE+"/"+id+"/attachments"),null,200).get("files");
        assertThat(files.size()).isEqualTo(2);
        assertThat(files.get(0).get("publicUrl").asText()).isEqualTo("https://cdn/pos.png");
        assertThat(files.get(0).get("attachmentId").asText()).isEqualTo(attached.get("attachmentId").asText());
        assertThat(files.get(1).get("publicUrl").asText()).isEqualTo("https://cdn/receipt.png");
        assertThat(files.get(1).get("movementId").asText()).isEqualTo(added.get("movementId").asText());
        assertThat(files.get(1).get("attachedBy").asText()).isEqualTo(added.get("createdBy").asText());
        request(post(BASE+"/"+id+"/movements"),"{\"kindCode\":\"POS_ERROR\",\"amount\":0.001,\"description\":\"test\"}",400);
    }

    @Test void posSnapshotUsesLatestRevisionAndCannotBeOverwritten() throws Exception {
        sql("INSERT INTO integration.shift_sales(business_id,branch_id,shift_type_id,business_date,cash_sales,source_vendor,revision) VALUES (?,?,?,'2026-09-21',100000,'IPOS',1), (?,?,?,'2026-09-21',200000,'IPOS',2)",business,branch,shift,business,branch,shift);
        JsonNode close = submitClose(branch,201);
        assertThat(close.get("posExpectedCash").decimalValue()).isEqualByComparingTo("200000");
        assertThat(close.get("expectedCashSource").asText()).isEqualTo("POS_SYNC");
        request(post(BASE+"/"+close.get("cashCloseId").asText()+"/corrections"),"{\"editReason\":\"POS correction\",\"figures\":{\"posExpectedCash\":1}}",422);
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
        String id = submitClose(branch,201).get("cashCloseId").asText();
        request(post(BASE+"/"+id+"/movements"),"{\"kindCode\":\"POS_ERROR\",\"amount\":1}",422);
        JsonNode movement = request(post(BASE+"/"+id+"/movements"),"{\"kindCode\":\"pos_error\",\"amount\":1,\"description\":\"test\"}",201);
        assertThat(movement.get("kindDisplayName").asText()).isEqualTo("Tenant reason");
        assertThat(movement.get("affectsDifference").asBoolean()).isTrue();
        assertThat(request(get(BASE+"/movements").param("kindCode","POS_ERROR"),null,200).get("content").size()).isEqualTo(1);
        request(post(BASE+"/"+id+"/movements"),"{\"kindCode\":\"TIPS\",\"amount\":1,\"differenceDirection\":\"OVER\"}",422);
        sql("UPDATE identity.staff_branch_position SET revoked_at=greatest(clock_timestamp(),assigned_at) WHERE staff_id=? AND branch_id=? AND revoked_at IS NULL",staff,branch);
        UUID readOnly=UUID.randomUUID();
        sql("INSERT INTO identity.position(position_id,business_id,position_code,position_name) VALUES (?,?,'TEST_READ_ONLY','Read only')",readOnly,business);
        sql("INSERT INTO identity.position_permission(position_id,business_id,permission_code) VALUES (?,?,'CLOSE_READ')",readOnly,business);
        assignPosition(staff,branch,readOnly);
        request(post(BASE+"/movements/"+movement.get("movementId").asText()+"/approve"),null,403);
    }

    @Test void movementCorrectionsKeepOneRowAuditEveryActionAndRecalculateClose() throws Exception {
        UUID involved = emailStaff("tip-recipient@example.test");
        String closeId = submitClose(branch,201).get("cashCloseId").asText();
        JsonNode created = request(post(BASE+"/"+closeId+"/movements"),
                "{\"kindCode\":\"TIPS\",\"amount\":10000,\"staffUserId\":\""+involved+"\"}",201);
        String movementId = created.get("movementId").asText();
        assertThat(created.get("staffUserId").asText()).isEqualTo(involved.toString());
        assertThat(created.get("createdBy").asText()).isEqualTo(staff.toString());
        assertThat(request(get(BASE+"/"+closeId),null,200).get("tipsTotal").decimalValue())
                .isEqualByComparingTo("10000");

        request(post(BASE+"/movements/"+movementId+"/approve"),null,200);
        JsonNode corrected = request(patch(BASE+"/movements/"+movementId),
                "{\"editReason\":\"Correct tip count\",\"amount\":20000}",200);
        assertThat(corrected.get("movementId").asText()).isEqualTo(movementId);
        assertThat(corrected.get("approvalStatus").asText()).isEqualTo("PENDING");
        assertThat(corrected.get("signedAmount").decimalValue()).isEqualByComparingTo("20000");
        JsonNode recalculated = request(get(BASE+"/"+closeId),null,200);
        assertThat(recalculated.get("tipsTotal").decimalValue()).isEqualByComparingTo("20000");
        assertThat(recalculated.get("cashRemaining").decimalValue()).isEqualByComparingTo("480000");
        JsonNode history = request(get(BASE+"/"+closeId+"/movement-history"),null,200);
        assertThat(history.size()).isEqualTo(2);
        assertThat(history.get(0).get("action").asText()).isEqualTo("APPROVE");
        assertThat(history.get(1).get("action").asText()).isEqualTo("EDIT");
        assertThat(history.get(1).get("changes").get("before").get("signedAmount").decimalValue())
                .isEqualByComparingTo("10000");
        assertThat(history.get(1).get("changes").get("after").get("signedAmount").decimalValue())
                .isEqualByComparingTo("20000");

        request(post(BASE+"/movements/"+movementId+"/reject"),"{\"note\":\"Not a tip\"}",200);
        assertThat(request(get(BASE+"/"+closeId),null,200).get("tipsTotal").decimalValue())
                .isEqualByComparingTo("0");
        assertThat(request(get(BASE+"/"+closeId+"/movement-history"),null,200).size()).isEqualTo(3);
        assertThatThrownBy(() -> sql("UPDATE cashclose.cash_movement SET signed_amount=99999 WHERE movement_id=?",
                UUID.fromString(movementId))).hasMessageContaining("Movement changes require a decision record");
        request(post(BASE+"/"+closeId+"/approve"),null,200);
        request(patch(BASE+"/movements/"+movementId),
                "{\"editReason\":\"Too late\",\"amount\":30000}",422);
    }

    @Test void withdrawalConfirmationRevisionsAndCashRemainingFollowTypedAmount() throws Exception {
        UUID manager = emailStaff("withdrawer@example.test");
        UUID withdrawerPosition=UUID.randomUUID();
        sql("INSERT INTO identity.position(position_id,business_id,position_code,position_name) VALUES (?,?,'TEST_WITHDRAWER','Withdrawer')",withdrawerPosition,business);
        sql("INSERT INTO identity.position_permission(position_id,business_id,permission_code) VALUES (?,?,'WITHDRAWAL_RECORD'),(?,?,'CLOSE_READ')",
                withdrawerPosition,business,withdrawerPosition,business);
        assignPosition(manager,branch,withdrawerPosition);
        String staffToken=token, managerToken=bearer(business,manager,Map.of());
        String funds="/api/v1/fund-withdrawals";
        String body=json.writeValueAsString(Map.of("shiftTypeId",shift,"businessDate","2026-09-21",
                "denominations",Map.of("counts",java.util.List.of(Map.of("faceValue",500000,"quantity",1))),
                "figures",Map.of("withdrawalAmount",100000,"withdrawnBy",manager,"withdrawnAt","2026-09-21T14:00:00Z"),
                "note","Withdrawal checked"));
        JsonNode close=request(post(BASE),body,201);
        String id=close.get("cashCloseId").asText();
        assertThat(close.get("cashRemaining").decimalValue()).isEqualByComparingTo("400000");
        JsonNode first=request(get(funds).param("cashCloseId",id),null,200).get("content").get(0);
        String firstId=first.get("id").asText();
        assertThat(first.get("status").asText()).isEqualTo("PENDING");
        request(post(BASE+"/"+id+"/approve"),null,422);
        request(post(funds+"/"+firstId+"/confirm"),null,403);
        token=managerToken;
        request(post(funds+"/"+firstId+"/confirm"),null,200);
        request(post(funds+"/"+firstId+"/confirm"),null,200);
        assertThat(request(get(funds+"/"+firstId+"/history"),null,200).size()).isEqualTo(1);
        token=staffToken;
        request(post(BASE+"/"+id+"/approve"),null,200);
        JsonNode corrected=request(post(BASE+"/"+id+"/corrections"),
                json.writeValueAsString(Map.of("editReason","Wrong amount", "figures",Map.of("withdrawalAmount",200000))),200);
        assertThat(corrected.get("cashRemaining").decimalValue()).isEqualByComparingTo("300000");
        assertThat(corrected.get("status").asText()).isEqualTo("PENDING_REVIEW");
        JsonNode second=request(get(funds).param("cashCloseId",id),null,200).get("content").get(0);
        assertThat(second.get("id").asText()).isEqualTo(firstId);
        assertThat(second.has("revision")).isFalse();
        JsonNode edits=request(get(funds+"/"+firstId+"/history"),null,200);
        assertThat(edits.size()).isEqualTo(2);
        assertThat(edits.get(1).get("action").asText()).isEqualTo("EDIT");
        assertThat(edits.get(1).get("changes").get("before").get("amount").decimalValue()).isEqualByComparingTo("100000");
        assertThat(edits.get(1).get("changes").get("after").get("amount").decimalValue()).isEqualByComparingTo("200000");
        assertThat(confirmedTransferTotal(UUID.fromString(first.get("id").asText())))
                .isEqualByComparingTo("100000"); // prior confirmed declaration remains until correction is confirmed
        request(post(BASE+"/"+id+"/approve"),null,422);
        token=managerToken;
        request(post(funds+"/"+second.get("id").asText()+"/confirm"),null,200);
        assertThat(confirmedTransferTotal(UUID.fromString(first.get("id").asText())))
                .isEqualByComparingTo("200000"); // revisions must never sum to 300000
        token=staffToken;
        request(post(BASE+"/"+id+"/approve"),null,200);
        JsonNode cancelled=request(post(BASE+"/"+id+"/corrections"),
                json.writeValueAsString(Map.of("editReason","No withdrawal occurred", "figures",Map.of("withdrawalAmount",0))),200);
        assertThat(cancelled.get("cashRemaining").decimalValue()).isEqualByComparingTo("500000");
        request(post(BASE+"/"+id+"/approve"),null,422);
        JsonNode zero=request(get(funds).param("cashCloseId",id),null,200).get("content").get(0);
        token=managerToken;
        request(post(funds+"/"+zero.get("id").asText()+"/confirm"),null,200);
        assertThat(confirmedTransferTotal(UUID.fromString(first.get("id").asText()))).isZero();
        token=staffToken;
        request(post(BASE+"/"+id+"/approve"),null,200);
        assertThat(request(get(funds).param("cashCloseId",id),null,200)
                .get("totalElements").asInt()).isEqualTo(1);
        assertThat(request(get(funds+"/"+firstId+"/history"),null,200).size()).isEqualTo(5);
    }

    @Test void linkedWithdrawalAndCloseCorrectionsAppendBothDecisionHistories() throws Exception {
        UUID newWithdrawer = emailStaff("new-withdrawer@example.test");
        UUID position = UUID.randomUUID();
        sql("INSERT INTO identity.position(position_id,business_id,position_code,position_name) VALUES (?,?,'TEST_WITHDRAWAL_EDIT','Withdrawal editor')", position,business);
        sql("INSERT INTO identity.position_permission(position_id,business_id,permission_code) VALUES (?,?,'WITHDRAWAL_RECORD')", position,business);
        assignPosition(newWithdrawer,branch,position);

        String funds="/api/v1/fund-withdrawals";
        JsonNode close=request(post(BASE),json.writeValueAsString(Map.of(
                "shiftTypeId",shift,"businessDate","2026-09-21",
                "denominations",Map.of("counts",java.util.List.of(Map.of("faceValue",500000,"quantity",1))),
                "figures",Map.of("withdrawalAmount",100000,"withdrawnBy",staff,"withdrawnAt","2026-09-21T14:00:00Z"),
                "note","Initial count")),201);
        String closeId=close.get("cashCloseId").asText();
        String withdrawalId=request(get(funds).param("cashCloseId",closeId),null,200)
                .get("content").get(0).get("id").asText();

        request(post(funds+"/"+withdrawalId+"/corrections"),json.writeValueAsString(Map.of(
                "amount",200000,"withdrawnBy",newWithdrawer,"withdrawnAt","2026-09-21T15:00:00Z",
                "editReason","Corrected handover")),200);
        JsonNode correctedClose=request(get(BASE+"/"+closeId),null,200);
        assertThat(correctedClose.get("status").asText()).isEqualTo("PENDING_REVIEW");
        assertThat(correctedClose.get("withdrawalAmount").decimalValue()).isEqualByComparingTo("200000");
        JsonNode closeHistory=request(get(BASE+"/"+closeId+"/history"),null,200);
        assertThat(closeHistory.size()).isEqualTo(2);
        assertThat(closeHistory.get(1).get("action").asText()).isEqualTo("EDIT");
        assertThat(closeHistory.get(1).get("actedPermission").asText()).isEqualTo("WITHDRAWAL_RECORD");
        assertThat(closeHistory.get(1).get("changes").get("withdrawal").get("before").get("amount").decimalValue())
                .isEqualByComparingTo("100000");
        assertThat(closeHistory.get(1).get("changes").get("withdrawal").get("after").get("withdrawnBy").asText())
                .isEqualTo(newWithdrawer.toString());
        assertThat(closeHistory.get(1).get("changes").get("withdrawal").get("after").get("withdrawnAt").asText())
                .startsWith("2026-09-21T15:00:00");

        request(post(BASE+"/"+closeId+"/corrections"),json.writeValueAsString(Map.of(
                "editReason","Second handover correction",
                "figures",Map.of("withdrawalAmount",300000,"withdrawnBy",staff,
                        "withdrawnAt","2026-09-21T16:00:00Z"))),200);
        JsonNode withdrawalHistory=request(get(funds+"/"+withdrawalId+"/history"),null,200);
        assertThat(withdrawalHistory.size()).isEqualTo(2);
        assertThat(withdrawalHistory.get(0).get("action").asText()).isEqualTo("EDIT");
        assertThat(withdrawalHistory.get(1).get("action").asText()).isEqualTo("EDIT");
        assertThat(withdrawalHistory.get(1).get("changes").get("after").get("amount").decimalValue())
                .isEqualByComparingTo("300000");
        assertThat(request(get(BASE+"/"+closeId+"/history"),null,200).size()).isEqualTo(3);
        assertThat(request(get(funds).param("cashCloseId",closeId),null,200)
                .get("content").get(0).get("id").asText()).isEqualTo(withdrawalId);
    }

    @Test void safeTransfersAreIndependentAndRejectedDeclarationsRemainInHistory() throws Exception {
        String funds="/api/v1/fund-withdrawals";
        JsonNode transfer=request(post(funds),json.writeValueAsString(Map.of("fromPot","BRANCH_SAFE",
                "toPot","CENTRAL_SAFE","amount",500000,"withdrawnBy",staff,"withdrawnAt","2026-09-21T14:00:00Z")),201);
        assertThat(transfer.get("cashCloseId").isNull()).isTrue();
        String id=transfer.get("id").asText();
        request(post(funds+"/"+id+"/reject"),json.writeValueAsString(Map.of("reason","Wrong amount")),200);
        JsonNode revised=request(post(funds+"/"+id+"/corrections"),json.writeValueAsString(Map.of(
                "amount",400000,"withdrawnBy",staff,"withdrawnAt","2026-09-21T14:00:00Z","editReason","Recounted")),200);
        request(post(funds+"/"+revised.get("id").asText()+"/confirm"),null,200);
        JsonNode old=request(get(funds+"/"+id),null,200);
        assertThat(old.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(revised.get("id").asText()).isEqualTo(id);
        assertThat(old.has("rejectionReason")).isFalse();
        JsonNode history=request(get(funds+"/"+id+"/history"),null,200);
        assertThat(history.size()).isEqualTo(3);
        assertThat(history.get(0).get("action").asText()).isEqualTo("REJECT");
        assertThat(history.get(0).get("note").asText()).isEqualTo("Wrong amount");
        assertThat(history.get(0).get("actedBy").asText()).isEqualTo(staff.toString());
        assertThat(history.get(0).get("actedAt").isNull()).isFalse();
        JsonNode confirmedHistory=request(get(funds+"/"+revised.get("id").asText()+"/history"),null,200);
        assertThat(confirmedHistory.get(1).get("action").asText()).isEqualTo("EDIT");
        assertThat(confirmedHistory.get(2).get("action").asText()).isEqualTo("CONFIRM");
        assertThat(confirmedTransferTotal(UUID.fromString(revised.get("id").asText())))
                .isEqualByComparingTo("400000");
    }

    @Test void withdrawalDecisionLedgerIsRequiredAndAppendOnly() throws Exception {
        String funds="/api/v1/fund-withdrawals";
        JsonNode transfer=request(post(funds),json.writeValueAsString(Map.of("fromPot","BRANCH_SAFE",
                "toPot","CENTRAL_SAFE","amount",500000,"withdrawnBy",staff,"withdrawnAt","2026-09-21T14:00:00Z")),201);
        UUID id=UUID.fromString(transfer.get("id").asText());
        // Even a direct SQL status update must append a matching decision before commit.
        try (var c=DriverManager.getConnection(System.getenv("FNB_CASHCLOSE_TEST_DB_URL"),"postgres","local-test-only")) {
            c.setAutoCommit(false);
            try (var q=c.prepareStatement("SELECT set_config('app.user_id',?,true)")) {
                q.setString(1,staff.toString()); q.execute();
            }
            try (var q=c.prepareStatement("UPDATE cashclose.fund_withdrawal SET status='CONFIRMED' WHERE fund_withdrawal_id=?")) {
                q.setObject(1,id);
                org.assertj.core.api.Assertions.assertThatThrownBy(q::executeUpdate)
                        .isInstanceOf(java.sql.SQLException.class).hasMessageContaining("decision record");
            }
            c.rollback();
        }
        assertThat(request(get(funds+"/"+id),null,200).get("status").asText()).isEqualTo("PENDING");
        request(post(funds+"/"+id+"/confirm"),null,200);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> sql(
                "UPDATE cashclose.fund_withdrawal_decision SET note='changed' WHERE fund_withdrawal_id=?",id))
                .isInstanceOf(java.sql.SQLException.class).hasMessageContaining("append-only");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> sql(
                "DELETE FROM cashclose.fund_withdrawal_decision WHERE fund_withdrawal_id=?",id))
                .isInstanceOf(java.sql.SQLException.class).hasMessageContaining("append-only");
        request(post(funds+"/"+id+"/reject"),"{\"reason\":\"too late\"}",409);
        assertThat(request(get(funds+"/"+id+"/history"),null,200).size()).isEqualTo(1);
    }

    private java.math.BigDecimal confirmedTransferTotal(UUID withdrawalId) throws Exception {
        try (var c=DriverManager.getConnection(System.getenv("FNB_CASHCLOSE_TEST_DB_URL"),"postgres","local-test-only");
             var q=c.prepareStatement("SELECT coalesce(sum(amount),0) FROM cashclose.v_confirmed_fund_transfer WHERE fund_withdrawal_id=?")) {
            q.setObject(1,withdrawalId);
            try (var rows=q.executeQuery()) { rows.next(); return rows.getBigDecimal(1); }
        }
    }

    private JsonNode submitClose(UUID atBranch, int status) throws Exception {
        return request(post(BASE).header("X-Branch-Id",atBranch),"{\"shiftTypeId\":\""+shift+"\",\"businessDate\":\"2026-09-21\",\"denominations\":{\"counts\":[{\"faceValue\":500000,\"quantity\":1}]},\"note\":\"Count checked\"}",status);
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
