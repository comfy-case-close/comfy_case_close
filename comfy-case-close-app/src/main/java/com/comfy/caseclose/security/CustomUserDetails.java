package com.comfy.caseclose.security;

import com.comfy.caseclose.entity.User;
import com.comfy.caseclose.utils.enums.UserRole;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class CustomUserDetails implements UserDetails {

    private final Long id;
    private final String employeeCode;
    private final String passcodeHash;
    private final UserRole role;
    private final boolean active;

    private CustomUserDetails(Long id, String employeeCode, String passcodeHash, UserRole role, boolean active) {
        this.id = id;
        this.employeeCode = employeeCode;
        this.passcodeHash = passcodeHash;
        this.role = role;
        this.active = active;
    }

    public static CustomUserDetails from(User user) {
        return new CustomUserDetails(
                user.getId(),
                user.getEmployeeCode(),
                user.getPasscodeHash(),
                user.getRole(),
                Boolean.TRUE.equals(user.getIsActive()));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passcodeHash;
    }

    @Override
    public String getUsername() {
        return employeeCode;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
