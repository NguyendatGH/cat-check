/**
 * MSW handlers + worker cho `features/export` — chế độ degraded khi backend chưa có (cùng quy
 * ước `features/onboarding/mocks.ts`). Mô phỏng job nền: `QUEUED` → (poll 2 lần) → `READY`.
 *
 * Endpoint mock (khớp p8 §8.4.10 nhóm J + D1 rút gọn):
 *  - GET  /api/v1/cats?status=ACTIVE        (D1, chọn mèo bước 1 wizard)
 *  - POST /api/v1/exports                   (J1)
 *  - GET  /api/v1/exports                   (J2)
 *  - GET  /api/v1/exports/:jobId            (J3, poll)
 *  - GET  /api/v1/exports/:jobId/download   (J4, trả PDF giả tối thiểu hợp lệ)
 */

import { http, HttpResponse, delay } from "msw";
import { setupWorker } from "msw/browser";
import type { Cat } from "@/entities/cat";
import type { ExportJob, ExportRangePreset } from "./types";
import type { CatPage } from "./types";

const LUNA_ID = "00000000-0000-7000-8000-000000000001";
const MOCHI_ID = "00000000-0000-7000-8000-000000000002";

const DEMO_CATS: Cat[] = [
  {
    id: LUNA_ID,
    publicCode: "CC-VN-DEMO01",
    name: "Luna",
    birthDate: "2023-03-14",
    approxAgeMonths: null,
    ageMonths: 30,
    breedCode: "BRITISH_SHORTHAIR",
    breedName: "Mèo Anh lông ngắn",
    breedOther: null,
    coatColor: "Xanh xám",
    sex: "FEMALE",
    neutered: true,
    weightKg: 4.2,
    weightUpdatedAt: null,
    avatarUrl: null,
    status: "ACTIVE",
    isPrimary: true,
    notes: null,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    lastScanAt: null,
    lastClassification: null,
    unacknowledgedFlagCount: 0,
  },
  {
    id: MOCHI_ID,
    publicCode: "CC-VN-DEMO02",
    name: "Mochi",
    birthDate: "2024-01-20",
    approxAgeMonths: null,
    ageMonths: 20,
    breedCode: "DOMESTIC_SHORTHAIR",
    breedName: "Mèo Ta",
    breedOther: null,
    coatColor: "Cam trắng",
    sex: "MALE",
    neutered: true,
    weightKg: 3.8,
    weightUpdatedAt: null,
    avatarUrl: null,
    status: "ACTIVE",
    isPrimary: false,
    notes: null,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    lastScanAt: null,
    lastClassification: null,
    unacknowledgedFlagCount: 0,
  },
];

let jobCounter = 0;
const jobs = new Map<string, ExportJob & { pollCount: number }>();

function toExportJob(job: ExportJob & { pollCount: number }): ExportJob {
  return {
    jobId: job.jobId,
    catId: job.catId,
    documentCode: job.documentCode,
    status: job.status,
    rangeFrom: job.rangeFrom,
    rangeTo: job.rangeTo,
    rangePreset: job.rangePreset,
    pageCount: job.pageCount,
    scanCount: job.scanCount,
    downloadCount: job.downloadCount,
    failureReason: job.failureReason,
    requestedAt: job.requestedAt,
    completedAt: job.completedAt,
    expiresAt: job.expiresAt,
  };
}

function resolveRange(preset: ExportRangePreset, from: string | null, to: string | null): { from: string; to: string } {
  const today = new Date().toISOString().slice(0, 10);
  if (preset === "CUSTOM" && from && to) return { from, to };
  const days = preset === "7D" ? 6 : preset === "90D" ? 89 : 29;
  const fromDate = new Date(Date.now() - days * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
  return { from: fromDate, to: today };
}

function problem(status: number, code: string, detail: string) {
  return HttpResponse.json(
    {
      type: `https://catcheck.vn/problems/${code.toLowerCase().replace(/_/g, "-")}`,
      title: code,
      status,
      detail,
      instance: "/mock",
      // Tên field là `errorCode`, KHÔNG phải `code`: `GlobalExceptionHandler` của backend gọi
      // `problemDetail.setProperty("errorCode", ...)`. Mock từng phát `code` nên khớp với
      // wrapper client cũng đang đọc sai — hai cái sai che nhau, chỉ lộ ra khi chạy server thật.
      errorCode: code,
    },
    { status },
  );
}

export const handlers = [
  http.get("/api/v1/cats", async ({ request }) => {
    await delay(100);
    const status = new URL(request.url).searchParams.get("status");
    const items = status ? DEMO_CATS.filter((c) => c.status === status) : DEMO_CATS;
    const body: CatPage<Cat> = { items, page: { limit: 20, nextCursor: null, hasMore: false } };
    return HttpResponse.json(body);
  }),

  http.post("/api/v1/exports", async ({ request }) => {
    await delay(300);
    const body = (await request.json()) as {
      catId: string;
      rangeFrom: string | null;
      rangeTo: string | null;
      rangePreset: ExportRangePreset;
    };
    const hasActive = [...jobs.values()].some((j) => j.pollCount < 2);
    if (hasActive) {
      return problem(409, "EXPORT_JOB_IN_PROGRESS", "Bạn đã có một bản xuất đang xử lý.");
    }
    jobCounter += 1;
    const jobId = `00000000-0000-7000-c000-${String(jobCounter).padStart(12, "0")}`;
    const range = resolveRange(body.rangePreset, body.rangeFrom, body.rangeTo);
    const job: ExportJob & { pollCount: number } = {
      jobId,
      catId: body.catId,
      documentCode: `CC-EXP-${String(new Date().getFullYear())}-${String(jobCounter).padStart(6, "0")}`,
      status: "QUEUED",
      rangeFrom: range.from,
      rangeTo: range.to,
      rangePreset: body.rangePreset,
      pageCount: null,
      scanCount: null,
      downloadCount: 0,
      failureReason: null,
      requestedAt: new Date().toISOString(),
      completedAt: null,
      expiresAt: null,
      pollCount: 0,
    };
    jobs.set(jobId, job);
    return HttpResponse.json(job, { status: 202 });
  }),

  http.get("/api/v1/exports", async () => {
    await delay(100);
    const items = [...jobs.values()]
      .sort((a, b) => new Date(b.requestedAt).getTime() - new Date(a.requestedAt).getTime())
      .map(toExportJob);
    return HttpResponse.json({ items, hasMore: false, nextCursor: null });
  }),

  http.get("/api/v1/exports/:jobId", async ({ params }) => {
    await delay(200);
    const job = jobs.get(String(params.jobId));
    if (!job) {
      return problem(404, "EXPORT_JOB_NOT_FOUND", "Không tìm thấy bản xuất này.");
    }
    job.pollCount += 1;
    if (job.status === "QUEUED") job.status = "RUNNING";
    else if (job.status === "RUNNING" && job.pollCount >= 2) {
      job.status = "READY";
      job.pageCount = 2;
      job.scanCount = 14;
      job.completedAt = new Date().toISOString();
      job.expiresAt = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString();
    }
    return HttpResponse.json(toExportJob(job));
  }),

  http.get("/api/v1/exports/:jobId/download", async ({ params }) => {
    await delay(150);
    const job = jobs.get(String(params.jobId));
    if (!job || job.status !== "READY") {
      return problem(409, "EXPORT_NOT_READY", "Bản xuất chưa sẵn sàng.");
    }
    job.downloadCount += 1;
    const pdfHeader = "%PDF-1.4\n%mock CatCheck export\n";
    return new HttpResponse(pdfHeader, {
      headers: {
        "Content-Type": "application/pdf",
        "Content-Disposition": `attachment; filename="${job.documentCode ?? "catcheck-export"}.pdf"`,
      },
    });
  }),
];

export const worker = setupWorker(...handlers);
