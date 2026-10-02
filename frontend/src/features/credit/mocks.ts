/**
 * MSW handlers cho `features/credit` — chế độ degraded khi backend chưa nối (W1 song song).
 * Hợp đồng khớp DTO thật `backend/.../credit/api/dto/*.java` (p8 §8.4.8 H1-H4).
 *
 * Endpoint mock:
 *  - POST /api/v1/activations         (H1)
 *  - GET  /api/v1/credits/balance     (H2)
 *  - GET  /api/v1/credits/ledger      (H3)
 *  - GET  /api/v1/entitlements/me     (H4)
 */
import { http, HttpResponse, delay } from "msw";
import { setupWorker } from "msw/browser";
import type { ActivationResult, CreditBalance, CreditBatch, Entitlement, LedgerEntry } from "./types";

interface MockActivationCode {
  code: string;
  packageCode: string;
  packageName: string;
  creditAmount: number;
  validityDays: number;
  status: "ACTIVE" | "REDEEMED" | "EXPIRED";
}

/** Bao gồm ví dụ `CC-PLUS-7K3M9QX2RT` của p5 §5.9 để demo khớp tài liệu. */
const ACTIVATION_CODES: MockActivationCode[] = [
  { code: "CCPLUS7K3M9QX2RT", packageCode: "PLUS", packageName: "CATCHECK Plus", creditAmount: 10, validityDays: 7, status: "ACTIVE" },
  { code: "CCMINI3K9QX2RTWM", packageCode: "MINI", packageName: "CATCHECK Mini – Trial", creditAmount: 3, validityDays: 7, status: "ACTIVE" },
  { code: "CCDAILY8K3M9QX2A", packageCode: "DAILY", packageName: "CATCHECK Daily", creditAmount: 8, validityDays: 7, status: "ACTIVE" },
  { code: "CCMULTI6K3M9QX2BC", packageCode: "MULTI", packageName: "CATCHECK Multi", creditAmount: 16, validityDays: 7, status: "ACTIVE" },
  { code: "CCPLUS000000000U", packageCode: "PLUS", packageName: "CATCHECK Plus", creditAmount: 10, validityDays: 7, status: "REDEEMED" },
  { code: "CCPLUS000000000E", packageCode: "PLUS", packageName: "CATCHECK Plus", creditAmount: 10, validityDays: 7, status: "EXPIRED" },
];

const ACTIVATION_CODE_REGEX = /^CC[A-Z0-9]+[0-9A-HJKMNP-TV-Z]{10}$/;

function nowIso(): string {
  return new Date().toISOString();
}

function problem(status: number, code: string, detail: string, extra?: Record<string, unknown>) {
  return HttpResponse.json(
    {
      type: `https://catcheck.vn/problems/${code.toLowerCase().replace(/_/g, "-")}`,
      title: code,
      status,
      detail,
      instance: "",
      // Tên field là `errorCode`, KHÔNG phải `code`: `GlobalExceptionHandler` của backend gọi
      // `problemDetail.setProperty("errorCode", ...)`. Mock từng phát `code` nên khớp với
      // wrapper client cũng đang đọc sai — hai cái sai che nhau, chỉ lộ ra khi chạy server thật.
      errorCode: code,
      ...extra,
    },
    { status, headers: { "Content-Type": "application/problem+json; charset=utf-8" } },
  );
}

const state: { batches: CreditBatch[]; ledger: LedgerEntry[]; entitlement: Entitlement } = {
  batches: [],
  ledger: [],
  entitlement: {
    currentPackage: null,
    maxCatProfiles: 1,
    hasHistory: false,
    hasTrend: false,
    hasReminder: false,
    hasExport: false,
    storeImage: false,
    writeAccessUntil: null,
    trialScansRemaining: 3,
  },
};

const PLAN_FEATURES: Record<string, Partial<Entitlement>> = {
  MINI: { maxCatProfiles: 1, hasHistory: false, hasTrend: false, hasReminder: false, hasExport: false, storeImage: false },
  DAILY: { maxCatProfiles: 1, hasHistory: true, hasTrend: false, hasReminder: false, hasExport: false, storeImage: true },
  PLUS: { maxCatProfiles: 1, hasHistory: true, hasTrend: true, hasReminder: true, hasExport: true, storeImage: true },
  MULTI: { maxCatProfiles: null, hasHistory: true, hasTrend: true, hasReminder: true, hasExport: true, storeImage: true },
  CARE_BOX: { maxCatProfiles: null, hasHistory: true, hasTrend: true, hasReminder: true, hasExport: true, storeImage: true },
};

function availableBalance(): number {
  const now = Date.now();
  return state.batches
    .filter((b) => new Date(b.expiresAt).getTime() > now)
    .reduce((sum, b) => sum + b.remainingAmount, 0);
}

export const handlers = [
  http.post("/api/v1/activations", async ({ request }) => {
    await delay(500);
    const body = (await request.json()) as { code?: string };
    const code = (body.code ?? "").toUpperCase().replace(/[^A-Z0-9]/g, "");

    if (!ACTIVATION_CODE_REGEX.test(code)) {
      return problem(400, "ACTIVATION_CODE_MALFORMED", "Mã kích hoạt chưa đúng định dạng. Kiểm tra lại mã in trên bao bì (dạng CC-PLUS-XXXXXXXXXX).", {
        expectedFormat: "CC-<PKG>-<10 ký tự>",
      });
    }
    const found = ACTIVATION_CODES.find((c) => c.code === code);
    if (!found) {
      return problem(422, "ACTIVATION_CODE_INVALID", "Mã kích hoạt không đúng. Kiểm tra lại mã in trên bao bì.");
    }
    if (found.status === "REDEEMED") {
      return problem(409, "ACTIVATION_CODE_ALREADY_USED", "Mã này đã được sử dụng.");
    }
    if (found.status === "EXPIRED") {
      return problem(410, "ACTIVATION_CODE_EXPIRED", "Mã đã hết hạn kích hoạt.");
    }

    const activatedAt = new Date();
    const expiresAt = new Date(activatedAt.getTime() + found.validityDays * 24 * 60 * 60 * 1000);
    const batch: CreditBatch = {
      batchId: crypto.randomUUID(),
      packageCode: found.packageCode,
      initialAmount: found.creditAmount,
      remainingAmount: found.creditAmount,
      activatedAt: activatedAt.toISOString(),
      expiresAt: expiresAt.toISOString(),
      remainingSeconds: found.validityDays * 24 * 60 * 60,
    };
    state.batches.push(batch);
    state.ledger.unshift({
      id: crypto.randomUUID(),
      type: "GRANT",
      amount: found.creditAmount,
      balanceAfter: availableBalance() + found.creditAmount,
      batchId: batch.batchId,
      packageCode: found.packageCode,
      refType: "ACTIVATION",
      note: null,
      createdAt: nowIso(),
    });

    const planFeatures = PLAN_FEATURES[found.packageCode];
    state.entitlement = {
      ...state.entitlement,
      ...planFeatures,
      currentPackage: found.packageCode,
      writeAccessUntil: expiresAt.toISOString(),
    };

    const result: ActivationResult = {
      packageCode: found.packageCode,
      packageName: found.packageName,
      creditsGranted: found.creditAmount,
      expiresAt: expiresAt.toISOString(),
      balanceAfter: availableBalance(),
    };
    return HttpResponse.json(result);
  }),

  http.get("/api/v1/credits/balance", async () => {
    await delay(150);
    const balance: CreditBalance = {
      availableBalance: availableBalance(),
      trialScansUsed: 3 - state.entitlement.trialScansRemaining,
      trialScansRemaining: state.entitlement.trialScansRemaining,
      batches: [...state.batches].sort((a, b) => a.expiresAt.localeCompare(b.expiresAt)),
    };
    return HttpResponse.json(balance);
  }),

  http.get("/api/v1/credits/ledger", async () => {
    await delay(200);
    return HttpResponse.json({ entries: state.ledger, hasMore: false, nextCursor: null });
  }),

  http.get("/api/v1/entitlements/me", async () => {
    await delay(150);
    return HttpResponse.json(state.entitlement);
  }),
];

export const worker = setupWorker(...handlers);
