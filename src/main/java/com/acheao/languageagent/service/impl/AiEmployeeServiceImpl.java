package com.acheao.languageagent.service.impl;

import com.acheao.languageagent.dto.AiEmployeeCreateRequest;
import com.acheao.languageagent.dto.AiEmployeeQueryRequest;
import com.acheao.languageagent.dto.AiEmployeeUpdateRequest;
import com.acheao.languageagent.entity.AiEmployee;
import com.acheao.languageagent.repository.AiEmployeeRepository;
import com.acheao.languageagent.service.AiEmployeeService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AiEmployeeServiceImpl implements AiEmployeeService {

    private final AiEmployeeRepository repository;

    public AiEmployeeServiceImpl(AiEmployeeRepository repository) {
        this.repository = repository;
    }

    /**
     * 新增 AI 员工
     */
    @Override
    @Transactional
    public AiEmployee create(AiEmployeeCreateRequest employee) {
        AiEmployee emp = new AiEmployee();
        BeanUtils.copyProperties(employee, emp);
        emp.setEmployeeCode(UUID.randomUUID().toString());
        if (repository.existsByEmployeeCode(emp.getEmployeeCode())) {
            throw new IllegalArgumentException("employeeCode 已存在");
        }
        return repository.save(emp);
    }

    /**
     * 更新 AI 员工
     */
    @Override
    @Transactional
    public AiEmployee update(Long id, AiEmployeeUpdateRequest req) {
        AiEmployee db = getById(id);
        AiEmployee emp = new AiEmployee();
        emp.setName(req.name());
        emp.setAvatar(req.avatar());
        emp.setDescription(req.description());
        emp.setRoleId(req.roleId());
        emp.setOnline(req.online());
        return repository.save(db);
    }

    /**
     * 软删除（推荐）
     */
    @Override
    @Transactional
    public void delete(Long id) {
        AiEmployee db = getById(id);
        db.setActive(false);
        repository.save(db);
    }

    /**
     * 根据 ID 查询
     */
    @Override
    public AiEmployee getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AI 员工不存在"));
    }

    /**
     * 按租户查询
     */
    @Override
    public List<AiEmployee> listByTenant(Long tenantId) {
        return repository.findByTenantIdAndActiveTrue(tenantId);
    }

    @Override
    @Transactional
    public void enable(Long id) {
        AiEmployee db = getById(id);
        db.setActive(true);
        repository.save(db);
    }

    @Override
    @Transactional
    public void disable(Long id) {
        AiEmployee db = getById(id);
        db.setActive(false);
        repository.save(db);
    }

    @Override
    public Page<AiEmployee> query(AiEmployeeQueryRequest req) {

        int page = req.page() == null || req.page() < 1 ? 0 : req.page() - 1;
        int size = req.size() == null ? 10 : req.size();

        Pageable pageable = PageRequest.of(page, size);

        Specification<AiEmployee> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 必须条件：tenantId
            predicates.add(cb.equal(root.get("tenantId"), req.tenantId()));

            if (req.name() != null && !req.name().isBlank()) {
                predicates.add(cb.like(root.get("name"), "%" + req.name() + "%"));
            }

            if (req.employeeCode() != null && !req.employeeCode().isBlank()) {
                predicates.add(cb.like(root.get("employeeCode"), "%" + req.employeeCode() + "%"));
            }

            if (req.roleId() != null) {
                predicates.add(cb.equal(root.get("roleId"), req.roleId()));
            }

            if (req.isActive() != null) {
                predicates.add(cb.equal(root.get("active"), req.isActive()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return repository.findAll(spec, pageable);
    }
}
