
export type CreditLedgerType = "GRANT" | "CONSUME" | "EXPIRE" | "REFUND" | "ADJUST";

export type CreditLedgerRefType = "SCAN" | "ACTIVATION" | "JOB" | "ADMIN";

export type PackageCode = "MINI" | "DAILY" | "PLUS" | "MULTI" | "CARE_BOX";

export interface PackagePlan {
  code: string;
  name: string;
  weightKg: number;
  creditAmount: number;
  creditValidityDays: number;
  maxCatProfiles: number | null;
  historyLevel: string;
  hasTrend: boolean;
  hasReminder: boolean;
  hasExport: boolean;
  storeImage: boolean;
}

export interface PackagePlanList {
  items: PackagePlan[];
}

export interface ActivationResult {
  packageCode: string;
  packageName: string;
  creditsGranted: number;
  expiresAt: string;
  balanceAfter: number;
}

export interface CreditBatch {
  batchId: string;
  packageCode: string;
  initialAmount: number;
  remainingAmount: number;
  activatedAt: string;
  expiresAt: string;
  remainingSeconds: number;
}

export interface CreditBalance {
  availableBalance: number;
  trialScansUsed: number;
  trialScansRemaining: number;
  batches: CreditBatch[];
}

export interface LedgerEntry {
  id: string;
  type: CreditLedgerType;
  amount: number;
  balanceAfter: number;
  batchId: string | null;
  packageCode: string | null;
  expiresAt: string | null;
  refType: CreditLedgerRefType | null;
  note: string | null;
  createdAt: string;
}

export interface LedgerPage {
  entries: LedgerEntry[];
  hasMore: boolean;
  nextCursor: string | null;
}

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
