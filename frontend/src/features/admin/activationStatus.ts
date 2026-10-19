import type { ActivationCodeAdmin, ActivationCodeEffectiveStatus } from "./types";

/** Thứ tự hiển thị trong bộ lọc và KPI: chưa dùng (còn hạn) → hết hạn → đã dùng → đã vô hiệu. */
export const ACTIVATION_CODE_STATUSES: readonly ActivationCodeEffectiveStatus[] = [
  "ISSUED",
  "EXPIRED",
  "REDEEMED",
  "VOID",
];

/**
 * Ưu tiên `effectiveStatus` backend trả; nếu thiếu (phản hồi cũ) thì tự suy: ISSUED mà
 * validUntil <= now ⇒ EXPIRED — cùng quy tắc ActivationCode.isIssuanceExpiredAt.
 */
export function activationEffectiveStatus(
  code: Pick<ActivationCodeAdmin, "status" | "validUntil"> & { effectiveStatus?: ActivationCodeEffectiveStatus | null },
  now: number = Date.now(),
): ActivationCodeEffectiveStatus {
  if (code.effectiveStatus) return code.effectiveStatus;
  if (code.status === "ISSUED" && code.validUntil) {
    const validUntil = Date.parse(code.validUntil);
    if (!Number.isNaN(validUntil) && validUntil <= now) return "EXPIRED";
  }
  return code.status;
}
