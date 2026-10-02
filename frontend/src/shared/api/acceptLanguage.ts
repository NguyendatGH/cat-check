import i18n from "@/shared/i18n/i18n";
import { DEFAULT_LOCALE } from "@/shared/config/constants";

/**
 * Ngôn ngữ gửi kèm mọi request dưới dạng `Accept-Language`.
 *
 * Server dùng header này để chọn nhãn/mô tả đa ngôn ngữ (ví dụ `label` của
 * `ph_classification_band`, message lỗi ProblemDetail). KHÔNG để trình duyệt tự quyết:
 * ngôn ngữ của ỨNG DỤNG là thứ người dùng chọn ở `/settings/language`, còn
 * `navigator.language` có thể là `en-US` trên cùng một máy — khi đó giao diện tiếng Việt
 * mà nhãn từ server lại tiếng Anh, lẫn lộn ngay trong một màn hình.
 *
 * `localeMiddleware` (openapi-fetch) đã làm đúng việc này cho `apiClient`; các wrapper
 * `apiFetch` riêng của từng feature phải gọi hàm này để không bỏ sót.
 */
export function currentAcceptLanguage(): string {
  return i18n.language || DEFAULT_LOCALE;
}
