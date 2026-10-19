import { afterEach, describe, expect, it, vi } from "vitest";
import type { SessionUser } from "@/entities/user";
import type { CreditBalance, CreditBatch } from "./types";
import {
  batchValidityDays,
  findWelcomeBatch,
  hasCelebratedWelcome,
  markWelcomeCelebrated,
  shouldCheckWelcome,
} from "./welcome";

const welcomeBatch: CreditBatch = {
  batchId: "b-1",
  packageCode: "WELCOME",
  initialAmount: 10,
  remainingAmount: 10,
  activatedAt: "2026-05-01T00:00:00Z",
  expiresAt: "2026-05-06T00:00:00Z",
  remainingSeconds: 5 * 86400,
};

const balance = (batches: CreditBatch[]): CreditBalance => ({
  availableBalance: 10,
  trialScansUsed: 0,
  trialScansRemaining: 3,
  batches,
});

const buyer: SessionUser = {
  id: "u-welcome",
  email: "a@example.test",
  displayName: "A",
  roles: ["USER"],
  onboarding: { completed: true, nextStep: null },
};

const base = { user: buyer, pathname: "/dashboard", splashPhase: "hidden" };

describe("welcome bonus", () => {
  afterEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
  });

  it("nhận ra lô WELCOME trong số dư và tính số ngày từ API (không hard-code)", () => {
    expect(findWelcomeBatch(balance([{ ...welcomeBatch, packageCode: "PLUS" }]))).toBeNull();
    expect(findWelcomeBatch(undefined)).toBeNull();
    expect(findWelcomeBatch(balance([welcomeBatch]))).toBe(welcomeBatch);
    expect(batchValidityDays(welcomeBatch)).toBe(5);
    expect(batchValidityDays({ activatedAt: "2026-05-01T00:00:00Z", expiresAt: "2026-05-08T00:00:00Z" })).toBe(7);
  });

  it("chỉ ăn mừng một lần cho mỗi user (localStorage theo user id)", () => {
    expect(shouldCheckWelcome(base)).toBe(true);
    markWelcomeCelebrated(buyer.id);
    expect(hasCelebratedWelcome(buyer.id)).toBe(true);
    expect(shouldCheckWelcome(base)).toBe(false);
    expect(shouldCheckWelcome({ ...base, user: { ...buyer, id: "u-other" } })).toBe(true);
  });

  it("storage bị chặn => coi như đã ăn mừng, không ném lỗi", () => {
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    expect(hasCelebratedWelcome(buyer.id)).toBe(true);
    expect(() => {
      markWelcomeCelebrated(buyer.id);
    }).not.toThrow();
    expect(shouldCheckWelcome(base)).toBe(false);
  });

  it("đợi splash ẩn, chỉ ở Trang chủ, chỉ người mua đã xong onboarding", () => {
    expect(shouldCheckWelcome({ ...base, splashPhase: "exit" })).toBe(false);
    expect(shouldCheckWelcome({ ...base, pathname: "/credits" })).toBe(false);
    expect(shouldCheckWelcome({ ...base, pathname: "/dashboard/" })).toBe(true);
    expect(shouldCheckWelcome({ ...base, user: null })).toBe(false);
    expect(shouldCheckWelcome({ ...base, user: { ...buyer, roles: ["USER", "ADMIN_SUPER"] } })).toBe(false);
    expect(
      shouldCheckWelcome({ ...base, user: { ...buyer, onboarding: { completed: false, nextStep: "cat" } } }),
    ).toBe(false);
  });
});
