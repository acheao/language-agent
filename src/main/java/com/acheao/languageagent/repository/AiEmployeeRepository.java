package com.acheao.languageagent.repository;

import com.acheao.languageagent.entity.AiEmployee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface AiEmployeeRepository extends JpaRepository<AiEmployee, Long>, JpaSpecificationExecutor<AiEmployee> {

    Optional<AiEmployee> findByEmployeeCode(String employeeCode);

    List<AiEmployee> findByTenantIdAndActiveTrue(Long tenantId);

    boolean existsByEmployeeCode(String employeeCode);
}
