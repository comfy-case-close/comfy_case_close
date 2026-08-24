package com.comfy.caseclose.repository;

import com.comfy.caseclose.entity.ShiftType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShiftTypeRepository extends JpaRepository<ShiftType, Long> {
    Optional<ShiftType> findByShiftTypeCode(String shiftTypeCode);

    boolean existsByShiftTypeCode(String shiftTypeCode);

    List<ShiftType> findByIsActiveTrueOrderBySortOrderAsc();
}
