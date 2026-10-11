import { isApiError } from "@/shared/api";

/** Khoá i18n (`community:report.errors.*`) cho lỗi của `POST /community/reports`. */
export type CommunityReportErrorKey = "duplicate" | "notFound" | "ownContent" | "generic";

/**
 * 409 `REPORT_DUPLICATE` = đã báo cáo rồi; 422 `REPORT_OWN_CONTENT` = báo cáo nội dung của chính
 * mình; 404 = bài/bình luận không còn tồn tại; còn lại (kể cả 400) = thông điệp chung. Không bao giờ để lộ mã lỗi thô cho người dùng.
 */
export function communityReportErrorKey(error: unknown): CommunityReportErrorKey {
  if (!isApiError(error)) return "generic";
  if (error.code === "REPORT_OWN_CONTENT") return "ownContent";
  if (error.status === 409 || error.code === "REPORT_DUPLICATE") return "duplicate";
  if (error.status === 404) return "notFound";
  return "generic";
}
