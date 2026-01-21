package com.acheao.languageagent.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "tenant")
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_code", nullable = false, unique = true, length = 64)
    private String tenantCode;

    @Column(name = "tenant_name", nullable = false, length = 128)
    private String tenantName;

    @Column(name = "plan_type", length = 32)
    private String planType;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime  updatedAt;

    @PrePersist
    public void prePersist() {
        OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
        this.createdAt = nowUtc;
        this.updatedAt = nowUtc;
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

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public String getPlanType() {
        return planType;
    }

    public void setPlanType(String planType) {
        this.planType = planType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
}
