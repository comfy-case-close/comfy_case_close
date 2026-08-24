package com.comfy.caseclose.security;

import com.comfy.caseclose.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String employeeCode) throws UsernameNotFoundException {
        return userRepository.findByEmployeeCodeIgnoreCase(employeeCode)
                .map(CustomUserDetails::from)
                .orElseThrow(() -> new UsernameNotFoundException("No user for employee code " + employeeCode));
    }
}
