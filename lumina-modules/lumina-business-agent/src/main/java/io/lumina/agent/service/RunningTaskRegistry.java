package io.lumina.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 运行中任务注册表（真取消的传播通道）
 *
 * <p>任务执行线程注册自己的中断动作，取消时由注册表触发——
 * 取消不再只是改状态位，而是真正中断正在执行的 LLM 调用/工具执行，
 * 停止 Token 消耗。借鉴 DeepSeek Harness 的 signal 思想：
 * 取消权必须传播到执行最深处，不能停在状态层。
 *
 * @author Lumina Team
 * @since 3.11.0
 */
@Slf4j
@Component
public class RunningTaskRegistry {

    private final Map<String, Runnable> cancellers = new ConcurrentHashMap<>();

    /**
     * 注册取消动作（在执行线程上调用）
     */
    public void register(String taskUuid, Runnable canceller) {
        cancellers.put(taskUuid, canceller);
    }

    /**
     * 注销（执行结束时调用，无论成败）
     */
    public void unregister(String taskUuid) {
        cancellers.remove(taskUuid);
    }

    /**
     * 取消一个正在执行的任务
     *
     * @return true=已发出中断（任务在执行中）；false=任务不在执行（可能已结束或未开始）
     */
    public boolean cancel(String taskUuid) {
        Runnable canceller = cancellers.remove(taskUuid);
        if (canceller == null) {
            return false;
        }
        canceller.run();
        log.info("已向运行中任务发出取消中断: {}", taskUuid);
        return true;
    }
}
