/**
 * MSW handlers + worker cho `features/scan` — chế độ degraded khi backend chưa có (cùng quy ước
 * `features/onboarding/mocks.ts`). W3 thay bằng API thật: xoá import handlers này khỏi bootstrap;
 * `hooks.ts` giữ nguyên vì đã theo hợp đồng p8 §8.5.4.
 *
 * Endpoint mock (khớp p8 §8.5.4 nhóm E + D1 rút gọn cho bước chọn mèo):
 *  - GET  /api/v1/scan/config
 *  - GET  /api/v1/cats?status=ACTIVE                (D1, rút gọn — chỉ để chọn mèo trước khi quét)
 *  - POST /api/v1/scans                             (E1, multipart)
 *  - GET  /api/v1/scans/:scanId                     (E4)
 *  - GET  /api/v1/scans/by-request/:scanRequestId   (E6)
 *  - POST /api/v1/scans/:scanId/reassign-cat        (E9)
 *  - POST /api/v1/scans/:scanId/dispute             (E10)
 *  - DELETE /api/v1/scans/:scanId/dispute           (E11)
 *  - DELETE /api/v1/scans/:scanId                   (E8)
 *
 * Hai mèo demo dùng chung id với `features/cat/mocks.ts` (Luna) để nhất quán khi cả hai bộ mock
 * cùng chạy — không import lẫn nhau (boundaries cấm feature → feature), chỉ trùng GIÁ TRỊ hằng.
 */

import { http, HttpResponse, delay } from "msw";
import { setupWorker } from "msw/browser";
import type { Cat } from "@/entities/cat";
import type { ScanClassification, ScanResult } from "@/entities/scan-result";
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

function catNameOf(catId: string | null): string | null {
  return DEMO_CATS.find((c) => c.id === catId)?.name ?? null;
}

/**
 * Bảng màu placeholder `CHART-PLACEHOLDER-v1` (ORCHESTRATOR §2) rút gọn cho mô phỏng — CHỈ dùng
 * để tạo dữ liệu demo nhất quán, KHÔNG phải nguồn sự thật hiển thị (đó luôn là
 * `entities/ph-bands`, tra qua API thật `/reference/ph-bands`).
 */
function classify(value: number): ScanClassification {
  if (value < 6.0) return "LOW";
  if (value < 6.3) return "SLIGHTLY_LOW";
  if (value <= 6.6) return "IN_RANGE";
  if (value <= 7.0) return "SLIGHTLY_HIGH";
  return "HIGH";
}

let scanCounter = 0;
const scansById = new Map<string, ScanResult>();
const scansByRequest = new Map<string, string>();

function nextScanId(): string {
  scanCounter += 1;
  return `00000000-0000-7000-9000-${String(scanCounter).padStart(12, "0")}`;
}

function buildScanResult(metadata: {
  scanRequestId: string;
  catId: string | null;
  assignment: string;
  capturedAt: string;
  captureSource?: string;
}): ScanResult {
  const seed = (Date.now() + scanCounter * 137) % 100;
  const value = Number((6.1 + (seed / 100) * 1.1).toFixed(2));
  const classification = classify(value);
  const inconclusive = classification === "INCONCLUSIVE";
  const scanId = inconclusive ? null : nextScanId();

  const result: ScanResult = {
    scanId,
    scanRequestId: metadata.scanRequestId,
    catId: metadata.assignment === "SHARED_UNKNOWN" ? null : metadata.catId,
    catName: metadata.assignment === "SHARED_UNKNOWN" ? null : catNameOf(metadata.catId),
    assignment: metadata.assignment === "SHARED_UNKNOWN" ? "SHARED_UNKNOWN" : "ASSIGNED",
    capturedAt: metadata.capturedAt,
    status: inconclusive ? "INCONCLUSIVE" : "ANALYZED",
    phValue: value,
    phLow: Number((value - 0.1).toFixed(2)),
    phHigh: Number((value + 0.1).toFixed(2)),
    classification,
    bandCode: classification,
    labelKey: `phBand.${classification}.label`,
    nearBoundary: Math.abs(value - 6.3) < 0.05 || Math.abs(value - 6.6) < 0.05,
    confidence: 0.82,
    confidenceBand: "HIGH",
    matchPercent: 96,
    displayHex: "#7CB342",
    calibrationMethod: "SUBSTRATE_WB",
    captureSource: (metadata.captureSource as ScanResult["captureSource"]) ?? "CAMERA",
    chartCode: "CHART-PLACEHOLDER-v1",
    chartVersion: 1,
    chartIsPlaceholder: true,
    engineVersion: "1.0.0-mock",
    qualityFlags: [],
    creditCharged: true,
    creditBalanceAfter: 6,
    isTrial: false,
    imageStored: true,
    imageUrl: scanId ? `/api/v1/scans/${scanId}/image` : null,
    imageRetainedUntil: null,
    storeImageReason: "STANDARD",
    reassignableUntil: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
    reassignRemaining: 3,
    triggeredFlags: [],
    disclaimerKey: "legal.disclaimer.short",
    emergencyDisclaimerKey: "legal.disclaimer.emergency",
    processingMs: 420,
    retryHintKey: inconclusive ? "scan.retry.generic" : null,
    disputedAt: null,
    disputedNote: null,
  };

  if (scanId) {
    scansById.set(scanId, result);
  }
  scansByRequest.set(metadata.scanRequestId, scanId ?? metadata.scanRequestId);
  if (!scanId) {
    scansByRequest.set(metadata.scanRequestId, `pending:${metadata.scanRequestId}`);
  }
  return result;
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
  http.get("/api/v1/scan/config", async () => {
    await delay(100);
    return HttpResponse.json({
      maxEdgePx: 4096,
      minEdgePx: 640,
      jpegQuality: 0.9,
      cropMarginPct: 5,
      maxBytes: 10 * 1024 * 1024,
      acceptedTypes: ["image/jpeg", "image/png", "image/webp"],
      requireCalibratedChart: false,
      precheckThresholds: {
        blurVarMin: 80,
        meanLumaMin: 60,
        meanLumaMax: 210,
        clipHighMax: 0.02,
        tiltDegMax: 15,
      },
      minResultConfidence: 0.4,
      activeChart: { code: "CHART-PLACEHOLDER-v1", version: 1, isPlaceholder: true },
    });
  }),

  http.get("/api/v1/cats", async ({ request }) => {
    await delay(100);
    const status = new URL(request.url).searchParams.get("status");
    const items = status ? DEMO_CATS.filter((c) => c.status === status) : DEMO_CATS;
    const body: CatPage<Cat> = { items, page: { limit: 20, nextCursor: null, hasMore: false } };
    return HttpResponse.json(body);
  }),

  http.post("/api/v1/scans", async ({ request }) => {
    await delay(600);
    const idempotencyKey = request.headers.get("Idempotency-Key");
    const form = await request.formData();
    const metadataPart = form.get("metadata");
    if (!metadataPart || !idempotencyKey) {
      return problem(400, "SCAN_METADATA_INVALID", "Thiếu metadata hoặc Idempotency-Key.");
    }
    const metadata = JSON.parse(await (metadataPart as Blob).text()) as {
      scanRequestId: string;
      catId: string | null;
      assignment: string;
      capturedAt: string;
      captureSource?: string;
    };
    const existingId = scansByRequest.get(metadata.scanRequestId);
    if (existingId && scansById.has(existingId)) {
      return HttpResponse.json(scansById.get(existingId));
    }
    const result = buildScanResult(metadata);
    return HttpResponse.json(result);
  }),

  http.get("/api/v1/scans/by-request/:scanRequestId", async ({ params }) => {
    await delay(100);
    const scanRequestId = String(params.scanRequestId);
    const scanId = scansByRequest.get(scanRequestId);
    if (!scanId || !scansById.has(scanId)) {
      return problem(404, "SCAN_NOT_FOUND", "Chưa có kết quả cho scanRequestId này.");
    }
    return HttpResponse.json(scansById.get(scanId));
  }),

  http.get("/api/v1/scans/:scanId", async ({ params }) => {
    await delay(100);
    const scan = scansById.get(String(params.scanId));
    if (!scan) {
      return problem(404, "SCAN_NOT_FOUND", "Không tìm thấy lần quét này.");
    }
    return HttpResponse.json(scan);
  }),

  http.get("/api/v1/scans/:scanId/analysis", async ({ params }) => {
    await delay(150);
    const scan = scansById.get(String(params.scanId));
    if (!scan) {
      return problem(404, "SCAN_NOT_FOUND", "Không tìm thấy lần quét này.");
    }
    return HttpResponse.json({
      labL: 62.4,
      labA: -18.2,
      labB: 24.6,
      labSpreadDe00: 2.1,
      blobCount: 18,
      indicatorPixelRatio: 0.08,
      substrateLabL: 78.3,
      substrateLabA: 0.4,
      substrateLabB: 1.1,
      deltaEMin: 3.2,
      perpResidualDe00: 0.9,
      calibrationMethod: scan.calibrationMethod,
      calibrationResidualDe00: 1.4,
      qualityMetrics: { blurVar: 210.5, meanLuma: 142.3, clipHigh: 0.001, clipLow: 0.004 },
      chartCode: scan.chartCode,
      chartVersion: scan.chartVersion,
      engineVersion: scan.engineVersion,
      computedAt: scan.capturedAt,
      processingMs: scan.processingMs,
      recomputeOf: null,
    });
  }),

  http.post("/api/v1/scans/:scanId/reassign-cat", async ({ params, request }) => {
    await delay(200);
    const scan = scansById.get(String(params.scanId));
    if (!scan) {
      return problem(404, "SCAN_NOT_FOUND", "Không tìm thấy lần quét này.");
    }
    if (scan.reassignRemaining <= 0) {
      return problem(409, "SCAN_REASSIGN_LIMIT_REACHED", "Đã hết lượt gán lại (tối đa 3 lần/24h).");
    }
    const body = (await request.json()) as { toCatId?: string; toAssignment?: string };
    const fromCatId = scan.catId;
    const toShared = body.toAssignment === "SHARED_UNKNOWN";
    scan.catId = toShared ? null : (body.toCatId ?? null);
    scan.catName = toShared ? null : catNameOf(scan.catId);
    scan.assignment = toShared ? "SHARED_UNKNOWN" : "ASSIGNED";
    scan.reassignRemaining -= 1;
    return HttpResponse.json({
      scanId: scan.scanId,
      fromCatId,
      toCatId: scan.catId,
      reassignRemaining: scan.reassignRemaining,
    });
  }),

  http.post("/api/v1/scans/:scanId/dispute", async ({ params, request }) => {
    await delay(150);
    const scan = scansById.get(String(params.scanId));
    if (!scan) {
      return problem(404, "SCAN_NOT_FOUND", "Không tìm thấy lần quét này.");
    }
    const body = (await request.json().catch(() => null)) as { note?: string } | null;
    scan.disputedAt = new Date().toISOString();
    scan.disputedNote = body?.note ?? null;
    return HttpResponse.json({ scanId: scan.scanId, disputedAt: scan.disputedAt, excludedFromTrends: true });
  }),

  http.delete("/api/v1/scans/:scanId/dispute", async ({ params }) => {
    await delay(150);
    const scan = scansById.get(String(params.scanId));
    if (scan) {
      scan.disputedAt = null;
      scan.disputedNote = null;
    }
    return new HttpResponse(null, { status: 204 });
  }),

  http.delete("/api/v1/scans/:scanId", async ({ params }) => {
    await delay(150);
    scansById.delete(String(params.scanId));
    return new HttpResponse(null, { status: 204 });
  }),
];

export const worker = setupWorker(...handlers);
