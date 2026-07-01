package io.lumina.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.RetrieveConfig;
import io.agentscope.core.rag.reader.PDFReader;
import io.agentscope.core.rag.reader.ReaderInput;
import io.agentscope.core.rag.reader.SplitStrategy;
import io.agentscope.core.rag.reader.TextReader;
import io.agentscope.core.rag.reader.WordReader;
import io.lumina.agent.infrastructure.entity.KnowledgeDocumentDO;
import io.lumina.agent.infrastructure.mapper.KnowledgeDocumentMapper;
import io.lumina.agent.service.KnowledgeService;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Slf4j
@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    @Autowired(required = false)
    private Knowledge knowledge;

    @Autowired
    private KnowledgeDocumentMapper documentMapper;

    @Value("${lumina.rag.reader.chunk-size:512}")
    private int chunkSize;

    @Value("${lumina.rag.reader.overlap:50}")
    private int overlap;

    @Value("${lumina.rag.retrieve.score-threshold:0.3}")
    private double scoreThreshold;

    @Override
    public String uploadDocument(MultipartFile file, Long agentId) {
        String filename = file.getOriginalFilename();
        String format = getFormat(filename);
        String uuid = UUID.randomUUID().toString().replace("-", "");

        log.info("上传知识文档: filename={}, format={}, size={}", filename, format, file.getSize());

        try {
            Path tempFile = Files.createTempFile("lumina_rag_", "_" + filename);
            file.transferTo(tempFile.toFile());

            List<Document> docs;
            ReaderInput input = ReaderInput.fromPath(tempFile);

            switch (format) {
                case "pdf":
                    docs = new PDFReader(chunkSize, SplitStrategy.PARAGRAPH, overlap).read(input).block();
                    break;
                case "doc":
                case "docx":
                    docs = new WordReader(chunkSize, SplitStrategy.PARAGRAPH, overlap,
                            false, true, io.agentscope.core.rag.reader.TableFormat.MARKDOWN).read(input).block();
                    break;
                default:
                    docs = new TextReader(chunkSize, SplitStrategy.PARAGRAPH, overlap).read(input).block();
            }

            Files.deleteIfExists(tempFile);

            if (docs != null && !docs.isEmpty() && knowledge != null) {
                knowledge.addDocuments(docs).block();
            }

            KnowledgeDocumentDO doc = new KnowledgeDocumentDO();
            doc.setDocumentUuid(uuid);
            doc.setTenantId(BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L);
            doc.setAgentId(agentId);
            doc.setTitle(filename);
            doc.setFormat(format);
            doc.setChunkCount(docs != null ? docs.size() : 0);
            doc.setFileSize(file.getSize());
            doc.setStatus(1);
            documentMapper.insert(doc);

            log.info("文档入库成功: uuid={}, chunks={}", uuid, docs != null ? docs.size() : 0);
            return uuid;

        } catch (Exception e) {
            log.error("文档入库失败: {}", filename, e);
            throw new RuntimeException("文档处理失败: " + e.getMessage(), e);
        }
    }

    @Override
    public PageResult<KnowledgeDocumentDO> listDocuments(Long agentId, Integer pageNum, Integer pageSize) {
        Long tenantId = BaseContext.getTenantId();
        LambdaQueryWrapper<KnowledgeDocumentDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeDocumentDO::getTenantId, tenantId != null ? tenantId : 0L);
        if (agentId != null) {
            wrapper.eq(KnowledgeDocumentDO::getAgentId, agentId);
        }
        wrapper.orderByDesc(KnowledgeDocumentDO::getCreateTime);

        Page<KnowledgeDocumentDO> page = new Page<>(pageNum, pageSize);
        Page<KnowledgeDocumentDO> result = documentMapper.selectPage(page, wrapper);

        PageResult<KnowledgeDocumentDO> pr = new PageResult<>();
        pr.setList(result.getRecords());
        pr.setTotal(result.getTotal());
        pr.setPageNum(pageNum);
        pr.setPageSize(pageSize);
        pr.setPages((int) result.getPages());
        return pr;
    }

    @Override
    public void deleteDocument(String uuid) {
        LambdaQueryWrapper<KnowledgeDocumentDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeDocumentDO::getDocumentUuid, uuid);
        KnowledgeDocumentDO doc = documentMapper.selectOne(wrapper);
        if (doc == null) return;
        documentMapper.deleteById(doc.getDocumentId());
        log.info("文档已删除: uuid={}", uuid);
    }

    @Override
    public List<Map<String, Object>> search(String query, int limit) {
        if (knowledge == null) {
            return Collections.emptyList();
        }
        double threshold = scoreThreshold;
        List<Document> results = knowledge.retrieve(query,
                RetrieveConfig.builder().limit(limit).scoreThreshold(threshold).build()).block();

        if (results == null) return Collections.emptyList();

        List<Map<String, Object>> list = new ArrayList<>();
        for (Document doc : results) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("content", doc.getPayloadValue("content"));
            item.put("score", doc.getScore());
            item.put("metadata", doc.getMetadata());
            list.add(item);
        }
        return list;
    }

    private String getFormat(String filename) {
        if (filename == null) return "txt";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) return "pdf";
        if (lower.endsWith(".doc")) return "doc";
        if (lower.endsWith(".docx")) return "docx";
        if (lower.endsWith(".md")) return "md";
        return "txt";
    }
}
