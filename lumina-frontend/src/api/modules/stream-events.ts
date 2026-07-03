/**
 * 流式事件类型常量
 *
 * 与后端 StreamEventType 保持一致，避免硬编码字符串。
 */

/** 推理片段（思考过程） */
export const REASONING_CHUNK = 'REASONING_CHUNK'
export const REASONING = 'REASONING'
export const POST_REASONING = 'POST_REASONING'

/** 行动片段（工具调用过程） */
export const ACTING_CHUNK = 'ACTING_CHUNK'
export const ACTING = 'ACTING'
export const POST_ACTING = 'POST_ACTING'

/** 最终结果 */
export const FINAL = 'FINAL'
export const AGENT_RESULT = 'AGENT_RESULT'

/** 错误 */
export const ERROR = 'ERROR'

/** 判断是否为推理类事件 */
export function isReasoningType(type: string): boolean {
  return type === REASONING_CHUNK || type === REASONING || type === POST_REASONING
}

/** 判断是否为行动类事件 */
export function isActingType(type: string): boolean {
  return type === ACTING_CHUNK || type === ACTING || type === POST_ACTING
}

/** 判断是否为最终结果类事件 */
export function isFinalResultType(type: string): boolean {
  return type === FINAL || type === AGENT_RESULT
}
