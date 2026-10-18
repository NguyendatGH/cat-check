import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router";
import i18next from "i18next";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useSessionStore, type SessionUser } from "@/entities/user";
import type { PhBand } from "@/entities/ph-bands";
import { AdminAccessProvider, computeCapabilities, type ColorChartDetail, type ColorChartSummary } from "@/features/admin";
import { hasAnyRole, ROLES } from "@/shared/auth/roles";
import adminVi from "@/shared/i18n/locales/vi/admin.json";
import { AdminPhColorChartPage } from "./AdminPhColorChartPage";
import { BandsSection } from "./phBandsSection";
import { bandRange, findActiveConflict, formatPhShort, isUsedByScans, publishChecks } from "./phChartModel";

const i18n = i18next.createInstance();
void i18n.init({
  lng: "vi",
  resources: { vi: { admin: adminVi } },
  interpolation: { escapeValue: false },
  initAsync: false,
});
const tVi = i18n.getFixedT("vi", "admin");

const m = vi.hoisted(() => ({
  list: [] as unknown[],
  details: new Map<string, unknown>(),
  bands: [] as unknown[],
  cloneMutate: vi.fn(),
  replaceMutate: vi.fn(),
  publishMutate: vi.fn(),
}));

vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({ t: tVi, i18n: { language: "vi" } }),
}));

vi.mock("@/features/admin", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/features/admin")>();
  const idle = {
    isPending: false,
    isError: false,
    isSuccess: false,
    data: undefined,
    error: null,
    mutate: vi.fn(),
    reset: vi.fn(),
  };
  const page = (items: unknown[]) => ({
    ...idle,
    data: { items, number: 0, size: 20, totalElements: items.length, totalPages: 1, hasMore: false },
  });
  return {
    ...actual,
    useAdminColorCharts: (status: string) =>
      page(
        status === "" ? m.list : m.list.filter((item) => (item as { status: string }).status === status),
      ),
    useAdminColorChart: (id: string) => ({ ...idle, data: { data: m.details.get(id), etag: '"1"' } }),
    useCreateColorChart: () => idle,
    useUpdateColorChart: () => idle,
    useReplaceChartPoints: () => ({ ...idle, mutate: m.replaceMutate }),
    usePublishColorChart: () => ({ ...idle, mutate: m.publishMutate }),
    useCloneColorChart: () => ({ ...idle, mutate: m.cloneMutate }),
    useAdminPhBands: () => ({ ...idle, data: { data: m.bands, etag: '"b"' } }),
    useUpdatePhBand: () => idle,
  };
});

/** Ghép mã màu lúc chạy để không hard-code hex trong .tsx. */
const hx = (digits: string) => `#${digits}`;

function point(id: string, phValue: number, hex: string, sortOrder: number) {
  return {
    id,
    phValue,
    sortOrder,
    labL: 50,
    labA: 10,
    labB: 20,
    toleranceDeltaE: 5,
    hexSrgb: null,
    displayHex: hex,
    displayNameVi: `Màu ${String(sortOrder)}`,
    displayNameEn: null,
  };
}

const activeChart: ColorChartDetail = {
  id: "c-active",
  code: "CHART-PLACEHOLDER",
  version: 1,
  name: "Bảng tạm",
  productLine: "STANDARD",
  productionBatch: null,
  status: "ACTIVE",
  placeholder: true,
  source: "MANUAL_HEX",
  points: [
    point("p1", 6.0, hx("F2C14E"), 1),
    point("p2", 6.3, hx("E59F3A"), 2),
    point("p3", 6.6, hx("8DB255"), 3),
    point("p4", 7.0, hx("3F8F6B"), 4),
  ],
};

const draftChart: ColorChartDetail = {
  id: "c-draft",
  code: "CHART-NEW",
  version: 2,
  name: "Bảng mới",
  productLine: "STANDARD",
  productionBatch: "BATCH-26B",
  status: "DRAFT",
  placeholder: false,
  source: "CALIBRATED",
  points: [point("d1", 6.3, hx("E59F3A"), 1)],
};

function summary(chart: ColorChartDetail): ColorChartSummary {
  return {
    id: chart.id,
    code: chart.code,
    version: chart.version,
    name: chart.name,
    productLine: chart.productLine,
    productionBatch: chart.productionBatch,
    status: chart.status,
    placeholder: chart.placeholder,
    source: chart.source,
  };
}

let mounted: Root | undefined;

function render(node: React.ReactElement): void {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  useSessionStore.getState().setSession({
    id: "u1",
    email: "admin@example.com",
    displayName: "Admin",
    roles: ["ADMIN_SUPER"],
  } as unknown as SessionUser);
  const container = document.createElement("div");
  document.body.append(container);
  const root = createRoot(container);
  act(() => {
    root.render(
      <QueryClientProvider client={new QueryClient()}>
        <AdminAccessProvider capabilities={computeCapabilities(ROLES, (allowed) => hasAnyRole(["ADMIN_SUPER"], allowed))}>
          <MemoryRouter>{node}</MemoryRouter>
        </AdminAccessProvider>
      </QueryClientProvider>,
    );
  });
  mounted = root;
}

function buttons(label: string): HTMLButtonElement[] {
  return Array.from(document.body.querySelectorAll("button")).filter((el) => el.textContent.trim() === label);
}

function click(el: HTMLElement | undefined): void {
  expect(el).toBeDefined();
  act(() => {
    el?.click();
  });
}

function typeReason(value: string): void {
  const textarea = document.body.querySelector<HTMLTextAreaElement>("[role=dialog] textarea");
  expect(textarea).not.toBeNull();
  act(() => {
    Reflect.set(HTMLTextAreaElement.prototype, "value", value, textarea);
    textarea?.dispatchEvent(new Event("input", { bubbles: true }));
  });
}

describe("AdminPhColorChartPage", () => {
  beforeEach(() => {
    m.list = [summary(activeChart), summary(draftChart)];
    m.details = new Map<string, unknown>([
      [activeChart.id, activeChart],
      [draftChart.id, draftChart],
    ]);
    m.cloneMutate.mockReset();
    m.replaceMutate.mockReset();
    m.publishMutate.mockReset();
  });

  afterEach(() => {
    act(() => {
      mounted?.unmount();
    });
    mounted = undefined;
    document.body.innerHTML = "";
    useSessionStore.getState().setSession(null);
  });

  it("hiện nhãn dễ hiểu thay cho mã thô và đánh dấu bảng máy quét đang dùng", () => {
    render(<AdminPhColorChartPage />);
    const text = document.body.textContent;
    expect(text).toContain("Bảng màu pH là gì?");
    expect(text).toContain("Tiêu chuẩn");
    expect(text).toContain("Nhập mã màu thủ công");
    expect(text).toContain("Hiệu chuẩn từ ảnh chụp mẫu");
    expect(text).toContain("Mặc định (không gắn lô)");
    expect(text).toContain("Máy quét đang dùng");
    expect(text).toContain("Bảng tạm, chưa hiệu chuẩn thật");
    expect(text).toContain("Máy quét đang đọc pH bằng bảng");
    expect(text).not.toMatch(/\bMANUAL_HEX\b|\bCALIBRATED\b|\bSTANDARD\b/);
    // Dải màu từng dòng có nhãn pH.
    expect(text).toContain("6,3");
    expect(document.body.querySelectorAll("[aria-label='Các ô màu và giá trị pH tương ứng']").length).toBeGreaterThan(0);
  });

  it("bảng đang dùng chỉ xem được, có nút tạo bản nháp hỏi lý do trong hộp thoại", () => {
    render(<AdminPhColorChartPage />);
    click(buttons("Xem").at(-1));
    expect(document.body.querySelectorAll("input:disabled").length).toBe(0);
    expect(document.body.querySelector("textarea")).toBeNull();
    click(buttons("Tạo bản nháp từ bảng này")[0]);
    typeReason("Chỉnh lại màu ô 6,6 theo mẫu in mới");
    const confirm = Array.from(document.body.querySelectorAll<HTMLButtonElement>("[role=dialog] button[type=submit]"))[0];
    click(confirm);
    expect(m.cloneMutate).toHaveBeenCalledWith(
      { source: activeChart, reason: "Chỉnh lại màu ô 6,6 theo mẫu in mới" },
      expect.anything(),
    );
  });

  it("bản nháp sửa được, lý do chỉ hỏi khi lưu và chặn xuất bản khi thiếu mức", () => {
    render(<AdminPhColorChartPage />);
    click(buttons("Mở để sửa")[0]);
    expect(document.body.querySelector("textarea")).toBeNull();
    expect(buttons("Xuất bản bảng màu")[0]?.disabled).toBe(true);
    click(buttons("Lưu toàn bộ mức")[0]);
    expect(document.body.querySelector("[role=dialog] textarea")).not.toBeNull();
  });
});

const bands: PhBand[] = [
  {
    code: "SLIGHTLY_LOW",
    phMin: 6.0,
    phMax: 6.3,
    minInclusive: true,
    maxInclusive: false,
    severity: "ATTENTION",
    label: "Hơi thấp",
    description: "",
    colorToken: "color-ph-mild",
    iconName: "arrow-down-circle",
    sortOrder: 2,
    triggersAlert: true,
  },
  {
    code: "IN_RANGE",
    phMin: 6.3,
    phMax: 6.6,
    minInclusive: true,
    maxInclusive: true,
    severity: "NORMAL",
    label: "Trong khoảng tham chiếu",
    description: "",
    colorToken: "color-ph-normal",
    iconName: "check-circle",
    sortOrder: 3,
    triggersAlert: false,
  },
  {
    code: "INCONCLUSIVE",
    phMin: null,
    phMax: null,
    minInclusive: true,
    maxInclusive: true,
    severity: "NEUTRAL",
    label: "Chưa đủ dữ liệu",
    description: "",
    colorToken: "color-ph-unknown",
    iconName: "help-circle",
    sortOrder: 6,
    triggersAlert: false,
  },
];

describe("BandsSection", () => {
  afterEach(() => {
    act(() => {
      mounted?.unmount();
    });
    mounted = undefined;
    document.body.innerHTML = "";
    useSessionStore.getState().setSession(null);
  });

  it("hiện thang pH và khoảng bằng lời, không lộ mã dải/token", () => {
    m.bands = bands;
    render(<BandsSection />);
    const text = document.body.textContent;
    expect(document.body.querySelector("figure")).not.toBeNull();
    expect(text).toContain("Từ 6,0 đến dưới 6,3");
    expect(text).toContain("Từ 6,3 đến 6,6");
    expect(text).toContain("Không có khoảng pH");
    expect(text).toContain("Lệch nhẹ");
    expect(text).toContain("Có nhắc người dùng");
    expect(text).not.toMatch(/SLIGHTLY_LOW|IN_RANGE|color-ph-|arrow-down-circle/);
  });
});

describe("phChartModel", () => {
  it("chỉ bảng ACTIVE dòng STANDARD không gắn lô là bảng máy quét dùng", () => {
    expect(isUsedByScans({ status: "ACTIVE", productLine: "STANDARD", productionBatch: null })).toBe(true);
    expect(isUsedByScans({ status: "ACTIVE", productLine: "STANDARD", productionBatch: " " })).toBe(true);
    expect(isUsedByScans({ status: "ACTIVE", productLine: "STANDARD", productionBatch: "B1" })).toBe(false);
    expect(isUsedByScans({ status: "DRAFT", productLine: "STANDARD", productionBatch: null })).toBe(false);
    expect(isUsedByScans({ status: "ACTIVE", productLine: "MINI", productionBatch: null })).toBe(false);
  });

  it("tìm bảng đang dùng trùng dòng + lô", () => {
    const other = summary(activeChart);
    expect(findActiveConflict({ id: "x", productLine: "STANDARD", productionBatch: "" }, [other])).toBe(other);
    expect(findActiveConflict({ id: "x", productLine: "STANDARD", productionBatch: "B1" }, [other])).toBeUndefined();
    expect(findActiveConflict(other, [other])).toBeUndefined();
  });

  it("xem trước luật xuất bản", () => {
    const ok = publishChecks(activeChart.points);
    expect(ok.every((check) => check.ok)).toBe(true);
    const bad = publishChecks([
      { phValue: 6.6, sortOrder: 1 },
      { phValue: 6.4, sortOrder: 2 },
    ]);
    expect(Object.fromEntries(bad.map((check) => [check.key, check.ok]))).toEqual({
      minPoints: false,
      has63: false,
      has66: true,
      ascending: false,
    });
  });

  it("phân loại khoảng của dải", () => {
    expect(bandRange({ phMin: null, phMax: 6, minInclusive: true, maxInclusive: false }).kind).toBe("below");
    expect(bandRange({ phMin: 7, phMax: null, minInclusive: false, maxInclusive: true }).kind).toBe("above");
    expect(bandRange({ phMin: null, phMax: null, minInclusive: true, maxInclusive: true }).kind).toBe("none");
  });
});

describe("formatPhShort", () => {
  afterEach(async () => {
    await i18next.changeLanguage("vi");
  });

  it("đổi dấu thập phân theo ngôn ngữ", async () => {
    expect(formatPhShort(6.3)).toBe("6,3");
    await i18next.init({ lng: "en", resources: {}, supportedLngs: ["vi", "en"] });
    expect(formatPhShort(6.3)).toBe("6.3");
  });
});
