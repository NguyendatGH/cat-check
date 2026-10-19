import { act, type ReactElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { MemoryRouter } from "react-router";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AdminAccessProvider, NO_ADMIN_CAPABILITIES, type AdminCapabilities } from "@/features/admin";
import { AdminMonitoringRulesPage } from "./AdminMonitoringRulesPage";
import { AdminPolicyVersionsPage } from "./AdminPolicyVersionsPage";
import { AdminPrivacyConsentsPage } from "./AdminPrivacyConsentsPage";
import { AdminScansPage } from "./AdminScansPage";
import { AdminSecurityPage } from "./AdminSecurityPage";


vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) => (options ? `${key}${JSON.stringify(options)}` : key),
    i18n: { language: "vi" },
  }),
}));
vi.mock("@/shared/api/acceptLanguage", () => ({ currentAcceptLanguage: () => "vi" }));

const ALL: AdminCapabilities = Object.fromEntries(
  Object.keys(NO_ADMIN_CAPABILITIES).map((key) => [key, true]),
) as unknown as AdminCapabilities;

const RULE = {
  code: "REPEATED_OUT_OF_RANGE",
  name: "Lặp lại ngoài khoảng",
  enabled: true,
  params: { windowHours: 48, sameDirection: true },
  severity: "ATTENTION",
  cooldownHours: 72,
  requiresCalibratedChart: true,
  messageKey: "insight.rule.repeated",
  pushEnabled: false,
  updatedAt: "2026-10-01T00:00:00Z",
  etag: 'W/"1-REPEATED_OUT_OF_RANGE"',
};

const INCIDENT = {
  id: "0b6f2a3e-1c2d-4e5f-8a9b-0c1d2e3f4a5b",
  publicRef: "INC-2026-0007",
  severity: "HIGH",
  category: "DATA_BREACH",
  summary: "Lộ tệp xuất",
  detectedAt: "2026-10-01T00:00:00Z",
  authorityDeadline: "2026-10-04T00:00:00Z",
  authorityNotificationOverdue: true,
  createdAt: "2026-10-01T00:05:00Z",
};

const POLICY = {
  id: "p1",
  policyType: "TERMS",
  version: "2.1.0",
  locale: "vi",
  title: "Điều khoản sử dụng",
  status: "DRAFT",
  requiresReconsent: false,
};

let routes: Partial<Record<string, { status: number; body: unknown }>> = {};
let mounted: { root: Root; container: HTMLElement } | undefined;

function okPage(items: unknown[]) {
  return {
    status: 200,
    body: { items, page: 0, size: 50, totalElements: items.length, totalPages: 1, hasMore: false },
  };
}

beforeEach(() => {
  routes = {
    "/api/v1/admin/monitoring-rules": { status: 200, body: { items: [RULE] } },
    "/api/v1/admin/security/incidents": okPage([INCIDENT]),
    "/api/v1/admin/policy-versions": okPage([POLICY]),
    "/api/v1/privacy/purposes": { status: 200, body: { items: [], limit: 50, hasMore: false } },
  };
  vi.stubGlobal(
    "fetch",
    vi.fn((input: string) => {
      const [path = ""] = input.split("?");
      const route = routes[path];
      return Promise.resolve(
        new Response(JSON.stringify(route?.body ?? {}), {
          status: route?.status ?? 404,
          headers: { "Content-Type": "application/json" },
        }),
      );
    }),
  );
});

afterEach(() => {
  act(() => {
    mounted?.root.unmount();
  });
  mounted?.container.remove();
  mounted = undefined;
  vi.unstubAllGlobals();
});

async function renderPage(page: ReactElement, capabilities: AdminCapabilities = ALL): Promise<string> {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  const container = document.createElement("div");
  document.body.append(container);
  const root = createRoot(container);
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  await act(async () => {
    root.render(
      <QueryClientProvider client={client}>
        <MemoryRouter>
          <AdminAccessProvider capabilities={capabilities}>{page}</AdminAccessProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
  for (let i = 0; i < 3; i += 1) {
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
  }
  mounted = { root, container };
  return container.textContent;
}

describe("5 trang quản trị mới", () => {
  it("không có provider (không vai trò) ⇒ mọi trang hiện thông báo không đủ quyền", async () => {
    for (const page of [
      <AdminScansPage key="s" />,
      <AdminMonitoringRulesPage key="r" />,
      <AdminSecurityPage key="x" />,
      <AdminPolicyVersionsPage key="p" />,
      <AdminPrivacyConsentsPage key="c" />,
    ]) {
      const text = await renderPage(page, NO_ADMIN_CAPABILITIES);
      expect(text).toContain("access.deniedTitle");
      act(() => {
        mounted?.root.unmount();
      });
    }
  });

  it("AdminMonitoringRulesPage: hiện rule và tham số; chỉ người có quyền ghi mới thấy nút Sửa", async () => {
    const text = await renderPage(<AdminMonitoringRulesPage />);
    expect(text).toContain("REPEATED_OUT_OF_RANGE");
    expect(text).toContain("windowHours=48");
    expect(text).toContain("rules.edit");
    expect(text).not.toContain("rules.readOnly");
  });

  it("AdminMonitoringRulesPage chỉ đọc: không có nút Sửa, có ghi chú chỉ xem", async () => {
    const text = await renderPage(<AdminMonitoringRulesPage />, {
      ...NO_ADMIN_CAPABILITIES,
      monitoringRulesRead: true,
    });
    expect(text).toContain("rules.readOnly");
    expect(text).toContain("rules.view");
    expect(text).not.toContain("rules.edit");
  });

  it("API trả 403 ⇒ thông báo không đủ quyền thay cho bảng", async () => {
    routes["/api/v1/admin/monitoring-rules"] = { status: 403, body: { detail: "x", errorCode: "ACCESS_DENIED" } };
    const text = await renderPage(<AdminMonitoringRulesPage />);
    expect(text).toContain("access.deniedTitle");
  });

  it("AdminSecurityPage: tab sự cố hiện hồ sơ quá hạn và nút mở hồ sơ cho DPO", async () => {
    const text = await renderPage(<AdminSecurityPage />);
    expect(text).toContain("INC-2026-0007");
    expect(text).toContain("security.overdue");
    expect(text).toContain("security.openIncident");
    expect(text).toContain("security.tabSessions");
  });

  it("AdminSecurityPage cho ADMIN_SUPER (đọc sự cố, thu hồi phiên, không ghi sự cố)", async () => {
    const text = await renderPage(<AdminSecurityPage />, {
      ...NO_ADMIN_CAPABILITIES,
      securityIncidentsRead: true,
      securitySessionsPurge: true,
    });
    expect(text).toContain("security.readOnly");
    expect(text).not.toContain("security.openIncident");
  });

  it("AdminPolicyVersionsPage: bản nháp có nút xuất bản khi có quyền ghi", async () => {
    const text = await renderPage(<AdminPolicyVersionsPage />);
    expect(text).toContain("2.1.0");
    expect(text).toContain("policies.publish");
  });

  it("AdminScansPage và AdminPrivacyConsentsPage chưa gọi API trước khi bấm Tra cứu", async () => {
    expect(await renderPage(<AdminScansPage />)).toContain("scans.idleTitle");
    act(() => {
      mounted?.root.unmount();
    });
    expect(await renderPage(<AdminPrivacyConsentsPage />)).toContain("consents.idleTitle");
    const calls = (fetch as unknown as { mock: { calls: [string][] } }).mock.calls.map(([url]) => url);
    expect(calls.some((url) => url.includes("/scans") || url.includes("/privacy/consents"))).toBe(false);
  });
});
