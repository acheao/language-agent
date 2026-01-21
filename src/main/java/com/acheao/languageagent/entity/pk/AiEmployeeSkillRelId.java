package com.acheao.languageagent.entity.pk;

import java.io.Serializable;
import java.util.Objects;

public class AiEmployeeSkillRelId implements Serializable {

    private Long employeeId;
    private Long skillId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AiEmployeeSkillRelId)) return false;
        AiEmployeeSkillRelId that = (AiEmployeeSkillRelId) o;
        return Objects.equals(employeeId, that.employeeId)
                && Objects.equals(skillId, that.skillId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(employeeId, skillId);
    }
}
