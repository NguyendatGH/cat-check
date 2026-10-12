import { ApiError } from "@/shared/api/errors";
import { currentAcceptLanguage } from "@/shared/api/acceptLanguage";
import { CSRF_COOKIE_NAME, CSRF_HEADER_NAME } from "@/shared/config/constants";
import type { CatTrendsResponse, TrendRange } from "./types";


const BASE_URL = "/api/v1";

export const FEATURE_NOT_IN_PLAN = "FEATURE_NOT_IN_PLAN";

interface ProblemDetail {
  detail?: string;
  status?: number;
  code?: string;
  errorCode?: string;
}

function readCsrfToken(): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${CSRF_COOKIE_NAME}=([^;]*)`));
  return match?.[1] ? decodeURIComponent(match[1]) : null;
}

export async function fetchCatTrends(catId: string, range: TrendRange): Promise<CatTrendsResponse> {
  const headers = new Headers({ Accept: "application/json", "Accept-Language": currentAcceptLanguage() });
  const csrf = readCsrfToken();
  if (csrf) headers.set(CSRF_HEADER_NAME, csrf);

  const response = await fetch(`${BASE_URL}/cats/${catId}/trends?range=${range}`, {
    credentials: "include",
    headers,
  });

  if (!response.ok) {
    let problem: ProblemDetail | undefined;
    try {
      problem = (await response.json()) as ProblemDetail;
    } catch {
    }
    throw new ApiError(
      problem?.detail ?? `Request failed with status ${String(response.status)}`,
      response.status,
      problem?.errorCode ?? problem?.code,
    );
  }
  return (await response.json()) as CatTrendsResponse;
}
