
import { http, HttpResponse, delay } from "msw";
import { setupWorker } from "msw/browser";
import type {
  ActivationResult,
  BreedListResponse,
  CatSummary,
  CatProfileDraft,
  CreatedCat,
  CreditBalance,
  PhBandListResponse,
  SurveyAnswers,
} from "./types";


const BREEDS: BreedListResponse = {
  items: [
    { code: "BRITISH_SHORTHAIR", name: "Mèo Anh lông ngắn" },
    { code: "DOMESTIC_SHORTHAIR", name: "Mèo Ta" },
    { code: "PERSIAN", name: "Mèo Ba Tư" },
    { code: "RAGDOLL", name: "Ragdoll" },
    { code: "SIAMESE", name: "Mèo Xiêm" },
    { code: "BENGAL", name: "Bengal" },
    { code: "MAINE_COON", name: "Maine Coon" },
    { code: "SPHYNX", name: "Mèo Sphynx" },
    { code: "MUNCHKIN", name: "Munchkin" },
    { code: "SCOTTISH_FOLD", name: "Mèo Scotland xoắn tai" },
  ],
};

const phBandList: PhBandListResponse = [
  {
    code: "LOW",
    phMax: 6.0,
    severity: "WATCH",
    label: "Thấp rõ rệt (thiên axit)",
    colorToken: "color-ph-abnormal",
    sortOrder: 1,
  },
  {
    code: "SLIGHTLY_LOW",
    phMin: 6.0,
    phMax: 6.3,
    severity: "ATTENTION",
    label: "Hơi thấp (thiên axit)",
    colorToken: "color-ph-mild",
    sortOrder: 2,
  },
  {
    code: "IN_RANGE",
    phMin: 6.3,
    phMax: 6.6,
    severity: "NORMAL",
    label: "Trong khoảng tham chiếu",
    colorToken: "color-ph-normal",
    sortOrder: 3,
  },
  {
    code: "SLIGHTLY_HIGH",
    phMin: 6.6,
    phMax: 7.0,
    severity: "ATTENTION",
    label: "Hơi cao (thiên kiềm)",
    colorToken: "color-ph-mild",
    sortOrder: 4,
  },
  {
    code: "HIGH",
    phMin: 7.0,
    severity: "WATCH",
    label: "Cao rõ rệt (thiên kiềm)",
    colorToken: "color-ph-abnormal",
    sortOrder: 5,
  },
  {
    code: "INCONCLUSIVE",
    severity: "NEUTRAL",
    label: "Chưa đủ dữ liệu để kết luận",
    colorToken: "color-ph-unknown",
    sortOrder: 6,
  },
];

interface MockActivationCode {
  code: string;
  packageCode: string;
  packageName: string;
  creditAmount: number;
  validityDays: number;
  status: "ACTIVE" | "REDEEMED" | "EXPIRED";
}

const ACTIVATION_CODES: MockActivationCode[] = [
  {
    code: "CCPLUS7K3M9QX2RT",
    packageCode: "PLUS",
    packageName: "CATCHECK Plus",
    creditAmount: 10,
    validityDays: 7,
    status: "ACTIVE",
  },
  {
    code: "CCMINI3K9QX2RTWM",
    packageCode: "MINI",
    packageName: "CATCHECK Mini – Trial",
    creditAmount: 3,
    validityDays: 7,
    status: "ACTIVE",
  },
  {
    code: "CCDAILY8K3M9QX2A",
    packageCode: "DAILY",
    packageName: "CATCHECK Daily",
    creditAmount: 8,
    validityDays: 7,
    status: "ACTIVE",
  },
  {
    code: "CCMULTI6K3M9QX2BC",
    packageCode: "MULTI",
    packageName: "CATCHECK Multi",
    creditAmount: 16,
    validityDays: 7,
    status: "ACTIVE",
  },
  {
    code: "CCPLUS000000000U",
    packageCode: "PLUS",
    packageName: "CATCHECK Plus",
    creditAmount: 10,
    validityDays: 7,
    status: "REDEEMED",
  },
  {
    code: "CCPLUS000000000E",
    packageCode: "PLUS",
    packageName: "CATCHECK Plus",
    creditAmount: 10,
    validityDays: 7,
    status: "EXPIRED",
  },
];

const ACTIVATION_CODE_REGEX = /^CC[A-Z0-9]+[0-9A-HJKMNP-TV-Z]{10}$/;

function problem(status: number, code: string, detail: string, extra?: Record<string, unknown>) {
  return HttpResponse.json(
    {
      type: `https://catcheck.vn/problems/${code.toLowerCase()}`,
      title: code,
      status,
      detail,
      instance: "",
      errorCode: code,
      ...extra,
    },
    { status, headers: { "Content-Type": "application/problem+json; charset=utf-8" } },
  );
}

function makeCat(draft: CatProfileDraft, id: string): CreatedCat {
  const breed = BREEDS.items.find((b) => b.code === draft.breedCode);
  const birthDate = draft.birthDate || null;
  const ageMonths = birthDate
    ? Math.max(
        0,
        Math.floor((Date.now() - new Date(`${birthDate}T00:00:00`).getTime()) / (1000 * 60 * 60 * 24 * 30.44)),
      )
    : null;
  return {
    id,
    publicCode: `CC-VN-${id.slice(0, 6).toUpperCase()}`,
    name: draft.name,
    breedCode: draft.breedCode,
    breedName: breed?.name ?? draft.breedCode,
    sex: draft.sex || "UNKNOWN",
    neutered: draft.neutered,
    birthDate,
    ageMonths,
    weightKg: draft.weightKg ? Number(draft.weightKg) : null,
    avatarUrl: null,
  };
}

function emptyBalance(): CreditBalance {
  return { availableBalance: 0, trialScansUsed: 0, trialScansRemaining: 3, batches: [] };
}

const createdCats = new Map<string, CreatedCat>();

export const handlers = [
  http.get("/api/v1/reference/cat-breeds", async () => {
    await delay(150);
    return HttpResponse.json(BREEDS);
  }),

  http.get("/api/v1/reference/ph-bands", async () => {
    await delay(100);
    return HttpResponse.json(phBandList);
  }),

  http.post("/api/v1/cats", async ({ request }) => {
    await delay(300);
    const body = (await request.json()) as Partial<CatProfileDraft>;
    if (!body.name || !body.breedCode || !body.sex) {
      return problem(400, "VALIDATION_FAILED", "Vui lòng điền đầy đủ thông tin bắt buộc.");
    }
    const id = crypto.randomUUID();
    const cat = makeCat(body as CatProfileDraft, id);
    createdCats.set(id, cat);
    return HttpResponse.json(cat, {
      status: 201,
      headers: { Location: `/api/v1/cats/${id}` },
    });
  }),

  http.patch("/api/v1/cats/:catId", async ({ request, params }) => {
    await delay(250);
    const body = (await request.json()) as Partial<CatProfileDraft>;
    const id = String(params.catId);
    const existing = createdCats.get(id);
    const merged: CatProfileDraft = {
      name: body.name ?? existing?.name ?? "",
      breedCode: body.breedCode ?? existing?.breedCode ?? "",
      sex: body.sex ?? existing?.sex ?? "UNKNOWN",
      neutered: body.neutered ?? existing?.neutered ?? false,
      birthDate: body.birthDate ?? existing?.birthDate ?? "",
      weightKg: body.weightKg ?? (existing?.weightKg != null ? String(existing.weightKg) : ""),
      avatarFile: null,
    };
    const cat = makeCat(merged, id);
    createdCats.set(id, cat);
    return HttpResponse.json(cat);
  }),

  http.put("/api/v1/cats/:catId/avatar", async () => {
    await delay(400);
    return HttpResponse.json({
      avatarUrl: `https://mock.local/avatars/${crypto.randomUUID()}.jpg`,
    });
  }),

  http.post("/api/v1/cats/:catId/health-survey", async ({ request, params }) => {
    await delay(250);
    const body = (await request.json()) as { answers?: SurveyAnswers; skipped?: boolean };
    return HttpResponse.json(
      {
        id: crypto.randomUUID(),
        catId: String(params.catId),
        questionnaireVersion: "v1",
        answers: body.answers ?? {},
        skipped: Boolean(body.skipped),
        submittedAt: new Date().toISOString(),
      },
      { status: 201 },
    );
  }),

  http.post("/api/v1/activations", async ({ request }) => {
    await delay(500);
    const body = (await request.json()) as { code?: string };
    const code = (body.code ?? "").toUpperCase().replace(/[^A-Z0-9]/g, "");

    if (!ACTIVATION_CODE_REGEX.test(code)) {
      return problem(
        400,
        "ACTIVATION_CODE_MALFORMED",
        "Mã kích hoạt chưa đúng định dạng. Kiểm tra lại mã in trên bao bì (dạng CC-PLUS-XXXXXXXXXX).",
        { expectedFormat: "CC-<PKG>-<10 ký tự>" },
      );
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
    const result: ActivationResult = {
      packageCode: found.packageCode,
      packageName: found.packageName,
      creditsGranted: found.creditAmount,
      expiresAt: expiresAt.toISOString(),
      balanceAfter: found.creditAmount,
    };
    return HttpResponse.json(result);
  }),

  http.get("/api/v1/credits/balance", async () => {
    await delay(150);
    return HttpResponse.json(emptyBalance());
  }),

  http.get("/api/v1/cats/:catId/summary", async ({ params }) => {
    await delay(200);
    const id = String(params.catId);
    const cat =
      createdCats.get(id) ??
      makeCat(
        {
          name: "Luna",
          breedCode: "BRITISH_SHORTHAIR",
          sex: "FEMALE",
          neutered: true,
          birthDate: "2024-03-14",
          weightKg: "4.2",
          avatarFile: null,
        },
        id,
      );
    const summary: CatSummary = {
      catId: cat.id,
      lastScan: null,
      scanCount30d: 0,
      inRangeRatio30d: null,
      unacknowledgedFlagCount: 0,
      nextReminderAt: null,
      hasEnoughDataForTrend: false,
    };
    return HttpResponse.json(summary);
  }),
];

export const worker = setupWorker(...handlers);
