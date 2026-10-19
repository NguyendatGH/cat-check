import { describe, expect, it } from "vitest";
import { ApiError } from "@/shared/api/errors";
import { communityCommentErrorKey } from "./commentError";

describe("communityCommentErrorKey", () => {
  it("maps backend error codes to comment messages", () => {
    expect(communityCommentErrorKey(new ApiError("x", 404, "COMMENT_NOT_FOUND"))).toBe("parentGone");
    expect(communityCommentErrorKey(new ApiError("x", 404, "POST_NOT_FOUND"))).toBe("postGone");
    expect(communityCommentErrorKey(new ApiError("x", 400, "STORAGE_UNSUPPORTED_TYPE"))).toBe("imageType");
    expect(communityCommentErrorKey(new ApiError("x", 413))).toBe("imageSize");
    expect(communityCommentErrorKey(new ApiError("x", 429))).toBe("rateLimited");
  });

  it("falls back to generic", () => {
    expect(communityCommentErrorKey(new Error("boom"))).toBe("generic");
    expect(communityCommentErrorKey(new ApiError("x", 500))).toBe("generic");
  });
});
