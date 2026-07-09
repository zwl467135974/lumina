package io.lumina.agent.api.controller;

import io.lumina.agent.api.dto.llm.CreateLlmProviderDTO;
import io.lumina.agent.api.dto.llm.QueryLlmProviderDTO;
import io.lumina.agent.api.vo.LlmProviderVO;
import io.lumina.agent.service.LlmProviderService;
import io.lumina.common.core.R;
import io.lumina.framework.audit.annotation.Audit;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agent/llm-providers")
@RequiredArgsConstructor
public class LlmProviderController {

    private final LlmProviderService llmProviderService;

    @GetMapping
    public R<List<LlmProviderVO>> list(QueryLlmProviderDTO query) {
        return R.success(llmProviderService.list(query));
    }

    @GetMapping("/{id}")
    public R<LlmProviderVO> getById(@PathVariable Long id) {
        return R.success(llmProviderService.getById(id));
    }

    @PostMapping
    @Audit(module = "LLM_PROVIDER", action = "CREATE")
    public R<LlmProviderVO> create(@Valid @RequestBody CreateLlmProviderDTO dto) {
        return R.success(llmProviderService.create(dto));
    }

    @PutMapping("/{id}")
    @Audit(module = "LLM_PROVIDER", action = "UPDATE")
    public R<LlmProviderVO> update(@PathVariable Long id, @Valid @RequestBody CreateLlmProviderDTO dto) {
        return R.success(llmProviderService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @Audit(module = "LLM_PROVIDER", action = "DELETE")
    public R<Void> delete(@PathVariable Long id) {
        llmProviderService.delete(id);
        return R.success();
    }

    @PostMapping("/{id}/test")
    @Audit(module = "LLM_PROVIDER", action = "TEST")
    public R<Boolean> testConnection(@PathVariable Long id) {
        return R.success(llmProviderService.testConnection(id));
    }
}
