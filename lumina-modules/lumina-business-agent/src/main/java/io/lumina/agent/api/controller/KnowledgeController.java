package io.lumina.agent.api.controller;

import io.lumina.agent.infrastructure.entity.KnowledgeDocumentDO;
import io.lumina.agent.service.KnowledgeService;
import io.lumina.common.core.PageResult;
import io.lumina.common.core.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {

    @Autowired
    private KnowledgeService knowledgeService;

    @PostMapping("/documents")
    public R<String> upload(@RequestParam("file") MultipartFile file,
                            @RequestParam(value = "agentId", required = false) Long agentId) {
        if (file.isEmpty()) {
            return R.fail("文件不能为空");
        }
        String uuid = knowledgeService.uploadDocument(file, agentId);
        return R.success(uuid);
    }

    @GetMapping("/documents")
    public R<PageResult<KnowledgeDocumentDO>> list(
            @RequestParam(value = "agentId", required = false) Long agentId,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        return R.success(knowledgeService.listDocuments(agentId, pageNum, pageSize));
    }

    @DeleteMapping("/documents/{uuid}")
    public R<Void> delete(@PathVariable String uuid) {
        knowledgeService.deleteDocument(uuid);
        return R.success();
    }

    @PostMapping("/search")
    public R<List<Map<String, Object>>> search(@RequestParam("query") String query,
                                                @RequestParam(value = "limit", defaultValue = "5") int limit) {
        return R.success(knowledgeService.search(query, limit));
    }
}
