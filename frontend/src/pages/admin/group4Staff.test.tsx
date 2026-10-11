import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { MemoryRouter } from "react-router";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
// Test đối chiếu với nguồn vai trò thật, dựng bảng khả năng y như AdminLayout; chỉ test được chạm `app`.
import { ROLES, hasAnyRole } from "@/shared/auth/roles";
import { useSessionStore } from "@/entities/user";
import {
  AdminAccessProvider,
  NO_ADMIN_CAPABILITIES,
  computeCapabilities,
  type AdminCapabilities,
} from "@/features/admin";
import adminViRaw from "@/shared/i18n/locales/vi/admin.json?raw";
import staffSource from "./AdminStaffPage.tsx?raw";
import meSecuritySource from "./AdminMeSecurityPage.tsx?raw";
import setup2faSource from "./AdminSetup2faPage.tsx?raw";
import { AdminStaffPage } from "./AdminStaffPage";

/**
 * Nhóm 4 — trang nhân sự: nút ghi theo `ROLES`, duyệt reset TOTP chỉ cho DPO và chặn tự duyệt
 * (quy tắc hai người), khoá i18n của ba trang đều có nghĩa.
 */

vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) => (options ? `${key}${JSON.stringify(options)}` : key),
    i18n: { language: "vi" },
  }),
}));
vi.mock("@/shared/api/acceptLanguage", () => ({ currentAcceptLanguage: () => "vi" }));

const SELF_ID = "11111111-1111-4111-8111-111111111111";
const OTHER_ID = "22222222-2222-4222-8222-222222222222";
const TARGET_ID = "33333333-3333-4333-8333-333333333333";
const FUTURE = new Date(Date.now() + 86_400_000).toISOString();
const PAST = new Date(Date.now() - 86_400_000).toISOString();

function request(id: string, requestedBy: string, expiresAt: string) {
  return {
    id,
    targetUserId: TARGET_ID,
    requestedBy,
    requestedAt: "2026-10-10T08:00:00Z",
    reason: "Mất điện thoại xác thực",
    status: "PENDING",
    expiresAt,
    // approvedBy/approvedAt null bị server bỏ khỏi JSON (non_null).
  };
}

const QUEUE = [
  request("req-other", OTHER_ID, FUTURE),
  request("req-self", SELF_ID, FUTURE),
  request("req-expired", OTHER_ID, PAST),
];

let mounted: { root: Root; container: HTMLElement } | undefined;
let fetchMock: ReturnType<typeof vi.fn>;

function capabilitiesFor(roles: readonly string[]): AdminCapabilities {
  return computeCapabilities(ROLES, (allowed) => hasAnyRole(roles, allowed));
}

function fetchCalls(): { url: string; method: string }[] {
  return (fetchMock.mock.calls as [string, RequestInit | undefined][]).map(([url, init]) => ({
    url,
    method: init?.method ?? "GET",
  }));
}

beforeEach(() => {
  fetchMock = vi.fn((input: string) => {
    const [path = ""] = input.split("?");
    const body = path === "/api/v1/admin/totp-reset-requests" ? QUEUE : QUEUE[0];
    return Promise.resolve(
      new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } }),
    );
  });
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  act(() => {
    mounted?.root.unmount();
  });
  mounted?.container.remove();
  mounted = undefined;
  useSessionStore.setState({ user: null, status: "anonymous" });
  vi.unstubAllGlobals();
});

async function flush(): Promise<void> {
  for (let i = 0; i < 4; i += 1) {
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
  }
}

async function renderAs(roles: readonly string[] | null): Promise<HTMLElement> {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  useSessionStore.setState({
    status: "authenticated",
    user: {
      id: SELF_ID,
      email: "staff@example.test",
      displayName: "Staff",
      roles: [],
      onboarding: { completed: true, nextStep: null },
    },
  });
  const container = document.createElement("div");
  document.body.append(container);
  const root = createRoot(container);
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const capabilities = roles === null ? NO_ADMIN_CAPABILITIES : capabilitiesFor(roles);
  await act(async () => {
    root.render(
      <QueryClientProvider client={client}>
        <MemoryRouter>
          <AdminAccessProvider capabilities={capabilities}>
            <AdminStaffPage />
          </AdminAccessProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
  await flush();
  mounted = { root, container };
  return container;
}

function buttonsLabelled(container: HTMLElement, key: string): HTMLButtonElement[] {
  return [...container.querySelectorAll("button")].filter((button) => button.textContent.trim() === key);
}

describe("ROLES cho trang nhân sự (khớp backend AdminStaffController)", () => {
  it("duyệt reset TOTP chỉ DPO; gán/thu vai trò và tạo yêu cầu chỉ ADMIN_SUPER; xem hàng đợi cả hai", () => {
    expect([...ROLES.totpResetApprove]).toEqual(["DPO"]);
    expect([...ROLES.staffRolesWrite]).toEqual(["ADMIN_SUPER"]);
    expect([...ROLES.totpResetRequestsRead].sort()).toEqual(["ADMIN_SUPER", "DPO"]);
    expect([...ROLES.staff].sort()).toEqual(["ADMIN_SUPER", "DPO"]);
  });
});

describe("AdminStaffPage — phân quyền", () => {
  it("DPO thấy nút duyệt cho yêu cầu của người khác, không thấy nút gán/thu vai trò", async () => {
    const container = await renderAs(["DPO"]);
    const approve = buttonsLabelled(container, "pages.staff.approve");
    expect(approve).toHaveLength(1);
    expect(approve[0]?.disabled).toBe(false);
    expect(container.textContent).toContain("pages.staff.rolesReadOnly");
    expect(container.textContent).not.toContain("pages.staff.approveReadOnly");
    expect(buttonsLabelled(container, "pages.staff.grant")).toHaveLength(0);
    expect(buttonsLabelled(container, "pages.staff.revoke")).toHaveLength(0);
    expect(buttonsLabelled(container, "pages.staff.requestReset")).toHaveLength(0);
  });

  it("DPO bấm duyệt ⇒ POST approve đúng yêu cầu", async () => {
    const container = await renderAs(["DPO"]);
    const [approve] = buttonsLabelled(container, "pages.staff.approve");
    expect(approve).toBeDefined();
    await act(async () => {
      approve.click();
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    expect(fetchCalls()).toContainEqual({
      url: "/api/v1/admin/totp-reset-requests/req-other/approve",
      method: "POST",
    });
  });

  it("quy tắc hai người: yêu cầu do chính mình tạo không có nút duyệt; yêu cầu hết hạn cũng không", async () => {
    const container = await renderAs(["DPO", "ADMIN_SUPER"]);
    expect(buttonsLabelled(container, "pages.staff.approve")).toHaveLength(1);
    expect(container.textContent).toContain("pages.staff.selfRequest");
    expect(container.textContent).toContain("pages.staff.expired");
  });

  it("ADMIN_SUPER chỉ xem hàng đợi, không có nút duyệt; có nút gán/thu vai trò và tạo yêu cầu", async () => {
    const container = await renderAs(["ADMIN_SUPER"]);
    expect(buttonsLabelled(container, "pages.staff.approve")).toHaveLength(0);
    expect(container.textContent).not.toContain("pages.staff.action");
    expect(container.textContent).toContain("pages.staff.approveReadOnly");
    expect(container.querySelectorAll("tbody tr")).toHaveLength(QUEUE.length);
    expect(buttonsLabelled(container, "pages.staff.grant")).toHaveLength(1);
    expect(buttonsLabelled(container, "pages.staff.revoke")).toHaveLength(1);
    expect(buttonsLabelled(container, "pages.staff.requestReset")).toHaveLength(1);
  });

  it("vai trò ngoài ROLES.staff (ADMIN_SUPPORT) hoặc thiếu provider ⇒ không đủ quyền, không gọi API", async () => {
    for (const roles of [["ADMIN_SUPPORT"], null]) {
      const container = await renderAs(roles);
      expect(container.textContent).toContain("access.deniedTitle");
      expect(container.querySelectorAll("button")).toHaveLength(0);
      act(() => {
        mounted?.root.unmount();
      });
    }
    expect(fetchCalls()).toHaveLength(0);
  });
});

/** Khoá mới chờ coordinator gộp từ scratchpad `i18n-group4.json` vào `admin.json`. */
const PENDING_GROUP4_KEYS = new Set([
  "pages.staff.rolesReadOnly",
  "pages.staff.approveReadOnly",
  "pages.staff.expiresAt",
  "pages.staff.selfRequest",
  "pages.staff.expired",
]);

function lookup(tree: unknown, key: string): unknown {
  return key.split(".").reduce<unknown>((node, part) => {
    return node !== null && typeof node === "object" ? (node as Record<string, unknown>)[part] : undefined;
  }, tree);
}

describe("i18n nhóm 4", () => {
  const adminVi: unknown = JSON.parse(adminViRaw);
  it.each([
    ["AdminStaffPage", staffSource],
    ["AdminMeSecurityPage", meSecuritySource],
    ["AdminSetup2faPage", setup2faSource],
  ])("%s: mọi khoá t() có nghĩa trong admin.json", (_name, source) => {
    const keys = [...source.matchAll(/\bt\(\s*"([^"]+)"/g)].map((match) => match[1]);
    expect(keys.length).toBeGreaterThan(0);
    const missing = keys.filter((key) => typeof lookup(adminVi, key) !== "string" && !PENDING_GROUP4_KEYS.has(key));
    expect(missing).toEqual([]);
  });
});
