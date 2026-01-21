package com.acheao.languageagent.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "ai_employee_skill")
public class AiEmployeeSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_code", nullable = false, unique = true, length = 64)
    private String skillCode;

    @Column(name = "skill_name", nullable = false, length = 64)
    private String skillName;

    @Column(name = "skill_type", length = 32)
    private String skillType;

    @Column(name = "description")
    private String description;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSkillCode() {
        return skillCode;
    }

    public void setSkillCode(String skillCode) {
        this.skillCode = skillCode;
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public String getSkillType() {
        return skillType;
    }

    public void setSkillType(String skillType) {
        this.skillType = skillType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
