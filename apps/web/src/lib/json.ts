/** Pretty-prints a JSON value for an editor; null/undefined → empty string. */
export function stringifyJson(value: unknown): string {
  if (value === null || value === undefined) {
    return ''
  }
  return JSON.stringify(value, null, 2)
}

/**
 * Parses editor text into a JSON object for the API ({@code schemaJson}/{@code exampleJson}
 * are objects). Empty → undefined (omit the field). Assumes the text already validated in the
 * editor; non-object JSON (e.g. a bare array/number) is rejected to match the backend shape.
 */
export function parseJsonObject(text: string): Record<string, unknown> | undefined {
  const trimmed = text.trim()
  if (trimmed === '') {
    return undefined
  }
  const parsed = JSON.parse(trimmed)
  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error('Expected a JSON object')
  }
  return parsed as Record<string, unknown>
}
