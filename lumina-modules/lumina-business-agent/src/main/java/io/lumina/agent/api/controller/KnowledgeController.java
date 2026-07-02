package io.lumina.agent.api.controller;

import io.lumina.agent.infrastructure.entity.KnowledgeDocumentDO;
import io.lumina.agent.service.KnowledgeService;
import io.lumina.common.core.PageResult;
import io.lumina.common.core.R;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/knowledge")
@Validated
public class KnowledgeController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_SEARCH_LIMIT = 20;

    @Autowired
    private KnowledgeService knowledgeService;

    @PostMapping("/documents")
    public R<String> upload(@RequestParam("file") MultipartFile file,
                            @RequestParam(value = "agentId", required = false) Long agentId) {
        if (file.isEmpty()) {
            return R.fail("文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            String lowerName = originalFilename.toLowerCase();
            if (!lowerName.endsWith(".pdf") && !lowerName.endsWith(".docx")
                    && !lowerName.endsWith(".txt") && !lowerName.endsWith(".md")) {
                return R.fail("仅支持 PDF、DOCX、TXT、MD 格式文件");
            }
        }
        String uuid = knowledgeService.uploadDocument(file, agentId);
        return R.success(uuid);
    }

    @GetMapping("/documents")
    public R<PageResult<KnowledgeDocumentDO>> list(
            @RequestParam(value = "agentId", required = false) Long agentId,
            @RequestParam(value = "pageNum", defaultValue = "1") @Min(1) Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") @Min(1) Integer pageSize) {
        if (pageSize > MAX_PAGE_SIZE) {
            pageSize = MAX_PAGE_SIZE;
        }
        return R.success(knowledgeService.listDocuments(agentId, pageNum, pageSize));
    }

    @DeleteMapping("/documents/{uuid}")
    public R<Void> delete(@PathVariable("uuid") String uuid) {
        knowledgeService.deleteDocument(uuid);
        return R.success();
    }

    @PostMapping("/search")
    public R<List<Map<String, Object>>> search(
            @RequestParam("query") @Size(min = 1, max = 500, message = "搜索内容长度须在 1-500 之间") String query,
            @RequestParam(value = "limit", defaultValue = "5") @Min(1) Integer limit) {
        if (limit > MAX_SEARCH_LIMIT) {
            limit = MAX_SEARCH_LIMIT;
        }
        return R.success(knowledgeService.search(query, limit));
    }
}
