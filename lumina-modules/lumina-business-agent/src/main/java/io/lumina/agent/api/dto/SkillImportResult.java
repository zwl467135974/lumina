package io.lumina.agent.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * SKILL.md 导入结果
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Data
public class SkillImportResult {

    /** 导入成功（scanStatus=PASSED 即启用；FLAGGED 为禁用待复核） */
    private List<ImportedSkill> imported = new ArrayList<>();

    /** 拒收项（解析失败/名称冲突/体检 REJECTED） */
    private List<RejectedSkill> rejected = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportedSkill {
        private Long id;
        private String name;
        /** PASSED / FLAGGED */
        private String scanStatus;
        private Boolean enabled;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RejectedSkill {
        /** 技能名或文件名 */
        private String name;
        private String reason;
    }

    public void addImported(ImportedSkill skill) {
        imported.add(skill);
    }

    public void addRejected(String name, String reason) {
        rejected.add(new RejectedSkill(name, reason));
    }
}
