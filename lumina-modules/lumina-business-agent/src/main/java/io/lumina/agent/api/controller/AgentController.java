package io.lumina.agent.api.controller;

import io.lumina.agent.api.dto.CreateAgentDTO;
import io.lumina.agent.api.vo.AgentVO;
import io.lumina.agent.domain.model.Agent;
import io.lumina.agent.service.AgentService;
import io.lumina.common.core.PageResult;
import io.lumina.common.core.R;
import io.lumina.common.exception.BusinessException;
import io.lumina.common.util.CollectionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * Agent Controller
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agents")
@Validated
public class AgentController {

    @Autowired
    private AgentService agentService;

    /**
     * 创建 Agent
     */
    @PostMapping
    public R<AgentVO> createAgent(@Valid @RequestBody CreateAgentDTO dto) {
        log.info("创建 Agent: {}", dto.getAgentName());

        // DTO 转领域模型
        Agent agent = new Agent();
        BeanUtils.copyProperties(dto, agent);

        // 调用服务
        Agent createdAgent = agentService.createAgent(agent);

        // 领域模型转 VO
        AgentVO vo = new AgentVO();
        BeanUtils.copyProperties(createdAgent, vo);

        return R.success(vo);
    }

    /**
     * 更新 Agent
     */
    @PutMapping("/{id}")
    public R<AgentVO> updateAgent(
            @PathVariable("id") Long id,
            @Valid @RequestBody CreateAgentDTO dto) {
        log.info("更新 Agent: id={}", id);

        // DTO 转领域模型
        Agent agent = new Agent();
        BeanUtils.copyProperties(dto, agent);

        // 调用服务
        Agent updatedAgent = agentService.updateAgent(id, agent);

        // 领域模型转 VO
        AgentVO vo = new AgentVO();
        BeanUtils.copyProperties(updatedAgent, vo);

        return R.success(vo);
    }

    /**
     * 删除 Agent
     */
    @DeleteMapping("/{id}")
    public R<Void> deleteAgent(@PathVariable("id") Long id) {
        log.info("删除 Agent: id={}", id);
        agentService.deleteAgent(id);
        return R.success();
    }

    /**
     * 获取 Agent 详情
     */
    @GetMapping("/{id}")
    public R<AgentVO> getAgent(@PathVariable("id") Long id) {
        log.info("查询 Agent: id={}", id);

        Agent agent = agentService.getAgentById(id);

        // 领域模型转 VO
        AgentVO vo = new AgentVO();
        BeanUtils.copyProperties(agent, vo);

        return R.success(vo);
    }

    /**
     * 分页查询 Agent 列表
     */
    @GetMapping
    public R<PageResult<AgentVO>> pageAgents(
            @RequestParam(required = false) String agentName,
            @RequestParam(required = false) String agentType,
            @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @RequestParam(defaultValue = "10") @Min(1) Integer pageSize) {
        log.info("分页查询 Agent: name={}, type={}, pageNum={}, pageSize={}",
                agentName, agentType, pageNum, pageSize);

        PageResult<Agent> pageResult = agentService.pageAgents(agentName, agentType, pageNum, pageSize);

        // 转换为 VO
        PageResult<AgentVO> voPageResult = new PageResult<>();
        voPageResult.setPageNum(pageResult.getPageNum());
        voPageResult.setPageSize(pageResult.getPageSize());
        voPageResult.setTotal(pageResult.getTotal());
        voPageResult.setPages(pageResult.getPages());

        // 转换列表
        java.util.List<AgentVO> voList = pageResult.getList().stream()
                .map(agent -> {
                    AgentVO vo = new AgentVO();
                    BeanUtils.copyProperties(agent, vo);
                    return vo;
                })
                .collect(java.util.stream.Collectors.toList());

        voPageResult.setList(voList);

        return R.success(voPageResult);
    }

    /**
     * 执行 Agent
     */
    @PostMapping("/{id}/execute")
    public R<String> executeAgent(
            @PathVariable("id") Long id,
            @RequestParam String task) {
        log.info("执行 Agent: id={}, task={}", id, task);

        if (task == null || task.trim().isEmpty()) {
            throw new BusinessException("任务描述不能为空");
        }

        String result = agentService.executeAgent(id, task);

        return R.success(result);
    }
}
