package com.fnbx.shared.security;

import java.util.*;
import com.fnbx.shared.enums.UserRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Signed branch grants; an ADMIN grant in branch A grants nothing in branch B. */
public record AccessPrincipal(UUID businessId, UUID staffId, Map<UUID, UserRole> branchRoles) {
    public AccessPrincipal { branchRoles = Map.copyOf(branchRoles); }

    public static AccessPrincipal from(Jwt jwt) {
        Map<String, String> claims = jwt.getClaim("branch_roles");
        Map<UUID, UserRole> roles = new HashMap<>();
        claims.forEach((id, role) -> roles.put(UUID.fromString(id), UserRole.valueOf(role)));
        return new AccessPrincipal(UUID.fromString(jwt.getClaimAsString("business_id")),
                UUID.fromString(jwt.getClaimAsString("uid")), roles);
    }

    public static AccessPrincipal current() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)) throw new AccessDeniedException("Authentication required");
        return from(jwt);
    }

    public void requireBranch(UUID branchId, UserRole... allowed) {
        UserRole granted = branchRoles.get(branchId);
        if (granted == null || (allowed.length > 0 && !List.of(allowed).contains(granted))) {
            throw new AccessDeniedException("Branch access denied");
        }
    }

    /**
     * Business-wide authority: one of {@code allowed} held at ANY branch in the token.
     *
     * <p>Every grant in this system is scoped to a branch, but some acts are not
     * branch-scoped - approving a join request, renaming the business, opening a new
     * branch. Those are the acts of ADMIN and HR, and this is the check that
     * expresses them. See docs/security/onboarding.md section 2.1.
     *
     * <p>Deliberately blunt: an HR at one branch may approve for all of them. That is
     * the right trade for a business of a handful of branches, and the honest
     * alternative - a business-level role on the token - is a separate change to the
     * contract that the gateway and every service validate.
     *
     * <p>Never use this for a branch-scoped resource. {@link #requireBranch} is the
     * check for "may this person act on THIS close, at THIS branch"; passing that job
     * to this method would let a MANAGER at branch A approve branch B's cash.
     */
    public void requireAnyBranch(UserRole... allowed) {
        if (!hasAnyBranch(allowed)) throw new AccessDeniedException("Business access denied");
    }

    /** {@link #requireAnyBranch} as a question rather than a demand, for branching logic. */
    public boolean hasAnyBranch(UserRole... allowed) {
        if (allowed.length == 0) return !branchRoles.isEmpty();
        List<UserRole> permitted = List.of(allowed);
        return branchRoles.values().stream().anyMatch(permitted::contains);
    }

    public Set<UUID> branches(UserRole... allowed) {
        Set<UUID> ids = new HashSet<>();
        branchRoles.forEach((id, role) -> { if (allowed.length == 0 || List.of(allowed).contains(role)) ids.add(id); });
        return Set.copyOf(ids);
    }
}
