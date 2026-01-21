package com.acheao.languageagent.controller;

import com.acheao.languageagent.dto.AiEmployeeCreateRequest;
import com.acheao.languageagent.dto.AiEmployeeQueryRequest;
import com.acheao.languageagent.dto.AiEmployeeUpdateRequest;
import com.acheao.languageagent.entity.AiEmployee;
import com.acheao.languageagent.service.AiEmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "AI 员工管理", description = "AI 员工增删改查接口")
@RestController
@RequestMapping("/api/ai-employee")
public class AiEmployeeController {

    private final AiEmployeeService service;

    public AiEmployeeController(AiEmployeeService service) {
        this.service = service;
    }

    /**
     * 新增 AI 员工
     */
    @Operation(summary = "新增 AI 员工")
    @PostMapping("/create")
    public AiEmployee create(@RequestBody AiEmployeeCreateRequest employee) {
        return service.create(employee);
    }

    /**
     * 修改 AI 员工（原 PUT）
     */
    @Operation(summary = "修改 AI 员工")
    @PostMapping("/update")
    public AiEmployee update(@RequestBody AiEmployeeUpdateRequest employee) {
        // employee.id 必须有值
        return service.update(employee.id(), employee);
    }

    /**
     * 删除 AI 员工（软删除，原 DELETE）
     */
    @Operation(summary = "删除 AI 员工（软删除）")
    @PostMapping("/delete")
    public void delete(@RequestParam Long id) {
        service.delete(id);
    }

    /**
     * 根据 ID 查询
     */
    @Operation(summary = "根据 ID 查询 AI 员工")
    @GetMapping("/{id}")
    public AiEmployee get(@PathVariable Long id) {
        return service.getById(id);
    }

    /**
     * 查询租户下 AI 员工列表
     */
    @Operation(summary = "查询租户下 AI 员工列表")
    @GetMapping("/tenant/{tenantId}")
    public List<AiEmployee> list(@PathVariable Long tenantId) {
        return service.listByTenant(tenantId);
    }

    @Operation(summary = "分页查询 AI 员工（支持过滤）")
    @PostMapping("/query")
    public Page<AiEmployee> query(@RequestBody AiEmployeeQueryRequest request) {
        return service.query(request);
    }


    /**
     * 启用 AI 员工
     */
    @Operation(summary = "启用 AI 员工")
    @PostMapping("/enable")
    public void enable(@RequestParam Long id) {
        service.enable(id);
    }

    /**
     * 禁用 AI 员工
     */
    @Operation(summary = "禁用 AI 员工")
    @PostMapping("/disable")
    public void disable(@RequestParam Long id) {
        service.disable(id);
    }
}
