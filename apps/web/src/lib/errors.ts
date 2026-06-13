import { ApiError } from '@rota/api-client'

/**
 * Maps an API error to a user-facing message. Pass a {@code byCode} map to translate known
 * ProblemDetail codes (e.g. {@code duplicate}); otherwise falls back to the server detail
 * or a generic message.
 */
export function apiErrorMessage(
  error: unknown,
  fallback: string,
  byCode: Record<string, string> = {},
): string {
  if (error instanceof ApiError) {
    const code = typeof error.problem?.code === 'string' ? error.problem.code : undefined
    if (code && byCode[code]) {
      return byCode[code]
    }
    if (error.problem?.detail) {
      return error.problem.detail
    }
  }
  return fallback
}

export function apiErrorStatus(error: unknown): number | undefined {
  return error instanceof ApiError ? error.status : undefined
}
