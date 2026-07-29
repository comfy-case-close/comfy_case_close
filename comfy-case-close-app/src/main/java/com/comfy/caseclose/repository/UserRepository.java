package com.comfy.caseclose.repository;

import com.comfy.caseclose.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmployeeCodeIgnoreCase(String employeeCode);
}
