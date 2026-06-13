import { describe, expect, it } from 'vitest'

import { parseJsonObject, stringifyJson } from '@/lib/json'

describe('json helpers', () => {
  it('stringifyJson pretty-prints objects and maps null/undefined to empty', () => {
    expect(stringifyJson({ a: 1 })).toBe('{\n  "a": 1\n}')
    expect(stringifyJson(null)).toBe('')
    expect(stringifyJson(undefined)).toBe('')
  })

  it('parseJsonObject returns an object and round-trips with stringifyJson', () => {
    const value = { type: 'object', nested: { ok: true } }
    expect(parseJsonObject(stringifyJson(value))).toEqual(value)
  })

  it('parseJsonObject treats empty/whitespace as undefined (field omitted)', () => {
    expect(parseJsonObject('')).toBeUndefined()
    expect(parseJsonObject('   ')).toBeUndefined()
  })

  it('parseJsonObject rejects non-object JSON (array, number, null)', () => {
    expect(() => parseJsonObject('[1,2]')).toThrow()
    expect(() => parseJsonObject('42')).toThrow()
    expect(() => parseJsonObject('null')).toThrow()
  })
})
