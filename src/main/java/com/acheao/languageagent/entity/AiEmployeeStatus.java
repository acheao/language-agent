package com.acheao.languageagent.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "ai_employee_status")
public class AiEmployeeStatus {

    @Id
    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "running")
    private Boolean running;

    @Column(name = "monitoring")
    private Boolean monitoring;

    @Column(name = "pending_task")
    private Boolean pendingTask;

    @Column(name = "last_heartbeat")
    private OffsetDateTime lastHeartbeat;

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public Boolean getRunning() {
        return running;
    }

    public void setRunning(Boolean running) {
        this.running = running;
    }

    public Boolean getMonitoring() {
        return monitoring;
    }

    public void setMonitoring(Boolean monitoring) {
        this.monitoring = monitoring;
    }

    public Boolean getPendingTask() {
        return pendingTask;
    }

    public void setPendingTask(Boolean pendingTask) {
        this.pendingTask = pendingTask;
    }

    public OffsetDateTime getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(OffsetDateTime lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }
}
