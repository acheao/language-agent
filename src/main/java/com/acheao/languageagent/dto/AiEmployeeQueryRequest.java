package com.acheao.languageagent.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 员工分页查询条件")
public record AiEmployeeQueryRequest(

        @Schema(description = "租户ID", required = true)
        Long tenantId,

        @Schema(description = "员工名称（模糊）")
        String name,

        @Schema(description = "员工编码（模糊）")
        String employeeCode,

        @Schema(description = "角色ID")
        Long roleId,

        @Schema(description = "是否启用")
        Boolean isActive,

        @Schema(description = "页码（从 1 开始）", defaultValue = "1")
        Integer page,

        @Schema(description = "每页大小", defaultValue = "10")
        Integer size
) {
}
