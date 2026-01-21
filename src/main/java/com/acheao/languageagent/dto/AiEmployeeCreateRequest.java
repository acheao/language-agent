package com.acheao.languageagent.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;

@Schema(description = "AI员工实体")
public record AiEmployeeCreateRequest(

        @Schema(description = "租户ID")
        Long tenantId,

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
        String personalizationPrompt

) { }
