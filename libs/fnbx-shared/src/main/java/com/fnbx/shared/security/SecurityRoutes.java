package com.fnbx.shared.security;

import java.util.List;

/**
 * The routes that are not authenticated by a bearer token, in one place.
 *
 * <p>Two different reasons a route appears here, and they must not be confused:
 * <ul>
 *   <li>{@link #PUBLIC_AUTH_PATHS} and {@link #PUBLIC_SUBMIT_PATHS} are genuinely
 *       public. They authenticate nothing, or they authenticate with something
 *       carried in the body (a password, an OTP proof).</li>
 *   <li>{@link #PLATFORM_ROUTES} are <b>not</b> public. They are exempt from
 *       Spring Security only because they are authenticated by a different
 *       mechanism - the shared platform key, checked by identity-service's
 *       {@code PlatformKeyFilter}. Marking them {@code permitAll} without that
 *       filter in place would leave tenant creation open to the internet.</li>
 * </ul>
 *
 * <p>That second point is why {@code PLATFORM_ROUTES} is a typed list read by
 * both the security configuration and the filter, rather than two hand-kept
 * string arrays. A route present in one and missing from the other is exactly how
 * an unauthenticated endpoint ships.
 */
public final class SecurityRoutes {
    private SecurityRoutes() {}

    /** The deliberately public authentication operations, accepted only as POST. */
    public static final String[] PUBLIC_AUTH_PATHS = {
        "/api/v1/auth/login", "/api/v1/auth/signin", "/api/v1/auth/refresh", "/api/v1/auth/logout",
        "/api/v1/auth/signup/start", "/api/v1/auth/signup/verify", "/api/v1/auth/signup", "/api/v1/auth/google",
        "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password/verify", "/api/v1/auth/reset-password"
    };

    public static final String[] PUBLIC_SWAGGER_API_DOCS_PATHS = {
            "/swagger-ui",
            "/swagger-ui/**",
            "/api-docs",
            "/api-docs/**"
    };

    /**
     * Public writes, accepted only as POST and outside {@code /auth}.
     *
     * <p>Filing a business registration is open to the internet by design - it is how
     * a stranger asks for a tenant. It creates a row in a review queue and nothing
     * else; only a platform administrator's approval creates a business. Note that
     * only submission and OTP start/verify paths are here: the decisions on {@code /registrations/{id}}
     * are platform routes below.
     */
    public static final String[] PUBLIC_SUBMIT_PATHS = {
        "/api/v1/businesses/registrations",
        "/api/v1/businesses/registrations/start",
        "/api/v1/businesses/registrations/verify"
    };

    /**
     * Tenant provisioning and the registration queue. Authenticated by the platform
     * key, never by a JWT: the business being created has no staff yet, so there is no
     * token it could issue, and a registration belongs to no tenant at all.
     *
     * <p>{@code POST /businesses/registrations} is NOT here - it is public, above.
     * The patterns are method-and-segment exact so the two never overlap: a POST to
     * the collection is public, a POST to {@code .../{id}/approve} is not.
     */
    public static final List<Route> PLATFORM_ROUTES = List.of(
        new Route("DELETE", "/api/v1/businesses/*"),
        new Route("GET", "/api/v1/businesses/registrations"),
        new Route("GET", "/api/v1/businesses/registrations/*"),
        new Route("POST", "/api/v1/businesses/registrations/*/approve"),
        new Route("POST", "/api/v1/businesses/registrations/*/reject"));

    /**
     * One HTTP method and one Ant path pattern. {@code *} matches a single path
     * segment, so {@code /api/v1/businesses/*} does not reach anything nested below.
     */
    public record Route(String method, String pattern) {}
}
