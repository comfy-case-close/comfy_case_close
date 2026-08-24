package com.comfy.caseclose.security;

import com.comfy.caseclose.exception.ForbiddenException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static CustomUserDetails currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new ForbiddenException("No authenticated user in the current request");
        }
        return userDetails;
    }

    public static Long currentUserId() {
        return currentUser().getId();
    }
}
