import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import i18next from "i18next";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { formatAdminDateTime, type AdminOrder } from "@/features/admin";
import adminVi from "@/shared/i18n/locales/vi/admin.json";
import { AdminOrdersPage } from "./AdminOrdersPage";

const i18n = i18next.createInstance();
void i18n.init({
  lng: "vi",
  resources: { vi: { admin: adminVi } },
  interpolation: { escapeValue: false },
  initAsync: false,
});
const tVi = i18n.getFixedT("vi", "admin");

const m = vi.hoisted(() => ({
  orders: [] as unknown[],
  list: vi.fn(),
  mutate: vi.fn(),
}));

vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({ t: tVi, i18n: { language: "vi" } }),
}));

vi.mock("@/features/admin", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/admin")>()),
  useAdminOrders: (status: string | undefined, page: number) => {
    m.list(status, page);
    return { isPending: false, isError: false, error: null, data: m.orders };
  },
  useChangeAdminOrderStatus: () => ({ isPending: false, error: null, mutate: m.mutate, reset: vi.fn() }),
}));

function order(overrides: Partial<AdminOrder>): AdminOrder {
  return {
    id: "o1",
    orderCode: "CC-1001",
    status: "PAID",
    paymentMethod: "PAYOS",
    receiverName: "Nguyễn An",
    receiverPhone: "0900000000",
    shippingAddress: "12 Lê Lợi, Đà Nẵng",
    subtotalVnd: 125000,
    discountVnd: 0,
    shippingFeeVnd: 30000,
    totalVnd: 155000,
    lines: [{ product: { id: "p1", sku: "CAT-01", name: "Cát vệ sinh" }, quantity: 2, totalVnd: 125000 }],
    createdAt: "2026-10-04T10:00:00Z",
    payosOrderCode: null,
    paymentExpiresAt: null,
    paidAt: "2026-10-04T10:05:00Z",
    paymentReference: null,
    cancelReason: null,
    refundNeeded: false,
    allowedNextStatuses: ["PACKING", "CANCELLED"],
    ...overrides,
  };
}

let mounted: Root | undefined;

function render(): void {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  const container = document.createElement("div");
  document.body.append(container);
  const root = createRoot(container);
  act(() => {
    root.render(<AdminOrdersPage />);
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

function submitDialog(): void {
  click(document.body.querySelector<HTMLButtonElement>("[role=dialog] button[type=submit]") ?? undefined);
}

describe("AdminOrdersPage", () => {
  beforeEach(() => {
    m.orders = [order({})];
    m.list.mockReset();
    m.mutate.mockReset();
  });

  afterEach(() => {
    act(() => {
      mounted?.unmount();
    });
    mounted = undefined;
    document.body.innerHTML = "";
  });

  it("chỉ hiện nút cho các trạng thái trong allowedNextStatuses", () => {
    m.orders = [order({}), order({ id: "o2", orderCode: "CC-1002", status: "DELIVERED", allowedNextStatuses: [] })];
    render();
    expect(buttons("Chuẩn bị hàng")).toHaveLength(1);
    expect(buttons("Huỷ đơn")).toHaveLength(1);
    expect(buttons("Giao hàng")).toHaveLength(0);
    expect(buttons("Đã giao")).toHaveLength(0);
  });

  it("chuyển trạng thái thường không hỏi lý do", () => {
    render();
    click(buttons("Chuẩn bị hàng")[0]);
    expect(m.mutate).toHaveBeenCalledWith({ id: "o1", status: "PACKING" }, expect.anything());
    expect(document.body.querySelector("[role=dialog]")).toBeNull();
  });

  it("huỷ đơn bắt buộc nhập lý do tối thiểu 10 ký tự", () => {
    render();
    click(buttons("Huỷ đơn")[0]);
    expect(document.body.querySelector("[role=dialog] textarea")).not.toBeNull();

    typeReason("  quá ngắn  ");
    submitDialog();
    expect(m.mutate).not.toHaveBeenCalled();
    expect(document.body.querySelector("[role=dialog]")?.textContent).toContain("tối thiểu 10 ký tự");

    typeReason("Khách đổi ý, không nhận hàng nữa");
    submitDialog();
    expect(m.mutate).toHaveBeenCalledWith(
      { id: "o1", status: "CANCELLED", reason: "Khách đổi ý, không nhận hàng nữa" },
      expect.anything(),
    );
  });

  it("đơn refundNeeded hiện nhãn Cần hoàn tiền, đơn thường thì không", () => {
    m.orders = [
      order({ id: "o2", orderCode: "CC-1002", status: "CANCELLED", refundNeeded: true, allowedNextStatuses: [] }),
      order({}),
    ];
    render();
    expect(document.body.textContent.match(/Cần hoàn tiền/g)).toHaveLength(1);
  });

  it("đơn PayOS cần hoàn tiền hiện mã CC, mã giao dịch và giờ nhận tiền để đối soát", () => {
    m.orders = [
      order({
        id: "o2",
        orderCode: "CC-1002",
        status: "CANCELLED",
        refundNeeded: true,
        payosOrderCode: 100123,
        paymentReference: "FT26277ABC",
        paidAt: "2026-10-04T10:05:00Z",
        cancelReason: "PAYMENT_EXPIRED",
        allowedNextStatuses: [],
      }),
    ];
    render();
    const text = document.body.textContent;
    expect(text).toContain("CC100123");
    expect(text).toContain("FT26277ABC");
    expect(text).toContain(formatAdminDateTime("2026-10-04T10:05:00Z"));
  });

  it("đơn COD không hiện mã PayOS", () => {
    m.orders = [order({ paymentMethod: "COD", paidAt: null, allowedNextStatuses: [] })];
    render();
    expect(document.body.textContent).not.toContain("Mã PayOS");
  });

  it("lý do huỷ hiện bằng nhãn, không hiện mã thô", () => {
    m.orders = [
      order({ id: "a", status: "CANCELLED", cancelReason: "PAYMENT_EXPIRED", allowedNextStatuses: [] }),
      order({ id: "b", status: "CANCELLED", cancelReason: "BUYER", allowedNextStatuses: [] }),
      order({ id: "c", status: "CANCELLED", cancelReason: "ADMIN", allowedNextStatuses: [] }),
    ];
    render();
    const text = document.body.textContent;
    expect(text).toContain(tVi("orderAdmin.cancelReason", { reason: tVi("orderAdmin.cancelReasons.PAYMENT_EXPIRED") }));
    expect(text).toContain(tVi("orderAdmin.cancelReasons.BUYER"));
    expect(text).toContain(tVi("orderAdmin.cancelReasons.ADMIN"));
    expect(text).not.toMatch(/PAYMENT_EXPIRED|BUYER|ADMIN/);
  });

  it("đổi bộ lọc trạng thái quay về trang đầu", () => {
    render();
    const select = document.body.querySelector<HTMLSelectElement>("select");
    expect(select).not.toBeNull();
    act(() => {
      Reflect.set(HTMLSelectElement.prototype, "value", "PAID", select);
      select?.dispatchEvent(new Event("change", { bubbles: true }));
    });
    expect(m.list).toHaveBeenLastCalledWith("PAID", 0);
  });
});
