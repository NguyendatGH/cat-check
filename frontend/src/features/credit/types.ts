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

/**
 * F3 — một gói trong `GET /reference/packages` (p8 §8.4.6). Tên gói, khối lượng, số credit và
 * hạn dùng đều do backend trả; client KHÔNG giữ bảng tên gói riêng (trước đây nằm cứng ở
 * `credit.json: packages.*` và lệch ngay khi admin đổi `package_plan.name`).
 *
 * KHÔNG có trường giá tiền trong response — endpoint này là danh mục gói, không phải bảng giá
 * (thanh toán là Phase 3). Vì vậy UI không hiển thị giá.
 */
export interface PackagePlan {
  code: string;
  name: string;
  weightKg: number;
  creditAmount: number;
  creditValidityDays: number;
  /** `null` = không giới hạn số hồ sơ mèo (p4 I27). */
  maxCatProfiles: number | null;
  historyLevel: string;
  hasTrend: boolean;
  hasReminder: boolean;
  hasExport: boolean;
  storeImage: boolean;
}

/** F3 — envelope `{items}` (danh mục công khai, không phân trang). */
export interface PackagePlanList {
  items: PackagePlan[];
}

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
