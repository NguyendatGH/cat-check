import { describe, expect, it } from "vitest";
import { ApiError } from "@/shared/api/errors";
import {
  classifyAdminError,
  computeCapabilities,
  filterNavGroups,
  findActiveNavItem,
  isAccessError,
  isNavItemActive,
  type AdminNavGroup,
  type AdminNavItemBase,
  type RoleCheck,
} from "./adminAccess";

/** Kiểm quyền giống `hasAnyRole` của shared/auth/roles.ts. */
function rolesCheck(userRoles: readonly string[]): RoleCheck {
  return (allowed) => userRoles.some((role) => allowed.includes(role));
}

const GROUPS: AdminNavGroup<AdminNavItemBase>[] = [
  {
    id: "overview",
    labelKey: "nav.groups.overview",
    items: [
      { id: "dashboard", to: "/admin", end: true, roles: ["ADMIN_SUPER", "DPO", "ADMIN_SUPPORT", "ADMIN_CATALOG"] },
    ],
  },
  {
    id: "catalog",
    labelKey: "nav.groups.catalog",
    items: [
      { id: "products", to: "/admin/products", roles: ["ADMIN_SUPER", "ADMIN_CATALOG"] },
      { id: "colorChart", to: "/admin/ph-color-chart", roles: ["ADMIN_SUPER", "ADMIN_CATALOG"] },
    ],
  },
  {
    id: "privacy",
    labelKey: "nav.groups.privacy",
    items: [
      { id: "consents", to: "/admin/privacy/consents", roles: ["DPO"] },
      {
        id: "incidents",
        to: "/admin/security",
        tab: "incidents",
        defaultTab: true,
        roles: ["ADMIN_SUPER", "DPO"],
      },
      { id: "sessions", to: "/admin/security", tab: "sessions", roles: ["ADMIN_SUPER"] },
    ],
  },
  {
    id: "community",
    labelKey: "nav.groups.community",
    items: [{ id: "reports", to: "/admin/community/reports", roles: ["ADMIN_SUPER", "MODERATOR"] }],
  },
];

describe("computeCapabilities", () => {
  it("biến bảng vai trò thành bảng boolean theo vai trò người dùng", () => {
    const table = { scans: ["ADMIN_SUPER", "DPO"], scanImage: ["DPO"], consentsRead: ["DPO"] } as const;
    expect(computeCapabilities(table, rolesCheck(["ADMIN_SUPER"]))).toEqual({
      scans: true,
      scanImage: false,
      consentsRead: false,
    });
    expect(computeCapabilities(table, rolesCheck(["DPO"]))).toEqual({
      scans: true,
      scanImage: true,
      consentsRead: true,
    });
    expect(computeCapabilities(table, rolesCheck([]))).toEqual({ scans: false, scanImage: false, consentsRead: false });
  });
});

describe("filterNavGroups", () => {
  const ids = (groups: AdminNavGroup<AdminNavItemBase>[]) =>
    groups.map((group) => `${group.id}:${group.items.map((item) => item.id).join(",")}`);

  it("ADMIN_CATALOG thấy danh mục, không thấy quyền riêng tư và cộng đồng", () => {
    expect(ids(filterNavGroups(GROUPS, rolesCheck(["ADMIN_CATALOG"])))).toEqual([
      "overview:dashboard",
      "catalog:products,colorChart",
    ]);
  });

  it("MODERATOR chỉ còn nhóm cộng đồng — nhóm rỗng bị bỏ hẳn", () => {
    expect(ids(filterNavGroups(GROUPS, rolesCheck(["MODERATOR"])))).toEqual(["community:reports"]);
  });

  it("DPO thấy consent và sự cố nhưng không thấy thu hồi phiên", () => {
    expect(ids(filterNavGroups(GROUPS, rolesCheck(["DPO"])))).toEqual([
      "overview:dashboard",
      "privacy:consents,incidents",
    ]);
  });

  it("nhiều vai trò thì hợp quyền", () => {
    expect(ids(filterNavGroups(GROUPS, rolesCheck(["DPO", "MODERATOR", "ADMIN_SUPER"])))).toEqual([
      "overview:dashboard",
      "catalog:products,colorChart",
      "privacy:consents,incidents,sessions",
      "community:reports",
    ]);
  });

  it("không có vai trò nào thì menu rỗng", () => {
    expect(filterNavGroups(GROUPS, rolesCheck(["USER"]))).toEqual([]);
  });
});

describe("isNavItemActive", () => {
  const dashboard = { id: "d", to: "/admin", end: true, roles: [] };
  const users = { id: "u", to: "/admin/users", roles: [] };
  const incidents = { id: "i", to: "/admin/security", tab: "incidents", defaultTab: true, roles: [] };
  const sessions = { id: "s", to: "/admin/security", tab: "sessions", roles: [] };

  it("`end` chỉ khớp đúng đường dẫn", () => {
    expect(isNavItemActive(dashboard, { pathname: "/admin", search: "" })).toBe(true);
    expect(isNavItemActive(dashboard, { pathname: "/admin/", search: "" })).toBe(true);
    expect(isNavItemActive(dashboard, { pathname: "/admin/users", search: "" })).toBe(false);
  });

  it("không `end` thì khớp cả trang con nhưng không khớp tiền tố lẫn", () => {
    expect(isNavItemActive(users, { pathname: "/admin/users/123", search: "" })).toBe(true);
    expect(isNavItemActive(users, { pathname: "/admin/users-x", search: "" })).toBe(false);
  });

  it("mục có `tab` khớp theo `?tab=`, không có `?tab=` thì mục mặc định được chọn", () => {
    expect(isNavItemActive(sessions, { pathname: "/admin/security", search: "?tab=sessions" })).toBe(true);
    expect(isNavItemActive(incidents, { pathname: "/admin/security", search: "?tab=sessions" })).toBe(false);
    expect(isNavItemActive(incidents, { pathname: "/admin/security", search: "" })).toBe(true);
    expect(isNavItemActive(sessions, { pathname: "/admin/security", search: "" })).toBe(false);
  });

  it("findActiveNavItem trả mục đầu tiên đang chọn", () => {
    const visible = filterNavGroups(GROUPS, rolesCheck(["ADMIN_SUPER"]));
    expect(findActiveNavItem(visible, { pathname: "/admin/security", search: "?tab=sessions" })?.id).toBe("sessions");
    expect(findActiveNavItem(visible, { pathname: "/admin/nowhere", search: "" })).toBeUndefined();
  });
});

describe("classifyAdminError", () => {
  it.each([
    [401, "unauthenticated"],
    [403, "forbidden"],
    [412, "stale"],
    [428, "preconditionRequired"],
    [409, "conflict"],
    [404, "notFound"],
    [410, "gone"],
    [400, "invalid"],
    [422, "invalid"],
    [500, "server"],
    [503, "server"],
    [418, "unknown"],
  ] as const)("HTTP %i ⇒ %s", (status, kind) => {
    expect(classifyAdminError(new ApiError("x", status))).toBe(kind);
  });

  it("fetch ném TypeError khi mất mạng ⇒ network", () => {
    expect(classifyAdminError(new TypeError("Failed to fetch"))).toBe("network");
  });

  it("lỗi lạ ⇒ unknown", () => {
    expect(classifyAdminError(new Error("x"))).toBe("unknown");
    expect(classifyAdminError("x")).toBe("unknown");
  });

  it("isAccessError chỉ đúng với 401/403", () => {
    expect(isAccessError(new ApiError("x", 401))).toBe(true);
    expect(isAccessError(new ApiError("x", 403))).toBe(true);
    expect(isAccessError(new ApiError("x", 412))).toBe(false);
    expect(isAccessError(new TypeError("x"))).toBe(false);
  });
});
