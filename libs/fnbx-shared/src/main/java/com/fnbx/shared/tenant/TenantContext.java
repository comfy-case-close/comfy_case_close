package com.fnbx.shared.tenant;

import java.util.UUID;

/**
 * Tenant context of the current request.
 *
 * <p>For protected requests, business_id and user_id come from the verified JWT.
 * Identity's public credential flows explicitly select a business namespace before
 * verifying credentials; selecting that namespace grants no authorization.
 *
 * <p>Loaded by {@link com.fnbx.shared.security.VerifiedTenantFilter} in each service,
 * and pushed down into PostgreSQL by {@link TenantAwareDataSource}.
 *
 * <p>Branch permissions are checked through
 * {@link com.fnbx.shared.security.AccessPrincipal}, using signed branch grants.
 */
public final class TenantContext {

    private static final ThreadLocal<TenantContext> CURRENT = new ThreadLocal<>();

    private final UUID businessId;
    private final UUID userId;

    private TenantContext(UUID businessId, UUID userId) {
        this.businessId = businessId;
        this.userId = userId;
    }

    public static TenantContext of(UUID businessId, UUID userId) {
        return new TenantContext(businessId, userId);
    }

    public static void set(TenantContext ctx) { CURRENT.set(ctx); }
    public static void clear() { CURRENT.remove(); }

    /** @return the current context, or null if none is loaded (e.g. a background job). */
    public static TenantContext currentOrNull() { return CURRENT.get(); }

    public static TenantContext current() {
        TenantContext c = CURRENT.get();
        if (c == null) {
            throw new IllegalStateException(
                "TenantContext is not loaded. Every request MUST pass through VerifiedTenantFilter. "
              + "For a background job, use TenantContext.runAs(...).");
        }
        return c;
    }

    /**
     * Run a piece of work under a specific tenant's context.
     * For schedulers and consumers, where there is no JWT.
     */
    public static <T> T runAs(TenantContext ctx, java.util.function.Supplier<T> work) {
        TenantContext previous = CURRENT.get();
        CURRENT.set(ctx);
        try {
            return work.get();
        } finally {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }

    public UUID businessId() { return businessId; }
    public UUID userId()     { return userId; }

    @Override public String toString() {
        return "TenantContext[business=" + businessId + ", user=" + userId + ']';
    }
}
