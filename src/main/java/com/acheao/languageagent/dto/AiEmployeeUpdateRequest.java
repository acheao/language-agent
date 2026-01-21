package com.acheao.languageagent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;


@Schema(description = "AI员工实体")
public record AiEmployeeUpdateRequest(

        @NotNull(message = "id 必须提供")
        @Schema(description = "AI员工ID")
        Long id,

        @Schema(description = "租户ID")
        Long tenantId,

        @Schema(description = "员工编码")
        String employeeCode,

        @Schema(description = "员工姓名")
        String name,

        @Schema(description = "角色ID")
        Long roleId,

        @Schema(description = "头像URL")
        String avatar,

        @Schema(description = "员工描述")
        String description,

        @Schema(description = "基础提示词")
        String basePrompt,

        @Schema(description = "场景提示词")
        String personalizationPrompt,

        @Schema(description = "是否激活")
        Boolean active,

        @Schema(description = "是否在线")
        Boolean online
) {
}
