import { isApiError } from "@/shared/api";

export type CommunityCommentErrorKey = "parentGone" | "postGone" | "imageType" | "imageSize" | "rateLimited" | "generic";

export function communityCommentErrorKey(error: unknown): CommunityCommentErrorKey {
  if (!isApiError(error)) return "generic";
  switch (error.code) {
    case "COMMENT_NOT_FOUND":
      return "parentGone";
    case "POST_NOT_FOUND":
      return "postGone";
    case "STORAGE_UNSUPPORTED_TYPE":
      return "imageType";
    case "STORAGE_PAYLOAD_TOO_LARGE":
      return "imageSize";
    case "COMMENT_RATE_LIMITED":
      return "rateLimited";
    default:
      if (error.status === 413) return "imageSize";
      if (error.status === 429) return "rateLimited";
      return "generic";
  }
}
