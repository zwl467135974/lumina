package io.lumina.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.lumina.agent.api.dto.EvaluationDatasetDTO;
import io.lumina.agent.api.dto.EvaluationRunDTO;
import io.lumina.agent.engine.AgentExecutionEngine;
import io.lumina.agent.evaluation.model.CaseResult;
import io.lumina.agent.evaluation.model.EvaluationDataset;
import io.lumina.agent.evaluation.model.RunReport;
import io.lumina.agent.evaluation.model.ScoringMethod;
import io.lumina.agent.evaluation.model.TestCase;
import io.lumina.agent.evaluation.scorer.EvaluationScorer;
import io.lumina.agent.evaluation.scorer.ScoreResult;
import io.lumina.agent.infrastructure.entity.AgentDO;
import io.lumina.agent.infrastructure.entity.EvaluationDatasetDO;
import io.lumina.agent.infrastructure.entity.EvaluationRunDO;
import io.lumina.agent.infrastructure.mapper.AgentMapper;
import io.lumina.agent.infrastructure.mapper.EvaluationDatasetMapper;
import io.lumina.agent.infrastructure.mapper.EvaluationRunMapper;
import io.lumina.agent.model.ExecuteResult;
import io.lumina.agent.model.AgentConfig;
import io.lumina.agent.service.EvaluationService;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.concurrent.Executor;

/**
 * Agent 评估服务实现
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@Service
public class EvaluationServiceImpl implements EvaluationService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final EvaluationDatasetMapper datasetMapper;
    private final EvaluationRunMapper runMapper;
    private final AgentMapper agentMapper;
    private final AgentExecutionEngine agentExecutionEngine;
    private final Map<ScoringMethod, EvaluationScorer> scorerMap;
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final Executor evaluationExecutor;

    @Autowired
    private io.lumina.agent.security.OutputSanitizer outputSanitizer;

    public EvaluationServiceImpl(EvaluationDatasetMapper datasetMapper,
                                 EvaluationRunMapper runMapper,
                                 AgentMapper agentMapper,
                                 AgentExecutionEngine agentExecutionEngine,
                                 List<EvaluationScorer> scorers,
                                 @org.springframework.beans.factory.annotation.Qualifier("agentTaskExecutor") Executor evaluationExecutor) {
        this.datasetMapper = datasetMapper;
        this.runMapper = runMapper;
        this.agentMapper = agentMapper;
        this.agentExecutionEngine = agentExecutionEngine;
        this.scorerMap = scorers.stream().collect(Collectors.toMap(EvaluationScorer::getMethod, Function.identity()));
        this.evaluationExecutor = evaluationExecutor;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EvaluationDataset createDataset(EvaluationDatasetDTO dto) {
        List<TestCase> cases = parseCases(dto.getCasesYaml());
        if (cases.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        EvaluationDatasetDO dataset = new EvaluationDatasetDO();
        dataset.setName(dto.getName());
        dataset.setDescription(dto.getDescription());
        dataset.setAgentType(dto.getAgentType());
        dataset.setCasesYaml(dto.getCasesYaml());
        dataset.setCaseCount(cases.size());
        dataset.setTenantId(currentTenantId());
        dataset.setCreateBy(BaseContext.getUserId());
        dataset.setIsDeleted(0);
        datasetMapper.insert(dataset);
        return toDataset(dataset, cases);
    }

    @Override
    public EvaluationDataset getDataset(Long id) {
        EvaluationDatasetDO dataset = getDatasetDO(id);
        return toDataset(dataset, parseCases(dataset.getCasesYaml()));
    }

    @Override
    public List<EvaluationDataset> listDatasets(String name) {
        LambdaQueryWrapper<EvaluationDatasetDO> wrapper = new LambdaQueryWrapper<EvaluationDatasetDO>()
                .eq(EvaluationDatasetDO::getTenantId, currentTenantId())
                .eq(EvaluationDatasetDO::getIsDeleted, 0)
                .orderByDesc(EvaluationDatasetDO::getCreateTime);
        if (StringUtils.hasText(name)) {
            wrapper.like(EvaluationDatasetDO::getName, name);
        }
        return datasetMapper.selectList(wrapper).stream()
                .map(dataset -> toDataset(dataset, parseCases(dataset.getCasesYaml())))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDataset(Long id) {
        EvaluationDatasetDO dataset = getDatasetDO(id);
        dataset.setIsDeleted(1);
        datasetMapper.updateById(dataset);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EvaluationDataset importDataset(MultipartFile file, String name, String agentType, String description) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        String yamlContent;
        try {
            yamlContent = new String(file.getBytes());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, e);
        }
        EvaluationDatasetDTO dto = new EvaluationDatasetDTO();
        dto.setName(name != null && !name.isBlank() ? name : file.getOriginalFilename());
        dto.setAgentType(agentType);
        dto.setDescription(description);
        dto.setCasesYaml(yamlContent);
        return createDataset(dto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RunReport runEvaluation(Long datasetId, EvaluationRunDTO dto) {
        EvaluationDatasetDO dataset = getDatasetDO(datasetId);
        AgentDO agent = Optional.ofNullable(agentMapper.selectById(dto.getAgentId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_NOT_FOUND));
        if (!currentTenantId().equals(agent.getTenantId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        List<TestCase> cases = parseCases(dataset.getCasesYaml());
        ScoringMethod method = dto.getScoringMethod() == null ? ScoringMethod.EXACT_MATCH : dto.getScoringMethod();
        EvaluationScorer scorer = Optional.ofNullable(scorerMap.get(method))
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST));
        double threshold = dto.getThreshold() == null ? 0.7 : dto.getThreshold();

        List<CaseResult> results = new ArrayList<>();
        for (TestCase testCase : cases) {
            results.add(runSingleCase(agent, testCase, scorer, threshold));
        }

        RunReport report = buildReport(dataset, agent, method, threshold, results);
        EvaluationRunDO run = toRunDO(report);
        run.setStatus("COMPLETED");
        run.setTenantId(currentTenantId());
        run.setCreateBy(BaseContext.getUserId());
        String[] modelInfo = resolveModelInfo(agent);
        run.setModelName(modelInfo[0]);
        run.setProvider(modelInfo[1]);
        try {
            run.setResultsJson(jsonMapper.writeValueAsString(results));
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, e);
        }
        runMapper.insert(run);
        report.setRunId(run.getId());
        return report;
    }

    @Override
    public Long runEvaluationAsync(Long datasetId, EvaluationRunDTO dto) {
        EvaluationDatasetDO dataset = getDatasetDO(datasetId);
        AgentDO agent = Optional.ofNullable(agentMapper.selectById(dto.getAgentId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_NOT_FOUND));
        if (!currentTenantId().equals(agent.getTenantId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        EvaluationRunDO placeholder = new EvaluationRunDO();
        placeholder.setDatasetId(datasetId);
        placeholder.setDatasetName(dataset.getName());
        placeholder.setAgentId(agent.getAgentId());
        placeholder.setAgentType(agent.getAgentType());
        placeholder.setScoringMethod(dto.getScoringMethod() != null ? dto.getScoringMethod().name() : ScoringMethod.EXACT_MATCH.name());
        placeholder.setThresholdValue(toBigDecimal(dto.getThreshold() != null ? dto.getThreshold() : 0.7, 2));
        placeholder.setTotalCases(0);
        placeholder.setPassedCases(0);
        placeholder.setPassRate(toBigDecimal(0, 2));
        placeholder.setAvgScore(toBigDecimal(0, 4));
        placeholder.setAvgLatencyMs(0L);
        placeholder.setTotalTokens(0);
        placeholder.setStatus("RUNNING");
        placeholder.setTenantId(currentTenantId());
        placeholder.setCreateBy(BaseContext.getUserId());
        runMapper.insert(placeholder);

        Long runId = placeholder.getId();
        Long tenantId = currentTenantId();
        Long userId = BaseContext.getUserId();

        evaluationExecutor.execute(() -> {
            io.lumina.common.core.BaseContext.setTenantId(tenantId);
            io.lumina.common.core.BaseContext.setUserId(userId);
            try {
                // 直接执行评估逻辑，不调用 runEvaluation()（避免产生重复行）
                EvaluationDatasetDO asyncDataset = getDatasetDO(datasetId);
                AgentDO agent2 = Optional.ofNullable(agentMapper.selectById(dto.getAgentId()))
                        .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_NOT_FOUND));
                List<TestCase> cases2 = parseCases(asyncDataset.getCasesYaml());
                ScoringMethod method2 = dto.getScoringMethod() == null ? ScoringMethod.EXACT_MATCH : dto.getScoringMethod();
                EvaluationScorer scorer2 = Optional.ofNullable(scorerMap.get(method2))
                        .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST));
                double threshold2 = dto.getThreshold() == null ? 0.7 : dto.getThreshold();
                List<CaseResult> results2 = new ArrayList<>();
                for (TestCase tc : cases2) {
                    results2.add(runSingleCase(agent2, tc, scorer2, threshold2));
                }
                RunReport report = buildReport(asyncDataset, agent2, method2, threshold2, results2);

                EvaluationRunDO update = new EvaluationRunDO();
                update.setId(runId);
                update.setStatus("COMPLETED");
                update.setTotalCases(report.getTotalCases());
                update.setPassedCases(report.getPassedCases());
                update.setPassRate(toBigDecimal(report.getPassRate(), 2));
                update.setAvgScore(toBigDecimal(report.getAvgScore(), 4));
                update.setAvgLatencyMs(report.getAvgLatencyMs());
                update.setTotalTokens(report.getTotalTokens());
                update.setResultsJson(jsonMapper.writeValueAsString(report.getResults()));
                runMapper.updateById(update);
            } catch (Exception e) {
                log.error("异步评估失败: runId={}", runId, e);
                EvaluationRunDO update = new EvaluationRunDO();
                update.setId(runId);
                update.setStatus("FAILED");
                update.setResultsJson("{\"error\":\"" + e.getMessage() + "\"}");
                runMapper.updateById(update);
            } finally {
                io.lumina.common.core.BaseContext.clear();
            }
        });

        return runId;
    }

    @Override
    public RunReport getRunReport(Long runId) {
        EvaluationRunDO run = Optional.ofNullable(runMapper.selectById(runId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!currentTenantId().equals(run.getTenantId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        RunReport report = new RunReport();
        report.setRunId(run.getId());
        report.setDatasetId(run.getDatasetId());
        report.setDatasetName(run.getDatasetName());
        report.setAgentId(run.getAgentId());
        report.setAgentType(run.getAgentType());
        report.setScoringMethod(ScoringMethod.valueOf(run.getScoringMethod()));
        report.setThreshold(run.getThresholdValue().doubleValue());
        report.setTotalCases(run.getTotalCases());
        report.setPassedCases(run.getPassedCases());
        report.setPassRate(run.getPassRate().doubleValue());
        report.setAvgScore(run.getAvgScore().doubleValue());
        report.setAvgLatencyMs(run.getAvgLatencyMs() == null ? 0L : run.getAvgLatencyMs());
        report.setTotalTokens(run.getTotalTokens() == null ? 0 : run.getTotalTokens());
        report.setResults(parseResults(run.getResultsJson()));
        report.setCategoryStats(buildCategoryStats(report.getResults()));
        return report;
    }

    @Override
    public Map<String, Object> compareRuns(Long runIdA, Long runIdB) {
        RunReport reportA = getRunReport(runIdA);
        RunReport reportB = getRunReport(runIdB);

        Map<String, CaseResult> mapA = reportA.getResults().stream()
                .collect(Collectors.toMap(r -> r.getCaseId() != null ? r.getCaseId() : r.getInput(),
                        r -> r, (a, b) -> a));

        List<Map<String, Object>> caseComparison = new ArrayList<>();
        int improved = 0, regressed = 0, unchanged = 0;

        for (CaseResult resultB : reportB.getResults()) {
            String key = resultB.getCaseId() != null ? resultB.getCaseId() : resultB.getInput();
            CaseResult resultA = mapA.get(key);
            if (resultA == null) continue;

            double scoreDiff = resultB.getScore() - resultA.getScore();
            String trend = scoreDiff > 0.001 ? "IMPROVED" : scoreDiff < -0.001 ? "REGRESSED" : "UNCHANGED";
            if ("IMPROVED".equals(trend)) improved++;
            else if ("REGRESSED".equals(trend)) regressed++;
            else unchanged++;

            Map<String, Object> caseEntry = new HashMap<>();
            caseEntry.put("caseId", key);
            caseEntry.put("category", resultB.getCategory());
            caseEntry.put("input", resultB.getInput());
            caseEntry.put("scoreA", resultA.getScore());
            caseEntry.put("scoreB", resultB.getScore());
            caseEntry.put("scoreDiff", Math.round(scoreDiff * 1000) / 1000.0);
            caseEntry.put("passedA", resultA.isPassed());
            caseEntry.put("passedB", resultB.isPassed());
            caseEntry.put("trend", trend);
            caseComparison.add(caseEntry);
        }

        return Map.of(
                "runA", Map.of("runId", runIdA, "passRate", reportA.getPassRate(), "avgScore", reportA.getAvgScore()),
                "runB", Map.of("runId", runIdB, "passRate", reportB.getPassRate(), "avgScore", reportB.getAvgScore()),
                "passRateDiff", Math.round((reportB.getPassRate() - reportA.getPassRate()) * 1000) / 1000.0,
                "avgScoreDiff", Math.round((reportB.getAvgScore() - reportA.getAvgScore()) * 10000) / 10000.0,
                "improved", improved,
                "regressed", regressed,
                "unchanged", unchanged,
                "cases", caseComparison
        );
    }

    @Override
    public List<EvaluationRunDO> listRuns(Long datasetId) {
        LambdaQueryWrapper<EvaluationRunDO> wrapper = new LambdaQueryWrapper<EvaluationRunDO>()
                .eq(EvaluationRunDO::getTenantId, currentTenantId())
                .orderByDesc(EvaluationRunDO::getCreateTime);
        if (datasetId != null) {
            wrapper.eq(EvaluationRunDO::getDatasetId, datasetId);
        }
        return runMapper.selectList(wrapper);
    }

    @Override
    public List<EvaluationRunDO> getRunTrend(Long datasetId) {
        return runMapper.selectList(new LambdaQueryWrapper<EvaluationRunDO>()
                .eq(EvaluationRunDO::getTenantId, currentTenantId())
                .eq(EvaluationRunDO::getDatasetId, datasetId)
                .orderByAsc(EvaluationRunDO::getCreateTime));
    }

    /**
     * 从 Agent DB 配置构建 AgentConfig（评估时使用 Agent 专属配置而非全局默认）
     */
    private AgentConfig buildEvalConfig(AgentDO agent) {
        AgentConfig config = new AgentConfig();
        config.setAgentName(agent.getAgentName());
        config.setAgentType(agent.getAgentType());
        if (agent.getLlmConfig() != null && !agent.getLlmConfig().isBlank()) {
            try {
                AgentConfig.LLMConfig llmConfig = jsonMapper.readValue(agent.getLlmConfig(), AgentConfig.LLMConfig.class);
                config.setLlmConfig(llmConfig);
            } catch (Exception e) {
                log.debug("评估：解析 Agent LLM 配置失败，使用全局默认: {}", e.getMessage());
            }
        }
        return config;
    }

    private CaseResult runSingleCase(AgentDO agent, TestCase testCase, EvaluationScorer scorer, double threshold) {
        long start = System.currentTimeMillis();
        CaseResult caseResult = new CaseResult();
        caseResult.setCaseId(testCase.getId());
        caseResult.setInput(testCase.getInput());
        caseResult.setExpected(testCase.getExpected());
        caseResult.setCategory(testCase.getCategory());
        try {
            AgentConfig evalConfig = buildEvalConfig(agent);
            ExecuteResult executeResult = agentExecutionEngine.executeSync(agent.getAgentType(), testCase.getInput(), evalConfig, null);
            String actualOutput = outputSanitizer != null ? outputSanitizer.sanitize(executeResult.getResult()) : executeResult.getResult();
            caseResult.setActual(actualOutput);
            caseResult.setLatencyMs(executeResult.getDuration() == null ? System.currentTimeMillis() - start : executeResult.getDuration());
            fillTokenUsage(caseResult, executeResult.getTokenUsage());
            if (!Boolean.TRUE.equals(executeResult.getSuccess())) {
                caseResult.setErrorMessage(executeResult.getError());
            }
            ScoreResult scoreResult = scorer.score(testCase, actualOutput);
            caseResult.setScore(scoreResult.getScore());
            caseResult.setScoreDetail(scoreResult.getDetail());
            caseResult.setPassed(scoreResult.getScore() >= threshold && Boolean.TRUE.equals(executeResult.getSuccess()));
        } catch (Exception e) {
            log.warn("评估用例执行失败: caseId={}", testCase.getId(), e);
            caseResult.setLatencyMs(System.currentTimeMillis() - start);
            caseResult.setScore(0.0);
            caseResult.setPassed(false);
            caseResult.setErrorMessage(e.getMessage());
        }
        return caseResult;
    }

    private RunReport buildReport(EvaluationDatasetDO dataset, AgentDO agent, ScoringMethod method,
                                  double threshold, List<CaseResult> results) {
        int total = results.size();
        int passed = (int) results.stream().filter(CaseResult::isPassed).count();
        double avgScore = total == 0 ? 0.0 : results.stream().mapToDouble(CaseResult::getScore).average().orElse(0.0);
        long avgLatency = total == 0 ? 0L : Math.round(results.stream().mapToLong(CaseResult::getLatencyMs).average().orElse(0.0));
        int totalTokens = results.stream().mapToInt(CaseResult::getTotalTokens).sum();
        RunReport report = new RunReport();
        report.setDatasetId(dataset.getId());
        report.setDatasetName(dataset.getName());
        report.setAgentId(agent.getAgentId());
        report.setAgentType(agent.getAgentType());
        report.setScoringMethod(method);
        report.setThreshold(threshold);
        report.setTotalCases(total);
        report.setPassedCases(passed);
        report.setPassRate(total == 0 ? 0.0 : (double) passed / total);
        report.setAvgScore(avgScore);
        report.setAvgLatencyMs(avgLatency);
        report.setTotalTokens(totalTokens);
        report.setResults(results);
        report.setCategoryStats(buildCategoryStats(results));
        return report;
    }

    private Map<String, RunReport.CategoryStats> buildCategoryStats(List<CaseResult> results) {
        Map<String, List<CaseResult>> grouped = results.stream()
                .collect(Collectors.groupingBy(result -> StringUtils.hasText(result.getCategory()) ? result.getCategory() : "未分类"));
        Map<String, RunReport.CategoryStats> statsMap = new HashMap<>();
        grouped.forEach((category, categoryResults) -> {
            RunReport.CategoryStats stats = new RunReport.CategoryStats();
            int total = categoryResults.size();
            int passed = (int) categoryResults.stream().filter(CaseResult::isPassed).count();
            stats.setTotalCases(total);
            stats.setPassedCases(passed);
            stats.setPassRate(total == 0 ? 0.0 : (double) passed / total);
            stats.setAvgScore(categoryResults.stream().mapToDouble(CaseResult::getScore).average().orElse(0.0));
            statsMap.put(category, stats);
        });
        return statsMap;
    }

    private EvaluationRunDO toRunDO(RunReport report) {
        EvaluationRunDO run = new EvaluationRunDO();
        run.setDatasetId(report.getDatasetId());
        run.setDatasetName(report.getDatasetName());
        run.setAgentId(report.getAgentId());
        run.setAgentType(report.getAgentType());
        run.setScoringMethod(report.getScoringMethod().name());
        run.setThresholdValue(toBigDecimal(report.getThreshold(), 2));
        run.setTotalCases(report.getTotalCases());
        run.setPassedCases(report.getPassedCases());
        run.setPassRate(toBigDecimal(report.getPassRate(), 2));
        run.setAvgScore(toBigDecimal(report.getAvgScore(), 4));
        run.setAvgLatencyMs(report.getAvgLatencyMs());
        run.setTotalTokens(report.getTotalTokens());
        return run;
    }

    private BigDecimal toBigDecimal(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }

    private void fillTokenUsage(CaseResult result, ExecuteResult.TokenUsage tokenUsage) {
        if (tokenUsage == null) {
            return;
        }
        result.setPromptTokens(tokenUsage.getPromptTokens() == null ? 0 : tokenUsage.getPromptTokens());
        result.setCompletionTokens(tokenUsage.getCompletionTokens() == null ? 0 : tokenUsage.getCompletionTokens());
        result.setTotalTokens(tokenUsage.getTotalTokens() == null ? 0 : tokenUsage.getTotalTokens());
    }

    private EvaluationDatasetDO getDatasetDO(Long id) {
        EvaluationDatasetDO dataset = Optional.ofNullable(datasetMapper.selectById(id))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!currentTenantId().equals(dataset.getTenantId()) || Integer.valueOf(1).equals(dataset.getIsDeleted())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return dataset;
    }

    private EvaluationDataset toDataset(EvaluationDatasetDO dataset, List<TestCase> cases) {
        EvaluationDataset result = new EvaluationDataset();
        result.setId(dataset.getId());
        result.setName(dataset.getName());
        result.setDescription(dataset.getDescription());
        result.setAgentType(dataset.getAgentType());
        result.setCases(cases);
        result.setTenantId(dataset.getTenantId());
        if (dataset.getCreateTime() != null) {
            result.setCreateTime(dataset.getCreateTime().format(DATE_TIME_FORMATTER));
        }
        return result;
    }

    private List<TestCase> parseCases(String casesYaml) {
        try {
            JsonNode root = yamlMapper.readTree(casesYaml);
            JsonNode caseNode = root.has("cases") ? root.get("cases") : root;
            return yamlMapper.convertValue(caseNode, new TypeReference<List<TestCase>>() {});
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    private List<CaseResult> parseResults(String resultsJson) {
        if (!StringUtils.hasText(resultsJson)) {
            return List.of();
        }
        try {
            return jsonMapper.readValue(resultsJson, new TypeReference<List<CaseResult>>() {});
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, e);
        }
    }

    private Long currentTenantId() {
        return BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L;
    }

    private String[] resolveModelInfo(AgentDO agent) {
        if (agent.getLlmConfig() != null && !agent.getLlmConfig().isBlank()) {
            try {
                var config = jsonMapper.readTree(agent.getLlmConfig());
                String modelName = config.has("modelName") ? config.get("modelName").asText() : null;
                String modelType = config.has("modelType") ? config.get("modelType").asText() : null;
                if (modelName != null) return new String[]{modelName, modelType != null ? modelType : "default"};
            } catch (Exception ignored) {}
        }
        return new String[]{null, null};
    }
}
