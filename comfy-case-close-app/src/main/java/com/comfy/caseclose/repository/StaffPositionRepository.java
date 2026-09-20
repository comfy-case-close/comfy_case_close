package com.comfy.caseclose.repository;

import com.comfy.caseclose.entity.StaffPositionEntity;
import com.comfy.caseclose.utils.enums.StaffPosition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffPositionRepository extends JpaRepository<StaffPositionEntity, StaffPosition> {
}
