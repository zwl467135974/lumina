package io.lumina.agent.steering;

import java.util.List;

/**
 * 运行中转向消息存储（v3.14 批次 3.2，借鉴 ZCode steering / DSH B2）
 *
 * <p>调用方在 Agent 执行期间向会话投递调整指令，引擎在消费点取走并注入：
 * <ul>
 *   <li><b>工具轮间（搭车注入）</b>：工具结果尾部追加标记段——ReAct 循环内即时生效</li>
 *   <li><b>段边界（正规注入）</b>：流式段完成 / 同步续跑点并入上下文为 USER 消息</li>
 *   <li><b>执行入口</b>：同步执行开始前并入任务描述</li>
 * </ul>
 *
 * <p>消息为<b>一次性消费</b>（drain 取走即清除），同一条指令只注入一次。
 *
 * <p>默认 {@link InMemorySteeringMessageStore} 为单实例实现——多实例部署下转向请求
 * 必须路由到执行实例（网关会话粘滞）；跨实例场景由业务模块提供 Redis 实现覆盖
 * （注册为 @Primary Bean 即可替换）。
 *
 * @author Lumina Team
 * @since 3.14.0
 */
public interface SteeringMessageStore {

    /**
     * 投递转向消息（空消息忽略；超长截断到配置上限）
     *
     * @param conversationId 会话 ID（null 拒绝）
     * @param message        转向指令内容
     */
    void offer(String conversationId, String message);

    /**
     * 取走全部积压消息（一次性消费，最多 max 条）
     *
     * @return 取走的消息列表（无积压返回空列表，永不返回 null）
     */
    List<String> drain(String conversationId, int max);

    /** 取走全部积压消息 */
    default List<String> drain(String conversationId) {
        return drain(conversationId, Integer.MAX_VALUE);
    }
}
