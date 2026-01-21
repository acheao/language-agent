package com.acheao.languageagent.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "ai_employee_runtime")
public class AiEmployeeRuntime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "runtime_type", length = 32)
    private String runtimeType;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "stopped_at")
    private OffsetDateTime stoppedAt;

    @Column(name = "meta", columnDefinition = "jsonb")
    private String meta;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public String getRuntimeType() {
        return runtimeType;
    }

    public void setRuntimeType(String runtimeType) {
        this.runtimeType = runtimeType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getStoppedAt() {
        return stoppedAt;
    }

    public void setStoppedAt(OffsetDateTime stoppedAt) {
        this.stoppedAt = stoppedAt;
    }

    public String getMeta() {
        return meta;
    }

    public void setMeta(String meta) {
        this.meta = meta;
    }
}
