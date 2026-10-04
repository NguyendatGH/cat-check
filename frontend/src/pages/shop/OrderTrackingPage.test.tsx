import { act, type ReactNode } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, describe, expect, it, vi } from "vitest";
import { OrderTrackingPage } from "./OrderTrackingPage";

const order = {
  id: "order-1",
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
  lines: [{ product: { id: "p1", sku: "CAT-01", name: "Cát vệ sinh", imageUrl: null }, quantity: 2, totalVnd: 125000 }],
  createdAt: "2026-10-04T10:00:00Z",
};

vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({ t: (key: string) => key }),
}));
vi.mock("@tanstack/react-query", () => ({ useQuery: () => ({ isPending: false, isError: false, data: order }) }));
vi.mock("react-router", () => ({
  useParams: () => ({ orderId: "order-1" }),
  Link: ({ children, to }: { children: ReactNode; to: string }) => <a href={to}>{children}</a>,
}));

describe("OrderTrackingPage", () => {
  afterEach(() => vi.clearAllMocks());

  it("renders order facts from the API and does not claim fake courier or live tracking data", () => {
    (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
    const container = document.createElement("div");
    const root = createRoot(container);
    act(() => {
      root.render(<OrderTrackingPage />);
    });

    expect(container.textContent).toContain("CC-REAL-123");
    expect(container.textContent).toContain("Nguyễn An");
    expect(container.textContent).toContain("12 Lê Lợi, Đà Nẵng");
    expect(container.textContent).toContain("order.status.PACKING");
    expect(container.textContent).toContain("155.000đ");
    expect(container.textContent).toContain("order.trackingDisclaimer");
    expect(container.textContent).not.toContain("MOCK_ORDER");
    expect(container.textContent).not.toContain("Gọi tài xế");
    act(() => {
      root.unmount();
    });
    container.remove();
  });
});
