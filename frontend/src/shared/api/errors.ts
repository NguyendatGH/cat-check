/** Lỗi API chuẩn hoá — KHÔNG log PII vào message/context (p17 §17.10b). */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string | undefined;

  constructor(message: string, status: number, code?: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
  }
}

export function isApiError(value: unknown): value is ApiError {
  return value instanceof ApiError;
}
