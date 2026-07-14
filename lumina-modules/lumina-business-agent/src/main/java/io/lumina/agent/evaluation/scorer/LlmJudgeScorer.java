package io.lumina.agent.evaluation.scorer;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.Model;
import io.lumina.agent.config.LuminaAgentProperties;
import io.lumina.agent.evaluation.model.ScoringMethod;
import io.lumina.agent.evaluation.model.TestCase;
import io.lumina.agent.model.AgentConfig;
import io.lumina.agent.model.ChatModelFactory;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM-as-Judge 评分器
 *
 * <p>调用 LLM 对 Agent 输出进行 1-5 分制评分，归一化到 0-1 区间。
 * 当 LLM 不可用或评分解析失败时，回退到关键词包含评分。
 *
 * <p>Judge prompt 要求 LLM 返回格式：
 * <pre>
 * 分数：X
 * 理由：...
 * </pre>
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmJudgeScorer implements EvaluationScorer {

    private static final String JUDGE_SYSTEM_PROMPT = """
            你是一个专业的回答质量评估专家。请根据用户问题、参考答案和实际回答，
            对实际回答的质量进行 1-5 分评分。

            评分标准：
            - 5 分：完全正确，信息完整，表述清晰
            - 4 分：基本正确，有少量遗漏或表述不够精准
            - 3 分：部分正确，缺少关键信息或有轻微错误
            - 2 分：大部分不正确或严重偏离主题
            - 1 分：完全错误或无关

            请严格按照以下格式回复（不要包含其他内容）：
            分数：X
            理由：简短说明

            其中 X 为 1 到 5 的整数。""";

    private static final Pattern SCORE_PATTERN = Pattern.compile("分数[：:]\\s*([1-5])");

    private final ContainsScorer containsScorer;

    private final ChatModelFactory chatModelFactory;

    private final LuminaAgentProperties agentProperties;

    @Override
    public ScoringMethod getMethod() {
        return ScoringMethod.LLM_JUDGE;
    }

    @Override
    public ScoreResult score(TestCase testCase, String actual) {
        if (chatModelFactory == null || agentProperties == null) {
            return fallbackToContains(testCase, actual, "ChatModelFactory 未注入");
        }

        try {
            return scoreWithLlm(testCase, actual);
        } catch (Exception e) {
            log.warn("LLM Judge 评分失败，回退到包含评分: {}", e.getMessage());
            return fallbackToContains(testCase, actual, e.getMessage());
        }
    }

    private ScoreResult scoreWithLlm(TestCase testCase, String actual) {
        String apiKey = getApiKey();

        AgentConfig.LLMConfig judgeConfig = new AgentConfig.LLMConfig();
        judgeConfig.setModelType(agentProperties.getLlm().getType());
        judgeConfig.setModelName(agentProperties.getLlm().getModel());
        judgeConfig.setApiKey(apiKey);
        judgeConfig.setBaseUrl(agentProperties.getLlm().getBaseUrl());
        judgeConfig.setTemperature(0.0);

        Model judgeModel = chatModelFactory.create(judgeConfig, agentProperties.getLlm(), apiKey);

        String userPrompt = buildJudgePrompt(testCase, actual);

        Msg systemMsg = Msg.builder()
                .role(MsgRole.SYSTEM)
                .textContent(JUDGE_SYSTEM_PROMPT)
                .build();

        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .textContent(userPrompt)
                .build();

        ChatResponse response = judgeModel.stream(List.of(systemMsg, userMsg), Collections.emptyList(), null)
                .blockLast();

        if (response == null || response.getContent() == null || response.getContent().isEmpty()) {
            throw new BusinessException(ErrorCode.AGENT_EXECUTE_FAILED, "Judge 模型返回空响应");
        }

        String judgeText = response.getContent().stream()
                .filter(block -> block instanceof TextBlock)
                .map(block -> ((TextBlock) block).getText())
                .reduce("", String::concat);

        if (judgeText.isBlank()) {
            throw new BusinessException(ErrorCode.AGENT_EXECUTE_FAILED, "Judge 模型返回内容为空");
        }

        return parseJudgeResponse(judgeText);
    }

    private String buildJudgePrompt(TestCase testCase, String actual) {
        return String.format("""
                【用户问题】
                %s

                【参考答案】
                %s

                【实际回答】
                %s

                请评分。""", testCase.getInput(), testCase.getExpected(), actual);
    }

    private ScoreResult parseJudgeResponse(String response) {
        Matcher matcher = SCORE_PATTERN.matcher(response);
        if (!matcher.find()) {
            throw new BusinessException(ErrorCode.AGENT_EXECUTE_FAILED, "无法从 Judge 响应中解析分数: " +
                    (response.length() > 100 ? response.substring(0, 100) : response));
        }

        int rawScore = Integer.parseInt(matcher.group(1));
        double normalizedScore = (rawScore - 1.0) / 4.0;

        String reason = response.replaceAll("分数[：:]\\s*[1-5]\\s*", "").trim();
        if (reason.startsWith("理由[：:]")) {
            reason = reason.substring(3);
        }

        return new ScoreResult(normalizedScore,
                String.format("LLM Judge: %d/5 — %s", rawScore,
                        reason.length() > 80 ? reason.substring(0, 80) + "..." : reason));
    }

    private ScoreResult fallbackToContains(TestCase testCase, String actual, String reason) {
        ScoreResult result = containsScorer.score(testCase, actual);
        result.setDetail(result.getDetail() + "（LLM 不可用，回退包含评分: " + reason + "）");
        return result;
    }

    private String getApiKey() {
        String apiKey = agentProperties.getLlm().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("LUMINA_LLM_API_KEY");
            if (apiKey == null) {
                apiKey = System.getenv("DASHSCOPE_API_KEY");
            }
        }
        return apiKey;
    }
}
