package com.fnbx.shared.security;

import java.io.IOException;
import java.util.UUID;
import com.fnbx.shared.tenant.TenantContext;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

/** Runs inside Spring Security, after bearer verification. Never trusts identity headers. */
public final class VerifiedTenantFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        TenantContext.clear();
        try {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof Jwt jwt) {
                TenantContext.set(TenantContext.of(UUID.fromString(jwt.getClaimAsString("business_id")),
                        UUID.fromString(jwt.getClaimAsString("uid"))));
            }
            chain.doFilter(request, response);
        } finally { TenantContext.clear(); }
    }
}
