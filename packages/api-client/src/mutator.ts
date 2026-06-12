/**
 * Custom fetch used by every Orval-generated hook.
 *
 * The host app wires auth in once via {@link configureApiClient}: an access-token getter and a
 * refresh callback. Authenticated requests that come back 401 trigger a single-flight token
 * refresh and one retry; requests without an Authorization header (login, register, refresh
 * itself) are never retried, so auth failures surface to the caller as {@link ApiError}.
 */

export interface ApiClientConfig {
  /** Prepended to request paths. Empty by default — dev uses the Vite proxy. */
  baseUrl?: string;
  /** Current access token, or null when unauthenticated. */
  getAccessToken: () => string | null;
  /** Try to obtain a fresh access token; resolve false (or reject) when not possible. */
  refreshSession: () => Promise<boolean>;
  /** Called when a refresh attempt fails — the app should clear state / go to login. */
  onSessionExpired: () => void;
}

let config: ApiClientConfig = {
  baseUrl: '',
  getAccessToken: () => null,
  refreshSession: async () => false,
  onSessionExpired: () => {},
};

export function configureApiClient(next: ApiClientConfig): void {
  config = { baseUrl: '', ...next };
}

/** RFC 9457 problem detail, as produced by the backend's ProblemDetail responses. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  [key: string]: unknown;
}

export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail | null;

  constructor(status: number, problem: ProblemDetail | null) {
    super(problem?.detail ?? problem?.title ?? `Request failed with status ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }
}

/** Deduplicates concurrent refreshes: every 401 in flight awaits the same attempt. */
let refreshInFlight: Promise<boolean> | null = null;

async function refreshOnce(): Promise<boolean> {
  refreshInFlight ??= config
    .refreshSession()
    .catch(() => false)
    .finally(() => {
      refreshInFlight = null;
    });
  return refreshInFlight;
}

export const customFetch = async <T>(url: string, init: RequestInit): Promise<T> => {
  const token = config.getAccessToken();
  const response = await doFetch(url, init, token);

  // Only authenticated calls are retried after refresh; the generated code never sets
  // Authorization itself — we add it in doFetch whenever a token exists, so "authenticated
  // call" == "we held a token". Login/register/refresh run tokenless and surface their 401s.
  if (response.status === 401 && token !== null) {
    if (await refreshOnce()) {
      const retried = await doFetch(url, init, config.getAccessToken());
      return parse<T>(retried);
    }
    config.onSessionExpired();
  }
  return parse<T>(response);
};

function doFetch(url: string, init: RequestInit, token: string | null): Promise<Response> {
  const headers = new Headers(init.headers);
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }
  return fetch(`${config.baseUrl ?? ''}${url}`, { ...init, headers });
}

async function parse<T>(response: Response): Promise<T> {
  const text = await response.text();
  const body = text ? safeJson(text) : null;
  if (!response.ok) {
    throw new ApiError(response.status, body as ProblemDetail | null);
  }
  return body as T;
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}
