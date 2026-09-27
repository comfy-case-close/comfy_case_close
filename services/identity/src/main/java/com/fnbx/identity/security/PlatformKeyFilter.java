package com.fnbx.identity.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fnbx.shared.exception.ErrorCode;
import com.fnbx.shared.exception.ErrorResponse;
import com.fnbx.shared.security.SecurityRoutes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates the tenant-provisioning routes with a shared platform key.
 *
 * <h2>Why these routes cannot use a bearer token</h2>
 * Creating a business is the act that brings a tenant into existence. Before it
 * runs there is no staff row, so there is no token that could authorise it, and no
 * {@code business_id} for {@code TokenClaimsValidator} to check. The routes are
 * therefore {@code permitAll} in the shared security chain - and this filter is
 * what makes that safe. The two halves read the same
 * {@link SecurityRoutes#PLATFORM_ROUTES} list precisely so they cannot drift apart:
 * a route exempted from Spring Security but missing from this filter would be open
 * to the internet.
 *
 * <h2>Ordering</h2>
 * Registered as an ordinary servlet filter bean, so it runs <b>after</b> Spring
 * Security's chain (which sits near the front of the chain). Security lets the
 * request through on {@code permitAll}; this filter then decides.
 *
 * <h2>What this is not</h2>
 * One shared secret is not an account. There is no record of <i>which</i>
 * administrator provisioned a tenant and no way to revoke one of them
 * individually - see docs/security/onboarding.md section 8.2 for the upgrade to
 * real platform accounts. What it does give is the property that matters most
 * here: tenant creation is no longer reachable by an anonymous caller.
 */
@Component
public class PlatformKeyFilter extends OncePerRequestFilter {

    /** Deliberately not Authorization: this is not a bearer token and must not be parsed as one. */
    public static final String HEADER = "X-Platform-Key";

    /** Short keys are guessable, and this key guards the creation of tenants. */
    private static final int MINIMUM_KEY_LENGTH = 32;

    private static final AntPathMatcher PATHS = new AntPathMatcher();

    private final String expectedHash;
    private final ObjectMapper mapper;

    public PlatformKeyFilter(@Value("${fnb.platform.admin-key:${PLATFORM_ADMIN_KEY:}}") String key, ObjectMapper mapper) {
        String trimmed = key == null ? "" : key.strip();
        // Fail at startup, not at the first request. A service that boots with no key
        // configured would serve tenant creation to anyone who found the URL.
        if (trimmed.length() < MINIMUM_KEY_LENGTH) {
            throw new IllegalStateException("PLATFORM_ADMIN_KEY must be set to at least "
                    + MINIMUM_KEY_LENGTH + " characters; tenant provisioning is unauthenticated without it");
        }
        this.expectedHash = hash(trimmed);
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isPlatformRoute(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!presented(request)) {
            deny(request, response);
            return;
        }
        chain.doFilter(request, response);
    }

    /** Compared as hashes so the check is constant-time in the key's content and its length. */
    private boolean presented(HttpServletRequest request) {
        String provided = request.getHeader(HEADER);
        if (provided == null) return false;
        return MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.UTF_8),
                hash(provided.strip()).getBytes(StandardCharsets.UTF_8));
    }

    private boolean isPlatformRoute(HttpServletRequest request) {
        String path = path(request);
        for (SecurityRoutes.Route route : SecurityRoutes.PLATFORM_ROUTES) {
            if (route.method().equalsIgnoreCase(request.getMethod()) && PATHS.match(route.pattern(), path)) return true;
        }
        return false;
    }

    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return context == null || context.isEmpty() || !uri.startsWith(context) ? uri : uri.substring(context.length());
    }

    /**
     * 401 with the same envelope every other failure uses, and no hint about what
     * was wrong: a missing key and a wrong key are indistinguishable to the caller.
     */
    private void deny(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (response.isCommitted()) return;
        response.setStatus(ErrorCode.UNAUTHENTICATED.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(),
                ErrorResponse.of(ErrorCode.UNAUTHENTICATED, request.getRequestURI()));
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
