import { useQuery, type UseQueryResult } from "@tanstack/react-query";
import { ApiError } from "@/shared/api/errors";
import { currentAcceptLanguage } from "@/shared/api/acceptLanguage";


const BASE_URL = "/api/v1";

export interface CareTipSummary {
  id: string;
  slug: string;
  locale: string;
  kind: string;
  category: string;
  title: string;
  summary: string | null;
  tags: string[];
  publishedAt: string | null;
}

interface CareTipPage {
  items: CareTipSummary[];
  limit: number;
  hasMore: boolean;
}

interface ProblemDetail {
  detail?: string;
  errorCode?: string;
}

async function fetchCareTips(limit: number): Promise<CareTipSummary[]> {
  const response = await fetch(`${BASE_URL}/care-tips?limit=${String(limit)}`, {
    credentials: "include",
    headers: { Accept: "application/json", "Accept-Language": currentAcceptLanguage() },
  });
  if (!response.ok) {
    let problem: ProblemDetail | undefined;
    try {
      problem = (await response.json()) as ProblemDetail;
    } catch {
      // Body không phải JSON: dùng thông báo lỗi mặc định theo status.
    }
    throw new ApiError(
      problem?.detail ?? `Request failed with status ${String(response.status)}`,
      response.status,
      problem?.errorCode,
    );
  }
  return ((await response.json()) as CareTipPage).items;
}

export const careTipKeys = {
  all: ["care-tips"] as const,
  list: (limit: number) => [...careTipKeys.all, limit] as const,
};

export function useCareTips(limit = 3): UseQueryResult<CareTipSummary[]> {
  return useQuery({
    queryKey: careTipKeys.list(limit),
    queryFn: () => fetchCareTips(limit),
    staleTime: 10 * 60 * 1000,
  });
}
