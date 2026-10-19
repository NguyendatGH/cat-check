import { describe, expect, it } from "vitest";
import type { OrderApi } from "./api";
import { orderRefetchMs } from "./hooks";

const future = () => new Date(Date.now() + 60_000).toISOString();
const order = (patch: Partial<OrderApi>) =>
  ({ status: "PENDING_PAYMENT", paymentMethod: "PAYOS", paymentExpiresAt: future(), ...patch }) as OrderApi;

describe("orderRefetchMs", () => {
  it("polls every 3s while an unpaid PayOS order has time left", () => {
    expect(orderRefetchMs(order({}))).toBe(3000);
  });

  it("falls back to 30s once expired, paid, or for other methods", () => {
    expect(orderRefetchMs(order({ paymentExpiresAt: new Date(Date.now() - 1000).toISOString() }))).toBe(30_000);
    expect(orderRefetchMs(order({ status: "PAID" }))).toBe(30_000);
    expect(orderRefetchMs(order({ paymentMethod: "COD", paymentExpiresAt: null }))).toBe(30_000);
    expect(orderRefetchMs(undefined)).toBe(30_000);
  });
});
