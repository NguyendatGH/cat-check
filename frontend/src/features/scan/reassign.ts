import type { ScanResult } from "@/entities/scan-result";

/**
 * Gán lại chỉ hợp lệ khi CẢ HAI điều kiện server còn đúng: còn lượt (`reassignRemaining`) và
 * chưa quá `reassignableUntil` (24h sau khi chụp, p6 §6.10.3). Thiếu điều kiện thứ hai, nút
 * vẫn hiện cho lượt quét hôm qua và người dùng chỉ nhận 409 `SCAN_REASSIGN_WINDOW_CLOSED` sau
 * khi bấm.
 */
export function isReassignWindowOpen(result: Pick<ScanResult, "reassignableUntil">, now: number = Date.now()): boolean {
  if (!result.reassignableUntil) return true;
  return new Date(result.reassignableUntil).getTime() > now;
}

export function canReassign(
  result: Pick<ScanResult, "reassignRemaining" | "reassignableUntil">,
  now: number = Date.now(),
): boolean {
  return result.reassignRemaining > 0 && isReassignWindowOpen(result, now);
}
