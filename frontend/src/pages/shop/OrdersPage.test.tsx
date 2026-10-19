import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { OrderApi } from "@/features/shop";
import { OrdersPage } from "./OrdersPage";

const state = vi.hoisted(() => ({
  query: {} as { isPending: boolean; isError: boolean; data?: unknown; refetch: () => void },
}));

vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({ t: (key: string) => key }),
}));
vi.mock("@/features/shop", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/shop")>()),
  useMyOrders: () => state.query,
}));

const baseOrder: OrderApi = {
  id: "01a1110f-315d-76c4-87b6-65879b68a3a3",
  orderCode: "CC-REAL-123",
  status: "PACKING",
  paymentMethod: "COD",
  receiverName: "Nguyễn An",
  receiverPhone: "0900000000",
  shippingAddress: "12 Lê Lợi, Đà Nẵng",
  subtotalVnd: 125000,
  discountVnd: 0,
  shippingFeeVnd: 30000,
  totalVnd: 155000,
  lines: [],
  createdAt: "2026-10-04T10:00:00Z",
  checkoutUrl: null,
  paymentExpiresAt: null,
  paidAt: null,
  cancelReason: null,
};

let mounted: Root | undefined;

function render(data: OrderApi[]): HTMLElement {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  state.query = { isPending: false, isError: false, data, refetch: vi.fn() };
  const container = document.createElement("div");
  document.body.append(container);
  const root = createRoot(container);
  act(() => {
    root.render(
      <MemoryRouter>
        <OrdersPage />
      </MemoryRouter>,
    );
  });
  mounted = root;
  return container;
}

afterEach(() => {
  act(() => {
    mounted?.unmount();
  });
  mounted = undefined;
  document.body.innerHTML = "";
});

describe("OrdersPage", () => {
  it("danh sách rỗng hiện EmptyState", () => {
    const container = render([]);
    expect(container.textContent).toContain("myOrders.empty.title");
    expect(container.querySelector("a[href^='/orders/']")).toBeNull();
  });

  it("mỗi đơn là một link tới /orders/{id}", () => {
    const other = { ...baseOrder, id: "01a1110f-315d-76c4-87b6-65879b68a3a4", orderCode: "CC-REAL-124" };
    const container = render([baseOrder, other]);
    const hrefs = Array.from(container.querySelectorAll("a[href^='/orders/']")).map((a) => a.getAttribute("href"));
    expect(hrefs).toEqual([`/orders/${baseOrder.id}`, `/orders/${other.id}`]);
    expect(container.textContent).toContain("CC-REAL-123");
    expect(container.textContent).toContain("order.status.PACKING");
  });
});
