package com.acheao.languageagent.service;

import com.acheao.languageagent.dto.AiEmployeeCreateRequest;
import com.acheao.languageagent.dto.AiEmployeeQueryRequest;
import com.acheao.languageagent.dto.AiEmployeeUpdateRequest;
import com.acheao.languageagent.entity.AiEmployee;
import org.springframework.data.domain.Page;

import java.util.List;

public interface AiEmployeeService {

    AiEmployee create(AiEmployeeCreateRequest employee);

    AiEmployee update(Long id, AiEmployeeUpdateRequest employee);

    void delete(Long id);

    AiEmployee getById(Long id);

    List<AiEmployee> listByTenant(Long tenantId);

    void enable(Long id);

    void disable(Long id);

    Page<AiEmployee> query(AiEmployeeQueryRequest req);
}
