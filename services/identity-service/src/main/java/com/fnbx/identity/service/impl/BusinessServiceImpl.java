package com.fnbx.identity.service.impl;

import java.util.Locale;
import java.util.UUID;
import com.fnbx.identity.dto.request.UpdateBusinessRequest;
import com.fnbx.identity.dto.response.BusinessResponse;
import com.fnbx.identity.dto.response.MessageResponse;
import com.fnbx.identity.entity.BusinessProfile;
import com.fnbx.identity.exception.OnboardingExceptions;
import com.fnbx.identity.repository.BusinessRepository;
import com.fnbx.identity.service.BusinessService;
import com.fnbx.identity.dto.NewBusiness;
import com.fnbx.identity.service.TenantTransactions;
import com.fnbx.shared.enums.UserRole;
import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.exception.ErrorCode;
import com.fnbx.shared.security.AccessPrincipal;
import org.springframework.stereotype.Service;

/** Management of existing businesses. Creation happens through registration approval. */
@Service
public class BusinessServiceImpl implements BusinessService {

    private final BusinessRepository businesses;
    private final TenantTransactions transactions;

    public BusinessServiceImpl(BusinessRepository businesses, TenantTransactions transactions) {
        this.businesses = businesses;
        this.transactions = transactions;
    }

    @Override
    public MessageResponse deactivate(UUID businessId) {
        return transactions.inTenant(businessId, null, () -> {
            businesses.findActive(businessId).orElseThrow(OnboardingExceptions::businessNotFound);
            businesses.deactivate(businessId);
            return new MessageResponse("Business deactivated.");
        });
    }

    @Override
    public BusinessResponse current() {
        AccessPrincipal caller = AccessPrincipal.current();
        return transactions.inCurrentTenant(() -> response(
                businesses.find(caller.businessId()).orElseThrow(OnboardingExceptions::businessNotFound), null));
    }

    @Override
    public BusinessResponse update(UpdateBusinessRequest request) {
        AccessPrincipal caller = AccessPrincipal.current();
        caller.requireAnyBranch(UserRole.ADMIN);
        String currency = blank(request.currencyCode()) ? null : request.currencyCode().strip().toUpperCase(Locale.ROOT);
        String timezone = blank(request.timezone()) ? null : zone(request.timezone().strip());
        return transactions.inCurrentTenant(() -> {
            businesses.findActive(caller.businessId()).orElseThrow(OnboardingExceptions::businessNotFound);
            businesses.update(caller.businessId(), trimmed(request.businessName()), request.businessType(),
                    currency, timezone);
            return response(businesses.find(caller.businessId()).orElseThrow(OnboardingExceptions::businessNotFound),
                    null);
        });
    }

    // ------------------------------------------------------------------------

    private static BusinessResponse response(BusinessProfile business, UUID firstBranchId) {
        return BusinessResponse.builder()
                .businessId(business.businessId()).businessCode(business.businessCode())
                .businessName(business.businessName()).businessType(business.businessType())
                .currencyCode(business.currencyCode()).timezone(business.timezone()).active(business.active())
                .createdAt(business.createdAt()).updatedAt(business.updatedAt())
                .firstBranchId(firstBranchId)
                .build();
    }

    /**
     * Update takes its own copy of the timezone check because it accepts a partial
     * request - {@link NewBusiness} normalises a whole business, which is the wrong
     * shape for "change only the timezone".
     */
    private static String zone(String timezone) {
        try {
            return java.time.ZoneId.of(timezone).getId();
        } catch (java.time.DateTimeException invalid) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "timezone must be a valid IANA zone ID");
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String trimmed(String value) { return blank(value) ? null : value.strip(); }
}
