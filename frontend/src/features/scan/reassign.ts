import type { ScanResult } from "@/entities/scan-result";

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
