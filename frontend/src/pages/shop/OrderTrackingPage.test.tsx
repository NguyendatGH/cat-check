import { act, type ReactNode } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { OrderApi } from "@/features/shop";
import { OrderTrackingPage } from "./OrderTrackingPage";

const ORDER_ID = "01a1110f-315d-76c4-87b6-65879b68a3a3";

const baseOrder: OrderApi = {
  id: ORDER_ID,
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
  lines: [
    {
      product: {
        id: "p1",
        sku: "CAT-01",
        name: "Cát vệ sinh",
        description: "",
        imageUrl: null,
        priceVnd: 62500,
        compareAtPriceVnd: null,
        stockQuantity: 0,
      },
      quantity: 2,
      totalVnd: 125000,
    },
  ],
  createdAt: "2026-10-04T10:00:00Z",
};

const state = vi.hoisted(() => ({
  orderId: "",
  query: {} as { isPending: boolean; isError: boolean; data?: unknown; error: unknown },
}));

vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({ t: (key: string) => key }),
}));
vi.mock("@/features/shop", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/shop")>()),
  useShopOrder: () => state.query,
}));
vi.mock("react-router", () => ({
  useParams: () => ({ orderId: state.orderId }),
  Link: ({ children, to }: { children: ReactNode; to: string }) => <a href={to}>{children}</a>,
}));

function render(): { text: string; cleanup: () => void } {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  const container = document.createElement("div");
  const root = createRoot(container);
  act(() => {
    root.render(<OrderTrackingPage />);
  });
  return {
    text: container.textContent,
    cleanup: () => {
      act(() => {
        root.unmount();
      });
      container.remove();
    },
  };
}

describe("OrderTrackingPage", () => {
  afterEach(() => vi.clearAllMocks());

  it("renders order facts from the API and does not claim fake courier or live tracking data", () => {
    state.orderId = ORDER_ID;
    state.query = { isPending: false, isError: false, data: baseOrder, error: null };
    const { text, cleanup } = render();

    expect(text).toContain("CC-REAL-123");
    expect(text).toContain("Nguyễn An");
    expect(text).toContain("12 Lê Lợi, Đà Nẵng");
    expect(text).toContain("order.status.PACKING");
    expect(text).toContain("payment.methods.COD.name");
    expect(text).toContain("155.000đ");
    expect(text).toContain("order.trackingDisclaimer");
    expect(text).toContain("order.successTitle");
    expect(text).not.toContain("MOCK_ORDER");
    expect(text).not.toContain("Gọi tài xế");
    cleanup();
  });

  it("shows the cancelled state instead of the delivery steps", () => {
    state.orderId = ORDER_ID;
    state.query = { isPending: false, isError: false, data: { ...baseOrder, status: "CANCELLED" }, error: null };
    const { text, cleanup } = render();

    expect(text).toContain("order.cancelledTitle");
    expect(text).toContain("order.cancelledNote");
    expect(text).not.toContain("order.status.SHIPPING");
    cleanup();
  });

  it("treats a malformed order id as not found without calling the API", () => {
    state.orderId = "not-a-uuid";
    state.query = { isPending: true, isError: false, error: null };
    const { text, cleanup } = render();

    expect(text).toContain("order.notFound");
    cleanup();
  });
});
