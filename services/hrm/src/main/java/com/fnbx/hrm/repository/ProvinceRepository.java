package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.Province;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProvinceRepository extends JpaRepository<Province, String> {

    List<Province> findAllByOrderByProvinceName();
}
