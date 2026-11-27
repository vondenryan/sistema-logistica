import { ApiError, formatApiError } from '../api'

export function formatFleetError(error: unknown, conflictMessage?: string): string {
  if (!(error instanceof ApiError)) {
    return formatApiError(error)
  }
  if (error.status === 409 && error.code === 'CONFLICT' && conflictMessage !== undefined) {
    return `HTTP 409: ${conflictMessage}`
  }
  return `HTTP ${error.status} — ${formatApiError(error)}`
}
