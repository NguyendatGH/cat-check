import { LANDING_PATHS } from "@/shared/auth/landing";
import { hasNonAdminRole, type SessionUser } from "@/entities/user";
import type { CreditBalance, CreditBatch } from "./types";

/** Khớp backend credit.domain.WelcomeBonus.PACKAGE_CODE (credit_batch.package_code của lô quà). */
export const WELCOME_PACKAGE_CODE = "WELCOME";

const KEY_PREFIX = "catcheck.welcomeBonus.v1:";
const DAY_MS = 24 * 60 * 60 * 1000;

export function isWelcomeBatch(batch: Pick<CreditBatch, "packageCode">): boolean {
  return batch.packageCode === WELCOME_PACKAGE_CODE;
}

export function findWelcomeBatch(balance: CreditBalance | undefined): CreditBatch | null {
  return balance?.batches.find(isWelcomeBatch) ?? null;
}

/** Số ngày hiệu lực của lô (cấu hình backend), làm tròn — không hard-code 5. */
export function batchValidityDays(batch: Pick<CreditBatch, "activatedAt" | "expiresAt">): number {
  const ms = new Date(batch.expiresAt).getTime() - new Date(batch.activatedAt).getTime();
  if (!Number.isFinite(ms) || ms <= 0) return 0;
  return Math.max(1, Math.round(ms / DAY_MS));
}

/**
 * Đã ăn mừng quà chào mừng cho user này chưa (localStorage theo user id). Storage bị chặn => coi như
 * đã ăn mừng để không lặp lại mỗi lần vào trang.
 */
export function hasCelebratedWelcome(userId: string): boolean {
  try {
    return window.localStorage.getItem(`${KEY_PREFIX}${userId}`) !== null;
  } catch {
    return true;
  }
}

export function markWelcomeCelebrated(userId: string): void {
  try {
    window.localStorage.setItem(`${KEY_PREFIX}${userId}`, "1");
  } catch {
    // Storage bị chặn: bỏ qua.
  }
}

export interface WelcomeCheckInput {
  user: SessionUser | null;
  pathname: string;
  splashPhase: string;
}

/** Chỉ kiểm tra quà ở Trang chủ, sau onboarding, khi brand splash đã ẩn, cho người mua chưa ăn mừng. */
export function shouldCheckWelcome({ user, pathname, splashPhase }: WelcomeCheckInput): boolean {
  if (!user) return false;
  if (!hasNonAdminRole(user)) return false;
  if (!user.onboarding.completed) return false;
  if (splashPhase !== "hidden") return false;
  if (pathname.replace(/\/+$/, "") !== LANDING_PATHS.buyer) return false;
  return !hasCelebratedWelcome(user.id);
}
