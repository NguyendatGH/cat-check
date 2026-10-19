import { describe, expect, it } from "vitest";
import type { OrderApi } from "@/features/shop";
import { nextStepAfterOrder } from "./shopFormat";

const order = (patch: Partial<OrderApi>) => ({ id: "ord-1", paymentMethod: "COD", checkoutUrl: null, ...patch }) as OrderApi;

describe("nextStepAfterOrder", () => {
  it("sends a PayOS order to the hosted payment page", () => {
    expect(
      nextStepAfterOrder(order({ paymentMethod: "PAYOS", checkoutUrl: "https://pay.payos.vn/web/abc" })),
    ).toEqual({ kind: "redirect", url: "https://pay.payos.vn/web/abc" });
  });

  it("opens the order page for COD", () => {
    expect(nextStepAfterOrder(order({}))).toEqual({ kind: "order", path: "/orders/ord-1" });
  });

  it("opens the order page when PayOS gave no checkout url", () => {
    expect(nextStepAfterOrder(order({ paymentMethod: "PAYOS" }))).toEqual({ kind: "order", path: "/orders/ord-1" });
  });
});
