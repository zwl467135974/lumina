import { describe, it, expect } from 'vitest'
import {
  REASONING_CHUNK, REASONING, POST_REASONING,
  ACTING_CHUNK, ACTING, POST_ACTING,
  FINAL, AGENT_RESULT, ERROR,
  isReasoningType, isActingType, isFinalResultType
} from '../stream-events'

describe('stream-events constants', () => {
  it('exports correct string values', () => {
    expect(REASONING_CHUNK).toBe('REASONING_CHUNK')
    expect(REASONING).toBe('REASONING')
    expect(POST_REASONING).toBe('POST_REASONING')
    expect(ACTING_CHUNK).toBe('ACTING_CHUNK')
    expect(ACTING).toBe('ACTING')
    expect(POST_ACTING).toBe('POST_ACTING')
    expect(FINAL).toBe('FINAL')
    expect(AGENT_RESULT).toBe('AGENT_RESULT')
    expect(ERROR).toBe('ERROR')
  })
})

describe('isReasoningType', () => {
  it('returns true for reasoning types', () => {
    expect(isReasoningType(REASONING_CHUNK)).toBe(true)
    expect(isReasoningType(REASONING)).toBe(true)
    expect(isReasoningType(POST_REASONING)).toBe(true)
  })

  it('returns false for non-reasoning types', () => {
    expect(isReasoningType(ACTING_CHUNK)).toBe(false)
    expect(isReasoningType(FINAL)).toBe(false)
    expect(isReasoningType(ERROR)).toBe(false)
    expect(isReasoningType('UNKNOWN')).toBe(false)
  })
})

describe('isActingType', () => {
  it('returns true for acting types', () => {
    expect(isActingType(ACTING_CHUNK)).toBe(true)
    expect(isActingType(ACTING)).toBe(true)
    expect(isActingType(POST_ACTING)).toBe(true)
  })

  it('returns false for non-acting types', () => {
    expect(isActingType(REASONING)).toBe(false)
    expect(isActingType(FINAL)).toBe(false)
    expect(isActingType('UNKNOWN')).toBe(false)
  })
})

describe('isFinalResultType', () => {
  it('returns true for final result types', () => {
    expect(isFinalResultType(FINAL)).toBe(true)
    expect(isFinalResultType(AGENT_RESULT)).toBe(true)
  })

  it('returns false for non-final types', () => {
    expect(isFinalResultType(REASONING_CHUNK)).toBe(false)
    expect(isFinalResultType(ACTING)).toBe(false)
    expect(isFinalResultType(ERROR)).toBe(false)
    expect(isFinalResultType('')).toBe(false)
  })
})
