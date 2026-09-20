package com.fnbx.identity.service.impl;

import java.util.List;
import java.util.Locale;
import com.fnbx.identity.dto.request.StartBusinessRegistrationRequest;
import com.fnbx.identity.dto.response.MessageResponse;
import com.fnbx.identity.exception.AuthExceptions;
import com.fnbx.identity.service.VerificationStore;
import com.fnbx.identity.service.OtpMailer;
import com.fnbx.identity.utils.enums.OtpPurpose;
import com.fnbx.identity.utils.BusinessCodeUtils;
import java.util.UUID;
import com.fnbx.identity.dto.request.VerifyBusinessRegistrationOtpRequest;
import com.fnbx.identity.dto.response.BusinessRegistrationVerificationResponse;
import com.fnbx.identity.dto.request.ApproveRegistrationRequest;
import com.fnbx.identity.dto.request.BusinessRegistrationRequest;
import com.fnbx.identity.dto.request.RejectRegistrationRequest;
import com.fnbx.identity.dto.response.BusinessRegistrationResponse;
import com.fnbx.identity.dto.response.RegistrationSubmittedResponse;
import com.fnbx.identity.entity.BusinessRegistration;
import com.fnbx.identity.enums.RegistrationStatus;
import com.fnbx.identity.exception.OnboardingExceptions;
import com.fnbx.identity.repository.BusinessRegistrationRepository;
import com.fnbx.identity.service.BusinessProvisioning;
import com.fnbx.identity.service.BusinessRegistrationService;
import com.fnbx.identity.dto.NewBusiness;
import com.fnbx.identity.dto.NewOwner;
import com.fnbx.identity.service.RegistrationMailer;
import com.fnbx.identity.service.TenantTransactions;
import com.fnbx.shared.utils.PagedResponse;
import com.fnbx.shared.utils.PaginationUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import java.util.concurrent.atomic.AtomicBoolean;

/** Implementation of {@link BusinessRegistrationService}. */
@Service
public class BusinessRegistrationServiceImpl implements BusinessRegistrationService {

    private final BusinessRegistrationRepository registrations;
    private final BusinessProvisioning provisioning;
    private final RegistrationMailer mailer;
    private final TenantTransactions transactions;
    private final VerificationStore verification;
    private final OtpMailer otpMailer;

    public BusinessRegistrationServiceImpl(BusinessRegistrationRepository registrations,
            BusinessProvisioning provisioning, RegistrationMailer mailer,
            TenantTransactions transactions, VerificationStore verification, OtpMailer otpMailer) {
        this.registrations = registrations; this.provisioning = provisioning;
        this.mailer = mailer; this.transactions = transactions;
        this.verification = verification; this.otpMailer = otpMailer;
    }

    @Override
    public MessageResponse start(StartBusinessRegistrationRequest request) {
        String email = request.ownerEmail().strip().toLowerCase(Locale.ROOT);
        if (!otpMailer.available()) throw AuthExceptions.deliveryUnavailable();
        String otp = verification.issueOtp(OtpPurpose.BUSINESS_REGISTRATION, email);
        try { otpMailer.mail(OtpPurpose.BUSINESS_REGISTRATION, email, email, otp); }
        catch (RuntimeException unavailable) {
            verification.discardOtp(OtpPurpose.BUSINESS_REGISTRATION, email);
            throw AuthExceptions.deliveryUnavailable();
        }
        return new MessageResponse("Verification code sent.");
    }

    /** Verify the address first; the single-use token authorizes the later submission. */
    @Override
    public BusinessRegistrationVerificationResponse verifyOtp(VerifyBusinessRegistrationOtpRequest request) {
        String email = request.ownerEmail().strip().toLowerCase(Locale.ROOT);
        verification.consumeOtp(OtpPurpose.BUSINESS_REGISTRATION, email, request.otp());
        return new BusinessRegistrationVerificationResponse(
                verification.issueToken(OtpPurpose.BUSINESS_REGISTRATION, email),
                VerificationStore.VERIFIED_TOKEN_TTL.toMillis());
    }

    @Override
    public RegistrationSubmittedResponse submit(BusinessRegistrationRequest request) {
        NewBusiness business = NewBusiness.normalised(BusinessCodeUtils.generate(request.businessName()), request.businessName(),
                request.businessType(), request.currencyCode(), request.timezone(),
                BusinessCodeUtils.generate(request.branchName()), request.branchName(), request.branchAddress());
        NewOwner owner = NewOwner.normalised(request.ownerEmail(), request.ownerFirstName(),
                request.ownerLastName(), request.ownerPhone());
        if (!verification.consumeToken(OtpPurpose.BUSINESS_REGISTRATION, owner.email(), request.registrationToken())) {
            throw OnboardingExceptions.invalidRegistrationToken();
        }
        UUID registrationId;
        try {
            registrationId = BusinessCodeUtils.retry(() -> transactions.outsideTenant(() ->
                registrations.submit(BusinessCodeUtils.generate(business.businessName()), business.businessName(),
                        business.businessType(), business.currencyCode(), business.timezone(),
                        business.branchCode(), business.branchName(), business.branchAddress(),
                        owner.email(), owner.firstName(), owner.lastName(), owner.phone())),
                    "uq_registration_pending_code", "business_registration_pkey");
        } catch (DuplicateKeyException collision) {
            if (BusinessCodeUtils.violates(collision, "uq_registration_pending_email")) {
                throw OnboardingExceptions.registrationPendingExists();
            }
            throw collision;
        }
        return RegistrationSubmittedResponse.pending(registrationId);
    }

    @Override
    public PagedResponse<BusinessRegistrationResponse> list(RegistrationStatus status, int page, int size) {
        return transactions.outsideTenant(() -> {
            long total = registrations.count(status);
            List<BusinessRegistration> rows = registrations.page(status, size, (long) page * size);
            return PaginationUtils.toPagedResponse(
                    new PageImpl<>(rows, PageRequest.of(page, size), total),
                    BusinessRegistrationServiceImpl::response);
        });
    }

    @Override
    public BusinessRegistrationResponse get(UUID registrationId) {
        return transactions.outsideTenant(() -> response(
                registrations.find(registrationId).orElseThrow(OnboardingExceptions::registrationNotFound)));
    }

    /**
     * The business, the branch, the owner and the decision are written in ONE
     * transaction. Split across two, a failure between them would leave a tenant with
     * no owner and a registration that still looks pending - and the applicant with an
     * approval letter for a business nobody can enter.
     *
     * <p>The whole thing runs in the NEW business's tenant context. That is what
     * satisfies the RLS WITH CHECK on the business, branch and staff inserts; the
     * registration row itself has no policy, so updating it from inside that context
     * is fine.
     */
    @Override
    public BusinessRegistrationResponse approve(UUID registrationId, ApproveRegistrationRequest request) {
        String password = UUID.randomUUID().toString();
        AtomicBoolean regenerateCode = new AtomicBoolean();
        String note = trimmed(request == null ? null : request.note());

        Decision decision = BusinessCodeUtils.retry(() -> {
            UUID businessId = UUID.randomUUID();
            try {
                return transactions.inTenant(businessId, null, () -> {
                    BusinessRegistration application = registrations.lockById(registrationId)
                            .orElseThrow(OnboardingExceptions::registrationNotFound);
                    if (application.status() != RegistrationStatus.PENDING) {
                        throw OnboardingExceptions.registrationAlreadyDecided();
                    }
                    if (application.ownerEmailVerifiedAt() == null) throw AuthExceptions.emailNotVerified();
                    String code = regenerateCode.get() ? BusinessCodeUtils.generate(application.businessName()) : application.businessCode();
                    registrations.updateCodes(registrationId, code, application.branchCode());
                    UUID branchId = provisioning.createBusinessWithFirstBranch(businessId, NewBusiness.normalised(
                            code, application.businessName(), application.businessType(),
                            application.currencyCode(), application.timezone(),
                            application.branchCode(), application.branchName(), application.branchAddress()));
                    UUID staffId = provisioning.createOwner(businessId, branchId, NewOwner.normalised(
                            application.ownerEmail(), application.ownerFirstName(),
                            application.ownerLastName(), application.ownerPhone()), password);
                    if (!registrations.approve(registrationId, businessId, staffId, note)) {
                        throw OnboardingExceptions.registrationAlreadyDecided();
                    }
                    return new Decision(application,
                            registrations.find(registrationId).orElseThrow(OnboardingExceptions::registrationNotFound));
                });
            } catch (DuplicateKeyException collision) {
                if (BusinessCodeUtils.violates(collision, "business_business_code_key", "uq_registration_pending_code")) {
                    regenerateCode.set(true);
                }
                throw collision;
            }
        }, "business_pkey", "business_business_code_key", "branch_pkey", "uq_registration_pending_code");

        // After the commit, never inside it. A letter sent from within the transaction
        // would go out even if the transaction then rolled back, and the applicant would
        // be told about a business that does not exist.
        mailer.approved(decision.updated(), password);
        return response(decision.updated());
    }

    @Override
    public BusinessRegistrationResponse reject(UUID registrationId, RejectRegistrationRequest request) {
        String reason = request.reason().strip();
        Decision decision = transactions.outsideTenant(() -> {
            BusinessRegistration application = registrations.lockById(registrationId)
                    .orElseThrow(OnboardingExceptions::registrationNotFound);
            if (application.status() != RegistrationStatus.PENDING) {
                throw OnboardingExceptions.registrationAlreadyDecided();
            }
            registrations.reject(registrationId, reason);
            return new Decision(application,
                    registrations.find(registrationId).orElseThrow(OnboardingExceptions::registrationNotFound));
        });
        mailer.rejected(decision.application(), reason);
        return response(decision.updated());
    }

    // ------------------------------------------------------------------------

    /** The row as it was read, and as it ended up: the first is what the letter is written from. */
    private record Decision(BusinessRegistration application, BusinessRegistration updated) {}

    private static BusinessRegistrationResponse response(BusinessRegistration registration) {
        return BusinessRegistrationResponse.builder()
                .registrationId(registration.registrationId())
                .businessCode(registration.businessCode()).businessName(registration.businessName())
                .businessType(registration.businessType()).currencyCode(registration.currencyCode())
                .timezone(registration.timezone())
                .branchCode(registration.branchCode()).branchName(registration.branchName())
                .branchAddress(registration.branchAddress())
                .ownerEmail(registration.ownerEmail()).ownerFirstName(registration.ownerFirstName())
                .ownerLastName(registration.ownerLastName()).ownerPhone(registration.ownerPhone())
                .status(registration.status()).submittedAt(registration.submittedAt())
                .decidedAt(registration.decidedAt()).decisionNote(registration.decisionNote())
                .createdBusinessId(registration.createdBusinessId())
                .createdStaffId(registration.createdStaffId())
                .notifiedAt(registration.notifiedAt()).ownerEmailVerifiedAt(registration.ownerEmailVerifiedAt())
                .build();
    }

    private static String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
