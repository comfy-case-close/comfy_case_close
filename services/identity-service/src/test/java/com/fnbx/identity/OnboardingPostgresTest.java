package com.fnbx.identity;

import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fnbx.identity.dto.request.*;
import com.fnbx.identity.dto.response.AuthResponse;
import com.fnbx.identity.dto.response.BusinessResponse;
import com.fnbx.identity.enums.SignUpOutcome;
import com.fnbx.identity.service.AuthService;
import com.fnbx.identity.service.BusinessService;
import com.fnbx.identity.service.GoogleTokenVerifier;
import com.fnbx.identity.service.OtpMailer;
import com.fnbx.identity.service.RegistrationMailer;
import com.fnbx.identity.service.VerificationStore;
import com.fnbx.identity.utils.enums.OtpPurpose;
import com.fnbx.shared.enums.BusinessType;
import com.fnbx.shared.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The onboarding path end to end, against the real database and the real security
 * chain: provision a tenant, provision its owner, let a stranger ask to join, and
 * approve them.
 *
 * <p>Separate from {@code AuthPostgresTest} because it is testing a different
 * thing. That class is about sessions - rotation, reuse, invalidation. This one is
 * about who is allowed to bring a business, a branch or a colleague into existence,
 * and about the two invariants that keep a tenant reachable: one active branch, one
 * live ADMIN.
 *
 * <p>Most assertions go through MockMvc rather than the services, because half of
 * what is being tested lives in the filter chain - the platform key in particular is
 * invisible from a direct service call.
 */
@SpringBootTest(classes = IdentityApplication.class, properties = {
    "fnb.security.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "fnb.security.jwt.revoked-token-cleanup-cron=-",
    "fnb.platform.admin-key=test-platform-admin-key-0123456789abcdef"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "FNB_AUTH_TEST_DB_URL", matches = ".+")
class OnboardingPostgresTest {

    /** Must stay in step with the {@code fnb.platform.admin-key} property above. */
    static final String PLATFORM_KEY = "test-platform-admin-key-0123456789abcdef";
    private static final String PASSWORD = "StrongPassword1!";

    @Autowired AuthService auth;
    @Autowired BusinessService businessService;
    @Autowired com.fnbx.identity.service.BusinessRegistrationService registrationService;
    @Autowired com.fnbx.identity.service.TenantTransactions transactions;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final java.util.Map<UUID, String> businessCodes = new java.util.HashMap<>();
    @Autowired VerificationStore store;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean OtpMailer mailer;
    @MockBean GoogleTokenVerifier google;
    /** Mocked so no letter is actually sent, and so the send can be asserted on. */
    @MockBean RegistrationMailer registrationMailer;

    private BusinessResponse business;
    private AuthResponse admin;
    private UUID mainBranchId;

    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("FNB_AUTH_TEST_DB_URL"));
        properties.add("spring.datasource.username", () -> "svc_identity");
        properties.add("spring.datasource.password", () -> "fnbx_auth_test_password");
    }

    @BeforeEach void tenant() {
        when(mailer.available()).thenReturn(true);
        String ownerEmail = address();
        admin = createApprovedOwner("Comfy Test", ownerEmail);
        mainBranchId = admin.getUser().getBranchRoles().keySet().iterator().next();
        business = BusinessResponse.builder().businessId(admin.getUser().getBusinessId())
                .businessCode(codeFor(admin.getUser().getBusinessId())).firstBranchId(mainBranchId).build();
        clearInvocations(registrationMailer);
    }

    @Test void obsoleteEndpointsAreRemoved() throws Exception {
        mvc.perform(post("/api/v1/businesses").header("Authorization", bearer(admin))
                .contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/businesses/" + business.getBusinessId() + "/owner")
                .header("Authorization", bearer(admin)).contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/businesses/lookup").header("Authorization", bearer(admin)).param("code", business.getBusinessCode()))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test void verifiesOtpSeparatelyAndSubmissionTokenIsEmailBoundAndSingleUse() throws Exception {
        String owner = address();
        mvc.perform(post("/api/v1/businesses/registrations/start").contentType("application/json")
                .content(json.writeValueAsString(new StartBusinessRegistrationRequest(owner.toUpperCase()))))
                .andExpect(status().isOk());
        var otp = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mailer).mail(eq(OtpPurpose.BUSINESS_REGISTRATION), eq(owner), eq(owner), otp.capture());
        mvc.perform(post("/api/v1/businesses/registrations/verify").contentType("application/json")
                .content(json.writeValueAsString(new VerifyBusinessRegistrationOtpRequest(address(), otp.getValue()))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value(2404));
        String verification = json.writeValueAsString(new VerifyBusinessRegistrationOtpRequest(owner.toUpperCase(), otp.getValue()));
        String verified = mvc.perform(post("/api/v1/businesses/registrations/verify").contentType("application/json")
                .content(verification)).andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationToken").isString())
                .andExpect(jsonPath("$.expiresInMs").value(VerificationStore.VERIFIED_TOKEN_TTL.toMillis()))
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(verified).path("registrationToken").asText();
        assertThat(transactions.outsideTenant(() -> jdbc.queryForObject(
                "SELECT count(*) FROM identity.business_registration WHERE owner_email = ?", Long.class, owner))).isZero();
        mvc.perform(post("/api/v1/businesses/registrations/verify").contentType("application/json")
                .content(verification)).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value(2404));

        mvc.perform(post("/api/v1/businesses/registrations").contentType("application/json")
                .content(json.writeValueAsString(application(address(), token))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value(2434));
        String body = json.writeValueAsString(application(owner.toUpperCase(), token));
        mvc.perform(post("/api/v1/businesses/registrations").contentType("application/json").content(body))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.businessCode").doesNotExist());
        mvc.perform(post("/api/v1/businesses/registrations").contentType("application/json").content(body))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value(2434));
        verifyNoInteractions(registrationMailer);
    }

    @Test void signupOtpAndTokenCannotAuthorizeBusinessRegistration() throws Exception {
        String owner = address();
        String otp = store.issueOtp(OtpPurpose.SIGNUP, owner);
        mvc.perform(post("/api/v1/businesses/registrations/verify").contentType("application/json")
                .content(json.writeValueAsString(new VerifyBusinessRegistrationOtpRequest(owner, otp))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value(2404));
        String signupToken = store.issueToken(OtpPurpose.SIGNUP, owner);
        mvc.perform(post("/api/v1/businesses/registrations").contentType("application/json")
                .content(json.writeValueAsString(application(owner, signupToken))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value(2434));
    }

    @Test void submissionRequiresTokenAndCannotUseRawOtp() throws Exception {
        String owner = address();
        String otp = store.issueOtp(OtpPurpose.BUSINESS_REGISTRATION, owner);
        var oldBody = json.valueToTree(application(owner, null));
        ((com.fasterxml.jackson.databind.node.ObjectNode) oldBody).put("otp", otp);
        mvc.perform(post("/api/v1/businesses/registrations").contentType("application/json").content(oldBody.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("registrationToken"));
        mvc.perform(post("/api/v1/businesses/registrations").contentType("application/json")
                .content(json.writeValueAsString(application(owner, otp))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value(2434));
        assertThat(registrationService.verifyOtp(new VerifyBusinessRegistrationOtpRequest(owner, otp)).registrationToken()).isNotBlank();
    }

    @Test void invalidApplicationDoesNotConsumeVerifiedToken() throws Exception {
        String owner = address();
        String token = verifiedRegistrationToken(owner);
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) json.valueToTree(application(owner, token));
        body.put("timezone", "Mars/Olympus_Mons");
        mvc.perform(post("/api/v1/businesses/registrations").contentType("application/json").content(body.toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/businesses/registrations").contentType("application/json")
                .content(json.writeValueAsString(application(owner, token))))
                .andExpect(status().isAccepted());
    }

    @Test void onlyPlatformMayReadAndApproveRegistrations() throws Exception {
        String id = submitRegistration(null, "Pending", address());
        mvc.perform(get("/api/v1/businesses/registrations")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/businesses/registrations/" + id + "/approve")
                .header("Authorization", bearer(admin))).andExpect(status().isUnauthorized());
    }

    @Test void approvingCreatesVerifiedOwnerWhoseEmailedPasswordWorksAndCanBeChanged() throws Exception {
        String owner = address();
        AuthResponse session = createApprovedOwner("Cà phê Đà Nẵng", owner);
        String code = codeFor(session.getUser().getBusinessId());
        assertThat(code).startsWith("CA-PHE-DA-NA-").hasSize(25);
        assertThat(session.getUser().getBranchRoles()).containsValue(UserRole.ADMIN);
        var password = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(registrationMailer).approved(any(), password.capture());
        mvc.perform(post("/api/v1/auth/change-password").header("Authorization", bearer(session))
                .contentType("application/json").content(json.writeValueAsString(
                        new ChangePasswordRequest(password.getValue(), PASSWORD))))
                .andExpect(status().isOk());
        assertThat(auth.login(new LoginRequest(code.toLowerCase(), owner, PASSWORD))).isNotNull();
    }

    @Test void duplicatePendingOwnerIsRefusedButNamesMayRepeat() throws Exception {
        String owner = address();
        submitRegistration(null, "Same Cafe", owner);
        submitRegistration(null, "Same Cafe", address());
        mvc.perform(post("/api/v1/businesses/registrations").contentType("application/json")
                .content(registrationBody(null, "Other Cafe", owner)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(2433));
    }

    @Test void legacyUnverifiedApplicationCannotBeApproved() throws Exception {
        String id = submitRegistration(null, "Legacy", address());
        transactions.outsideTenant(() -> jdbc.update(
                "UPDATE identity.business_registration SET owner_email_verified_at = NULL WHERE registration_id = ?", UUID.fromString(id)));
        mvc.perform(post("/api/v1/businesses/registrations/" + id + "/approve")
                .header("X-Platform-Key", PLATFORM_KEY)).andExpect(status().isForbidden());
        verifyNoInteractions(registrationMailer);
    }

    @Test void approvalRetriesBusinessCodeCollisionAndEmailsTheFinalCode() throws Exception {
        String id = submitRegistration(null, "Collision Cafe", address());
        transactions.outsideTenant(() -> jdbc.update(
                "UPDATE identity.business_registration SET business_code = ? WHERE registration_id = ?", business.getBusinessCode(), UUID.fromString(id)));
        var result = registrationService.approve(UUID.fromString(id), null);
        assertThat(result.getBusinessCode()).isNotEqualTo(business.getBusinessCode());
        var application = org.mockito.ArgumentCaptor.forClass(com.fnbx.identity.entity.BusinessRegistration.class);
        verify(registrationMailer).approved(application.capture(), any());
        assertThat(application.getValue().businessCode()).isEqualTo(result.getBusinessCode());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> registrationService.approve(UUID.fromString(id), null))
                .isInstanceOf(com.fnbx.shared.exception.AppException.class);
    }

    @Test void rejectionNeedsAReasonAndIsWhatTheApplicantIsTold() throws Exception {
        String registrationId = submitRegistration(code(), "Turned Away", address());

        mvc.perform(post("/api/v1/businesses/registrations/" + registrationId + "/reject")
                .contentType("application/json").header("X-Platform-Key", PLATFORM_KEY).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(2000));

        String reason = "The business licence number does not match the registered name";
        mvc.perform(post("/api/v1/businesses/registrations/" + registrationId + "/reject")
                .contentType("application/json").header("X-Platform-Key", PLATFORM_KEY)
                .content(json.writeValueAsString(new RejectRegistrationRequest(reason))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decisionNote").value(reason))
                .andExpect(jsonPath("$.createdBusinessId").doesNotExist());

        verify(registrationMailer).rejected(any(), eq(reason));

        // Rejecting frees the code and the address for a fresh application.
        mvc.perform(post("/api/v1/businesses/registrations/" + registrationId + "/reject")
                .contentType("application/json").header("X-Platform-Key", PLATFORM_KEY)
                .content(json.writeValueAsString(new RejectRegistrationRequest("again"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(2432));
    }

    @Test void aRegistrationWithAnUnusableTimezoneIsRefusedAtTheDoor() throws Exception {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("businessCode", code());
        body.put("businessName", "Bad Zone");
        body.put("timezone", "Mars/Olympus_Mons");
        body.put("branchName", "Main");
        body.put("ownerEmail", address());
        body.put("ownerFirstName", "Zone");
        body.put("ownerLastName", "Owner");
        mvc.perform(post("/api/v1/businesses/registrations").contentType("application/json")
                .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(2000));
    }

    // ---- join requests ------------------------------------------------------

    @Test void anApplicantBecomesStaffOnlyWhenSomebodyApprovesThem() throws Exception {
        String applicant = address();
        fileJoinRequest(applicant, "New", "Hire");

        String queue = mvc.perform(get("/api/v1/join-requests").param("status", "PENDING")
                .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].email").value(applicant))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                // The stored credential must never reach a response body.
                .andExpect(jsonPath("$.content[0].passcodeHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String requestId = json.readTree(queue).path("content").get(0).path("joinRequestId").asText();

        // Until it is approved, the applicant has no way in.
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(json.writeValueAsString(new LoginRequest(codeFor(business.getBusinessId()), applicant, PASSWORD))))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/join-requests/" + requestId + "/approve").contentType("application/json")
                .header("Authorization", bearer(admin))
                .content(json.writeValueAsString(new ApproveJoinRequest(mainBranchId, UserRole.STAFF, "Welcome"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(applicant))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.branchRoles['" + mainBranchId + "']").value("STAFF"));

        // The password they chose during signup is the one that now works.
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(json.writeValueAsString(new LoginRequest(codeFor(business.getBusinessId()), applicant, PASSWORD))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isString());

        // And the decision is final in both directions.
        mvc.perform(post("/api/v1/join-requests/" + requestId + "/approve").contentType("application/json")
                .header("Authorization", bearer(admin))
                .content(json.writeValueAsString(new ApproveJoinRequest(mainBranchId, UserRole.STAFF, null))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(2426));
    }

    @Test void rejectionNeedsAReasonAndClosesTheRequest() throws Exception {
        String applicant = address();
        fileJoinRequest(applicant, "Turned", "Down");
        String requestId = firstPendingId();

        mvc.perform(post("/api/v1/join-requests/" + requestId + "/reject").contentType("application/json")
                .header("Authorization", bearer(admin)).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(2000));

        mvc.perform(post("/api/v1/join-requests/" + requestId + "/reject").contentType("application/json")
                .header("Authorization", bearer(admin))
                .content(json.writeValueAsString(new RejectJoinRequest("Not hiring right now"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decisionNote").value("Not hiring right now"));
    }

    /** A second request from the same address replaces the first rather than colliding. */
    @Test void resubmittingReplacesTheLivingRequest() throws Exception {
        String applicant = address();
        fileJoinRequest(applicant, "First", "Attempt");
        fileJoinRequest(applicant, "Second", "Attempt");
        mvc.perform(get("/api/v1/join-requests").param("status", "PENDING")
                .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].firstName").value("Second"));
    }

    @Test void staffCannotReviewAndHrCannotCreateAnAdmin() throws Exception {
        // An HR, made by the ADMIN.
        String hrEmail = address();
        fileJoinRequest(hrEmail, "Human", "Resources");
        approve(firstPendingId(), mainBranchId, UserRole.HR, bearer(admin));
        AuthResponse hr = login(hrEmail);

        // Ordinary staff have no business reading the queue at all.
        String staffEmail = address();
        fileJoinRequest(staffEmail, "Regular", "Staff");
        approve(firstPendingId(), mainBranchId, UserRole.STAFF, bearer(hr));
        AuthResponse staff = login(staffEmail);
        mvc.perform(get("/api/v1/join-requests").header("Authorization", bearer(staff)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(2410));

        // HR may approve, but not into the role that would let the new person undo it.
        String candidate = address();
        fileJoinRequest(candidate, "Would", "BeAdmin");
        String requestId = firstPendingId();
        mvc.perform(post("/api/v1/join-requests/" + requestId + "/approve").contentType("application/json")
                .header("Authorization", bearer(hr))
                .content(json.writeValueAsString(new ApproveJoinRequest(mainBranchId, UserRole.ADMIN, null))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(2429));
        approve(requestId, mainBranchId, UserRole.ADMIN, bearer(admin));
    }

    // ---- branches and assignment --------------------------------------------

    @Test void branchesAreCreatedListedAndDeactivatedButNeverTheLastOne() throws Exception {
        mvc.perform(delete("/api/v1/branches/" + mainBranchId).header("Authorization", bearer(admin)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value(2428));

        String created = mvc.perform(post("/api/v1/branches").contentType("application/json")
                .header("Authorization", bearer(admin))
                .content(json.writeValueAsString(new CreateBranchRequest("District 2", "12 Nguyen Van Huong",
                        null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.branchCode").isString())
                .andReturn().getResponse().getContentAsString();
        String secondBranchId = json.readTree(created).path("branchId").asText();

        mvc.perform(delete("/api/v1/branches/" + secondBranchId).header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/branches").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/branches").param("includeInactive", "true")
                .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test void assignmentIsIdempotentAndTheBusinessAlwaysKeepsAnAdmin() throws Exception {
        String memberEmail = address();
        fileJoinRequest(memberEmail, "Branch", "Member");
        String staffId = approve(firstPendingId(), mainBranchId, UserRole.STAFF, bearer(admin));

        String assignment = "/api/v1/branches/" + mainBranchId + "/staff/" + staffId;
        mvc.perform(put(assignment).contentType("application/json").header("Authorization", bearer(admin))
                .content(json.writeValueAsString(new AssignBranchRoleRequest(UserRole.MANAGER))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("MANAGER"));
        // Same call again: a changed grant, never a duplicated one.
        mvc.perform(put(assignment).contentType("application/json").header("Authorization", bearer(admin))
                .content(json.writeValueAsString(new AssignBranchRoleRequest(UserRole.SHIFT_LEAD))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("SHIFT_LEAD"));
        mvc.perform(get("/api/v1/branches/" + mainBranchId + "/staff").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2));

        mvc.perform(delete(assignment).header("Authorization", bearer(admin))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/branches/" + mainBranchId + "/staff").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/branches/" + mainBranchId + "/staff").param("includeRevoked", "true")
                .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.content.length()").value(4))
                .andExpect(jsonPath("$.totalElements").value(4));

        // The owner cannot revoke themselves into a business with nobody in charge.
        mvc.perform(delete("/api/v1/branches/" + mainBranchId + "/staff/" + admin.getUser().getId())
                .header("Authorization", bearer(admin)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value(2430));
    }

    @Test void theBusinessProfileIsReadableByAnyoneAndWritableByAdminOnly() throws Exception {
        String staffEmail = address();
        fileJoinRequest(staffEmail, "Regular", "Staff");
        approve(firstPendingId(), mainBranchId, UserRole.STAFF, bearer(admin));
        AuthResponse staff = login(staffEmail);

        mvc.perform(get("/api/v1/businesses/me").header("Authorization", bearer(staff)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.businessName").value("Comfy Test"));
        mvc.perform(patch("/api/v1/businesses/me").contentType("application/json")
                .header("Authorization", bearer(staff))
                .content(json.writeValueAsString(new UpdateBusinessRequest("Renamed", null, null, null))))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/businesses/me").contentType("application/json")
                .header("Authorization", bearer(admin))
                .content(json.writeValueAsString(new UpdateBusinessRequest("Renamed", null, null, "Europe/Berlin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Renamed"))
                .andExpect(jsonPath("$.timezone").value("Europe/Berlin"))
                // The public handle is not editable, whoever asks.
                .andExpect(jsonPath("$.businessCode").value(business.getBusinessCode()));
        mvc.perform(patch("/api/v1/businesses/me").contentType("application/json")
                .header("Authorization", bearer(admin))
                .content(json.writeValueAsString(new UpdateBusinessRequest(null, null, null, "Mars/Olympus_Mons"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(2000));
    }

    /** RLS, from the outside: one tenant's queue is invisible to another's ADMIN. */
    @Test void joinRequestsAreInvisibleToAnotherTenant() throws Exception {
        fileJoinRequest(address(), "Ours", "Applicant");

        AuthResponse otherAdmin = createApprovedOwner("Other", address());

        mvc.perform(get("/api/v1/join-requests").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.content.length()").value(1));
        mvc.perform(get("/api/v1/join-requests").header("Authorization", bearer(otherAdmin)))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    // ---- helpers ------------------------------------------------------------

    private AuthResponse createApprovedOwner(String name, String email) {
        String token = verifiedRegistrationToken(email);
        UUID id = registrationService.submit(new BusinessRegistrationRequest(name, BusinessType.CAFE, null, null,
                "Main", null, email, "Test", "Owner", null, token)).getRegistrationId();
        var approved = registrationService.approve(id, null);
        businessCodes.put(approved.getCreatedBusinessId(), approved.getBusinessCode());
        var password = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(registrationMailer, atLeastOnce()).approved(any(), password.capture());
        return auth.login(new LoginRequest(approved.getBusinessCode(), email, password.getValue()));
    }

    private String verifiedRegistrationToken(String email) {
        String otp = store.issueOtp(OtpPurpose.BUSINESS_REGISTRATION, email);
        return registrationService.verifyOtp(new VerifyBusinessRegistrationOtpRequest(email, otp)).registrationToken();
    }

    private String codeFor(UUID id) { return businessCodes.getOrDefault(id, "MISSING-BUSINESS"); }

    private void fileJoinRequest(String email, String first, String last) {
        String proof = store.issueToken(OtpPurpose.SIGNUP, business.getBusinessId() + ":" + email);
        var result = auth.signup(new SignUpRequest(codeFor(business.getBusinessId()), email, proof, PASSWORD, first, last, null));
        assertThat(result.getOutcome()).isEqualTo(SignUpOutcome.PENDING_APPROVAL);
    }

    private String firstPendingId() throws Exception {
        String queue = mvc.perform(get("/api/v1/join-requests").param("status", "PENDING")
                .header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(queue).path("content").get(0).path("joinRequestId").asText();
    }

    /** @return the staff ID the approval created */
    private String approve(String requestId, UUID branchId, UserRole role, String bearer) throws Exception {
        String created = mvc.perform(post("/api/v1/join-requests/" + requestId + "/approve")
                .contentType("application/json").header("Authorization", bearer)
                .content(json.writeValueAsString(new ApproveJoinRequest(branchId, role, null))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(created).path("id").asText();
    }

    private AuthResponse login(String email) {
        return auth.login(new LoginRequest(codeFor(business.getBusinessId()), email, PASSWORD));
    }

    private String registrationBody(String businessCode, String businessName, String ownerEmail) throws Exception {
        String token = verifiedRegistrationToken(ownerEmail);
        return json.writeValueAsString(new BusinessRegistrationRequest(
                businessName, BusinessType.CAFE, null, null, "Main", "12 Le Loi",
                ownerEmail, "Reg", "Owner", "0900000000", token));
    }

    private BusinessRegistrationRequest application(String email, String token) {
        return new BusinessRegistrationRequest("OTP Cafe", null, null, null, "Main", null,
                email, "Test", "Owner", null, token);
    }

    /** @return the registration ID, which is all a public applicant gets back */
    private String submitRegistration(String businessCode, String businessName, String ownerEmail) throws Exception {
        String body = mvc.perform(post("/api/v1/businesses/registrations").contentType("application/json")
                .content(registrationBody(businessCode, businessName, ownerEmail)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.registrationId").isString())
                // The acknowledgement echoes nothing of the application back.
                .andExpect(jsonPath("$.ownerEmail").doesNotExist())
                .andExpect(jsonPath("$.businessCode").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("registrationId").asText();
    }

    private static String bearer(AuthResponse session) { return "Bearer " + session.getAccessToken(); }

    private static String address() { return UUID.randomUUID() + "@example.test"; }

    /** Unique across every tenant, so tests never collide on it. */
    private static String code() { return "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 15); }
}
