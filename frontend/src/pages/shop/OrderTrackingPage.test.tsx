import { StrictMode, act, type ReactNode } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
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
  checkoutUrl: null,
  paymentExpiresAt: null,
  paidAt: null,
  cancelReason: null,
};

const state = vi.hoisted(() => ({
  orderId: "",
  search: "",
  cancel: vi.fn(),
  query: {} as { isPending: boolean; isError: boolean; data?: unknown; error: unknown },
}));

vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({
    t: (key: string, options?: { time?: string }) => (options?.time ? `${key} ${options.time}` : key),
  }),
}));
vi.mock("@/features/shop", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/shop")>()),
  useShopOrder: () => state.query,
  useCancelOrder: () => ({ mutate: state.cancel, isPending: false }),
}));
vi.mock("react-router", () => ({
  useParams: () => ({ orderId: state.orderId }),
  useLocation: () => ({ pathname: "/orders/x", search: state.search, hash: "", state: null, key: "default" }),
  Link: ({ children, to }: { children: ReactNode; to: string }) => <a href={to}>{children}</a>,
}));

function render(): { text: string; container: HTMLElement; cleanup: () => void } {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  const container = document.createElement("div");
  const root = createRoot(container);
  act(() => {
    root.render(
      <StrictMode>
        <OrderTrackingPage />
      </StrictMode>,
    );
  });
  return {
    text: container.textContent,
    container,
    cleanup: () => {
      act(() => {
        root.unmount();
      });
      container.remove();
    },
  };
}

// Every test runs on a fixed fake clock, so the countdown is exact: NOW + 2:05.
const NOW = new Date("2026-10-19T03:00:00Z");

const pendingPayos: OrderApi = {
  ...baseOrder,
  status: "PENDING_PAYMENT",
  paymentMethod: "PAYOS",
  checkoutUrl: "https://pay.payos.vn/web/abc",
  paymentExpiresAt: "2026-10-19T03:02:05Z",
};

function show(data: OrderApi, search = "") {
  state.orderId = ORDER_ID;
  state.search = search;
  state.query = { isPending: false, isError: false, data, error: null };
}

describe("OrderTrackingPage", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(NOW);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    vi.clearAllMocks();
    state.search = "";
  });

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

  it("shows the countdown and a continue-paying button for an unpaid PayOS order", () => {
    show(pendingPayos);
    const assign = vi.fn();
    vi.stubGlobal("location", { assign });
    const { text, container, cleanup } = render();

    expect(text).toContain("order.expiresIn 2:05");
    expect(text).toContain("order.payAgain");
    const payAgain = [...container.querySelectorAll("button")].find((b) => b.textContent === "order.payAgain");
    act(() => payAgain?.click());
    expect(assign).toHaveBeenCalledWith("https://pay.payos.vn/web/abc");
    cleanup();
  });

  it("lets the buyer cancel any unpaid order, but only offers payment for PayOS", () => {
    show({ ...baseOrder, status: "PENDING_PAYMENT" });
    const { text, container, cleanup } = render();

    expect(text).not.toContain("order.payAgain");
    const cancel = [...container.querySelectorAll("button")].find((b) => b.textContent === "order.cancel");
    act(() => cancel?.click());
    expect(state.cancel).toHaveBeenCalledWith(ORDER_ID);
    cleanup();
  });

  it("cancels exactly once when PayOS sends the buyer back with payment=cancel", () => {
    show(pendingPayos, "?payment=cancel");
    const { cleanup } = render();

    expect(state.cancel).toHaveBeenCalledTimes(1);
    expect(state.cancel).toHaveBeenCalledWith(ORDER_ID);
    cleanup();
  });

  it("also cancels on PayOS's own cancel=true parameter", () => {
    show(pendingPayos, "?code=00&id=abc&cancel=true&status=CANCELLED&orderCode=100001");
    const { cleanup } = render();

    expect(state.cancel).toHaveBeenCalledTimes(1);
    expect(state.cancel).toHaveBeenCalledWith(ORDER_ID);
    cleanup();
  });

  it("does not cancel on payment=return or when the order is already paid", () => {
    show(pendingPayos, "?payment=return&code=00&cancel=false&status=PAID");
    render().cleanup();
    show({ ...pendingPayos, status: "PAID", checkoutUrl: null }, "?payment=cancel");
    const { text, cleanup } = render();

    expect(state.cancel).not.toHaveBeenCalled();
    expect(text).not.toContain("order.payAgain");
    cleanup();
  });

  it("explains an order that expired unpaid", () => {
    show({ ...pendingPayos, status: "CANCELLED", checkoutUrl: null, cancelReason: "PAYMENT_EXPIRED" });
    const { text, cleanup } = render();

    expect(text).toContain("order.cancelReason.PAYMENT_EXPIRED");
    expect(text).not.toContain("order.payAgain");
    cleanup();
  });

  it("still labels legacy MOMO and bank transfer orders", () => {
    show({ ...baseOrder, paymentMethod: "MOMO" });
    const momo = render();
    expect(momo.text).toContain("payment.methods.MOMO.name");
    momo.cleanup();

    show({ ...baseOrder, paymentMethod: "BANK_TRANSFER" });
    const bank = render();
    expect(bank.text).toContain("payment.methods.BANK_TRANSFER.name");
    bank.cleanup();
  });

  it("displays confirmed status, cod reassurance, and COD timeline without payment countdown for COD orders", () => {
    show({ ...baseOrder, status: "PENDING_PAYMENT", paymentMethod: "COD" });
    const { text, cleanup } = render();

    expect(text).toContain("order.status.CONFIRMED");
    expect(text).toContain("order.codConfirmedTitle");
    expect(text).toContain("order.codConfirmedBody");
    expect(text).not.toContain("order.expiresIn");
    expect(text).not.toContain("order.payAgain");
    expect(text).not.toContain("order.status.PENDING_PAYMENT");
    cleanup();
  });
});
