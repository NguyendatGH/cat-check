import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { CartApi } from "@/features/shop";
import { CartPage } from "./CartPage";
import { usePaymentMethodStore } from "./paymentMethodStore";

const cart: CartApi = {
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
        stockQuantity: 5,
      },
      quantity: 2,
      totalVnd: 125000,
    },
  ],
  subtotalVnd: 125000,
  shippingFeeVnd: 30000,
  totalVnd: 155000,
};

const state = vi.hoisted(() => ({ methods: undefined as string[] | undefined }));

vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({ t: (key: string) => key }),
}));
vi.mock("@/features/shop", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/shop")>()),
  useShopCart: () => ({ isPending: false, isError: false, data: cart, refetch: vi.fn() }),
  useClearCart: () => ({ mutate: vi.fn(), isPending: false }),
  useShopPaymentMethods: () => ({ data: state.methods }),
}));
vi.mock("./useCartActions", () => ({
  useCartLineActions: () => ({ pendingProductId: null, isUpdating: false, setQuantity: vi.fn(), remove: vi.fn() }),
}));

let mounted: Root | undefined;

function render(): HTMLElement {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  const container = document.createElement("div");
  document.body.append(container);
  const root = createRoot(container);
  act(() => {
    root.render(
      <MemoryRouter>
        <CartPage />
      </MemoryRouter>,
    );
  });
  mounted = root;
  return container;
}

function offered(container: HTMLElement): string[] {
  return [...container.querySelectorAll<HTMLInputElement>("input[name=payment-method]")].map((input) => input.value);
}

afterEach(() => {
  act(() => {
    mounted?.unmount();
  });
  mounted = undefined;
  document.body.innerHTML = "";
  usePaymentMethodStore.setState({ paymentMethodId: "COD" });
});

describe("CartPage payment methods", () => {
  it("hides PayOS when the server offers only COD and moves a stored PayOS choice back to COD", () => {
    state.methods = ["COD"];
    usePaymentMethodStore.setState({ paymentMethodId: "PAYOS" });

    const container = render();

    expect(offered(container)).toEqual(["COD"]);
    expect(usePaymentMethodStore.getState().paymentMethodId).toBe("COD");
  });

  it("offers only COD while the methods are still loading", () => {
    state.methods = undefined;

    expect(offered(render())).toEqual(["COD"]);
  });

  it("offers PayOS when the server has it configured", () => {
    state.methods = ["COD", "PAYOS"];
    usePaymentMethodStore.setState({ paymentMethodId: "PAYOS" });

    expect(offered(render())).toEqual(["COD", "PAYOS"]);
    expect(usePaymentMethodStore.getState().paymentMethodId).toBe("PAYOS");
  });
});
