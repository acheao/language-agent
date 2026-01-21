package com.acheao.languageagent.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "ai_employee")
public class AiEmployee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "employee_code", nullable = false, unique = true, length = 64)
    private String employeeCode;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "role_id")
    private Long roleId;

    @Column(name = "avatar", length = 255)
    private String avatar;

    @Column(name = "description")
    private String description;

    @Column(name = "base_prompt")
    private String basePrompt;

    @Column(name = "personalization_prompt")
    private String personalizationPrompt;

    @Column(name = "is_active")
    private Boolean active;

    @Column(name = "is_online")
    private Boolean online;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
        this.createdAt = nowUtc;
        this.updatedAt = this.createdAt;
        this.active = Boolean.TRUE;
        this.online = Boolean.FALSE;
    }

    @PreUpdate
    public void preUpdate() {
        OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
        this.updatedAt = nowUtc;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public void setEmployeeCode(String employeeCode) {
        this.employeeCode = employeeCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Boolean getOnline() {
        return online;
    }

    public void setOnline(Boolean online) {
        this.online = online;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getBasePrompt() {
        return basePrompt;
    }

    public void setBasePrompt(String basePrompt) {
        this.basePrompt = basePrompt;
    }

    public String getPersonalizationPrompt() {
        return personalizationPrompt;
    }

    public void setPersonalizationPrompt(String personalizationPrompt) {
        this.personalizationPrompt = personalizationPrompt;
    }


}
