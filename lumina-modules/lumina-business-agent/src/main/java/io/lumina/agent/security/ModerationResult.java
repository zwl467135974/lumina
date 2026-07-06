package io.lumina.agent.security;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 内容审核结果
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Data
public class ModerationResult {

    private boolean allowed;
    private String reason;
    private List<String> flaggedCategories = new ArrayList<>();
    private double score;

    public static ModerationResult allowed() {
        ModerationResult result = new ModerationResult();
        result.setAllowed(true);
        result.setScore(0.0);
        return result;
    }

    public static ModerationResult blocked(String reason, String category, double score) {
        ModerationResult result = new ModerationResult();
        result.setAllowed(false);
        result.setReason(reason);
        result.getFlaggedCategories().add(category);
        result.setScore(score);
        return result;
    }
}
