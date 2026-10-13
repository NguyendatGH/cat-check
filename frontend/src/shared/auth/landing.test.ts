import { describe, expect, it } from "vitest";
import { resolveRedirectTarget } from "./landing";

describe("redirect after login and MFA", () => {
  it("hands off to the role-aware guard when the session roles are unavailable", () => {
    expect(resolveRedirectTarget(null)).toBe("/");
    expect(resolveRedirectTarget("/dashboard")).toBe("/");
  });

  it("opens admin UI even when the login URL asks for the buyer dashboard", () => {
    expect(resolveRedirectTarget("/dashboard", ["USER", "ADMIN_SUPER"])).toBe("/admin");
    expect(resolveRedirectTarget(null, ["USER", "ADMIN_SUPER"])).toBe("/admin");
  });

  it("preserves deep links inside the admin portal", () => {
    expect(resolveRedirectTarget("/admin/system/jobs", ["USER", "ADMIN_SUPER"])).toBe("/admin/system/jobs");
  });

  it("opens seller UI for catalog staff and buyer UI for ordinary users", () => {
    expect(resolveRedirectTarget("/dashboard", ["USER", "ADMIN_CATALOG"])).toBe("/seller");
    expect(resolveRedirectTarget(null, ["USER"])).toBe("/dashboard");
    expect(resolveRedirectTarget("/cats", ["USER"])).toBe("/cats");
  });

  it.each(["//evil.example", "/\\evil.example", "https://evil.example"])(
    "does not navigate to an external destination: %s",
    (next) => {
      expect(resolveRedirectTarget(next, ["USER", "ADMIN_SUPER"])).toBe("/admin");
    },
  );
});
