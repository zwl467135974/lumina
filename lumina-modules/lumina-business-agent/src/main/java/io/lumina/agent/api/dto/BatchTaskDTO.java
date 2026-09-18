package io.lumina.agent.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 大输入分治批次任务 DTO
 *
 * <p>工程代码确定性拆分 → 子任务隔离上下文并发执行 → 合并。
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Data
public class BatchTaskDTO {

    /** 每个分片的处理指令（会拼在分片内容前），如"用一句话总结这段内容" */
    @NotBlank(message = "处理指令不能为空")
    @Size(max = 2000, message = "处理指令最长 2000 字符")
    private String instruction;

    /** 待分治的大文本 */
    @NotBlank(message = "分治内容不能为空")
    private String inputText;

    /** 拆分策略：BY_LINES（按行）/ BY_CHARS（按字符，带 10% 重叠） */
    @Pattern(regexp = "BY_LINES|BY_CHARS", message = "拆分策略必须是 BY_LINES 或 BY_CHARS")
    private String splitStrategy = "BY_LINES";

    /** BY_LINES：每片行数；BY_CHARS：每片字符数 */
    @Min(value = 1, message = "分片大小至少为 1")
    @Max(value = 50000, message = "分片大小最大 50000")
    private Integer bundleSize = 50;

    /** 分片数上限（超限报错，防止误操作打爆任务队列） */
    @Min(1)
    @Max(50)
    private Integer maxBundles = 20;

    /** 合并模式：CONCAT（工程拼接，带分片标头）/ LLM（再调一次模型汇总） */
    @Pattern(regexp = "CONCAT|LLM", message = "合并模式必须是 CONCAT 或 LLM")
    private String mergeMode = "CONCAT";

    @NotNull(message = "agentId 不能为空")
    private Long agentId;
}
