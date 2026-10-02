import { CSRF_COOKIE_NAME, CSRF_HEADER_NAME, IDEMPOTENCY_HEADER_NAME } from "@/shared/config/constants";
import { ApiError } from "./errors";

function readCsrfCookie(): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${CSRF_COOKIE_NAME}=([^;]*)`));
  return match?.[1] ? decodeURIComponent(match[1]) : null;
}

/**
 * Upload multipart/form-data (ảnh scan) — tách riêng khỏi apiClient vì openapi-fetch không
 * phù hợp cho multipart. TODO: nối endpoint thật khi có API scan.
 */
export async function uploadFile(path: string, file: File): Promise<Response> {
  const formData = new FormData();
  formData.append("file", file);

  const headers = new Headers({ [IDEMPOTENCY_HEADER_NAME]: crypto.randomUUID() });
  const csrf = readCsrfCookie();
  if (csrf) {
    headers.set(CSRF_HEADER_NAME, csrf);
  }

  const response = await fetch(`/api${path}`, {
    method: "POST",
    body: formData,
    credentials: "include",
    headers,
  });

  if (!response.ok) {
    throw new ApiError(`Upload failed with status ${String(response.status)}`, response.status);
  }

  return response;
}
