import { act, type ReactElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { MemoryRouter, Route, Routes } from "react-router";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AdminAccessProvider, computeCapabilities, type AdminCapabilities } from "@/features/admin";
// Test đối chiếu với nguồn vai trò thật (giống cách AdminLayout tính); chỉ test được chạm `app`.
import { ROLES, hasAnyRole } from "@/shared/auth/roles";
import { GROUP1_CAPABILITIES } from "./group1Access";
import { AdminActivationCodesPage } from "./AdminActivationCodesPage";
import { AdminPackagesPage } from "./AdminPackagesPage";
import { AdminPrivacyRequestsPage } from "./AdminPrivacyRequestsPage";
import { AdminPrivacyRetentionPage } from "./AdminPrivacyRetentionPage";
import { AdminUserDetailPage } from "./AdminUserDetailPage";
import { AdminUsersPage } from "./AdminUsersPage";

/**
 * Nhóm trang người dùng, gói, mã kích hoạt, quyền riêng tư: nút ghi ẩn/hiện đúng theo vai
 * trò. Bảng khả năng dựng y như AdminLayout (`computeCapabilities(ROLES, …)`), nên test này
 * cũng bắt lỗi nếu khoá trong `group1Access` lệch khỏi `ROLES`.
 */

vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) => (options ? `${key}${JSON.stringify(options)}` : key),
    i18n: { language: "vi" },
  }),
}));
vi.mock("@/shared/api/acceptLanguage", () => ({ currentAcceptLanguage: () => "vi" }));

type Role = "ADMIN_SUPER" | "ADMIN_SUPPORT" | "ADMIN_CATALOG" | "DPO";

/** Bảng khả năng của một người chỉ có một vai trò — cùng công thức với AdminLayout. */
function capsFor(role: Role): AdminCapabilities {
  return computeCapabilities(ROLES, (allowed) => hasAnyRole([role], allowed));
}

const USER_ID = "7d0c1e7a-0000-4000-8000-000000000001";

const USER = {
  userId: USER_ID,
  emailMasked: "a***@example.com",
  fullName: "Người dùng thử",
  status: "ACTIVE",
  onboardingStatus: "DONE",
  emailVerified: true,
  createdAt: "2026-09-01T00:00:00Z",
  lastLoginAt: null,
  lockedUntil: null,
  catCount: 2,
  scanCount: 5,
  highestPackage: "STANDARD",
};

const DSAR = {
  id: "d1",
  publicRef: "DSAR-2026-0001",
  userId: USER_ID,
  contactEmailMasked: "a***@example.com",
  requestType: "ERASE",
  channel: "WEB_FORM",
  status: "RECEIVED",
  receivedAt: "2026-09-01T00:00:00Z",
  ackDueAt: "2026-09-04T00:00:00Z",
  ackSentAt: null,
  fulfilDueAt: "2026-09-30T00:00:00Z",
  extendedTo: null,
  extensionReason: null,
  thirdPartyInvolved: false,
  completedAt: null,
  rejectionReason: null,
  handledBy: null,
  createdAt: "2026-09-01T00:00:00Z",
};

function offsetPage(items: unknown[]) {
  return {
    status: 200,
    body: { items, number: 0, size: 20, totalElements: items.length, totalPages: 1, hasMore: false },
  };
}

let routes: Partial<Record<string, { status: number; body: unknown }>> = {};
let calls: string[] = [];
let mounted: { root: Root; container: HTMLElement } | undefined;

beforeEach(() => {
  calls = [];
  routes = {
    "/api/v1/admin/users": offsetPage([USER]),
    [`/api/v1/admin/users/${USER_ID}`]: {
      status: 200,
      body: {
        ...USER,
        phoneMasked: null,
        locale: "vi",
        timezone: null,
        emailVerifiedAt: null,
        deletionScheduledAt: null,
        processingRestricted: false,
        totpEnabled: false,
        activeSessionCount: 1,
        roles: [],
      },
    },
    [`/api/v1/admin/users/${USER_ID}/sessions`]: {
      status: 200,
      body: {
        items: [
          {
            id: "s1",
            deviceLabel: "Chrome",
            ipAddressMasked: "10.0.*.*",
            rememberMe: false,
            createdAt: "2026-09-01T00:00:00Z",
            lastSeenAt: "2026-09-02T00:00:00Z",
            expiresAt: "2026-10-02T00:00:00Z",
          },
        ],
        revokedSessions: 0,
      },
    },
    "/api/v1/admin/activation-codes": offsetPage([
      {
        id: "c1",
        codePrefix: "CC7Q",
        packageCode: "STANDARD",
        productionBatch: "LOT-01",
        issuedAt: "2026-09-01T00:00:00Z",
        validUntil: "2027-09-01T00:00:00Z",
        status: "ISSUED",
        redeemedBy: null,
        redeemedAt: null,
      },
    ]),
    "/api/v1/admin/activation-codes/batches": offsetPage([
      {
        batchId: "b1",
        productionBatch: "LOT-01",
        packageCode: "STANDARD",
        totalCodes: 10,
        issuedCodes: 10,
        redeemedCodes: 0,
        voidedCodes: 0,
        issuedAt: "2026-09-01T00:00:00Z",
        validUntil: "2027-09-01T00:00:00Z",
        csvAvailable: true,
      },
    ]),
    "/api/v1/admin/package-plans": {
      status: 200,
      body: [
        {
          code: "STANDARD",
          name: "Tiêu chuẩn",
          weightKg: 1,
          creditAmount: 30,
          creditValidityDays: 365,
          maxCatProfiles: 3,
          features: { history: "BASIC", trend: true, reminder: true, export: false, storeImage: false },
          active: true,
          version: 2,
          updatedAt: "2026-09-01T00:00:00Z",
          etag: 'W/"2"',
        },
      ],
    },
    "/api/v1/admin/privacy/requests": offsetPage([DSAR]),
    "/api/v1/admin/privacy/requests/d1": { status: 200, body: DSAR },
    "/api/v1/admin/privacy/retention-policies": {
      status: 200,
      body: [
        {
          code: "SCAN_IMAGE",
          dataInventoryCode: null,
          targetTable: "scan_image",
          retentionDays: 14,
          anchorColumn: "created_at",
          actionOnExpiry: "HARD_DELETE",
          jobName: null,
          safetyThresholdPercent: 20,
          enabled: true,
          legalBasis: null,
          updatedBy: null,
          createdAt: "2026-09-01T00:00:00Z",
        },
      ],
    },
    "/api/v1/privacy/data-inventory": { status: 200, body: { items: [], limit: 50, nextCursor: null, hasMore: false } },
    "/api/v1/privacy/purposes": { status: 200, body: { items: [], limit: 50, nextCursor: null, hasMore: false } },
  };
  vi.stubGlobal(
    "fetch",
    vi.fn((input: string) => {
      const [path = ""] = input.split("?");
      calls.push(path);
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

async function settle() {
  for (let i = 0; i < 4; i += 1) {
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
  }
}

async function renderPage(page: ReactElement, role: Role, path = "/"): Promise<HTMLElement> {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  const container = document.createElement("div");
  document.body.append(container);
  const root = createRoot(container);
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  await act(async () => {
    root.render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={[path]}>
          <AdminAccessProvider capabilities={capsFor(role)}>
            <Routes>
              <Route path="/admin/users/:userId" element={page} />
              <Route path="*" element={page} />
            </Routes>
          </AdminAccessProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
  await settle();
  mounted = { root, container };
  return container;
}

function text(container: HTMLElement): string {
  return container.textContent;
}

describe("group1Access", () => {
  it("mọi khoá của nhóm có trong ROLES và trong bảng khả năng AdminLayout tính", () => {
    const caps = computeCapabilities(ROLES, () => true) as Record<string, boolean>;
    for (const key of GROUP1_CAPABILITIES) {
      expect(ROLES).toHaveProperty(key);
      expect(caps[key]).toBe(true);
    }
  });
});

describe("AdminUsersPage", () => {
  it("ADMIN_SUPPORT chỉ xem: không có nút khoá, có ghi chú chỉ xem", async () => {
    const view = text(await renderPage(<AdminUsersPage />, "ADMIN_SUPPORT"));
    expect(view).toContain("Người dùng thử");
    expect(view).toContain("users.readOnlyNote");
    expect(view).not.toContain("users.lock");
  });

  it("ADMIN_SUPER thấy nút khoá", async () => {
    const view = text(await renderPage(<AdminUsersPage />, "ADMIN_SUPER"));
    expect(view).toContain("users.lock");
    expect(view).not.toContain("users.readOnlyNote");
  });
});

describe("AdminUserDetailPage", () => {
  it("DPO mở che được nhưng không xem/thu hồi phiên và không gọi API phiên", async () => {
    const view = text(await renderPage(<AdminUserDetailPage />, "DPO", `/admin/users/${USER_ID}`));
    expect(view).toContain("userDetail.unmask");
    expect(view).toContain("userDetail.sessionsReadOnly");
    expect(view).not.toContain("userDetail.revokeSessions");
    expect(calls).not.toContain(`/api/v1/admin/users/${USER_ID}/sessions`);
  });

  it("ADMIN_SUPER thấy bảng phiên và nút thu hồi", async () => {
    const view = text(await renderPage(<AdminUserDetailPage />, "ADMIN_SUPER", `/admin/users/${USER_ID}`));
    expect(view).toContain("userDetail.revokeSessions");
    expect(view).toContain("Chrome");
    expect(view).not.toContain("userDetail.sessionsReadOnly");
  });
});

describe("AdminActivationCodesPage", () => {
  it("ADMIN_SUPPORT chỉ tra cứu: không sinh lô, không vô hiệu, không tải CSV", async () => {
    const view = text(await renderPage(<AdminActivationCodesPage />, "ADMIN_SUPPORT"));
    expect(view).toContain("CC7Q");
    expect(view).toContain("activation.readOnlyNote");
    for (const key of ["activation.issueButton", "activation.void", "activation.voidBatch", "activation.downloadCsv"]) {
      expect(view).not.toContain(key);
    }
  });

  it("ADMIN_SUPER có đủ nút ghi", async () => {
    const view = text(await renderPage(<AdminActivationCodesPage />, "ADMIN_SUPER"));
    for (const key of ["activation.issueButton", "activation.void", "activation.voidBatch", "activation.downloadCsv"]) {
      expect(view).toContain(key);
    }
  });
});

describe("AdminPackagesPage", () => {
  it("ADMIN_CATALOG chỉ xem gói, không có nút Sửa", async () => {
    const view = text(await renderPage(<AdminPackagesPage />, "ADMIN_CATALOG"));
    expect(view).toContain("Tiêu chuẩn");
    expect(view).toContain("packageAdmin.readOnlyNote");
    expect(view).not.toContain("actions.edit");
  });

  it("ADMIN_SUPER có nút Sửa", async () => {
    const view = text(await renderPage(<AdminPackagesPage />, "ADMIN_SUPER"));
    expect(view).toContain("actions.edit");
  });
});

describe("AdminPrivacyRequestsPage", () => {
  it("ADMIN_SUPPORT không tạo được DSAR nhưng vẫn chuyển trạng thái được", async () => {
    const container = await renderPage(<AdminPrivacyRequestsPage />, "ADMIN_SUPPORT");
    expect(text(container)).not.toContain("pages.privacyRequests.createTitle");
    const open = [...container.querySelectorAll("button")].find((b) => b.textContent === "DSAR-2026-0001");
    expect(open).toBeDefined();
    await act(async () => {
      open?.click();
      await Promise.resolve();
    });
    await settle();
    expect(text(container)).toContain("pages.privacyRequests.apply");
  });

  it("DPO thấy khối tạo DSAR", async () => {
    const view = text(await renderPage(<AdminPrivacyRequestsPage />, "DPO"));
    expect(view).toContain("pages.privacyRequests.createTitle");
  });
});

describe("AdminPrivacyRetentionPage", () => {
  it("ADMIN_SUPER chỉ dry-run, không sửa chính sách", async () => {
    const view = text(await renderPage(<AdminPrivacyRetentionPage />, "ADMIN_SUPER"));
    expect(view).toContain("SCAN_IMAGE");
    expect(view).toContain("actions.dryRun");
    expect(view).not.toContain("retention.edit");
  });

  it("DPO sửa được chính sách", async () => {
    const view = text(await renderPage(<AdminPrivacyRetentionPage />, "DPO"));
    expect(view).toContain("retention.edit");
    expect(view).toContain("actions.dryRun");
  });
});
