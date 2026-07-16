import request from '../request'
import type { R } from '@/types/api'

export type ScoringMethod = 'EXACT_MATCH' | 'CONTAINS' | 'SEMANTIC_SIMILARITY' | 'LLM_JUDGE'

export interface EvaluationTestCase {
  id?: string
  input: string
  expected: string
  category?: string
  tags?: Record<string, string>
}

export interface EvaluationDataset {
  id: number
  name: string
  description?: string
  agentType?: string
  cases: EvaluationTestCase[]
  tenantId?: number
  createTime?: string
}

export interface EvaluationDatasetDTO {
  name: string
  description?: string
  agentType?: string
  casesYaml: string
}

export interface CaseResult {
  caseId?: string
  input: string
  expected: string
  actual?: string
  score: number
  scoreDetail?: string
  passed: boolean
  latencyMs: number
  totalTokens?: number
  errorMessage?: string
  category?: string
}

export interface RunReport {
  runId: number
  datasetId: number
  datasetName: string
  agentId: number
  agentType?: string
  scoringMethod: ScoringMethod
  threshold: number
  totalCases: number
  passedCases: number
  passRate: number
  avgScore: number
  avgLatencyMs: number
  totalTokens: number
  categoryStats?: Record<string, { totalCases: number; passedCases: number; passRate: number; avgScore: number }>
  results: CaseResult[]
}

export interface EvaluationRunRecord {
  id: number
  datasetId: number
  datasetName: string
  agentId: number
  agentType?: string
  scoringMethod: ScoringMethod
  thresholdValue: number
  totalCases: number
  passedCases: number
  passRate: number
  avgScore: number
  avgLatencyMs?: number
  totalTokens?: number
  status?: 'RUNNING' | 'COMPLETED' | 'FAILED'
  createTime?: string
}

export function createEvaluationDataset(data: EvaluationDatasetDTO) {
  return request.post<R<EvaluationDataset>>('/api/v1/evaluations/datasets', data)
}

export function listEvaluationDatasets(params?: { name?: string }) {
  return request.get<R<EvaluationDataset[]>>('/api/v1/evaluations/datasets', { params })
}

export function deleteEvaluationDataset(id: number) {
  return request.delete<R<void>>(`/api/v1/evaluations/datasets/${id}`)
}

export function runEvaluation(id: number, data: { agentId: number; scoringMethod: ScoringMethod; threshold: number }) {
  return request.post<R<RunReport>>(`/api/v1/evaluations/datasets/${id}/runs`, data)
}

export function runEvaluationAsync(id: number, data: { agentId: number; scoringMethod: ScoringMethod; threshold: number }) {
  return request.post<R<number>>(`/api/v1/evaluations/datasets/${id}/runs/async`, data)
}

export function listEvaluationRuns(params?: { datasetId?: number }) {
  return request.get<R<EvaluationRunRecord[]>>('/api/v1/evaluations/runs', { params })
}

export function getEvaluationRunReport(id: number) {
  return request.get<R<RunReport>>(`/api/v1/evaluations/runs/${id}`)
}

export function getEvaluationTrend(datasetId: number) {
  return request.get<R<EvaluationRunRecord[]>>(`/api/v1/evaluations/datasets/${datasetId}/trend`)
}

export function compareEvaluationRuns(runA: number, runB: number) {
  return request.get<R<Record<string, any>>>('/api/v1/evaluations/runs/compare', { params: { runA, runB } })
}

export function importEvaluationDataset(file: File, name?: string, agentType?: string, description?: string) {
  const formData = new FormData()
  formData.append('file', file)
  if (name) formData.append('name', name)
  if (agentType) formData.append('agentType', agentType)
  if (description) formData.append('description', description)
  return request.post<R<EvaluationDataset>>('/api/v1/evaluations/datasets/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// ==================== 评估回归（v3.3 新增） ====================

/** 批量回归 DTO */
export interface BatchRegressionDTO {
  datasetIds: number[]
  agentId: number
  scoringMethod?: ScoringMethod
  threshold?: number
  promptName?: string
  promptVersion?: number
  baselineRunId?: number
}

/** Prompt 版本 diff 结果 */
export interface PromptDiffResult {
  name: string
  versionA: number
  versionB: number
  diffLines: Array<{
    line: number
    type: 'ADDED' | 'REMOVED' | 'MODIFIED'
    content?: string
    oldContent?: string
    newContent?: string
  }>
  totalChanges: number
}

/**
 * 批量回归测试
 */
export function runBatchRegression(data: BatchRegressionDTO) {
  return request.post<R<Record<string, any>>>('/api/v1/evaluations/regression/batch', data, { timeout: 300000 })
}

/**
 * 标记基线 run
 */
export function markBaseline(runId: number) {
  return request.post<R<void>>(`/api/v1/evaluations/runs/${runId}/baseline`)
}

/**
 * 对比两个 Prompt 版本
 */
export function comparePromptVersions(name: string, vA: number, vB: number) {
  return request.get<R<PromptDiffResult>>('/api/v1/evaluations/prompts/compare', { params: { name, vA, vB } })
}
