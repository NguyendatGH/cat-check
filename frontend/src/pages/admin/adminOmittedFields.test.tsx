import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useSessionStore, type SessionUser } from "@/entities/user";
import { AdminAccessProvider, computeCapabilities } from "@/features/admin";
import { ROLES } from "@/shared/auth/roles";
import { AdminContentPage } from "./AdminContentPage";
import { AdminJobsPage } from "./AdminJobsPage";
import { AdminNotificationsOutboxPage } from "./AdminNotificationsOutboxPage";
import { AdminPackagesPage } from "./AdminPackagesPage";
import { AdminPrivacyRetentionPage } from "./AdminPrivacyRetentionPage";
import { AdminProductsPage } from "./AdminProductsPage";
import { AdminUserDetailPage } from "./AdminUserDetailPage";
import { AdminUsersPage } from "./AdminUsersPage";

/**
 * Backend chạy `default-property-inclusion: non_null` ⇒ field null KHÔNG có trong JSON. Mọi
 * fixture dưới đây là JSON "như server gửi": field null bị BỎ HẲN (không phải `null`), nên chạy
 * qua `unknown` thay vì khai `null` như type viết tay.
 */

const hooks = vi.hoisted((): { data: Record<string, unknown>; mutate: ReturnType<typeof vi.fn> } => ({
  data: {},
  mutate: vi.fn(),
}));

vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) => (options ? `${key}${JSON.stringify(options)}` : key),
    i18n: { language: "vi" },
  }),
}));
vi.mock("react-router", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-router")>()),
  useParams: () => ({ userId: "u1" }),
}));
// Mọi hook `use*` của feature admin mặc định là query/mutation rỗng; từng test ghi đè hook nó cần.
vi.mock("@/features/admin", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/features/admin")>();
  const idle = {
    isPending: false,
    isError: false,
    isSuccess: false,
    data: undefined,
    error: null,
    mutate: hooks.mutate,
    mutateAsync: vi.fn(),
    reset: vi.fn(),
  };
  const patched: Record<string, unknown> = { ...actual };
  for (const [name, value] of Object.entries(actual)) {
    // `useAdminCan` không mock: trang đọc quyền từ AdminAccessProvider thật (xem renderPage).
    if (/^use[A-Z]/.test(name) && typeof value === "function" && name !== "useAdminCan") {
      patched[name] = () => (name in hooks.data ? { ...idle, data: hooks.data[name] } : idle);
    }
  }
  return patched;
});

let mounted: { root: Root; container: HTMLElement } | undefined;

function renderPage(page: React.ReactElement): string {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  // Đăng nhập với mọi vai trò để các trang kiểm quyền bằng session (nhóm vận hành) đều hiện nội dung.
  useSessionStore.getState().setSession({
    id: "u1",
    email: "admin@example.com",
    displayName: "Admin",
    roles: ["ADMIN_SUPER", "ADMIN_SUPPORT", "ADMIN_CATALOG", "DPO", "MODERATOR"],
  } as unknown as SessionUser);
  const container = document.createElement("div");
  document.body.append(container);
  const root = createRoot(container);
  act(() => {
    root.render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <AdminAccessProvider capabilities={computeCapabilities(ROLES, () => true)}>{page}</AdminAccessProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    );
  });
  mounted = { root, container };
  return container.textContent;
}

function clickButton(name: string, index = 0): void {
  const buttons = Array.from(mounted?.container.querySelectorAll("button") ?? []).filter(
    (button) => button.textContent === name,
  );
  act(() => {
    buttons[index].click();
  });
}

function inputOfLabel(labelText: string): HTMLInputElement {
  const label = Array.from(mounted?.container.querySelectorAll("label") ?? []).find((item) =>
    item.textContent.includes(labelText),
  );
  const input = label?.querySelector("input");
  if (!input) throw new Error(`Không thấy ô nhập có nhãn ${labelText}`);
  return input;
}

describe("trang admin với field null bị server bỏ khỏi JSON", () => {
  beforeEach(() => {
    hooks.data = {};
    hooks.mutate.mockClear();
  });

  afterEach(() => {
    act(() => {
      mounted?.root.unmount();
    });
    mounted?.container.remove();
    mounted = undefined;
  });

  it("AdminUsersPage: tài khoản chưa từng đăng nhập (không có lastLoginAt) không làm sập trang", () => {
    hooks.data.useAdminUsers = {
      items: [
        {
          userId: "u1",
          emailMasked: "a***@x.vn",
          fullName: "An",
          status: "ACTIVE",
          onboardingStatus: "DONE",
          emailVerified: true,
          createdAt: "2026-10-01T00:00:00Z",
          catCount: 0,
          scanCount: 0,
        },
      ],
      page: 0,
      size: 20,
      total: 1,
    };
    expect(renderPage(<AdminUsersPage />)).toContain("An");
  });

  it("AdminJobsPage: job đang chạy (không finishedAt/durationMs) hiện dấu gạch, không sập, không in 'undefined ms'", () => {
    hooks.data.useAdminJobRuns = {
      items: [
        {
          id: "r1",
          jobName: "expire-credit",
          status: "RUNNING",
          startedAt: "2026-10-10T01:00:00Z",
        },
      ],
      page: 0,
      size: 20,
      total: 1,
    };
    const text = renderPage(<AdminJobsPage />);
    expect(text).toContain("expire-credit");
    expect(text).not.toContain("undefined");
  });

  it("AdminNotificationsOutboxPage: dòng đã gửi (không nextAttemptAt) không làm sập trang", () => {
    hooks.data.useAdminOutbox = {
      items: [
        {
          id: "o1",
          channel: "EMAIL",
          status: "SENT",
          reference: "ref-1",
          recipientMasked: "a***@x.vn",
          attempts: 1,
          sentAt: "2026-10-10T01:00:00Z",
          createdAt: "2026-10-10T00:59:00Z",
        },
      ],
      page: 0,
      size: 20,
      total: 1,
    };
    expect(renderPage(<AdminNotificationsOutboxPage />)).toContain("ref-1");
  });

  it("AdminUserDetailPage: người dùng chưa đăng nhập / phiên chưa có lastSeenAt không làm sập trang", () => {
    hooks.data.useAdminUserDetail = {
      userId: "u1",
      emailMasked: "a***@x.vn",
      fullName: "An",
      status: "ACTIVE",
      onboardingStatus: "DONE",
      emailVerified: true,
      createdAt: "2026-10-01T00:00:00Z",
      catCount: 0,
      scanCount: 0,
      processingRestricted: false,
      totpEnabled: false,
      activeSessionCount: 0,
      roles: [],
    };
    expect(renderPage(<AdminUserDetailPage />)).toContain("An");
  });

  it("AdminContentPage: bài có claimType nhưng chưa có sourceReference hiện cảnh báo thiếu nguồn, không sập", () => {
    hooks.data.useAdminCareTips = {
      items: [
        {
          id: "t1",
          slug: "uong-nuoc",
          translationGroupId: "g1",
          locale: "vi",
          kind: "ARTICLE",
          title: "Uống nước",
          status: "DRAFT",
          claimType: "HEALTH_CLAIM",
          createdAt: "2026-10-01T00:00:00Z",
          updatedAt: "2026-10-01T00:00:00Z",
        },
      ],
      page: 0,
      size: 20,
      total: 1,
    };
    const text = renderPage(<AdminContentPage />);
    expect(text).toContain("Uống nước");
    expect(text).toContain("content.sourceMissingShort");
  });

  it("AdminProductsPage: sản phẩm không có giá gốc không in giá gạch ngang 'undefined'", () => {
    hooks.data.useAdminProducts = [
      {
        id: "p1",
        sku: "PAD-01",
        name: "Cát vệ sinh",
        description: "Mô tả",
        priceVnd: 100000,
        stockQuantity: 5,
        status: "PUBLISHED",
        updatedAt: "2026-10-01T00:00:00Z",
      },
    ];
    renderPage(<AdminProductsPage />);
    expect(mounted?.container.querySelector(".line-through")).toBeNull();
  });

  it("AdminPackagesPage: gói không giới hạn số mèo (không có maxCatProfiles) mở form với ô 'không giới hạn' được tích", () => {
    hooks.data.useAdminPackagePlans = [
      {
        code: "PREMIUM",
        name: "Premium",
        weightKg: 1,
        creditAmount: 100,
        creditValidityDays: 365,
        features: { history: "ADVANCED", trend: true, reminder: true, export: true, storeImage: true },
        active: true,
        version: 1,
        updatedAt: "2026-10-01T00:00:00Z",
        etag: "e1",
      },
    ];
    renderPage(<AdminPackagesPage />);
    clickButton("actions.edit");
    expect(inputOfLabel("packageAdmin.unlimited").checked).toBe(true);
  });

  it("AdminPrivacyRetentionPage: chính sách giữ vô hạn (không có retentionDays) gửi retentionDays = null, không phải NaN", () => {
    hooks.data.useAdminRetentionPolicies = [
      {
        code: "RP1",
        targetTable: "scan",
        anchorColumn: "created_at",
        actionOnExpiry: "ANONYMIZE",
        safetyThresholdPercent: 10,
        enabled: true,
        createdAt: "2026-10-01T00:00:00Z",
      },
    ];
    renderPage(<AdminPrivacyRetentionPage />);
    clickButton("retention.edit");
    clickButton("retention.save");
    expect(hooks.mutate).toHaveBeenCalledTimes(1);
    const call = hooks.mutate.mock.calls[0][0] as { payload: { retentionDays: number | null } };
    expect(call.payload.retentionDays).toBeNull();
  });
});

describe("AdminProductsPage — If-Match khi sửa sản phẩm", () => {
  beforeEach(() => {
    hooks.data = {};
    hooks.mutate.mockClear();
  });

  afterEach(() => {
    act(() => {
      mounted?.root.unmount();
    });
    mounted?.container.remove();
    mounted = undefined;
  });

  it('PUT gửi etag dạng W/"<updatedAt epoch ms>-<id>" của bản đã tải (backend AdminETag.of → 428 nếu thiếu)', () => {
    hooks.data.useAdminProducts = [
      {
        id: "p1",
        sku: "PAD-01",
        name: "Cát vệ sinh",
        description: "Mô tả",
        priceVnd: 100000,
        stockQuantity: 5,
        status: "DRAFT",
        updatedAt: "2026-10-01T03:04:05.678901Z",
      },
    ];
    renderPage(<AdminProductsPage />);
    clickButton("actions.edit");
    clickButton("productAdmin.save");
    expect(hooks.mutate).toHaveBeenCalledTimes(1);
    const call = hooks.mutate.mock.calls[0][0] as { id: string; etag: string };
    expect(call.id).toBe("p1");
    expect(call.etag).toBe(`W/"${String(Date.UTC(2026, 9, 1, 3, 4, 5, 678))}-p1"`);
  });
});
