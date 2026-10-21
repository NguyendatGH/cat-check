import { StrictMode, act } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { CheckoutPage } from "./CheckoutPage";

const state = vi.hoisted(() => ({
  cartQuery: {
    isPending: false,
    isError: false,
    data: {
      subtotalVnd: 200000,
      shippingFeeVnd: 30000,
      totalVnd: 230000,
      lines: [
        {
          product: {
            id: "prod-1",
            sku: "CAT-LITTER-1",
            name: "Cát vệ sinh 2.5kg",
            priceVnd: 200000,
            compareAtPriceVnd: null,
            stockQuantity: 10,
            imageUrl: null,
            description: "",
          },
          quantity: 1,
          totalVnd: 200000,
        },
      ],
    },
  },
  createOrder: vi.fn(),
}));

vi.mock("@/features/shop", () => ({
  useShopCart: () => state.cartQuery,
  useCreateOrder: () => ({ mutate: state.createOrder, isPending: false }),
}));

vi.mock("@/entities/user", () => ({
  useSessionStore: () => "Nguyễn Đạt",
}));

vi.mock("react-router", () => ({
  useNavigate: () => vi.fn(),
  Link: ({ children, to }: { children: React.ReactNode; to: string }) => <a href={to}>{children}</a>,
}));

vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => {
      if (typeof opts?.code === "string") return `${key}:${opts.code}`;
      if (typeof opts?.count === "number" || typeof opts?.count === "string") return `${key}:${String(opts.count)}`;
      return key;
    },
  }),
}));

function renderCheckout() {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  const container = document.createElement("div");
  document.body.appendChild(container);
  const root = createRoot(container);
  act(() => {
    root.render(
      <StrictMode>
        <CheckoutPage />
      </StrictMode>,
    );
  });
  return {
    container,
    cleanup: () => {
      act(() => {
        root.unmount();
      });
      container.remove();
    },
  };
}

describe("CheckoutPage Enhancements", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it("renders geolocation button, save info checkbox, and promo code section", () => {
    const { container, cleanup } = renderCheckout();

    expect(container.textContent).toContain("checkout.useCurrentLocation");
    expect(container.textContent).toContain("checkout.saveInfoLabel");
    expect(container.textContent).toContain("checkout.promoLabel");
    expect(container.textContent).toContain("CATCHECK10");
    expect(container.textContent).toContain("FREESHIP");
    expect(container.textContent).toContain("CATFRIEND");

    cleanup();
  });

  it("applies voucher CATCHECK10 (10% off) and updates total display", () => {
    const { container, cleanup } = renderCheckout();

    const catcheck10Btn = [...container.querySelectorAll("button")].find(
      (b) => b.textContent.trim() === "CATCHECK10",
    );
    expect(catcheck10Btn).toBeDefined();

    act(() => {
      catcheck10Btn?.click();
    });

    // 10% of 200,000 is 20,000 VND discount -> total is 230,000 - 20,000 = 210,000đ
    expect(container.textContent).toContain("checkout.promoDiscount:CATCHECK10");
    expect(container.textContent).toContain("-20.000đ");
    expect(container.textContent).toContain("210.000đ");

    cleanup();
  });

  it("applies FREESHIP (30k off shipping) and updates total display", () => {
    const { container, cleanup } = renderCheckout();

    const freeshipBtn = [...container.querySelectorAll("button")].find(
      (b) => b.textContent.trim() === "FREESHIP",
    );
    expect(freeshipBtn).toBeDefined();

    act(() => {
      freeshipBtn?.click();
    });

    // Shipping fee is 30,000 VND -> 30,000 discount -> total is 200,000đ
    expect(container.textContent).toContain("checkout.promoDiscount:FREESHIP");
    expect(container.textContent).toContain("-30.000đ");
    expect(container.textContent).toContain("200.000đ");

    cleanup();
  });

  it("loads and saves shipping details in localStorage when saveInfo is checked", () => {
    localStorage.setItem(
      "catcheck_saved_shipping_info",
      JSON.stringify({
        receiverName: "Đạt Test",
        receiverPhone: "0901234567",
        shippingAddress: "Nam Phước, Duy Xuyên, Quảng Nam",
      }),
    );

    const { container, cleanup } = renderCheckout();

    const nameInput = container.querySelector<HTMLInputElement>('input[name="receiverName"]');
    const phoneInput = container.querySelector<HTMLInputElement>('input[name="receiverPhone"]');
    const addressInput = container.querySelector<HTMLTextAreaElement>('textarea[name="shippingAddress"]');

    expect(nameInput?.value).toBe("Đạt Test");
    expect(phoneInput?.value).toBe("0901234567");
    expect(addressInput?.value).toBe("Nam Phước, Duy Xuyên, Quảng Nam");

    cleanup();
  });
});
