package io.lumina.agent.orchestration.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 自主编排节点（模型/配置生成 JS 编排脚本，沙箱执行）
 *
 * <p>借鉴 DeepSeek Harness 的"代码即工作流"：脚本在 GraalJS 沙箱内运行，
 * 通过四个桥接函数编排子 Agent——{@code agent(prompt)} 发起子调用、
 * {@code parallel(thunks)} 并行、{@code pipeline(items, ...stages)} 逐段加工、
 * {@code log(msg)} 打日志。脚本以 {@code return <json>} 结束。
 *
 * <p>与 Flowable 声明式 DAG 的关系：autonomy 是第 7 种节点类型，用于
 * 需要 fan-out 并行子任务的场景（如"对 N 个文档各自总结"），不是替代。
 *
 * <p>边界纪律（照抄 dsh）：
 * <ul>
 *   <li>返回值必须是纯 JSON（物化校验，拒函数/宿主对象/危险属性名）</li>
 *   <li>fatal 与单条失败分离：拼错选项/触顶限额/超时必须响亮失败，
 *       parallel/pipeline 的单项失败映射为 null</li>
 *   <li>限额内执行，超时先中断再强制关闭（有界宽限终止）</li>
 * </ul>
 *
 * @author Lumina Team
 * @since 3.11.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AutonomyNode extends WorkflowNode {

    /** JS 编排脚本（以 return 结束） */
    private String script;

    /** agent(prompt) 调用的目标 Agent ID（脚本不能自选 Agent——无提权面） */
    private Long agentId;

    /** 会话 UUID（可选，子调用带多轮上下文） */
    private String conversationUuid;

    /** 子调用总数上限（默认 20，runaway 兜底） */
    private Integer maxTotalAgents = 20;

    /** 子调用并发上限（默认 5） */
    private Integer maxConcurrentAgents = 5;

    /** parallel/pipeline 单次条目上限（默认 200） */
    private Integer maxItemsPerCall = 200;

    /** 脚本执行超时（秒，默认 120） */
    private Integer timeoutSeconds = 120;
}
