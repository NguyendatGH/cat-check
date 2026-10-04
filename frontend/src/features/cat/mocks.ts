/**
 * MSW handlers cho `features/cat` — chế độ degraded khi backend chưa có (W1 song song).
 * `CatController` HTTP thật CHƯA tồn tại ở backend hiện tại (chỉ có domain/application/DTO
 * — xem `docs/handovers/A3.md` mục 3) nên FE dựng theo đúng hợp đồng `p8 §8.4.4` (D1-D20).
 * W3 nối API thật: xoá import handlers này khỏi nơi wire (dev-only), `hooks.ts` giữ nguyên.
 *
 * Endpoint mock (khớp p8 §8.4.4 + F2):
 *  - GET    /api/v1/cats                          (D1)
 *  - POST   /api/v1/cats                          (D2)
 *  - GET    /api/v1/cats/:catId                   (D3)
 *  - PATCH  /api/v1/cats/:catId                   (D4)
 *  - DELETE /api/v1/cats/:catId                   (D5)
 *  - POST   /api/v1/cats/:catId/archive           (D6)
 *  - POST   /api/v1/cats/:catId/unarchive         (D7)
 *  - PUT    /api/v1/cats/:catId/primary           (D8)
 *  - PUT    /api/v1/cats/:catId/avatar            (D10)
 *  - DELETE /api/v1/cats/:catId/avatar            (D11)
 *  - GET    /api/v1/cats/:catId/health-survey     (D15)
 *  - POST   /api/v1/cats/:catId/health-survey     (D14)
 *  - GET    /api/v1/cats/:catId/notes             (D16)
 *  - POST   /api/v1/cats/:catId/notes             (D17)
 *  - PATCH  /api/v1/cat-notes/:noteId             (D18)
 *  - DELETE /api/v1/cat-notes/:noteId             (D19)
 *  - POST   /api/v1/cats/:catId/clinical-signs    (D20)
 *  - GET    /api/v1/reference/cat-breeds          (F2)
 */

import { http, HttpResponse, delay } from "msw";
import { setupWorker } from "msw/browser";
import type { Cat, CatHealthSurvey, CatNote } from "@/entities/cat";

const BREEDS = [
  { code: "BRITISH_SHORTHAIR", name: "Mèo Anh lông ngắn", popular: true },
  { code: "DOMESTIC_SHORTHAIR", name: "Mèo Ta", popular: true },
  { code: "PERSIAN", name: "Mèo Ba Tư", popular: true },
  { code: "RAGDOLL", name: "Ragdoll", popular: false },
  { code: "SIAMESE", name: "Mèo Xiêm", popular: false },
  { code: "BENGAL", name: "Bengal", popular: false },
  { code: "MAINE_COON", name: "Maine Coon", popular: false },
  { code: "SPHYNX", name: "Mèo Sphynx", popular: false },
  { code: "MUNCHKIN", name: "Munchkin", popular: false },
  { code: "SCOTTISH_FOLD", name: "Mèo Scotland xoắn tai", popular: false },
];

function breedName(code: string | null): string | null {
  return BREEDS.find((b) => b.code === code)?.name ?? code;
}

function nowIso(): string {
  return new Date().toISOString();
}

function ageMonthsFrom(birthDate: string | null, approxAgeMonths: number | null): number | null {
  if (approxAgeMonths !== null) return approxAgeMonths;
  if (!birthDate) return null;
  const months = (Date.now() - new Date(`${birthDate}T00:00:00`).getTime()) / (1000 * 60 * 60 * 24 * 30.44);
  return Math.max(0, Math.floor(months));
}

const DEMO_CAT_ID = "00000000-0000-7000-8000-000000000001";

const cats = new Map<string, Cat>([
  [
    DEMO_CAT_ID,
    {
      id: DEMO_CAT_ID,
      publicCode: "CC-VN-DEMO01",
      name: "Luna",
      birthDate: "2023-03-14",
      approxAgeMonths: null,
      ageMonths: ageMonthsFrom("2023-03-14", null),
      breedCode: "BRITISH_SHORTHAIR",
      breedName: breedName("BRITISH_SHORTHAIR"),
      breedOther: null,
      coatColor: "Xanh xám",
      sex: "FEMALE",
      neutered: true,
      weightKg: 4.2,
      weightUpdatedAt: nowIso(),
      avatarUrl: null,
      status: "ACTIVE",
      isPrimary: true,
      notes: null,
      createdAt: nowIso(),
      updatedAt: nowIso(),
      lastScanAt: null,
      lastClassification: null,
      unacknowledgedFlagCount: 0,
    },
  ],
]);

const notes = new Map<string, CatNote[]>([[DEMO_CAT_ID, []]]);
const surveys = new Map<string, CatHealthSurvey>();

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

function pageOf<T>(items: T[]) {
  return { items, page: { limit: items.length, nextCursor: null, hasMore: false } };
}

export const handlers = [
  http.get("/api/v1/reference/cat-breeds", async () => {
    await delay(120);
    return HttpResponse.json({ items: BREEDS });
  }),

  http.get("/api/v1/cats", async ({ request }) => {
    await delay(200);
    const url = new URL(request.url);
    const status = url.searchParams.get("status") ?? "ACTIVE";
    const items = [...cats.values()]
      .filter((c) => c.status === status)
      .sort((a, b) => (b.isPrimary ? 1 : 0) - (a.isPrimary ? 1 : 0) || a.name.localeCompare(b.name));
    return HttpResponse.json(pageOf(items));
  }),

  http.post("/api/v1/cats", async ({ request }) => {
    await delay(300);
    const body = (await request.json()) as Record<string, unknown>;
    if (!body.name || !body.sex) {
      return problem(400, "VALIDATION_FAILED", "Vui lòng điền đầy đủ thông tin bắt buộc.");
    }
    const id = crypto.randomUUID();
    const isFirst = cats.size === 0;
    const birthDate = (body.birthDate as string | null) ?? null;
    const approxAgeMonths = (body.approxAgeMonths as number | null) ?? null;
    const cat: Cat = {
      id,
      publicCode: `CC-VN-${id.slice(0, 6).toUpperCase()}`,
      name: body.name as string,
      birthDate,
      approxAgeMonths,
      ageMonths: ageMonthsFrom(birthDate, approxAgeMonths),
      breedCode: (body.breedCode as string | null) ?? null,
      breedName: breedName((body.breedCode as string | null) ?? null),
      breedOther: (body.breedOther as string | null) ?? null,
      coatColor: (body.coatColor as string | null) ?? null,
      sex: body.sex as Cat["sex"],
      neutered: (body.neutered as boolean | null) ?? null,
      weightKg: (body.weightKg as number | null) ?? null,
      weightUpdatedAt: body.weightKg ? nowIso() : null,
      avatarUrl: null,
      status: "ACTIVE",
      isPrimary: isFirst || Boolean(body.isPrimary),
      notes: (body.notes as string | null) ?? null,
      createdAt: nowIso(),
      updatedAt: nowIso(),
      lastScanAt: null,
      lastClassification: null,
      unacknowledgedFlagCount: 0,
    };
    if (cat.isPrimary) {
      for (const existing of cats.values()) existing.isPrimary = false;
    }
    cats.set(id, cat);
    notes.set(id, []);
    return HttpResponse.json(cat, { status: 201, headers: { Location: `/api/v1/cats/${id}` } });
  }),

  http.get("/api/v1/cats/:catId", async ({ params }) => {
    await delay(150);
    const cat = cats.get(String(params.catId));
    if (!cat) return problem(404, "CAT_NOT_FOUND", "Không tìm thấy hồ sơ mèo.");
    return HttpResponse.json(cat);
  }),

  http.patch("/api/v1/cats/:catId", async ({ request, params }) => {
    await delay(250);
    const id = String(params.catId);
    const existing = cats.get(id);
    if (!existing) return problem(404, "CAT_NOT_FOUND", "Không tìm thấy hồ sơ mèo.");
    const body = (await request.json()) as Record<string, unknown>;
    const birthDate = "birthDate" in body ? (body.birthDate as string | null) : existing.birthDate;
    const approxAgeMonths =
      "approxAgeMonths" in body ? (body.approxAgeMonths as number | null) : existing.approxAgeMonths;
    const merged: Cat = {
      ...existing,
      name: (body.name as string | undefined) ?? existing.name,
      birthDate: body.birthDate !== undefined ? birthDate : existing.birthDate,
      approxAgeMonths: body.approxAgeMonths !== undefined ? approxAgeMonths : existing.approxAgeMonths,
      breedCode: body.breedCode !== undefined ? (body.breedCode as string | null) : existing.breedCode,
      breedOther: body.breedOther !== undefined ? (body.breedOther as string | null) : existing.breedOther,
      coatColor: body.coatColor !== undefined ? (body.coatColor as string | null) : existing.coatColor,
      sex: (body.sex as Cat["sex"] | undefined) ?? existing.sex,
      neutered: body.neutered !== undefined ? (body.neutered as boolean | null) : existing.neutered,
      weightKg: body.weightKg !== undefined ? (body.weightKg as number | null) : existing.weightKg,
      notes: body.notes !== undefined ? (body.notes as string | null) : existing.notes,
      updatedAt: nowIso(),
    };
    merged.breedName = breedName(merged.breedCode);
    merged.ageMonths = ageMonthsFrom(merged.birthDate, merged.approxAgeMonths);
    cats.set(id, merged);
    return HttpResponse.json(merged);
  }),

  http.delete("/api/v1/cats/:catId", async ({ params }) => {
    await delay(200);
    const id = String(params.catId);
    if (!cats.has(id)) return problem(404, "CAT_NOT_FOUND", "Không tìm thấy hồ sơ mèo.");
    cats.delete(id);
    return new HttpResponse(null, { status: 204 });
  }),

  http.post("/api/v1/cats/:catId/archive", async ({ params }) => {
    await delay(200);
    const cat = cats.get(String(params.catId));
    if (!cat) return problem(404, "CAT_NOT_FOUND", "Không tìm thấy hồ sơ mèo.");
    if (cat.status === "ARCHIVED") {
      return problem(409, "CAT_ALREADY_ARCHIVED", "Hồ sơ đã ở trạng thái lưu trữ.");
    }
    cat.status = "ARCHIVED";
    cat.isPrimary = false;
    cat.updatedAt = nowIso();
    return HttpResponse.json(cat);
  }),

  http.post("/api/v1/cats/:catId/unarchive", async ({ params }) => {
    await delay(200);
    const cat = cats.get(String(params.catId));
    if (!cat) return problem(404, "CAT_NOT_FOUND", "Không tìm thấy hồ sơ mèo.");
    cat.status = "ACTIVE";
    cat.updatedAt = nowIso();
    return HttpResponse.json(cat);
  }),

  http.put("/api/v1/cats/:catId/primary", async ({ params }) => {
    await delay(200);
    const id = String(params.catId);
    const cat = cats.get(id);
    if (!cat) return problem(404, "CAT_NOT_FOUND", "Không tìm thấy hồ sơ mèo.");
    if (cat.status === "ARCHIVED") {
      return problem(409, "CAT_PRIMARY_REQUIRES_ACTIVE", "Chỉ mèo đang theo dõi mới đặt làm mặc định được.");
    }
    if (cat.isPrimary) {
      return problem(409, "CAT_ALREADY_PRIMARY", "Mèo này đã là mèo mặc định.");
    }
    let previousPrimaryCatId: string | null = null;
    for (const other of cats.values()) {
      if (other.isPrimary) previousPrimaryCatId = other.id;
      other.isPrimary = other.id === id;
    }
    return HttpResponse.json({ catId: id, previousPrimaryCatId });
  }),

  http.put("/api/v1/cats/:catId/avatar", async ({ params }) => {
    await delay(400);
    const cat = cats.get(String(params.catId));
    const avatarUrl = `https://mock.local/cat-avatars/${crypto.randomUUID()}.jpg`;
    if (cat) {
      cat.avatarUrl = avatarUrl;
      cat.updatedAt = nowIso();
    }
    return HttpResponse.json({ avatarUrl });
  }),

  http.delete("/api/v1/cats/:catId/avatar", async ({ params }) => {
    await delay(200);
    const cat = cats.get(String(params.catId));
    if (cat) {
      cat.avatarUrl = null;
      cat.updatedAt = nowIso();
    }
    return new HttpResponse(null, { status: 200 });
  }),

  http.get("/api/v1/cats/:catId/health-survey", async ({ params }) => {
    await delay(150);
    const survey = surveys.get(String(params.catId));
    if (!survey) return problem(404, "SURVEY_NOT_FOUND", "Mèo chưa có bản khảo sát nào.");
    return HttpResponse.json(survey);
  }),

  http.post("/api/v1/cats/:catId/health-survey", async ({ request, params }) => {
    await delay(250);
    const catId = String(params.catId);
    const body = (await request.json()) as {
      questionnaireVersion?: string;
      answers?: CatHealthSurvey["answers"];
      skipped?: boolean;
    };
    const survey: CatHealthSurvey = {
      id: crypto.randomUUID(),
      catId,
      questionnaireVersion: body.questionnaireVersion ?? "v1",
      answers: body.answers ?? {
        litterType: "",
        urinaryHistory: "",
        dietType: "",
        urinationFrequency: "",
        symptoms: [],
      },
      skipped: Boolean(body.skipped),
      submittedAt: body.skipped ? null : nowIso(),
      createdAt: nowIso(),
    };
    surveys.set(catId, survey);
    return HttpResponse.json(survey, { status: 201 });
  }),

  http.get("/api/v1/cats/:catId/notes", async ({ params }) => {
    await delay(150);
    const list = notes.get(String(params.catId)) ?? [];
    const sorted = [...list].sort((a, b) => (b.occurredOn ?? b.createdAt).localeCompare(a.occurredOn ?? a.createdAt));
    return HttpResponse.json(pageOf(sorted));
  }),

  http.post("/api/v1/cats/:catId/notes", async ({ request, params }) => {
    await delay(200);
    const catId = String(params.catId);
    if (!cats.has(catId)) return problem(404, "CAT_NOT_FOUND", "Không tìm thấy hồ sơ mèo.");
    const body = (await request.json()) as {
      noteType?: CatNote["noteType"];
      body?: string;
      occurredOn?: string | null;
    };
    if (!body.body) return problem(400, "VALIDATION_FAILED", "Nội dung ghi chú không được để trống.");
    const note: CatNote = {
      id: crypto.randomUUID(),
      catId,
      noteType: body.noteType ?? "GENERAL",
      body: body.body,
      occurredOn: body.occurredOn ?? null,
      scanId: null,
      createdAt: nowIso(),
      updatedAt: nowIso(),
    };
    notes.set(catId, [...(notes.get(catId) ?? []), note]);
    return HttpResponse.json(note, { status: 201 });
  }),

  http.patch("/api/v1/cat-notes/:noteId", async ({ request, params }) => {
    await delay(200);
    const noteId = String(params.noteId);
    let found: CatNote | undefined;
    for (const [catId, list] of notes.entries()) {
      const idx = list.findIndex((n) => n.id === noteId);
      if (idx >= 0) {
        const body = (await request.json()) as Partial<Pick<CatNote, "noteType" | "body" | "occurredOn">>;
        const updated: CatNote = {
          ...list[idx],
          ...(body.noteType !== undefined ? { noteType: body.noteType } : {}),
          ...(body.body !== undefined ? { body: body.body } : {}),
          ...(body.occurredOn !== undefined ? { occurredOn: body.occurredOn } : {}),
          updatedAt: nowIso(),
        };
        const nextList = [...list];
        nextList[idx] = updated;
        notes.set(catId, nextList);
        found = updated;
      }
    }
    if (!found) return problem(404, "CAT_NOTE_NOT_FOUND", "Không tìm thấy ghi chú.");
    return HttpResponse.json(found);
  }),

  http.delete("/api/v1/cat-notes/:noteId", async ({ params }) => {
    await delay(200);
    const noteId = String(params.noteId);
    for (const [catId, list] of notes.entries()) {
      if (list.some((n) => n.id === noteId)) {
        notes.set(
          catId,
          list.filter((n) => n.id !== noteId),
        );
        return new HttpResponse(null, { status: 204 });
      }
    }
    return problem(404, "CAT_NOTE_NOT_FOUND", "Không tìm thấy ghi chú.");
  }),

  http.post("/api/v1/cats/:catId/clinical-signs", async ({ request, params }) => {
    await delay(300);
    const catId = String(params.catId);
    if (!cats.has(catId)) return problem(404, "CAT_NOT_FOUND", "Không tìm thấy hồ sơ mèo.");
    const body = (await request.json()) as { signs?: string[] };
    if (!body.signs || body.signs.length === 0) {
      return problem(400, "CLINICAL_SIGN_INVALID", "Vui lòng chọn ít nhất một dấu hiệu.");
    }
    // URGENT giả lập: bất kỳ dấu hiệu nào trong nhóm khẩn cấp p15 EMERGENCY đều bắn cờ.
    const urgentSigns = ["STRAINING", "NO_URINE", "CRYING", "BLOOD_VISIBLE"];
    const triggered = body.signs.some((s) => urgentSigns.includes(s));
    return HttpResponse.json(
      {
        reportId: crypto.randomUUID(),
        triggeredFlag: triggered
          ? { flagId: crypto.randomUUID(), ruleCode: "URGENT_CLINICAL_SIGN", severity: "URGENT" }
          : null,
      },
      { status: 201 },
    );
  }),
];

export const worker = setupWorker(...handlers);
