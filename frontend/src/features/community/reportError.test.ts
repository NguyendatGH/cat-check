import { describe, expect, it } from "vitest";
import { ApiError } from "@/shared/api";
import vi from "@/shared/i18n/locales/vi/community.json";
import { communityReportErrorKey } from "./reportError";

describe("communityReportErrorKey", () => {
  it("409 REPORT_DUPLICATE -> duplicate", () => {
    expect(communityReportErrorKey(new ApiError("x", 409, "REPORT_DUPLICATE"))).toBe("duplicate");
    expect(vi.report.errors.duplicate).toBe("Bạn đã báo cáo nội dung này rồi.");
  });
  it("404 -> notFound", () => {
    expect(communityReportErrorKey(new ApiError("x", 404))).toBe("notFound");
    expect(vi.report.errors.notFound).toBe("Nội dung không còn tồn tại.");
  });
  it("400 và lỗi khác -> generic", () => {
    expect(communityReportErrorKey(new ApiError("x", 400, "VALIDATION"))).toBe("generic");
    expect(communityReportErrorKey(new Error("net"))).toBe("generic");
  });
});
