import { ApiError } from "@/shared/api/errors";
import { currentAcceptLanguage } from "@/shared/api/acceptLanguage";
import { CSRF_COOKIE_NAME, CSRF_HEADER_NAME, IDEMPOTENCY_HEADER_NAME } from "@/shared/config/constants";

export interface AiCitation {
  title: string;
  sourceUrl: string;
  rank: number;
}

export interface AiChatResponse {
  conversationId: string;
  messageId: string;
  answer: string;
  provider: string;
  citations: AiCitation[];
  createdAt: string;
}

function csrfToken(): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${CSRF_COOKIE_NAME}=([^;]*)`));
  return match?.[1] ? decodeURIComponent(match[1]) : null;
}

/**
 * @param catId tuỳ chọn — khi có, câu hỏi được làm giàu bằng ngữ cảnh của một bé mèo cụ thể
 *              (xem backend `ai.api.dto.AiChatRequest.catId`). Mèo không thuộc người dùng hiện
 *              tại được backend xử lý y hệt "không gửi catId", không có lỗi riêng ở đây.
 */
export async function chatWithAssistant(
  message: string,
  conversationId?: string,
  catId?: string,
): Promise<AiChatResponse> {
  const headers = new Headers({
    Accept: "application/json",
    "Content-Type": "application/json",
    "Accept-Language": currentAcceptLanguage(),
    [IDEMPOTENCY_HEADER_NAME]: crypto.randomUUID(),
  });
  const csrf = csrfToken();
  if (csrf !== null) headers.set(CSRF_HEADER_NAME, csrf);
  const response = await fetch("/api/v1/ai/chat", {
    method: "POST",
    credentials: "include",
    headers,
    body: JSON.stringify({ message, conversationId, catId }),
  });
  if (!response.ok) {
    let detail: { detail?: string; errorCode?: string } | undefined;
    try {
      detail = (await response.json()) as { detail?: string; errorCode?: string };
    } catch {
      // Keep the HTTP status when the server did not return ProblemDetail JSON.
    }
    throw new ApiError(
      detail?.detail ?? `Request failed with status ${String(response.status)}`,
      response.status,
      detail?.errorCode,
    );
  }
  return (await response.json()) as AiChatResponse;
}
