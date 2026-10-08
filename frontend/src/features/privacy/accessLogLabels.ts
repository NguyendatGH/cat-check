/**
 * Nhãn thân thiện cho dòng nhật ký truy cập (B13). `action`/`actorType`/`result` là mã thô từ
 * audit log — chỉ trả về KHOÁ i18n (`legal:privacyCenter.accessLog.*`); mã chưa có trong bảng
 * dịch rơi về nhãn chung để không bao giờ lộ mã thô ra UI.
 */
const KNOWN_ACTIONS = new Set([
  "ADMIN_USER_VIEW",
  "ADMIN_USER_SEARCH",
  "ADMIN_USER_CATS_VIEW",
  "ADMIN_USER_SCANS_VIEW",
  "ADMIN_USER_CREDITS_VIEW",
  "ADMIN_USER_SESSIONS_VIEW",
  "ADMIN_USER_SESSIONS_REVOKE",
  "ADMIN_SCAN_IMAGE_VIEW",
  "ADMIN_SCAN_REASSIGNED",
  "ADMIN_PII_UNMASKED",
  "ADMIN_LOCK_ACCOUNT",
  "ADMIN_UNLOCK_ACCOUNT",
  "ADMIN_MFA_RESET_REQUESTED",
  "ADMIN_ROLE_GRANTED",
  "ADMIN_ROLE_REVOKED",
  "ADMIN_NOTIFICATION_OUTBOX_VIEW",
  "ADMIN_OUTBOX_RESEND",
]);
const KNOWN_ACTORS = new Set(["USER", "ADMIN", "DPO", "SYSTEM", "JOB"]);
const KNOWN_RESULTS = new Set(["SUCCESS", "DENIED", "ERROR"]);

const BASE = "privacyCenter.accessLog";

export function accessLogActionKey(action: string): string {
  return `${BASE}.actions.${KNOWN_ACTIONS.has(action) ? action : "generic"}`;
}

/** `null` khi loại tác nhân lạ — UI ẩn dòng thay vì hiện mã thô. */
export function accessLogActorKey(actorType: string): string | null {
  return KNOWN_ACTORS.has(actorType) ? `${BASE}.actors.${actorType}` : null;
}

/** `null` khi kết quả lạ — UI ẩn thay vì hiện mã thô. */
export function accessLogResultKey(result: string): string | null {
  return KNOWN_RESULTS.has(result) ? `${BASE}.results.${result}` : null;
}
