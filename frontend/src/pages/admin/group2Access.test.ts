import { describe, expect, it } from "vitest";
import { hasAnyRole, ROLES } from "@/shared/auth/roles";

describe("quyền ghi nhóm danh mục (ROLES)", () => {
  it("ADMIN_CATALOG và ADMIN_SUPER ghi được sản phẩm và bảng màu", () => {
    for (const key of ["products", "colorChart"] as const) {
      expect(hasAnyRole(["ADMIN_CATALOG"], ROLES[key])).toBe(true);
      expect(hasAnyRole(["ADMIN_SUPER"], ROLES[key])).toBe(true);
    }
  });
  it("ADMIN_SUPPORT không ghi được nhóm danh mục và nội dung", () => {
    for (const key of ["products", "colorChart", "careTipsWrite", "careTipsPublish", "communityModeration"] as const) {
      expect(hasAnyRole(["ADMIN_SUPPORT"], ROLES[key])).toBe(false);
    }
  });
});
