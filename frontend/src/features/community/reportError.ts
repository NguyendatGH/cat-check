import { isApiError } from "@/shared/api";

export type CommunityReportErrorKey = "duplicate" | "notFound" | "ownContent" | "generic";

export function communityReportErrorKey(error: unknown): CommunityReportErrorKey {
  if (!isApiError(error)) return "generic";
  if (error.code === "REPORT_OWN_CONTENT") return "ownContent";
  if (error.status === 409 || error.code === "REPORT_DUPLICATE") return "duplicate";
  if (error.status === 404) return "notFound";
  return "generic";
}
