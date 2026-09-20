package com.fnbx.identity;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fnbx.identity.dto.request.*;
import com.fnbx.identity.dto.response.AuthResponse;
import com.fnbx.identity.dto.response.BusinessResponse;
import com.fnbx.identity.enums.SignUpOutcome;
import com.fnbx.shared.exception.AppException;
import com.fnbx.identity.repository.*;
import com.fnbx.identity.scheduler.RevokedTokenCleanupJob;
import com.fnbx.identity.service.*;
import com.fnbx.identity.utils.enums.OtpPurpose;
import com.fnbx.shared.enums.BusinessType;
import com.fnbx.shared.security.AccessPrincipal;
import com.fnbx.shared.tenant.TenantContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full production identity wiring, restricted DB role, actual migrations. Run tools/test-auth-postgres.sh.
 *
 * <p>The fixture is now three acts instead of one, and that is the refactor in
 * miniature: a business is <b>registered</b>, its owner is <b>provisioned</b>
 * unverified, and only then does signup <b>activate</b> that account. Signup used to
 * do all three at once, which is exactly why an anonymous caller could create
 * tenants. See docs/security/onboarding.md.
 */
@SpringBootTest(classes = {IdentityApplication.class, AuthPostgresTest.TimeConfiguration.class}, properties = {
    "fnb.security.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "fnb.security.jwt.revoked-token-cleanup-cron=-",
    "fnb.platform.admin-key=test-platform-admin-key-0123456789abcdef"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "FNB_AUTH_TEST_DB_URL", matches = ".+")
class AuthPostgresTest {
    private static final String PASSWORD = "StrongPassword1!";
    @Autowired AuthService auth;
    @Autowired BusinessService businessService;
    @Autowired BusinessRepository businesses;
    @Autowired BranchRepository branches;
    @Autowired StaffBranchRoleRepository assignments;
    @Autowired TenantTransactions tenantTransactions;
    private final Map<UUID, String> businessCodes = new HashMap<>();
    @Autowired VerificationStore store;
    @Autowired MutableClock clock;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtDecoder decoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;
    @Autowired RevokedTokenRepository revoked;
    @Autowired StaffRepository staffRepository;
    @Autowired RevokedTokenCleanupJob cleanup;
    @MockBean OtpMailer mailer;
    @MockBean GoogleTokenVerifier google;
    private String email;
    private AuthResponse initial;

    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("FNB_AUTH_TEST_DB_URL"));
        properties.add("spring.datasource.username", () -> "svc_identity");
        properties.add("spring.datasource.password", () -> "fnbx_auth_test_password");
    }

    @BeforeEach void account() {
        clock.set(Instant.now().minusSeconds(2));
        email = UUID.randomUUID() + "@example.test";
        when(mailer.available()).thenReturn(true);
        UUID businessId = registerBusiness().getBusinessId();
        provisionLegacyOwner(businessId, email, "Test", "Owner", null);
        initial = activate(businessId, email, PASSWORD, "Test", "Owner");
        assertThat(TenantContext.currentOrNull()).isNull();
    }

    @Test void roleVersionsPreserveChangesRevocationAndReassignment() {
        UUID business = initial.getUser().getBusinessId();
        UUID staffId = initial.getUser().getId();
        UUID branch = initial.getUser().getBranchRoles().keySet().iterator().next();
        TenantContext.runAs(TenantContext.of(business, staffId), () -> transactions.execute(status -> {
            assignments.assign(staffId, branch, business, com.fnbx.shared.enums.UserRole.MANAGER);
            assignments.assign(staffId, branch, business, com.fnbx.shared.enums.UserRole.MANAGER);
            assignments.assign(staffId, branch, business, com.fnbx.shared.enums.UserRole.ACCOUNTANT);
            var history = assignments.membersOf(branch, true);
            assertThat(history).hasSize(3);
            assertThat(history.get(0).role()).isEqualTo(com.fnbx.shared.enums.UserRole.ACCOUNTANT);
            assertThat(history.get(1).role()).isEqualTo(com.fnbx.shared.enums.UserRole.MANAGER);
            assertThat(history.get(1).revokedAt()).isEqualTo(history.get(0).assignedAt());
            assertThat(assignments.rolesFor(staffId)).containsEntry(branch, com.fnbx.shared.enums.UserRole.ACCOUNTANT);
            assertThat(assignments.revoke(staffId, branch)).isTrue();
            assertThat(assignments.revoke(staffId, branch)).isFalse();
            assertThat(assignments.rolesFor(staffId)).isEmpty();
            assignments.assign(staffId, branch, business, com.fnbx.shared.enums.UserRole.MANAGER);
            assertThat(assignments.membersOf(branch, true)).hasSize(4);
            assertThat(assignments.membersOf(branch, false)).hasSize(1);
            assertThat(assignments.countMembersOf(branch, true)).isEqualTo(4);
            return null;
        }));
    }

    @Test void roleHistoryRejectsRewritesDeletesAndOverlappingVersions() {
        UUID business = initial.getUser().getBusinessId();
        UUID staffId = initial.getUser().getId();
        UUID branch = initial.getUser().getBranchRoles().keySet().iterator().next();
        var tenant = TenantContext.of(business, staffId);
        TenantContext.runAs(tenant, () -> transactions.execute(status -> {
            assignments.assign(staffId, branch, business, com.fnbx.shared.enums.UserRole.MANAGER);
            return null;
        }));
        for (String sql : List.of(
                "UPDATE identity.staff_branch_role SET role = 'STAFF' WHERE staff_id = ?",
                "DELETE FROM identity.staff_branch_role WHERE staff_id = ?",
                "UPDATE identity.staff_branch_role SET revoked_at = NULL WHERE staff_id = ? AND revoked_at IS NOT NULL",
                "INSERT INTO identity.staff_branch_role (staff_id, branch_id, business_id, role, assigned_at, revoked_at) "
                        + "SELECT staff_id, branch_id, business_id, role, assigned_at, clock_timestamp() + interval '1 hour' "
                        + "FROM identity.staff_branch_role WHERE staff_id = ? AND revoked_at IS NULL")) {
            assertThatThrownBy(() -> TenantContext.runAs(tenant, () -> transactions.execute(status ->
                    jdbc.update(sql, staffId)))).isInstanceOf(org.springframework.dao.DataAccessException.class);
        }
    }

    @Test void concurrentIdenticalAssignmentsCreateOnlyOneSuccessor() throws Exception {
        UUID business = initial.getUser().getBusinessId();
        UUID staffId = initial.getUser().getId();
        UUID branch = initial.getUser().getBranchRoles().keySet().iterator().next();
        try (var workers = Executors.newFixedThreadPool(4)) {
            var start = new CountDownLatch(1);
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                results.add(workers.submit(() -> {
                    start.await();
                    return TenantContext.runAs(TenantContext.of(business, staffId), () -> transactions.execute(status -> {
                        assignments.assign(staffId, branch, business, com.fnbx.shared.enums.UserRole.MANAGER);
                        return null;
                    }));
                }));
            }
            start.countDown();
            for (var result : results) result.get(15, TimeUnit.SECONDS);
        }
        TenantContext.runAs(TenantContext.of(business, staffId), () -> transactions.execute(status -> {
            assertThat(assignments.membersOf(branch, true)).hasSize(2);
            assertThat(assignments.membersOf(branch, false)).hasSize(1);
            return null;
        }));
    }

    @Test void loginRefreshLogoutAndStatelessAccess() throws Exception {
        String loginBody = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(json.writeValueAsString(new LoginRequest(codeFor(initial.getUser().getBusinessId()), email, PASSWORD))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.result").doesNotExist()).andExpect(jsonPath("$.code").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        var signedIn = json.readValue(loginBody, AuthResponse.class);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + signedIn.getAccessToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        String refreshBody = mvc.perform(post("/api/v1/auth/refresh").contentType("application/json")
                .content(json.writeValueAsString(new RefreshTokenRequest(signedIn.getRefreshToken()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.result").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        var rotated = json.readValue(refreshBody, AuthResponse.class);
        assertThat(rotated.getRefreshToken()).isNotEqualTo(signedIn.getRefreshToken());
        assertFailure(2418, () -> auth.refresh(new RefreshTokenRequest(signedIn.getRefreshToken())));
        mvc.perform(post("/api/v1/auth/logout").contentType("application/json")
                .content(json.writeValueAsString(new RefreshTokenRequest(rotated.getRefreshToken()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Logged out."))
                .andExpect(jsonPath("$.result").doesNotExist());
        auth.logout(new RefreshTokenRequest(rotated.getRefreshToken()));
        assertFailure(2418, () -> auth.refresh(new RefreshTokenRequest(rotated.getRefreshToken())));
        assertThat(decoder.decode(rotated.getAccessToken())).isNotNull(); // Documented stateless limitation.
        auth.logout(new RefreshTokenRequest("invalid"));
    }

    @Test void concurrentRefreshHasOneWinnerAndTheWinnerCanContinue() throws Exception {
        List<Object> results = parallel(8, () -> {
            try { return auth.refresh(new RefreshTokenRequest(initial.getRefreshToken())); }
            catch (AppException ex) { return ex.getErrorCode().getCode(); }
        });
        assertThat(results.stream().filter(AuthResponse.class::isInstance)).hasSize(1);
        assertThat(results.stream().filter(Integer.class::isInstance)).containsOnly(2418).hasSize(7);
        var winner = (AuthResponse) results.stream().filter(AuthResponse.class::isInstance).findFirst().orElseThrow();
        assertThat(auth.refresh(new RefreshTokenRequest(winner.getRefreshToken()))).isNotNull();
    }

    @Test void centralizedApiPrefixAppliesOnceAndLeavesFrameworkRoutesUnchanged() throws Exception {
        String bearer = "Bearer " + initial.getAccessToken();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
        mvc.perform(get("/auth/me").header("Authorization", bearer))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/api/v1/auth/me").header("Authorization", bearer))
                .andExpect(status().isNotFound());
        // Inspect springdoc's registered route without depending on OpenAPI rendering.
        var mappings = mvc.getDispatcherServlet().getWebApplicationContext()
                .getBean("requestMappingHandlerMapping", org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping.class);
        assertThat(mappings.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getValue().getBeanType().getPackageName().startsWith("org.springdoc"))
                .flatMap(entry -> entry.getKey().getPatternValues().stream()).toList())
                .contains("/api-docs").noneMatch(path -> path.startsWith("/api/v1/"));
    }

    @Test void loginAcceptsEmailCaseInsensitivelyAndRejectsEmployeeCodesAndLegacyUsername() throws Exception {
        UUID businessId = initial.getUser().getBusinessId();
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(json.writeValueAsString(new LoginRequest(codeFor(businessId), email.toUpperCase(Locale.ROOT), PASSWORD))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.email").value(email));
        for (String invalid : List.of(initial.getUser().getEmployeeCode(), "not-an-email", "")) {
            mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                    .content(json.writeValueAsString(new LoginRequest(codeFor(businessId), invalid, PASSWORD))))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(2000));
        }
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("businessId", businessId, "username", email, "password", PASSWORD))))
                .andExpect(status().isBadRequest());
        assertFailure(2402, () -> auth.login(new LoginRequest(codeFor(businessId), initial.getUser().getEmployeeCode(), PASSWORD)));
    }

    @Test void emailUniquenessIgnoresCaseWithinABusinessAndAllowsSeparateBusinesses() {
        UUID businessId = initial.getUser().getBusinessId();
        assertThatThrownBy(() -> insertStaff(businessId, email.toUpperCase(Locale.ROOT)))
                .isInstanceOf(DuplicateKeyException.class);

        // The same address in a different tenant is a different person, and always was.
        UUID otherBusinessId = registerBusiness().getBusinessId();
        provisionLegacyOwner(otherBusinessId, email, "Other", "Owner", null);
        var otherSession = activate(otherBusinessId, email, PASSWORD, "Other", "Owner");
        assertThat(otherSession.getUser().getBusinessId()).isNotEqualTo(businessId);
        assertThat(login().getUser().getId()).isEqualTo(initial.getUser().getId());
        assertThat(auth.login(new LoginRequest(codeFor(otherBusinessId), email, PASSWORD)).getUser().getId())
                .isEqualTo(otherSession.getUser().getId());
    }

    @Test void concurrentCaseVariantStaffEmailsHaveOneDatabaseWinner() throws Exception {
        String address = UUID.randomUUID() + "@example.test";
        var attempt = new java.util.concurrent.atomic.AtomicInteger();
        List<Object> results = parallel(2, () -> {
            String variant = attempt.getAndIncrement() == 0 ? address : address.toUpperCase(Locale.ROOT);
            try {
                insertStaff(initial.getUser().getBusinessId(), variant);
                return true;
            } catch (DuplicateKeyException duplicate) {
                return false;
            }
        });
        assertThat(results).containsExactlyInAnyOrder(true, false);
    }

    private void insertStaff(UUID businessId, String address) {
        TenantContext.runAs(TenantContext.of(businessId, null), () -> transactions.execute(status ->
                jdbc.update("""
                    INSERT INTO identity.staff (business_id, employee_code, first_name, last_name, email, passcode_hash)
                    VALUES (?, ?, 'Uniqueness', 'test', ?, 'unused-test-hash')
                    """, businessId, UUID.randomUUID().toString(), address)));
    }

    @Test void replayCommitsAccountInvalidationAndStaleReplayCannotInvalidateANewLogin() {
        var winner = auth.refresh(new RefreshTokenRequest(initial.getRefreshToken()));
        clock.advance(Duration.ofSeconds(20));
        assertFailure(2407, () -> auth.refresh(new RefreshTokenRequest(initial.getRefreshToken())));
        assertFailure(2407, () -> auth.refresh(new RefreshTokenRequest(winner.getRefreshToken())));
        clock.advance(Duration.ofSeconds(1));
        var recovered = login();
        clock.advance(Duration.ofSeconds(20));
        assertFailure(2407, () -> auth.refresh(new RefreshTokenRequest(initial.getRefreshToken())));
        assertThat(auth.refresh(new RefreshTokenRequest(recovered.getRefreshToken()))).isNotNull();
    }

    @Test void passwordChangeInvalidatesEveryRefreshSession() {
        var sibling = login();
        clock.advance(Duration.ofSeconds(3));
        var principal = AccessPrincipal.from(decoder.decode(initial.getAccessToken()));
        assertFailure(2406, () -> auth.changePassword(principal, new ChangePasswordRequest("incorrect", "NewPassword2!")));
        auth.changePassword(principal, new ChangePasswordRequest(PASSWORD, "NewPassword2!"));
        assertFailure(2407, () -> auth.refresh(new RefreshTokenRequest(initial.getRefreshToken())));
        assertFailure(2407, () -> auth.refresh(new RefreshTokenRequest(sibling.getRefreshToken())));
        assertFailure(2402, this::login);
        assertThat(auth.login(new LoginRequest(codeFor(initial.getUser().getBusinessId()), email, "NewPassword2!"))).isNotNull();
    }

    @Test void sameSecondPasswordChangeInvalidatesOldRefreshAndAllowsImmediateLogin() {
        var before = login();
        var principal = AccessPrincipal.from(decoder.decode(before.getAccessToken()));
        auth.changePassword(principal, new ChangePasswordRequest(PASSWORD, "NewPassword2!"));
        assertFailure(2407, () -> auth.refresh(new RefreshTokenRequest(before.getRefreshToken())));
        var after = auth.login(new LoginRequest(codeFor(initial.getUser().getBusinessId()), email, "NewPassword2!"));
        assertThat(auth.refresh(new RefreshTokenRequest(after.getRefreshToken()))).isNotNull();
    }

    @Test void reuseInvalidatesEvenASessionMintedInTheDetectionSecond() {
        auth.refresh(new RefreshTokenRequest(initial.getRefreshToken()));
        clock.advance(Duration.ofSeconds(20));
        var sameSecondSession = login();
        assertFailure(2407, () -> auth.refresh(new RefreshTokenRequest(initial.getRefreshToken())));
        assertFailure(2407, () -> auth.refresh(new RefreshTokenRequest(sameSecondSession.getRefreshToken())));
        var recoveredInSameSecond = login();
        assertFailure(2407, () -> auth.refresh(new RefreshTokenRequest(initial.getRefreshToken())));
        assertThat(auth.refresh(new RefreshTokenRequest(recoveredInSameSecond.getRefreshToken()))).isNotNull();
    }

    @Test void bearerValidationRejectsRefreshTokensAndSpoofedHeaders() throws Exception {
        mvc.perform(get("/api/v1/auth/me").header("X-Business-Id", initial.getUser().getBusinessId()).header("X-User-Id", initial.getUser().getId()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(2409));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + initial.getRefreshToken()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(2407));
        mvc.perform(post("/api/v1/auth/refresh").contentType("application/json")
                .content(json.writeValueAsString(new RefreshTokenRequest(initial.getAccessToken()))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(2407));
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(2000))
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
    }

    @Test void corsIsRestrictedAndRefreshConflictsKeepVakotsClientCode() throws Exception {
        mvc.perform(options("/api/v1/auth/refresh").header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
        mvc.perform(options("/api/v1/auth/refresh").header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST")).andExpect(status().isForbidden());
        auth.refresh(new RefreshTokenRequest(initial.getRefreshToken()));
        mvc.perform(post("/api/v1/auth/refresh").contentType("application/json")
                .content(json.writeValueAsString(new RefreshTokenRequest(initial.getRefreshToken()))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(2418));
    }

    @Test void tenantContextIsEnforcedInsideTransactionsAndClearedOnConnectionReuse() {
        int own = TenantContext.runAs(TenantContext.of(initial.getUser().getBusinessId(), null),
                () -> transactions.execute(status -> jdbc.queryForObject("SELECT count(*) FROM identity.staff", Integer.class)));
        assertThat(own).isEqualTo(1);
        assertThat(transactions.<Integer>execute(status -> jdbc.queryForObject("SELECT count(*) FROM identity.staff", Integer.class))).isZero();
        assertFailure(2402, () -> auth.login(new LoginRequest(codeFor(UUID.randomUUID()), email, PASSWORD)));
        assertThat(TenantContext.currentOrNull()).isNull();
    }

    /**
     * The replacement for "cannot self-enroll in another business". An outsider with a
     * verified address no longer gets a 2402 - they get a pending request, which is the
     * whole point of the refactor. What they still do not get is a way in.
     */
    @Test void signupNeedsProofAndAnUnknownAddressOnlyEarnsAPendingRequest() {
        UUID businessId = initial.getUser().getBusinessId();
        assertFailure(2412, () -> auth.signup(new SignUpRequest(codeFor(businessId), "no-proof@example.test", "fake",
                PASSWORD, "Test", "Intruder", null)));

        String outsider = UUID.randomUUID() + "@example.test";
        String proof = store.issueToken(OtpPurpose.SIGNUP, businessId + ":" + outsider);
        var filed = auth.signup(new SignUpRequest(codeFor(businessId), outsider, proof, PASSWORD, "Test", "Outsider", null));
        assertThat(filed.getOutcome()).isEqualTo(SignUpOutcome.PENDING_APPROVAL);
        assertThat(filed.getSession()).isNull();
        assertFailure(2402, () -> auth.login(new LoginRequest(codeFor(businessId), outsider, PASSWORD)));

        // The tenant must exist. Signup can no longer bring one into being.
        String proofElsewhere = store.issueToken(OtpPurpose.SIGNUP, UUID.randomUUID() + ":" + outsider);
        assertFailure(2420, () -> auth.signup(new SignUpRequest(codeFor(UUID.randomUUID()), outsider, proofElsewhere,
                PASSWORD, "Test", "Outsider", null)));
    }

    @Test void emailRecoveryIsGenericTenantBoundSingleUseAndRevokesRefreshInTheSameSecond() {
        var known = auth.forgotPassword(new ForgotPasswordRequest(codeFor(initial.getUser().getBusinessId()), email));
        var unknown = auth.forgotPassword(new ForgotPasswordRequest(codeFor(initial.getUser().getBusinessId()), "unknown@example.test"));
        assertThat(known).isEqualTo(unknown);
        String scope = initial.getUser().getBusinessId() + ":" + email;
        store.discardOtp(OtpPurpose.PASSWORD_RESET, scope);
        String otp = store.issueOtp(OtpPurpose.PASSWORD_RESET, scope);
        assertFailure(2404, () -> auth.verifyResetPasswordOtp(new VerifyOtpRequest(codeFor(UUID.randomUUID()), email, otp)));
        String proof = auth.verifyResetPasswordOtp(new VerifyOtpRequest(codeFor(initial.getUser().getBusinessId()), email, otp)).getResetToken();
        var reset = new ResetPasswordRequest(codeFor(initial.getUser().getBusinessId()), email, proof, "NewPassword2!");
        auth.resetPassword(reset);
        assertFailure(2415, () -> auth.resetPassword(reset));
        assertFailure(2407, () -> auth.refresh(new RefreshTokenRequest(initial.getRefreshToken())));
        assertThat(auth.login(new LoginRequest(codeFor(initial.getUser().getBusinessId()), email, "NewPassword2!"))).isNotNull();
    }

    /**
     * A verified Google email proves who somebody is. It never proved they work here,
     * and it no longer creates a business either - both endings are a request now.
     */
    @Test void googleProofEarnsAJoinRequestRatherThanMembership() {
        when(google.verify("verified-google-token")).thenReturn(
                new GoogleTokenVerifier.GoogleUser("outsider@example.test", "Outside", "User", null));
        var result = auth.google(new GoogleAuthRequest(codeFor(initial.getUser().getBusinessId()), "verified-google-token"));
        assertThat(result.getOutcome()).isEqualTo(SignUpOutcome.PENDING_APPROVAL);
        assertThat(result.getSession()).isNull();
        assertFailure(2420, () -> auth.google(new GoogleAuthRequest(codeFor(UUID.randomUUID()), "verified-google-token")));
    }

    @Test void provisionedOwnerActivatesOverHttpAndTheProofIsSingleUse() throws Exception {
        UUID businessId = registerBusiness().getBusinessId();
        String newEmail = UUID.randomUUID() + "@example.test";
        provisionLegacyOwner(businessId, newEmail, "Ho", "Viet Bach", null);

        mvc.perform(post("/api/v1/auth/signup/start").contentType("application/json")
                .content(json.writeValueAsString(new StartSignUpRequest(codeFor(businessId), newEmail))))
                .andExpect(status().isOk());
        var otp = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mailer).mail(eq(OtpPurpose.SIGNUP), eq(businessId + ":" + newEmail), eq(newEmail), otp.capture());
        String verified = mvc.perform(post("/api/v1/auth/signup/verify").contentType("application/json")
                .content(json.writeValueAsString(new VerifyOtpRequest(codeFor(businessId), newEmail, otp.getValue()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String proof = json.readTree(verified).path("signupToken").asText();
        String signup = json.writeValueAsString(new SignUpRequest(codeFor(businessId), newEmail, proof, PASSWORD,
                "Ho", "Viet Bach", null));
        mvc.perform(post("/api/v1/auth/signup").contentType("application/json").content(signup))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("SESSION_ISSUED"))
                .andExpect(jsonPath("$.session.user.email").value(newEmail))
                .andExpect(jsonPath("$.session.user.firstName").value("Ho"))
                .andExpect(jsonPath("$.session.user.lastName").value("Viet Bach"))
                .andExpect(jsonPath("$.session.user.employeeCode").value("bachho"))
                .andExpect(jsonPath("$.session.user.fullName").doesNotExist())
                .andExpect(jsonPath("$.session.accessToken").isString())
                .andExpect(jsonPath("$.result").doesNotExist());
        assertThat(store.consumeToken(OtpPurpose.SIGNUP, businessId + ":" + newEmail, proof)).isFalse();
        mvc.perform(post("/api/v1/auth/signup").contentType("application/json").content(signup))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(2401));
    }

    /** An unknown address gets 202 and no session, and the body says which ending it was. */
    @Test void anUnknownAddressGetsAcceptedRatherThanCreatedOverHttp() throws Exception {
        UUID businessId = initial.getUser().getBusinessId();
        String applicant = UUID.randomUUID() + "@example.test";
        String proof = store.issueToken(OtpPurpose.SIGNUP, businessId + ":" + applicant);
        mvc.perform(post("/api/v1/auth/signup").contentType("application/json")
                .content(json.writeValueAsString(new SignUpRequest(codeFor(businessId), applicant, proof, PASSWORD,
                        "New", "Applicant", null))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.outcome").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.session.accessToken").doesNotExist());
    }

    /**
     * The removed onboarding fields are ignored, not honoured.
     *
     * <p>Jackson tolerates unknown properties here, so this cannot assert a 400 - and
     * asserting "no business called Smuggled Business exists" would be vacuous, because
     * RLS returns zero rows to a query with no tenant context whatever the truth is.
     * What it does assert is the reachable half: an old client still sending
     * {@code businessName} gets an ordinary pending request. That signup can no longer
     * bring a tenant into being at all is covered by the 2420 in
     * {@link #signupNeedsProofAndAnUnknownAddressOnlyEarnsAPendingRequest}, which fails
     * precisely because the business has to already exist.
     */
    @Test void signupIgnoresTheRemovedBusinessCreationFields() throws Exception {
        UUID businessId = initial.getUser().getBusinessId();
        String applicant = UUID.randomUUID() + "@example.test";
        String proof = store.issueToken(OtpPurpose.SIGNUP, businessId + ":" + applicant);
        var legacyBody = new LinkedHashMap<String, Object>();
        legacyBody.put("businessCode", codeFor(businessId));
        legacyBody.put("email", applicant);
        legacyBody.put("signupToken", proof);
        legacyBody.put("password", PASSWORD);
        legacyBody.put("firstName", "New");
        legacyBody.put("lastName", "Applicant");
        legacyBody.put("businessName", "Smuggled Business");
        legacyBody.put("branchName", "Main");
        legacyBody.put("businessType", "CAFE");
        mvc.perform(post("/api/v1/auth/signup").contentType("application/json")
                .content(json.writeValueAsString(legacyBody)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.outcome").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.session.accessToken").doesNotExist());
    }

    @Test void employeeCodesIncrementForAnyBaseCollisionAndAreScopedToBusiness() {
        UUID business = initial.getUser().getBusinessId();
        assertThat(createNamedStaff(business, "Ho", "Viet Bach")).isEqualTo("bachho");
        assertThat(createNamedStaff(business, "Ho", "Viet Bach")).isEqualTo("bachho1");
        assertThat(createNamedStaff(business, "Ho", "Nam Bach")).isEqualTo("bachho2");
        assertThat(createNamedStaff(registerBusiness().getBusinessId(), "Ho", "Viet Bach")).isEqualTo("bachho");
    }

    @Test void concurrentSameNameStaffCreationAllocatesDistinctSequentialCodes() throws Exception {
        List<Object> codes = parallel(8, () -> createNamedStaff(initial.getUser().getBusinessId(), "Ho", "Viet Bach"));
        assertThat(codes).containsExactlyInAnyOrder("bachho", "bachho1", "bachho2", "bachho3",
                "bachho4", "bachho5", "bachho6", "bachho7");
    }

    @Test void profileNamesCanChangeWithoutRenamingTheExistingEmployeeCode() throws Exception {
        mvc.perform(patch("/api/v1/auth/me").header("Authorization", "Bearer " + initial.getAccessToken())
                .contentType("application/json")
                .content(json.writeValueAsString(new UpdateProfileRequest("Ho", "Viet Bach", null, null))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.firstName").value("Ho"))
                .andExpect(jsonPath("$.lastName").value("Viet Bach"))
                .andExpect(jsonPath("$.employeeCode").value(initial.getUser().getEmployeeCode()))
                .andExpect(jsonPath("$.fullName").doesNotExist());
    }

    private String createNamedStaff(UUID business, String firstName, String lastName) {
        return TenantContext.runAs(TenantContext.of(business, null), () -> transactions.execute(status -> {
            UUID staffId = staffRepository.create(business, UUID.randomUUID() + "@example.test",
                    firstName, lastName, null, "unused-test-hash", "LOCAL", null, true);
            return staffRepository.find(staffId).orElseThrow().employeeCode();
        }));
    }

    @Test void tokenLifetimesMatchVakotAndRefreshExtendsTheSlidingWindow() throws Exception {
        var access = decoder.decode(initial.getAccessToken());
        assertThat(access.getClaims()).doesNotContainKey("role").containsKey("branch_roles");
        assertThat(Duration.between(access.getIssuedAt(), access.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
        var refresh = com.nimbusds.jwt.SignedJWT.parse(initial.getRefreshToken()).getJWTClaimsSet();
        assertThat(refresh.getClaims()).doesNotContainKey("role").containsKey("branch_roles");
        assertThat(Duration.between(refresh.getIssueTime().toInstant(), refresh.getExpirationTime().toInstant()))
                .isEqualTo(Duration.ofDays(7));
        clock.advance(Duration.ofHours(1));
        var renewed = auth.refresh(new RefreshTokenRequest(initial.getRefreshToken()));
        var next = com.nimbusds.jwt.SignedJWT.parse(renewed.getRefreshToken()).getJWTClaimsSet();
        assertThat(next.getExpirationTime()).isAfter(refresh.getExpirationTime());
    }

    @Test void failedIssuanceRollsBackRotationAndReadOnlyTransactionsCarryTheTenant() throws Exception {
        UUID business = initial.getUser().getBusinessId();
        TenantContext.runAs(TenantContext.of(business, null), () -> transactions.execute(status ->
                assignments.revoke(initial.getUser().getId(), initial.getUser().getBranchRoles().keySet().iterator().next())));
        assertFailure(2410, () -> auth.refresh(new RefreshTokenRequest(initial.getRefreshToken())));
        UUID jti = UUID.fromString(com.nimbusds.jwt.SignedJWT.parse(initial.getRefreshToken()).getJWTClaimsSet().getJWTID());
        assertThat(revoked.revokedAt(jti)).isEmpty();
        TenantContext.runAs(TenantContext.of(business, null), () -> transactions.execute(status ->
                { assignments.assign(initial.getUser().getId(), initial.getUser().getBranchRoles().keySet().iterator().next(),
                        business, com.fnbx.shared.enums.UserRole.ADMIN); return null; }));
        assertThat(auth.refresh(new RefreshTokenRequest(initial.getRefreshToken()))).isNotNull();
        var readOnly = new TransactionTemplate(transactions.getTransactionManager());
        readOnly.setReadOnly(true);
        int visible = TenantContext.runAs(TenantContext.of(business, null), () -> readOnly.execute(status ->
                jdbc.queryForObject("SELECT count(*) FROM identity.staff", Integer.class)));
        assertThat(visible).isEqualTo(1);
    }

    @Test void disabledAccountsCannotLoginOrRefreshAndCleanupKeepsSkewMargin() {
        TenantContext.runAs(TenantContext.of(initial.getUser().getBusinessId(), null), () -> transactions.execute(status ->
                jdbc.update("UPDATE identity.staff SET is_active = false WHERE staff_id = ?", initial.getUser().getId())));
        assertFailure(2403, this::login);
        assertFailure(2403, () -> auth.refresh(new RefreshTokenRequest(initial.getRefreshToken())));
        UUID expired = UUID.randomUUID(); UUID withinMargin = UUID.randomUUID();
        transactions.execute(status -> {
            revoked.revoke(expired, clock.instant().minus(Duration.ofDays(2)), clock.instant().minus(Duration.ofDays(3)));
            revoked.revoke(withinMargin, clock.instant().minusSeconds(20), clock.instant().minusSeconds(30));
            return null;
        });
        cleanup.purgeExpired();
        assertThat(revoked.revokedAt(expired)).isEmpty();
        assertThat(revoked.revokedAt(withinMargin)).isPresent();
    }

    /** A deactivated tenant locks out everyone in it, which is why only platform can do it. */
    @Test void deactivatingTheBusinessStopsItsStaffSigningIn() {
        businessService.deactivate(initial.getUser().getBusinessId());
        assertFailure(2402, this::login);
        assertFailure(2403, () -> auth.refresh(new RefreshTokenRequest(initial.getRefreshToken())));
    }

    // ---- fixture helpers ----------------------------------------------------

    private BusinessResponse registerBusiness() {
        // Codes are unique across every tenant, so each test gets its own.
        String code = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 15).toUpperCase(Locale.ROOT);
        UUID id = UUID.randomUUID();
        UUID branchId = tenantTransactions.inTenant(id, null, () -> {
            businesses.insert(id, code, "Test Business", BusinessType.CAFE, "VND", "Asia/Ho_Chi_Minh");
            return branches.insert(id, "MAIN", "Main", null, null, null);
        });
        businessCodes.put(id, code);
        return BusinessResponse.builder().businessId(id).businessCode(code).firstBranchId(branchId).build();
    }

    private void provisionLegacyOwner(UUID businessId, String address, String first, String last, String phone) {
        tenantTransactions.inTenant(businessId, null, () -> {
            UUID staffId = staffRepository.create(businessId, address, first, last, phone, "unusable", "LOCAL", null, false);
            assignments.assign(staffId, branches.firstActive().orElseThrow().branchId(), businessId,
                    com.fnbx.shared.enums.UserRole.ADMIN);
            return null;
        });
    }

    private String codeFor(UUID id) { return businessCodes.getOrDefault(id, "MISSING-BUSINESS"); }

    /** The provisioned account has no usable password until its owner sets one here. */
    private AuthResponse activate(UUID businessId, String address, String password, String first, String last) {
        String proof = store.issueToken(OtpPurpose.SIGNUP, businessId + ":" + address);
        var result = auth.signup(new SignUpRequest(codeFor(businessId), address, proof, password, first, last, null));
        assertThat(result.getOutcome()).isEqualTo(SignUpOutcome.SESSION_ISSUED);
        return result.getSession();
    }

    private AuthResponse login() { return auth.login(new LoginRequest(codeFor(initial.getUser().getBusinessId()), email.toUpperCase(Locale.ROOT), PASSWORD)); }
    private static void assertFailure(int code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(AppException.class, ex -> assertThat(ex.getErrorCode().getCode()).isEqualTo(code));
    }
    private static List<Object> parallel(int count, Callable<Object> task) throws Exception {
        try (var pool = Executors.newFixedThreadPool(count)) {
            var start = new CountDownLatch(1);
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) futures.add(pool.submit(() -> { start.await(); return task.call(); }));
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        }
    }
    @TestConfiguration static class TimeConfiguration {
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
    }
    static class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.now());
        void set(Instant instant) { now.set(instant); }
        void advance(Duration duration) { now.updateAndGet(value -> value.plus(duration)); }
        @Override public Instant instant() { return now.get(); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }
}
