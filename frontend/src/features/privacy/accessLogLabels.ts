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

export function accessLogActorKey(actorType: string): string | null {
  return KNOWN_ACTORS.has(actorType) ? `${BASE}.actors.${actorType}` : null;
}

export function accessLogResultKey(result: string): string | null {
  return KNOWN_RESULTS.has(result) ? `${BASE}.results.${result}` : null;
}
