package com.comfy.caseclose.repository;

import com.comfy.caseclose.entity.Tip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TipRepository extends JpaRepository<Tip, Long> {

    List<Tip> findByCashCloseId(Long cashCloseId);

    List<Tip> findByCashCloseIdIn(List<Long> cashCloseIds);
}
