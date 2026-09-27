package com.fnbx.identity.service;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import com.fnbx.shared.tenant.TenantContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs a unit of work inside one tenant's transaction.
 *
 * <p>The order matters and is the whole reason this is not {@code @Transactional}:
 * the tenant must be selected <b>before</b> the transaction opens, because
 * {@code TenantAwareDataSource} pushes {@code app.business_id} down with
 * {@code set_config(..., true)} - transaction-local, as PgBouncer requires. A
 * transaction that begins before the context is chosen runs with
 * {@code current_business_id()} null, and every RLS policy then evaluates to null:
 * the query returns nothing rather than everything, which fails closed but also
 * fails confusingly.
 *
 * <p>Two entry points, for the two ways identity learns who the tenant is:
 * {@link #inTenant} for the credential and provisioning flows, which select a
 * business namespace explicitly and hold no token, and {@link #inCurrentTenant} for
 * ordinary requests, where {@code VerifiedTenantFilter} already put the verified
 * JWT's tenant in scope.
 */
@Component
public class TenantTransactions {

    private final TransactionTemplate transactions;

    public TenantTransactions(TransactionTemplate transactions) { this.transactions = transactions; }

    /**
     * Selecting a business namespace is not an authorization decision. The caller
     * must still establish who they are inside {@code work} - by password, by a
     * verified OTP proof, or by holding the platform key.
     */
    public <T> T inTenant(UUID businessId, UUID userId, Supplier<T> work) {
        return TenantContext.runAs(TenantContext.of(Objects.requireNonNull(businessId, "businessId"), userId),
                () -> transactions.execute(status -> work.get()));
    }

    /** For a request that already passed through {@code VerifiedTenantFilter}. */
    public <T> T inCurrentTenant(Supplier<T> work) {
        TenantContext current = TenantContext.current();
        return inTenant(current.businessId(), current.userId(), work);
    }

    /**
     * Runs in a transaction with <b>no</b> tenant selected, for the handful of rows
     * that belong to no business: {@code business_registration} above all, where the
     * business is the thing being applied for.
     *
     * <p>This is not a way to see across tenants. {@code TenantAwareDataSource} still
     * pushes an empty {@code app.business_id}, so {@code current_business_id()} is
     * null and every RLS policy evaluates to null rather than true - a tenant table
     * queried from in here returns <b>zero rows</b>, not all of them. Only a table
     * with no policy at all is reachable, which is exactly the set this is for.
     *
     * <p>Any context the caller was already in is restored afterwards, so this is safe
     * to use from an async mailer running on its own thread.
     */
    public <T> T outsideTenant(Supplier<T> work) {
        return TenantContext.runAs(TenantContext.of(null, null),
                () -> transactions.execute(status -> work.get()));
    }
}
