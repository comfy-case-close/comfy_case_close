package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.PayComponent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayComponentRepository extends JpaRepository<PayComponent, UUID> {

    List<PayComponent> findAllByOrderByDisplayOrder();

    Optional<PayComponent> findByComponentCode(String componentCode);
}
