package com.fnbx.identity.service.impl;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import com.fnbx.identity.dto.request.*;
import com.fnbx.identity.dto.response.*;
import com.fnbx.identity.entity.AuthAccount;
import com.fnbx.shared.exception.AppException;
import com.fnbx.identity.exception.AuthExceptions;
import com.fnbx.identity.exception.OnboardingExceptions;
import com.fnbx.identity.repository.AuthAccountRepository;
import com.fnbx.identity.repository.BusinessRepository;
import com.fnbx.identity.repository.RevokedTokenRepository;
import com.fnbx.identity.repository.StaffBranchRoleRepository;
import com.fnbx.identity.repository.StaffJoinRequestRepository;
import com.fnbx.identity.security.JwtService;
import com.fnbx.identity.security.StaffPasswordEncoder;
import com.fnbx.identity.service.*;
import com.fnbx.identity.utils.enums.OtpPurpose;
import com.fnbx.shared.security.AccessPrincipal;
import com.fnbx.shared.security.JwtSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

/** Tenant-scoped sessions, staff signup, and password recovery. Public requests select a business by code. */
@Service
public class AuthServiceImpl implements AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);
    private static final String RESET_MESSAGE = "If an account exists for that email, a verification code has been sent.";
    private final AuthAccountRepository accounts;
    private final RevokedTokenRepository revoked;
    private final BusinessRepository businesses;
    private final StaffBranchRoleRepository assignments;
    private final StaffJoinRequestRepository joinRequests;
    private final StaffPasswordEncoder passwords;
    private final JwtService jwtService;
    private final JwtSettings settings;
    private final TenantTransactions transactions;
    private final Clock clock;
    private final VerificationStore verification;
    private final GoogleTokenVerifier googleVerifier;
    private final OtpMailer mailer;

    public AuthServiceImpl(AuthAccountRepository accounts, RevokedTokenRepository revoked,
            BusinessRepository businesses, StaffBranchRoleRepository assignments,
            StaffJoinRequestRepository joinRequests, StaffPasswordEncoder passwords,
            JwtService jwtService, JwtSettings settings, TenantTransactions transactions, Clock clock,
            VerificationStore verification, GoogleTokenVerifier googleVerifier, OtpMailer mailer) {
        this.accounts = accounts; this.revoked = revoked; this.businesses = businesses;
        this.assignments = assignments; this.joinRequests = joinRequests; this.passwords = passwords;
        this.jwtService = jwtService; this.settings = settings; this.transactions = transactions; this.clock = clock;
        this.verification = verification; this.googleVerifier = googleVerifier; this.mailer = mailer;
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        UUID businessId = businesses.lookupByCode(request.businessCode())
                .orElseThrow(AuthExceptions::invalidCredentials).businessId();
        return inTenant(businessId, null, () -> {
            AuthAccount account = accounts.lockByEmail(normalize(request.email())).orElse(null);
            if (!passwords.matches(account, request.password())) throw AuthExceptions.invalidCredentials();
            assert account != null;
            requireEnabled(account);
            if (!account.emailVerified()) throw AuthExceptions.emailNotVerified();
            accounts.recordLogin(account.staffId(), clock.instant());
            return issueTokens(account);
        });
    }

    @Override
    public AuthResponse refresh(RefreshTokenRequest request) {
        Jwt jwt = decodeRefresh(request.refreshToken());
        UUID staffId = UUID.fromString(jwt.getClaimAsString("uid"));
        RefreshOutcome outcome = inTenant(businessId(jwt), staffId, () -> {
            AuthAccount account = accounts.lockById(staffId).orElseThrow(AuthExceptions::invalidToken);
            // Check staleness first: repeatedly replaying an already-dead token cannot log the owner out again.
            if (((Number) jwt.getClaim("refresh_version")).longValue() != account.refreshVersion()) throw AuthExceptions.invalidToken();
            Optional<Instant> previous = revoked.revokedAt(UUID.fromString(jwt.getId()));
            if (previous.isPresent()) {
                if (previous.get().plusSeconds(settings.refreshRotationGraceSeconds()).isAfter(clock.instant())) {
                    return new RefreshOutcome(null, AuthExceptions.refreshTokenRotated());
                }
                accounts.invalidateSessions(staffId, cutoff());
                log.warn("All refresh sessions invalidated after refresh-token reuse");
                return new RefreshOutcome(null, AuthExceptions.invalidToken());
            }
            requireEnabled(account);
            if (!account.emailVerified()) throw AuthExceptions.emailNotVerified();
            if (!revoked.revoke(UUID.fromString(jwt.getId()), jwt.getExpiresAt(), clock.instant())) {
                return new RefreshOutcome(null, AuthExceptions.refreshTokenRotated());
            }
            return new RefreshOutcome(issueTokens(account), null);
        });
        // A failure is returned from the transaction, not thrown inside it: revocation MUST commit.
        if (outcome.failure() != null) throw outcome.failure();
        return outcome.response();
    }

    @Override
    public MessageResponse logout(RefreshTokenRequest request) {
        Jwt jwt;
        try { jwt = jwtService.decodeRefresh(request.refreshToken()); }
        catch (JwtException | IllegalArgumentException invalid) { return new MessageResponse("Logged out."); }
        inTenant(businessId(jwt), UUID.fromString(jwt.getClaimAsString("uid")), () -> {
            accounts.lockById(UUID.fromString(jwt.getClaimAsString("uid")));
            revoked.revoke(UUID.fromString(jwt.getId()), jwt.getExpiresAt(), clock.instant());
            return null;
        });
        return new MessageResponse("Logged out.");
    }

    @Override
    public MessageResponse changePassword(AccessPrincipal caller, ChangePasswordRequest request) {
        validatePasswordBytes(request.newPassword());
        return inTenant(caller.businessId(), caller.staffId(), () -> {
            AuthAccount account = accounts.lockById(caller.staffId()).orElseThrow(AuthExceptions::invalidCredentials);
            requireEnabled(account);
            if (!passwords.matches(account, request.currentPassword())) throw AuthExceptions.currentPasswordIncorrect();
            accounts.changePassword(account.staffId(), passwords.encode(request.newPassword()), cutoff());
            return new MessageResponse("Password changed successfully.");
        });
    }

    @Override
    public AuthUserResponse currentUser(AccessPrincipal caller) {
        return inTenant(caller.businessId(), caller.staffId(), () -> userResponse(
                accounts.findById(caller.staffId()).orElseThrow(AuthExceptions::invalidCredentials)));
    }

    @Override
    public AuthUserResponse updateProfile(AccessPrincipal caller, UpdateProfileRequest request) {
        return inTenant(caller.businessId(), caller.staffId(), () -> {
            AuthAccount account = accounts.lockById(caller.staffId()).orElseThrow(AuthExceptions::invalidCredentials);
            requireEnabled(account);
            String firstName = request.firstName() == null ? null : required(request.firstName(), "firstName");
            String lastName = request.lastName() == null ? null : required(request.lastName(), "lastName");
            accounts.updateProfile(caller.staffId(), firstName, lastName, request.phone(), request.avatarUrl());
            return userResponse(accounts.findById(caller.staffId()).orElseThrow(AuthExceptions::invalidCredentials));
        });
    }

    /**
     * Mails the code that proves the address belongs to whoever asked.
     *
     * <p>It used to refuse when no account existed, because signing up into an existing
     * business meant activating one. That answer is now both wrong - an applicant has
     * no account yet, and that is the point - and a disclosure: differing responses
     * would turn this route into a way to ask who works at a business. So the only
     * thing that stops here is an address that already has a live, verified account,
     * which is a person who should be logging in instead.
     */
    @Override
    public MessageResponse startSignup(StartSignUpRequest request) {
        String email = normalize(request.email());
        UUID businessId = resolveBusiness(request.businessCode());
        inTenant(businessId, null, () -> {
            businesses.findActive(businessId).orElseThrow(OnboardingExceptions::businessNotFound);
            AuthAccount account = accounts.lockByEmail(email).orElse(null);
            if (account != null) {
                requireEnabled(account);
                if (account.emailVerified()) throw AuthExceptions.emailAlreadyExists();
            }
            return null;
        });
        mailOtp(OtpPurpose.SIGNUP, scope(businessId, email), email);
        return new MessageResponse("Verification code sent.");
    }

    @Override
    public SignUpVerificationResponse verifySignupOtp(VerifyOtpRequest request) {
        String scope = scope(resolveBusiness(request.businessCode()), normalize(request.email()));
        verification.consumeOtp(OtpPurpose.SIGNUP, scope, request.otp());
        return SignUpVerificationResponse.builder()
                .signupToken(verification.issueToken(OtpPurpose.SIGNUP, scope))
                .expiresInMs(VerificationStore.VERIFIED_TOKEN_TTL.toMillis())
                .build();
    }

    /**
     * Two endings, one request. The proof is consumed before either branch is chosen,
     * so a wrong token cannot be used to probe which ending an address would get, and
     * a valid one cannot be spent twice.
     */
    @Override
    public SignUpResponse signup(SignUpRequest request) {
        String email = normalize(request.email());
        validatePasswordBytes(request.password());
        String firstName = required(request.firstName(), "firstName");
        String lastName = required(request.lastName(), "lastName");
        UUID businessId = resolveBusiness(request.businessCode());
        return inTenant(businessId, null, () -> {
            businesses.findActive(businessId).orElseThrow(OnboardingExceptions::businessNotFound);
            AuthAccount existing = accounts.lockByEmail(email).orElse(null);
            if (existing != null) {
                requireEnabled(existing);
                if (existing.emailVerified()) throw AuthExceptions.emailAlreadyExists();
            }
            if (!verification.consumeToken(OtpPurpose.SIGNUP, scope(businessId, email), request.signupToken())) {
                throw AuthExceptions.invalidSignupToken();
            }
            if (existing == null) {
                joinRequests.submit(businessId, email, firstName, lastName, trimmed(request.phone()),
                        passwords.encode(request.password()), "LOCAL", null);
                return SignUpResponse.pending();
            }
            // A provisioned account: the platform administrator (or an approver) created
            // the row and the grants; this is the owner claiming it with a password.
            UUID staffId = existing.staffId();
            accounts.changePassword(staffId, passwords.encode(request.password()), cutoff());
            accounts.verifyEmail(staffId);
            accounts.updateProfile(staffId, firstName, lastName, trimmed(request.phone()), null);
            return SignUpResponse.session(
                    issueTokens(accounts.findById(staffId).orElseThrow(AuthExceptions::invalidCredentials)));
        });
    }

    @Override
    public SignUpResponse google(GoogleAuthRequest request) {
        var google = googleVerifier.verify(request.idToken());
        String email = normalize(google.email());
        UUID businessId = resolveBusiness(request.businessCode());
        return inTenant(businessId, null, () -> {
            businesses.findActive(businessId).orElseThrow(OnboardingExceptions::businessNotFound);
            String firstName = required(google.firstName(), "firstName");
            String lastName = google.lastName().trim();
            // A Google email proves identity, never membership of an arbitrary existing
            // tenant. An address nobody here knows gets to ask, not to walk in.
            AuthAccount account = accounts.lockByEmail(email).orElse(null);
            if (account == null) {
                joinRequests.submit(businessId, email, firstName, lastName, null, null, "GOOGLE", google.avatarUrl());
                return SignUpResponse.pending();
            }
            requireEnabled(account);
            if (!account.emailVerified()) {
                accounts.claimWithGoogle(account.staffId(), passwords.encode(UUID.randomUUID().toString()),
                        firstName, lastName, google.avatarUrl());
                account = accounts.findById(account.staffId()).orElseThrow(AuthExceptions::invalidCredentials);
            }
            accounts.recordLogin(account.staffId(), clock.instant());
            return SignUpResponse.session(issueTokens(account));
        });
    }

    @Override
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        String email = normalize(request.email());
        var business = businesses.lookupByCode(request.businessCode());
        if (business.isEmpty()) return new MessageResponse(RESET_MESSAGE);
        UUID businessId = business.get().businessId();
        boolean exists = inTenant(businessId, null, () -> accounts.emailExists(email));
        if (exists) {
            try { mailOtp(OtpPurpose.PASSWORD_RESET, scope(businessId, email), email); }
            catch (AppException ex) { log.warn("Password reset email could not be queued"); }
        }
        return new MessageResponse(RESET_MESSAGE);
    }

    @Override
    public PasswordResetVerificationResponse verifyResetPasswordOtp(VerifyOtpRequest request) {
        UUID businessId = businesses.lookupByCode(request.businessCode()).orElseThrow(AuthExceptions::invalidOtp).businessId();
        String scope = scope(businessId, normalize(request.email()));
        verification.consumeOtp(OtpPurpose.PASSWORD_RESET, scope, request.otp());
        return PasswordResetVerificationResponse.builder()
                .resetToken(verification.issueToken(OtpPurpose.PASSWORD_RESET, scope))
                .expiresInMs(VerificationStore.VERIFIED_TOKEN_TTL.toMillis())
                .build();
    }

    @Override
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        validatePasswordBytes(request.newPassword());
        String email = normalize(request.email());
        UUID businessId = businesses.lookupByCode(request.businessCode())
                .orElseThrow(AuthExceptions::invalidPasswordResetToken).businessId();
        return inTenant(businessId, null, () -> {
            AuthAccount account = accounts.lockByEmail(email).orElseThrow(AuthExceptions::invalidPasswordResetToken);
            if (!verification.consumeToken(OtpPurpose.PASSWORD_RESET, scope(businessId, email), request.resetToken())) {
                throw AuthExceptions.invalidPasswordResetToken();
            }
            accounts.changePassword(account.staffId(), passwords.encode(request.newPassword()), cutoff());
            return new MessageResponse("Password reset successfully.");
        });
    }

    private AuthResponse issueTokens(AuthAccount account) {
        requireEnabled(account);
        var roles = assignments.rolesFor(account.staffId());
        // An account with no live grant cannot be given a token: the access token's
        // branch_roles map is what every service authorizes against, and an empty one is
        // rejected by TokenClaimsValidator. This is why approval assigns a branch in the
        // same act, and why the last active branch of a business cannot be switched off.
        if (roles.isEmpty()) throw AuthExceptions.noActiveBranchAssignment();
        return AuthResponse.builder()
                .accessToken(jwtService.access(account, roles))
                .refreshToken(jwtService.refresh(account, roles))
                .tokenType("Bearer")
                .expiresInMs(settings.accessExpirationMs())
                .user(userResponse(account))
                .build();
    }

    private AuthUserResponse userResponse(AuthAccount account) {
        return AuthUserResponse.builder()
                .id(account.staffId())
                .businessId(account.businessId())
                .employeeCode(account.employeeCode())
                .firstName(account.firstName())
                .lastName(account.lastName())
                .email(account.email())
                .phone(account.phone())
                .avatarUrl(account.avatarUrl())
                .active(account.active())
                .branchRoles(Map.copyOf(assignments.rolesFor(account.staffId())))
                .build();
    }

    private void mailOtp(OtpPurpose purpose, String scope, String email) {
        if (!mailer.available()) throw AuthExceptions.deliveryUnavailable();
        String otp = verification.issueOtp(purpose, scope);
        try { mailer.mail(purpose, scope, email, otp); }
        catch (RuntimeException unavailable) {
            verification.discardOtp(purpose, scope);
            throw AuthExceptions.deliveryUnavailable();
        }
    }

    private UUID resolveBusiness(String code) {
        return businesses.lookupByCode(code).orElseThrow(OnboardingExceptions::businessNotFound).businessId();
    }

    private Jwt decodeRefresh(String token) {
        try { return jwtService.decodeRefresh(token); }
        catch (JwtException | IllegalArgumentException invalid) { throw AuthExceptions.invalidToken(); }
    }

    private <T> T inTenant(UUID businessId, UUID userId, java.util.function.Supplier<T> operation) {
        return transactions.inTenant(businessId, userId, operation);
    }

    private Instant cutoff() { return clock.instant().truncatedTo(ChronoUnit.SECONDS); }
    private static UUID businessId(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("business_id")); }
    private static String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    /** Codes are namespaced by business AND purpose, so one can never be replayed elsewhere. */
    private static String scope(UUID businessId, String email) { return businessId + ":" + email; }
    private static String trimmed(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    private static void requireEnabled(AuthAccount account) {
        if (!account.active() || !account.businessActive()) throw AuthExceptions.accountDisabled();
    }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw AuthExceptions.requiredField(field);
        return value.trim();
    }
    private static void validatePasswordBytes(String password) {
        if (!StaffPasswordEncoder.validLength(password)) throw AuthExceptions.passwordTooLong();
    }
    private record RefreshOutcome(AuthResponse response, AppException failure) {}
}
