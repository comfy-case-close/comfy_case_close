package com.comfy.caseclose.security;

import com.comfy.caseclose.repository.UserRepository;
import com.comfy.caseclose.repository.UserPositionRepository;
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
    private final UserPositionRepository userPositionRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String employeeCode) throws UsernameNotFoundException {
        return userRepository.findByEmployeeCodeIgnoreCase(employeeCode)
                .map(user -> CustomUserDetails.from(
                        user, userPositionRepository.findPositionDisplayTitlesByUserId(user.getId())))
                .orElseThrow(() -> new UsernameNotFoundException("No user for employee code " + employeeCode));
    }
}
