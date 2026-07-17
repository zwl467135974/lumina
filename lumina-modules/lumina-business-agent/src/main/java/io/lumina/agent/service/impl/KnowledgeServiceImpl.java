package io.lumina.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.RetrieveConfig;
import io.agentscope.core.rag.reader.PDFReader;
import io.agentscope.core.rag.reader.ReaderInput;
import io.agentscope.core.rag.reader.SplitStrategy;
import io.agentscope.core.rag.reader.TextReader;
import io.agentscope.core.rag.reader.WordReader;
import io.agentscope.core.rag.store.VDBStoreBase;
import io.lumina.agent.infrastructure.entity.KnowledgeDocumentDO;
import io.lumina.agent.infrastructure.mapper.KnowledgeDocumentMapper;
import io.lumina.agent.mq.DocumentIngestMessage;
import io.lumina.agent.service.KnowledgeService;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.core.PageResult;
import io.lumina.common.exception.BusinessException;
import io.lumina.framework.config.RocketMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.apache.rocketmq.spring.core.RocketMQTemplate;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Slf4j
@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    @Autowired(required = false)
    private Knowledge knowledge;

    @Autowired(required = false)
    private VDBStoreBase embeddingStore;

    @Autowired
    private KnowledgeDocumentMapper documentMapper;

    @Autowired(required = false)
    private io.lumina.agent.infrastructure.mapper.KnowledgeChunkMapper chunkMapper;

    @Autowired(required = false)
    private RocketMQTemplate rocketMQTemplate;

    @Autowired(required = false)
    private io.lumina.agent.config.RagProperties ragProperties;

    @Autowired(required = false)
    private io.lumina.agent.rag.PdfOcrProcessor pdfOcrProcessor;

    @Value("${lumina.rag.reader.chunk-size:512}")
    private int chunkSize;

    @Value("${lumina.rag.reader.overlap:50}")
    private int overlap;

    @Value("${lumina.rag.retrieve.score-threshold:0.3}")
    private double scoreThreshold;

    @Value("${lumina.rag.storage-path:./data/knowledge}")
    private String storagePath;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public String uploadDocument(MultipartFile file, Long agentId, Long kbId) {
        String filename = file.getOriginalFilename();
        String format = getFormat(filename);
        String uuid = UUID.randomUUID().toString().replace("-", "");

        log.info("上传知识文档: filename={}, format={}, size={}, kbId={}", filename, format, file.getSize(), kbId);

        Long tenantId = BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L;

        if (rocketMQTemplate != null) {
            return uploadAsync(file, agentId, kbId, filename, format, uuid, tenantId);
        } else {
            log.info("RocketMQ 不可用，降级为同步处理");
            return uploadSync(file, agentId, kbId, filename, format, uuid, tenantId);
        }
    }

    /**
     * 异步上传：保存文件 → 创建 PENDING 记录 → 发送 MQ 消息 → 立即返回
     */
    private String uploadAsync(MultipartFile file, Long agentId, Long kbId, String filename,
                               String format, String uuid, Long tenantId) {
        try {
            Path storageDir = Path.of(storagePath);
            Files.createDirectories(storageDir);
            Path destFile = storageDir.resolve(uuid + "_" + filename);
            file.transferTo(destFile.toFile());

            KnowledgeDocumentDO doc = new KnowledgeDocumentDO();
            doc.setDocumentUuid(uuid);
            doc.setTenantId(tenantId);
            doc.setAgentId(agentId);
            doc.setKbId(kbId);
            doc.setTitle(filename);
            doc.setFormat(format);
            doc.setFileSize(file.getSize());
            doc.setLanguage(detectLanguage(filename));
            doc.setEmbeddingModel(getEmbeddingModelName());
            doc.setStatus(0);
            documentMapper.insert(doc);

            DocumentIngestMessage msg = new DocumentIngestMessage(
                    uuid, destFile.toAbsolutePath().toString(), format,
                    agentId, tenantId, chunkSize, overlap);

            rocketMQTemplate.convertAndSend(RocketMQConfig.TOPIC_KNOWLEDGE_INGEST, msg);

            log.info("文档已提交异步处理: uuid={}", uuid);
            return uuid;

        } catch (Exception e) {
            log.error("文档上传失败: {}", filename, e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "文档上传失败: " + e.getMessage(), e);
        }
    }

    /**
     * 同步上传（降级模式，RocketMQ 不可用时使用）
     */
    private String uploadSync(MultipartFile file, Long agentId, Long kbId, String filename,
                              String format, String uuid, Long tenantId) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("lumina_rag_", "_" + filename);
            file.transferTo(tempFile.toFile());

            List<Document> docs;
            ReaderInput input = ReaderInput.fromPath(tempFile);

            switch (format) {
                case "pdf":
                    docs = new PDFReader(chunkSize, getSplitStrategy(), overlap).read(input).block();
                    break;
                case "doc":
                case "docx":
                    docs = new WordReader(chunkSize, getSplitStrategy(), overlap,
                            false, true, io.agentscope.core.rag.reader.TableFormat.MARKDOWN).read(input).block();
                    break;
                default:
                    docs = new TextReader(chunkSize, getSplitStrategy(), overlap).read(input).block();
            }

            // 扫描件检测：PDF 解析后无任何文本内容（扫描件/图片型 PDF 无可提取文本层）
            if ((docs == null || docs.isEmpty()) && "pdf".equalsIgnoreCase(format)) {
                log.warn("PDF 文档无可提取文本（疑似扫描件），尝试 OCR: uuid={}, file={}", uuid, filename);

                // 尝试 OCR 识别（tempFile 此时仍存在）
                if (pdfOcrProcessor != null) {
                    String ocrText = pdfOcrProcessor.processPdf(tempFile);
                    if (!ocrText.isBlank()) {
                        // OCR 成功，将文本写入临时文件走 TextReader 分块
                        java.nio.file.Path ocrTempFile = null;
                        try {
                            ocrTempFile = java.nio.file.Files.createTempFile("lumina_ocr_", ".txt");
                            java.nio.file.Files.writeString(ocrTempFile, ocrText);
                            docs = new TextReader(chunkSize, getSplitStrategy(), overlap)
                                    .read(ReaderInput.fromPath(ocrTempFile)).block();
                            log.info("OCR 识别成功: file={}, textLen={}", filename, ocrText.length());
                        } finally {
                            if (ocrTempFile != null) {
                                try { java.nio.file.Files.deleteIfExists(ocrTempFile); } catch (Exception ignored) {}
                            }
                        }
                    }
                }

                // OCR 后仍为空 → 抛异常
                if (docs == null || docs.isEmpty()) {
                    throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED,
                            "文档 " + filename + " 似乎是扫描件或图片型 PDF，无法提取文本。"
                                    + "请上传可复制文字的电子版 PDF。");
                }
            }

            if (docs != null && !docs.isEmpty() && knowledge != null) {
                knowledge.addDocuments(docs).block();
            }

            List<String> vectorDocIds = new ArrayList<>();
            if (docs != null) {
                for (Document d : docs) {
                    if (d.getId() != null) {
                        vectorDocIds.add(d.getId());
                    }
                }
            }

            KnowledgeDocumentDO doc = new KnowledgeDocumentDO();
            doc.setDocumentUuid(uuid);
            doc.setTenantId(tenantId);
            doc.setAgentId(agentId);
            doc.setKbId(kbId);
            doc.setTitle(filename);
            doc.setFormat(format);
            doc.setChunkCount(docs != null ? docs.size() : 0);
            doc.setVectorDocIds(objectMapper.writeValueAsString(vectorDocIds));
            doc.setFileSize(file.getSize());
            doc.setLanguage(detectLanguage(filename));
            doc.setEmbeddingModel(getEmbeddingModelName());
            doc.setStatus(1);
            documentMapper.insert(doc);

            // 双写 chunk 原文到 MySQL（混合检索关键词路数据源）
            saveChunksToMysql(docs, uuid, tenantId, kbId);

            log.info("文档同步入库成功: uuid={}, chunks={}", uuid, docs != null ? docs.size() : 0);
            return uuid;

        } catch (Exception e) {
            log.error("文档入库失败: {}", filename, e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "文档处理失败: " + e.getMessage(), e);
        } finally {
            // 确保临时文件在任何路径下都被清理
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * 双写 chunk 原文到 MySQL（混合检索关键词路数据源）
     *
     * <p>当 chunkMapper 可用（混合检索启用）时，将每个 chunk 的原文写入 lumina_knowledge_chunk 表，
     * 支持后续 MySQL FULLTEXT 关键词检索。失败仅记录日志，不影响主入库流程。
     *
     * @since 3.3.0
     */
    private void saveChunksToMysql(List<Document> docs, String docUuid, Long tenantId, Long kbId) {
        if (chunkMapper == null || docs == null || docs.isEmpty()) {
            return;
        }
        try {
            for (int i = 0; i < docs.size(); i++) {
                Document d = docs.get(i);
                String content = d.getMetadata() != null ? d.getMetadata().getContentText() : null;
                if (content == null || content.isBlank()) {
                    continue;
                }
                io.lumina.agent.infrastructure.entity.KnowledgeChunkDO chunk =
                        new io.lumina.agent.infrastructure.entity.KnowledgeChunkDO();
                chunk.setChunkId(d.getId() != null ? d.getId() : docUuid + "_chunk_" + i);
                chunk.setDocUuid(docUuid);
                chunk.setKbId(kbId);
                chunk.setTenantId(tenantId != null ? tenantId : 0L);
                chunk.setContent(content);
                chunk.setChunkIndex(i);
                chunk.setVectorDocId(d.getId());
                chunkMapper.insert(chunk);
            }
            log.debug("chunk 原文双写 MySQL 成功: docUuid={}, chunks={}", docUuid, docs.size());
        } catch (Exception e) {
            log.warn("chunk 原文双写 MySQL 失败（不影响向量入库）: docUuid={}, error={}", docUuid, e.getMessage());
        }
    }

    @Override
    public KnowledgeDocumentDO getDocumentStatus(String uuid) {
        Long tenantId = BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L;
        LambdaQueryWrapper<KnowledgeDocumentDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeDocumentDO::getDocumentUuid, uuid);
        wrapper.eq(KnowledgeDocumentDO::getTenantId, tenantId);
        wrapper.select(KnowledgeDocumentDO::getDocumentUuid, KnowledgeDocumentDO::getTitle,
                KnowledgeDocumentDO::getStatus, KnowledgeDocumentDO::getChunkCount,
                KnowledgeDocumentDO::getFileSize, KnowledgeDocumentDO::getCreateTime);
        return documentMapper.selectOne(wrapper);
    }

    @Override
    public PageResult<KnowledgeDocumentDO> listDocuments(Long agentId, Long kbId, Integer pageNum, Integer pageSize) {
        Long tenantId = BaseContext.getTenantId();
        LambdaQueryWrapper<KnowledgeDocumentDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeDocumentDO::getTenantId, tenantId != null ? tenantId : 0L);
        if (agentId != null) {
            wrapper.eq(KnowledgeDocumentDO::getAgentId, agentId);
        }
        if (kbId != null) {
            wrapper.eq(KnowledgeDocumentDO::getKbId, kbId);
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
        Long tenantId = BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L;
        LambdaQueryWrapper<KnowledgeDocumentDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeDocumentDO::getDocumentUuid, uuid);
        wrapper.eq(KnowledgeDocumentDO::getTenantId, tenantId);
        KnowledgeDocumentDO doc = documentMapper.selectOne(wrapper);
        if (doc == null) return;

        removeVectorDocuments(doc.getVectorDocIds());

        documentMapper.deleteById(doc.getDocumentId());
        log.info("文档已删除: uuid={}", uuid);
    }

    /**
     * 删除向量数据；任一向量删除失败即抛异常中止，避免 MySQL 记录被删后留下孤儿向量
     */
    private void removeVectorDocuments(String vectorDocIdsJson) {
        if (embeddingStore == null || vectorDocIdsJson == null || vectorDocIdsJson.isBlank()) {
            return;
        }
        List<String> ids;
        try {
            ids = objectMapper.readValue(vectorDocIdsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("解析向量文档ID列表失败: {}", vectorDocIdsJson, e);
            return;
        }
        for (String id : ids) {
            try {
                Boolean ok = embeddingStore.delete(id).block();
                if (Boolean.TRUE.equals(ok)) {
                    log.info("向量文档已删除: id={}", id);
                } else {
                    log.error("删除向量数据失败（Qdrant 返回失败），中止删除: id={}", id);
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                            "删除知识库文档失败（向量数据清理失败），请重试或联系管理员");
                }
            } catch (BusinessException be) {
                throw be;
            } catch (Exception e) {
                log.error("删除向量数据失败，中止删除: id={}", id, e);
                throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "删除知识库文档失败（向量数据清理失败），请重试或联系管理员", e);
            }
        }
    }

    @Override
    public List<Map<String, Object>> search(String query, int limit) {
        if (knowledge == null) {
            return Collections.emptyList();
        }
        Long tenantId = BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L;
        double threshold = scoreThreshold;
        List<Document> results = knowledge.retrieve(query,
                RetrieveConfig.builder().limit(limit).scoreThreshold(threshold).build()).block();

        if (results == null) return Collections.emptyList();

        List<Map<String, Object>> list = new ArrayList<>();
        for (Document doc : results) {
            Object docTenantId = doc.getPayloadValue("tenantId");
            if (docTenantId != null && !String.valueOf(tenantId).equals(String.valueOf(docTenantId))) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("content", doc.getPayloadValue("content"));
            item.put("score", doc.getScore());
            item.put("metadata", doc.getMetadata());
            list.add(item);
        }
        return list;
    }

    private io.agentscope.core.rag.reader.SplitStrategy getSplitStrategy() {
        if (ragProperties != null && ragProperties.getReader() != null) {
            String strategy = ragProperties.getReader().getSplitStrategy();
            if (strategy != null) {
                try {
                    return io.agentscope.core.rag.reader.SplitStrategy.valueOf(strategy.toUpperCase());
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return SplitStrategy.PARAGRAPH;
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

    /**
     * 简单语言检测：文件名含 CJK 字符判定为中文
     */
    private String detectLanguage(String text) {
        if (text == null) return "auto";
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if ((ch >= '\u4E00' && ch <= '\u9FFF') || (ch >= '\u3040' && ch <= '\u30FF')) {
                return "zh";
            }
        }
        return "en";
    }

    /**
     * 获取当前配置的 Embedding 模型名称
     */
    private String getEmbeddingModelName() {
        if (ragProperties != null && ragProperties.getEmbedding() != null) {
            return ragProperties.getEmbedding().getModel();
        }
        return "unknown";
    }
}
