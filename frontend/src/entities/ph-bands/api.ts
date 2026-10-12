import { ApiError } from "@/shared/api/errors";

const BASE_URL = "/api/v1";

interface ProblemDetail {
  detail?: string;
  code?: string;
}

export async function phBandsApiFetch<T>(path: string): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, {
    credentials: "include",
    headers: { Accept: "application/json" },
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
      problem?.code,
    );
  }

  return (await response.json()) as T;
}
