package com.acheao.languageagent.entity;

import com.acheao.languageagent.entity.pk.AiEmployeeSkillRelId;
import jakarta.persistence.*;

@Entity
@Table(name = "ai_employee_skill_rel")
@IdClass(AiEmployeeSkillRelId.class)
public class AiEmployeeSkillRel {

    @Id
    @Column(name = "employee_id")
    private Long employeeId;

    @Id
    @Column(name = "skill_id")
    private Long skillId;

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public Long getSkillId() {
        return skillId;
    }

    public void setSkillId(Long skillId) {
        this.skillId = skillId;
    }
}
