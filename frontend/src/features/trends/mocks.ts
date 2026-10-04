/**
 * MSW handlers + worker — fixture DEV, không có file ứng dụng nào import (chỉ dùng khi bật
 * worker thủ công). Giữ nguyên hai handler `GET /scans` / `GET /scans/summary` vì đó là
 * endpoint THẬT mà `features/history` vẫn gọi; `features/trends` thì không gọi chúng nữa —
 * từ W1-E module này chỉ dùng D13 `GET /cats/{catId}/trends`.
 *
 * Endpoint mock (khớp p8 §8.5.4, có lọc `from`/`to` mà `features/history/mocks.ts` không cần):
 *  - GET /api/v1/scans          (E2)
 *  - GET /api/v1/scans/summary  (E3)
 */

import { http, HttpResponse, delay } from "msw";
import { setupWorker } from "msw/browser";
import type { ScanClassification, ScanListItem, ScanListPage, ScanSummary } from "@/entities/scan-result";

const LUNA_ID = "00000000-0000-7000-8000-000000000001";
const DAY_MS = 24 * 60 * 60 * 1000;

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
  // 45 điểm trải trong 90 ngày (mỗi ~2 ngày), 1 đỉnh kiềm nhẹ mô phỏng mockup `08` (12 Th09).
  for (let index = 0; index < 45; index += 1) {
    const spike = index === 20 ? 0.6 : 0;
    const value = Number((6.5 + Math.sin(index / 3) * 0.15 + spike).toFixed(2));
    const capturedAt = new Date(now - index * 2 * DAY_MS).toISOString();
    const classification = classify(value);
    items.push({
      scanId: `00000000-0000-7000-b000-${String(index + 1).padStart(12, "0")}`,
      catId: LUNA_ID,
      catName: "Luna",
      capturedAt,
      phValue: value,
      classification,
      bandCode: classification,
      confidence: 0.8,
      confidenceBand: "HIGH",
      nearBoundary: false,
      thumbnailHex: "#7CB342",
      imageAvailable: true,
      disputed: false,
      hasNote: false,
    });
  }
  return items;
}

const DEMO_SCANS = buildDemoScans();

function inRange(iso: string, from: string | null, to: string | null): boolean {
  const t = new Date(iso).getTime();
  if (from && t < new Date(from).getTime()) return false;
  if (to && t > new Date(to).getTime()) return false;
  return true;
}

export const handlers = [
  http.get("/api/v1/scans", async ({ request }) => {
    await delay(150);
    const url = new URL(request.url);
    const catId = url.searchParams.get("catId");
    const from = url.searchParams.get("from");
    const to = url.searchParams.get("to");
    const cursor = url.searchParams.get("cursor");
    const limit = Number(url.searchParams.get("limit") ?? "50");

    let filtered = DEMO_SCANS;
    if (catId) filtered = filtered.filter((s) => s.catId === catId);
    filtered = filtered.filter((s) => inRange(s.capturedAt, from, to));

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
    const from = url.searchParams.get("from");
    const to = url.searchParams.get("to");
    let items = catId ? DEMO_SCANS.filter((s) => s.catId === catId) : DEMO_SCANS;
    items = items.filter((s) => inRange(s.capturedAt, from, to));

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
