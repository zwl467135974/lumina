package io.lumina.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Lumina Agent 配置属性
 *
 * <p>从配置文件读取 Agent 相关配置。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "lumina.agent")
public class LuminaAgentProperties {

    /**
     * LLM 配置
     */
    private LLMConfig llm = new LLMConfig();

    /**
     * ReAct 循环最大迭代次数（每次迭代 = 一次推理 + 一次工具调用）
     *
     * <p>防止 Agent 陷入死循环无限烧 Token。单个 Agent 可通过 AgentConfig.maxIterations 覆盖。
     *
     * @since 3.8.0
     */
    private Integer maxIterations = 10;

    /**
     * 动态模型路由配置
     *
     * @since 3.8.0
     */
    private ModelRoutingConfig modelRouting = new ModelRoutingConfig();

    /**
     * 输出护栏配置
     *
     * @since 3.8.0
     */
    private GuardrailConfig guardrail = new GuardrailConfig();

    /**
     * 动态模型路由配置
     *
     * <p>启用后，每次请求先用轻量 LLM 判断复杂度，简单问题用便宜模型，复杂问题用强力模型。
     *
     * @since 3.8.0
     */
    @Data
    public static class ModelRoutingConfig {
        /** 是否启用动态模型路由（默认 false） */
        private boolean enabled = false;
        /** 简单问题使用的模型名（如 glm-4-flash） */
        private String simpleModel;
        /** 复杂问题使用的模型名（如 glm-4） */
        private String complexModel;
    }

    /**
     * 输出护栏配置
     *
     * <p>启用后，Agent 返回结果前进行安全检查（关键词/长度/重复）。
     *
     * @since 3.8.0
     */
    @Data
    public static class GuardrailConfig {
        /** 是否启用输出护栏（默认 false） */
        private boolean enabled = false;
        /** 输出最大长度（字符数，0=不限制） */
        private int maxOutputLength = 10000;
        /** 敏感关键词列表（命中即拦截） */
        private java.util.List<String> blockedKeywords;
        /** 判定为"循环输出"的连续重复行数阈值（默认 20） */
        private int repetitionConsecutiveLines = 20;
        /** 判定为"大量重复"的唯一行占比阈值（0-1，去重后行数/总行数 低于此值则拦截，默认 0.1） */
        private double repetitionUniqueRatio = 0.1;
    }

    /**
     * LLM 配置
     */
    @Data
    public static class LLMConfig {
        /**
         * API Key（优先从环境变量读取）
         */
        private String apiKey;

        /**
         * 模型名称（默认：qwen-plus）
         */
        private String model = "qwen-plus";

        /**
         * 模型类型（dashscope/openai/anthropic[或claude]/ollama，
         * 或预设: glm/kimi/doubao/minimax/deepseek/yi/qwen，默认：dashscope）
         */
        private String type = "dashscope";

        /**
         * Base URL（仅用于 OpenAI 等需要自定义 URL 的模型）
         */
        private String baseUrl;

        /**
         * Gemini Vertex AI 模式：是否启用（true 时使用 project/location 而非 apiKey）
         *
         * <p>启用后需设置 GOOGLE_APPLICATION_CREDENTIALS 环境变量指向服务账号 JSON 文件。
         *
         * @since 3.3.1
         */
        private Boolean vertexAi = false;

        /**
         * Gemini Vertex AI：GCP 项目 ID
         *
         * @since 3.3.1
         */
        private String projectId;

        /**
         * Gemini Vertex AI：区域（如 us-central1）
         *
         * @since 3.3.1
         */
        private String location;

        /**
         * 自定义 OpenAI 兼容预设（v3.3.0 新增）
         *
         * <p>配置示例：
         * <pre>
         * lumina.agent.llm.presets.acme: https://api.acme.com/v1
         * lumina.agent.llm.presets.myllm: https://my-llm.local/v1
         * </pre>
         * 配置后 type=acme 即可自动使用对应 baseUrl，无需改代码。
         */
        private Map<String, String> presets;

        /**
         * 温度（0-1，默认：0.7）
         */
        private Double temperature = 0.7;

        /**
         * 最大 Token 数（默认：2000）
         */
        private Integer maxTokens = 2000;

        /**
         * 是否启用流式输出（默认：false）
         */
        private Boolean stream = false;

        /**
         * 是否启用思考模式（默认：false）
         */
        private Boolean enableThinking = false;

        /**
         * Top-P 核采样（0-1，null 则不传）
         */
        private Double topP;

        /**
         * 频率惩罚（-2.0 到 2.0，null 则不传）
         */
        private Double frequencyPenalty;

        /**
         * 存在惩罚（-2.0 到 2.0，null 则不传）
         */
        private Double presencePenalty;

        /**
         * 随机种子（可复现输出，null 则不传）
         */
        private Long seed;

        /**
         * Top-K 采样（null 则不传）
         */
        private Integer topK;

        /**
         * 思考 Token 预算（Anthropic/Gemini 扩展思考模式，null 则不传）
         *
         * @since 3.3.1
         */
        private Integer thinkingBudget;

        /**
         * 推理强度（OpenAI o-series: low/medium/high，null 则不传）
         *
         * @since 3.3.1
         */
        private String reasoningEffort;
    }

    /**
     * 记忆配置
     */
    private MemoryConfig memory = new MemoryConfig();

    /**
     * 代码解释器配置
     */
    private CodeInterpreterConfig codeInterpreter = new CodeInterpreterConfig();

    /**
     * 技能系统配置（渐进披露：目录进上下文，全文按需加载）
     *
     * @since 3.11.0
     */
    private SkillConfig skill = new SkillConfig();

    /**
     * 决策型生命周期 Hook 配置（企业合规门介入轮次决策点）
     *
     * @since 3.14.0
     */
    private HooksConfig hooks = new HooksConfig();

    /**
     * 运行中转向配置（v3.14 批次 3.2）
     *
     * <p>调用方向执行中的会话投递调整指令（steering），引擎在工具轮间
     * （结果搭车）与段边界（USER 消息注入）消费。
     *
     * @since 3.14.0
     */
    private SteeringConfig steering = new SteeringConfig();

    /**
     * 工具配置
     */
    private ToolConfig tool = new ToolConfig();

    /**
     * 记忆配置（含 Reflective Memory）
     *
     * @since 3.3.1
     */
    @Data
    public static class MemoryConfig {
        /**
         * 是否启用反思记忆（对话后异步提取关键事实）
         */
        private ReflectiveConfig reflective = new ReflectiveConfig();

        /**
         * 记忆整理代理配置（v3.14 批次 4.1：后台对已归档长会话做提炼 + 召回打分）
         *
         * @since 3.14.0
         */
        private MemoryCompactionConfig compaction = new MemoryCompactionConfig();

        /**
         * 上下文压缩配置（长对话滚动摘要）
         *
         * @since 3.8.0
         */
        private CompressionConfig compression = new CompressionConfig();

        /**
         * 输入侧上下文 Token 预算（估算值，默认 16000）
         *
         * <p>预算覆盖 [系统提示词 + 长期记忆 + 历史消息 + 当前输入] 的估算 token 总量，
         * 历史消息按预算从最新向最旧装填，装不下即停——替代固定条数窗口，
         * 避免长消息（大文档全文等）打爆模型上下文窗口。
         * <p>设为 0 关闭 Token 预算，退回固定条数窗口（{@link #historyWindowSize}）。
         * 本地小窗口模型（如 ollama 4K/8K）建议按模型窗口调低。
         *
         * @since 3.11.0
         */
        private int contextWindowTokens = 16000;

        /**
         * 历史消息条数上限（Token 预算模式下的硬上限，防止超长列表，默认 100）
         *
         * @since 3.11.0
         */
        private int maxHistoryMessages = 100;

        /**
         * 历史窗口条数（Token 预算关闭时的固定窗口，默认 20）
         *
         * @since 3.11.0
         */
        private int historyWindowSize = 20;
    }

    /**
     * 记忆整理代理配置（v3.14 批次 4.1）
     *
     * <p>后台作业对已归档长会话做 LLM 提炼 + 召回打分，产出
     * {@code memory_type = compaction} 的长期记忆。<b>产生后台模型调用费用</b>：
     * 默认关闭 + 租户白名单双闸门，每轮会话数硬上限即计量上限。
     *
     * @since 3.14.0
     */
    @Data
    public static class MemoryCompactionConfig {
        /** 是否启用（默认 false） */
        private boolean enabled = false;
        /**
         * 租户白名单：开启后仍只对列入的租户整理（空 = 无租户生效——
         * 双闸门设计，租户级费用授权必须显式列举）
         */
        private java.util.Set<Long> tenantWhitelist = new java.util.HashSet<>();
        /** 扫描间隔（毫秒，默认 1 小时） */
        private long intervalMs = 3_600_000;
        /** 启动初始延迟（毫秒，默认 5 分钟——避开启动高峰） */
        private long initialDelayMs = 300_000;
        /** 每轮最多整理会话数（计量硬上限：每会话一次 LLM 调用） */
        private int maxConversationsPerRun = 10;
        /** 会话消息数下限（低于此不整理） */
        private int minMessages = 20;
        /** 闲置天数（更新时间早于 N 天的会话才整理——活跃会话不打扰） */
        private int idleDays = 7;
        /** 每会话最多提炼记忆条数 */
        private int maxFactsPerConversation = 5;
        /** 送入 LLM 的对话摘录上限（字符） */
        private int maxPromptChars = 12000;
    }

    /**
     * 上下文压缩配置
     *
     * <p>当对话历史超过阈值时，将较早的消息用 LLM 摘要替代，避免直接丢弃导致信息丢失。
     *
     * @since 3.8.0
     */
    @Data
    public static class CompressionConfig {
        /** 是否启用上下文压缩（LLM 检查点摘要，默认 false） */
        private boolean enabled = false;
        /** 触发压缩的消息条数阈值（超过此值时压缩旧消息） */
        private int threshold = 15;
        /** 保留最近 N 条消息不压缩 */
        private int recentKeepCount = 5;
        /** 摘要最大 Token 数 */
        private int summaryMaxTokens = 500;
        /**
         * 是否启用确定性修剪（压缩第一级，免 LLM，默认 true）
         *
         * <p>历史消息超过 pruneThresholdChars 时做 head/tail 修剪 + 省略标记，
         * 在 Token 预算装填和 LLM 摘要之前先行削减。
         *
         * @since 3.11.0
         */
        private boolean pruneEnabled = true;
        /** 单条历史消息触发修剪的长度阈值（字符数） */
        private int pruneThresholdChars = 4000;
        /** 修剪后保留头部字符数 */
        private int pruneHeadChars = 1500;
        /** 修剪后保留尾部字符数 */
        private int pruneTailChars = 500;
        /**
         * 溢出恢复：LLM 报上下文超限后紧急压缩并重试的次数上限（默认 1）
         *
         * @since 3.11.0
         */
        private int maxOverflowRetries = 1;
    }

    /**
     * Reflective Memory 配置
     */
    @Data
    public static class ReflectiveConfig {
        /** 是否启用反思记忆提取（默认 false） */
        private boolean enabled = false;
        /** 每次对话最多提取的事实条数 */
        private int maxFactsPerTurn = 5;
        /** 加载到上下文的最大记忆条数 */
        private int maxContextMemories = 20;
        /** 事实内容最大长度（字符） */
        private int maxFactLength = 500;
    }

    /**
     * 技能系统配置（渐进披露）
     *
     * <p>启用后系统提示注入 {@code <available_skills>} 目录（仅名称+截断描述），
     * 模型按需调用 util.loadSkill 取全文。
     *
     * @since 3.11.0
     */
    @Data
    public static class SkillConfig {
        /** 是否启用技能目录注入（默认 false，需业务模块提供 SkillCatalogProvider） */
        private boolean enabled = false;
        /** 目录最多条目数（超出截断，防目录本身撑爆上下文） */
        private int maxCatalogEntries = 50;
        /** 目录中描述的截断长度（字符） */
        private int descriptionMaxLength = 200;
    }

    /**
     * 决策型生命周期 Hook 配置（v3.14 批次 3.1）
     *
     * <p>企业合规门实现 {@code AgentLifecycleHook} 并注册 Bean，开启本开关即介入
     * 轮次决策点（不改引擎）。零钩子时本就空转，开关是合规介入的显式授权动作。
     *
     * @since 3.14.0
     */
    @Data
    public static class HooksConfig {
        /** 是否启用（默认 false） */
        private boolean enabled = false;
        /** 单钩子调用超时（毫秒）：超时按中立放行处理，绝不崩回合 */
        private long timeoutMs = 5000;
        /** Stop 钩子 continue 防循环上限（每回合），达上限强制结束 */
        private int maxStopContinues = 3;
        /** add-context 输出大小上限（字符），超出截断 */
        private int maxContextChars = 8000;
        /** deny 理由大小上限（字符），超出截断 */
        private int maxReasonChars = 500;
        /** replace-input 替换文本大小上限（字符），超出截断 */
        private int maxReplacementChars = 20000;
    }

    /**
     * 运行中转向配置（v3.14 批次 3.2）
     *
     * @since 3.14.0
     */
    @Data
    public static class SteeringConfig {
        /** 是否启用（默认 false） */
        private boolean enabled = false;
        /** 单条转向消息大小上限（字符），超出截断 */
        private int maxMessageChars = 4000;
        /**
         * 段边界续跑预算（每回合，转向与 Stop 钩子 continue 共享）；
         * steering 启用时该预算同时覆盖 Stop continue，未启用时 Stop 用 hooks.max-stop-continues
         */
        private int maxSegmentContinues = 3;
    }

    /**
     * 代码解释器配置
     */
    @Data
    public static class CodeInterpreterConfig {
        /** 是否启用代码解释器（默认 false） */
        private boolean enabled = false;
        /** 单次执行超时（秒） */
        private int timeoutSeconds = 30;
        /** 标准输出最大长度（字符） */
        private int maxOutputLength = 10000;
        /** Python 解释器路径 */
        private String pythonPath = "python3";
        /** Node.js 解释器路径 */
        private String nodePath = "node";
        /** 临时脚本工作目录 */
        private String workDir = System.getProperty("java.io.tmpdir") + "/lumina-code";

        /**
         * 执行模式：process（本地进程）或 docker（Docker 容器隔离）
         */
        private String mode = "process";

        /**
         * Docker 镜像（Python）
         */
        private String pythonImage = "python:3.11-slim";

        /**
         * Docker 镜像（Node.js）
         */
        private String nodeImage = "node:20-slim";

        /**
         * Docker 内存限制（MB）
         */
        private int memoryLimitMb = 256;

        /**
         * Docker CPU 核心数限制
         */
        private double cpuLimit = 1.0;

        /**
         * 是否禁止网络访问
         */
        private boolean networkDisabled = true;

        /** 运行时依赖安装（pip install / npm install），执行前先安装 */
        private boolean autoInstallDeps = false;

        /** 容器池大小（常驻容器复用，0=不启用容器池每次新建） */
        private int poolSize = 2;

        /** 容器空闲超时（分钟，超过自动销毁） */
        private int poolIdleTimeoutMinutes = 10;

        /**
         * 流式输出（预留字段，默认 false）
         *
         * <p>后续实现方向：在 Agent 执行层面通过 StreamChunk/SSE 推送分段输出，
         * 工具本身仍一次性返回完整结果。
         */
        private boolean streamOutput = false;
    }

    /**
     * 工具配置
     */
    @Data
    public static class ToolConfig {

        /**
         * 单次工具执行超时（毫秒），默认 60 秒
         */
        private Integer executionTimeoutMs = 60000;

        /**
         * 熔断失败阈值（连续失败次数）
         */
        private Integer failureThreshold = 5;

        /**
         * 熔断恢复超时（毫秒）
         */
        private Long resetTimeoutMs = 60000L;

        /**
         * 工具安全管线配置（拦截器 + 审批 + 单调守卫）
         *
         * @since 3.11.0
         */
        private SecurityConfig security = new SecurityConfig();

        /**
         * 工具结果外存化配置（超大结果 spill）
         *
         * @since 3.11.0
         */
        private SpillConfig spill = new SpillConfig();
    }

    /**
     * 工具结果外存化配置
     *
     * <p>启用后超过阈值的工具结果全文存档（ToolArtifactStore），
     * 模型侧只保留 head/tail 预览 + 存档 ID（util.getArtifact 按需取回）。
     *
     * @since 3.11.0
     */
    @Data
    public static class SpillConfig {
        /** 是否启用外存化（默认 false；需业务侧提供 ToolArtifactStore 实现） */
        private boolean enabled = false;
        /** 触发外存化的长度阈值（字符数） */
        private int thresholdChars = 8000;
        /** 预览保留头部字符数 */
        private int headChars = 3000;
        /** 预览保留尾部字符数 */
        private int tailChars = 1000;
        /** 历史消息外存化开关（v3.13 批次 2.3，默认 false）：超阈值的
         * 历史 user/assistant 消息全文存档、记忆只留预览 + artifactId，
         * 模型可经 util.getArtifact 按需水合取回 */
        private boolean historyEnabled = false;
        /** 历史消息外存化阈值（字符数，独立于工具结果阈值） */
        private int historyThresholdChars = 4000;
        /** 历史消息预览保留的头部字符数 */
        private int historyHeadChars = 1500;
        /** 历史消息预览保留的尾部字符数 */
        private int historyTailChars = 500;
    }

    /**
     * 工具安全管线配置
     *
     * <p>启用后每次工具调用经"拦截器链 → 审批 → 单调守卫"三段检查，
     * 拒绝结果对模型可见（可自纠），全程 fail-closed。
     *
     * @since 3.11.0
     */
    @Data
    public static class SecurityConfig {
        /** 是否启用工具安全管线（默认 false，向后兼容） */
        private boolean enabled = false;
        /** 平台禁用的工具名单（命中即 DENY） */
        private java.util.List<String> denyTools = new java.util.ArrayList<>();
        /** 需人工审批的工具名单（命中即 ASK，fail-closed） */
        private java.util.List<String> approvalTools = new java.util.ArrayList<>();
        /** 审批等待超时（秒，超时按拒绝处理） */
        private long approvalTimeoutSeconds = 30;
        /** 审批人用户 ID（空则通知发起调用的用户） */
        private java.util.List<Long> approverUserIds = new java.util.ArrayList<>();
        /**
         * 只读自动放行总开关（默认 false）：启用后"可证明只读"的工具调用
         * 豁免人工审批直接执行（仅豁免 ASK；DENY 与单调守卫不受影响）
         */
        private boolean readonlyAutoApprove = false;
        /**
         * 只读工具名单（精确名或 {@code 前缀*} 通配），命中即视为可证明只读
         */
        private java.util.List<String> readonlyTools = new java.util.ArrayList<>(
                java.util.List.of("util.search", "util.time", "util.math"));
    }
}

