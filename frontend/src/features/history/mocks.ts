/**
 * MSW handlers + worker cho `features/history` — chế độ degraded khi backend chưa có (cùng quy
 * ước `features/onboarding/mocks.ts`). Dữ liệu demo ĐỘC LẬP với `features/scan/mocks.ts` (mỗi
 * feature tự seed — không import lẫn nhau, boundaries cấm feature → feature).
 *
 * Endpoint mock (khớp p8 §8.5.4):
 *  - GET /api/v1/scans          (E2, phân trang con trỏ + lọc classification[]/disputed)
 *  - GET /api/v1/scans/summary  (E3)
 *  - GET /api/v1/cats/:catId    (D3, rút gọn — chỉ để hiển thị tên mèo ở header)
 */

import { http, HttpResponse, delay } from "msw";
import { setupWorker } from "msw/browser";
import type { Cat } from "@/entities/cat";
import type { ScanClassification, ScanListItem, ScanListPage, ScanSummary } from "@/entities/scan-result";

const LUNA_ID = "00000000-0000-7000-8000-000000000001";
const DAY_MS = 24 * 60 * 60 * 1000;

const DEMO_CAT: Cat = {
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
};

function classify(value: number): ScanClassification {
  if (value < 6.0) return "LOW";
  if (value < 6.3) return "SLIGHTLY_LOW";
  if (value <= 6.6) return "IN_RANGE";
  if (value <= 7.0) return "SLIGHTLY_HIGH";
  return "HIGH";
}

function buildDemoScans(): ScanListItem[] {
  const items: ScanListItem[] = [];
  const now = Date.now();
  const values = [6.8, 6.7, 7.4, 6.8, 6.5, 6.4, 6.9, 6.6, 6.3, 6.7, 6.8, 6.2, 6.5, 6.6, 6.9];
  values.forEach((value, index) => {
    const capturedAt = new Date(now - index * 2 * DAY_MS).toISOString();
    const classification = classify(value);
    items.push({
      scanId: `00000000-0000-7000-a000-${String(index + 1).padStart(12, "0")}`,
      catId: LUNA_ID,
      catName: "Luna",
      capturedAt,
      phValue: value,
      classification,
      bandCode: classification,
      confidence: 0.8,
      confidenceBand: "HIGH",
      nearBoundary: Math.abs(value - 6.3) < 0.05 || Math.abs(value - 6.6) < 0.05,
      thumbnailHex: "#7CB342",
      imageAvailable: true,
      disputed: index === 6,
      hasNote: false,
    });
  });
  return items;
}

const DEMO_SCANS = buildDemoScans();

export const handlers = [
  http.get("/api/v1/cats/:catId", async ({ params }) => {
    await delay(80);
    if (String(params.catId) !== LUNA_ID) {
      return HttpResponse.json(
        { type: "https://catcheck.vn/problems/cat-not-found", title: "CAT_NOT_FOUND", status: 404, detail: "Không tìm thấy mèo.", instance: "/mock", code: "CAT_NOT_FOUND" },
        { status: 404 },
      );
    }
    return HttpResponse.json(DEMO_CAT);
  }),

  http.get("/api/v1/scans", async ({ request }) => {
    await delay(150);
    const url = new URL(request.url);
    const catId = url.searchParams.get("catId");
    const disputed = url.searchParams.get("disputed");
    const classifications = url.searchParams.getAll("classification");
    const cursor = url.searchParams.get("cursor");
    const limit = Number(url.searchParams.get("limit") ?? "20");

    let filtered = DEMO_SCANS;
    if (catId) filtered = filtered.filter((s) => s.catId === catId);
    if (disputed === "true") filtered = filtered.filter((s) => s.disputed);
    if (classifications.length > 0) filtered = filtered.filter((s) => classifications.includes(s.classification));

    const startIndex = cursor ? filtered.findIndex((s) => s.scanId === cursor) + 1 : 0;
    const page = filtered.slice(startIndex, startIndex + limit);
    const hasMore = startIndex + limit < filtered.length;
    const body: ScanListPage = {
      items: page,
      hasMore,
      nextCursor: hasMore ? (page[page.length - 1]?.scanId ?? null) : null,
    };
    return HttpResponse.json(body);
  }),

  http.get("/api/v1/scans/summary", async ({ request }) => {
    await delay(150);
    const url = new URL(request.url);
    const catId = url.searchParams.get("catId");
    const items = catId ? DEMO_SCANS.filter((s) => s.catId === catId) : DEMO_SCANS;
    const values = items.map((i) => i.phValue).filter((v): v is number => v !== null);
    const byClassification: Record<string, number> = {};
    items.forEach((i) => {
      byClassification[i.classification] = (byClassification[i.classification] ?? 0) + 1;
    });
    const sorted = [...values].sort((a, b) => a - b);
    const median = sorted.length ? sorted[Math.floor(sorted.length / 2)] : null;
    const body: ScanSummary = {
      count: items.length,
      byClassification,
      median: median ?? null,
      min: sorted.length ? sorted[0] : null,
      max: sorted.length ? sorted[sorted.length - 1] : null,
      inconclusiveCount: 0,
      lowConfidenceCount: 0,
      firstAt: items.length ? items[items.length - 1].capturedAt : null,
      lastAt: items.length ? items[0].capturedAt : null,
    };
    return HttpResponse.json(body);
  }),
];

export const worker = setupWorker(...handlers);
