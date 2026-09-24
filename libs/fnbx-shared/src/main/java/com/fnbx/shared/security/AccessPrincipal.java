package com.fnbx.shared.security;

import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Verified identity only. All authority is resolved from the live database. */
public record AccessPrincipal(UUID businessId, UUID staffId) {
    public static AccessPrincipal from(Jwt jwt) {
        return new AccessPrincipal(UUID.fromString(jwt.getClaimAsString("business_id")),
                UUID.fromString(jwt.getClaimAsString("uid")));
    }
    public static AccessPrincipal current() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)) throw new AccessDeniedException("Authentication required");
        return from(jwt);
    }
}
