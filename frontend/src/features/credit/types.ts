/**
 * Types cho `features/credit` — tên field khớp DTO thật
 * `backend/src/main/java/com/catcheck/credit/api/dto/*.java` (p8 §8.4.8 nhóm H1-H4).
 */

/** `credit_ledger.type` (p4 §4.4.6) — dấu `amount` bị ràng buộc theo loại ở DB. */
export type CreditLedgerType = "GRANT" | "CONSUME" | "EXPIRE" | "REFUND" | "ADJUST";

/** `credit_ledger.ref_type` — tài nguyên mà dòng ledger tham chiếu, đa hình theo loại. */
export type CreditLedgerRefType = "SCAN" | "ACTIVATION" | "JOB" | "ADMIN";

/** 5 mã gói hiện có trong `package_plan` (p5 §5.3) — admin có thể thêm gói mới ngoài tập này. */
export type PackageCode = "MINI" | "DAILY" | "PLUS" | "MULTI" | "CARE_BOX";

/** H1 — `POST /activations`. */
export interface ActivationResult {
  packageCode: string;
  packageName: string;
  creditsGranted: number;
  expiresAt: string;
  balanceAfter: number;
}

/** Một lô credit trong `BalanceResponse.batches` (H2). */
export interface CreditBatch {
  batchId: string;
  packageCode: string;
  initialAmount: number;
  remainingAmount: number;
  activatedAt: string;
  expiresAt: string;
  /** Số giây còn lại tới hạn — `0` nếu vừa hết hạn. BE gửi số giây, không phải ISO duration. */
  remainingSeconds: number;
}

/** H2 — `GET /credits/balance`. */
export interface CreditBalance {
  availableBalance: number;
  trialScansUsed: number;
  trialScansRemaining: number;
  batches: CreditBatch[];
}

/** Một dòng sổ cái trong `LedgerPageResponse.entries` (H3). */
export interface LedgerEntry {
  id: string;
  type: CreditLedgerType;
  /** Dương = vào, âm = ra. */
  amount: number;
  balanceAfter: number;
  batchId: string | null;
  packageCode: string | null;
  refType: CreditLedgerRefType | null;
  note: string | null;
  createdAt: string;
}

/** H3 — `GET /credits/ledger`, phân trang keyset con trỏ mờ. */
export interface LedgerPage {
  entries: LedgerEntry[];
  hasMore: boolean;
  nextCursor: string | null;
}

/** H4 — `GET /entitlements/me`. */
export interface Entitlement {
  currentPackage: string | null;
  maxCatProfiles: number | null;
  hasHistory: boolean;
  hasTrend: boolean;
  hasReminder: boolean;
  hasExport: boolean;
  storeImage: boolean;
  writeAccessUntil: string | null;
  trialScansRemaining: number;
}
