import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import {
  AlertTriangle,
  BarChart3,
  CheckCircle2,
  ClipboardList,
  Download,
  Droplet,
  RefreshCw,
  ScanLine,
  Share2,
  Sparkles,
} from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { PhBadge, PhGaugeBar, findBandForPh, phTokenStyle, type PhBand } from "@/entities/ph-bands";
import { PhTrendChart, type CatTrendPoint, type CatTrendsResponse, type TrendRange } from "@/features/trends";

import bead01 from "@/shared/assets/images/web-scan/bead-01.png";
import bead02 from "@/shared/assets/images/web-scan/bead-02.png";
import bead03 from "@/shared/assets/images/web-scan/bead-03.png";
import bead04 from "@/shared/assets/images/web-scan/bead-04.png";

/**
 * Bố cục DESKTOP (>= lg) cho màn Xu hướng & Phân tích — dựng từ bản export gốc của Figma
 * `Web - 08 & 10. Xu hướng & Phân tích Chuyên sâu (Trends & Export Analytics)` (node
 * `17:10514`).
 *
 * KHÔNG tự thêm padding ngang ở `lg` — `AppLayout` đã cấp khung nội dung 944px có sẵn padding.
 *
 * DỮ LIỆU: lấy từ D13 `GET /cats/{id}/trends` (endpoint thật). Màn có BA trạng thái:
 * khoá gói (403 `FEATURE_NOT_IN_PLAN`), rỗng (chưa có lần quét nào trong kỳ), và có dữ liệu.
 * Ngưỡng/màu dải pH luôn đến từ API (`entities/ph-bands` + `bands` trong response) — tuyệt đối
 * không hard-code số pH.
 */

/**
 * CÒN LÀ MOCK THEO THIẾT KẾ — API không trả mục này: ảnh thumbnail hạt chỉ thị của từng lần
 * quét (D13 chỉ trả số liệu, không trả ảnh mẫu). 4 ảnh lấy từ chính bản export SVG của Figma.
 */
const DESIGN_MOCK_BEADS = [bead01, bead02, bead03, bead04];

/** Thứ Hai → Chủ nhật. `Date.UTC(2024, 0, 1)` là một thứ Hai, dùng làm mốc sinh nhãn thứ. */
const WEEK_REF_UTC = Date.UTC(2024, 0, 1);
const DAY_MS = 24 * 60 * 60 * 1000;
const WEEKDAY_ORDER = [1, 2, 3, 4, 5, 6, 0];

function Card({ children, className = "" }: { children: React.ReactNode; className?: string }) {
  return <div className={`rounded-2xl bg-surface p-4 shadow-xs ${className}`}>{children}</div>;
}

const RANGES: TrendRange[] = ["7D", "30D", "90D"];

function formatPh(value: number | null | undefined, fallback: string): string {
  return value === null || value === undefined ? fallback : value.toFixed(2);
}

/** API BỎ HẲN key `phMin`/`phMax` ở dải mở — `!== null` không đủ, phải kiểm tra kiểu. */
function finiteBound(value: number | null | undefined): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

/** Gom số lần quét theo thứ trong tuần, giữ đủ 7 cột kể cả thứ không có lần quét nào. */
function buildWeekdayBars(points: CatTrendPoint[]): { day: number; count: number; height: number }[] {
  const counts = new Map<number, number>();
  for (const p of points) {
    const day = new Date(p.capturedAt).getDay();
    counts.set(day, (counts.get(day) ?? 0) + 1);
  }
  const max = Math.max(1, ...counts.values());
  return WEEKDAY_ORDER.map((day) => {
    const count = counts.get(day) ?? 0;
    return { day, count, height: Math.round((count / max) * 100) };
  });
}

function weekdayLabel(day: number): string {
  const offset = WEEKDAY_ORDER.indexOf(day);
  return new Intl.DateTimeFormat("vi-VN", { weekday: "short", timeZone: "UTC" }).format(
    new Date(WEEK_REF_UTC + offset * DAY_MS),
  );
}

export interface WebTrendsScreenProps {
  bands: PhBand[];
  data: CatTrendsResponse | undefined;
  isPending: boolean;
  /** 403 `FEATURE_NOT_IN_PLAN` — gói hiện tại không có tính năng xu hướng. */
  locked: boolean;
  isError: boolean;
  range: TrendRange;
  onRangeChange: (range: TrendRange) => void;
  onRetry: () => void;
}

export function WebTrendsScreen({
  bands,
  data,
  isPending,
  locked,
  isError,
  range,
  onRangeChange,
  onRetry,
}: WebTrendsScreenProps) {
  const { t } = useTranslation(["trends", "common"]);
  /** Lọc nhật ký theo dải pH — thuần client, D13 trả trọn kỳ trong một response. */
  const [logBandFilter, setLogBandFilter] = useState<string>("ALL");

  const points = data?.points ?? [];
  const stats = data?.stats;
  const weekdayBars = buildWeekdayBars(points);
  const dash = t("web.valueUnavailable");

  const referenceBand = bands.find((b) => b.severity === "NORMAL");
  /** Dải cảnh báo cao nhất (sortOrder lớn nhất trong nhóm `WATCH`) — cặp chú giải như Figma. */
  const watchBand = bands
    .filter((b) => b.severity === "WATCH" && finiteBound(b.phMin) !== null)
    .sort((a, b) => b.sortOrder - a.sortOrder)[0];
  const legendBands = [referenceBand, watchBand].filter((b): b is PhBand => b !== undefined);

  const medianBand = findBandForPh(bands, stats?.median);
  const passPercent = stats && stats.count > 0 ? Math.round((stats.inRangeCount / stats.count) * 100) : null;
  const activeDays = weekdayBars.filter((b) => b.count > 0).length;
  const avgPerDay = stats && activeDays > 0 ? (stats.count / activeDays).toFixed(1) : dash;

  const confidences = points.map((p) => p.confidence).filter((c): c is number => c !== null);
  const avgConfidence = confidences.length
    ? Math.round((confidences.reduce((sum, c) => sum + c, 0) / confidences.length) * 100)
    : null;
  const chartVersions = data?.chartVersions ?? [];

  /**
   * Dải của MỘT lần quét = `classification` backend đã gán, không suy lại từ con số pH:
   * `findBandForPh` so `band.phMin === null` nhưng API BỎ HẲN key ở dải mở (`LOW`/`HIGH`) nên
   * `undefined !== null` ⇒ hai dải đó không bao giờ khớp và ô trạng thái in ra mã thô
   * (`SLIGHTLY_HIGH`). Chỉ suy lại khi gặp mã lạ.
   */
  const bandForPoint = (p: CatTrendPoint): PhBand | undefined =>
    bands.find((b) => b.code === p.classification) ?? findBandForPh(bands, p.phValue);

  const bandRangeLabel = (band: PhBand): string | null => {
    const min = finiteBound(band.phMin);
    const max = finiteBound(band.phMax);
    if (min !== null && max !== null) {
      return t("distribution.rangeLabel", { min: min.toFixed(1), max: max.toFixed(1) });
    }
    if (max !== null) return t("distribution.rangeLabelBelow", { max: max.toFixed(1) });
    if (min !== null) return t("distribution.rangeLabelAbove", { min: min.toFixed(1) });
    return null;
  };

  const header = (
    // Frame Figma đặt khối tiêu đề + 2 CTA TRONG một thẻ trắng, không trôi trên nền trang.
    <Card className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
      <div className="max-w-[620px]">
        <p className="text-overline font-bold tracking-[0.4px] text-primary-dark">{t("web.eyebrow")}</p>
        <h1 className="pt-1 text-[28px] font-bold leading-9 tracking-[-0.5px] text-primary-dark">{t("web.title")}</h1>
        <p className="pt-2 text-caption leading-relaxed text-text-secondary">{t("web.subtitle")}</p>
      </div>
      <div className="flex shrink-0 items-start gap-3">
        <span className="rounded-2xl bg-chip-bg px-3 py-2 text-caption font-semibold leading-tight text-primary-dark">
          {t("web.versionPill")}
        </span>
        <div className="flex flex-col gap-2">
          <Link
            to="/export"
            className="inline-flex items-center justify-center gap-2 rounded-xl border border-border bg-surface px-4 py-2.5 text-caption font-semibold text-primary-dark shadow-xs"
          >
            <Download className="size-4 shrink-0" aria-hidden="true" />
            {t("web.exportCta")}
          </Link>
          <Link
            to="/export"
            className="inline-flex items-center justify-center gap-2 rounded-xl bg-primary px-4 py-2.5 text-caption font-bold text-white shadow-sm"
          >
            <Share2 className="size-4 shrink-0" aria-hidden="true" />
            {t("web.shareVetCta")}
          </Link>
        </div>
      </div>
    </Card>
  );

  // ── Trạng thái 1: gói không có tính năng xu hướng (403) ───────────────────────────────
  if (locked) {
    return (
      <div className="flex flex-col gap-4">
        {header}
        <Card className="flex flex-col items-start gap-3">
          <span className="rounded-full bg-chip-bg px-3 py-1 text-caption font-semibold text-primary-dark">
            {t("upsell.title", { ns: "common" })}
          </span>
          <p className="text-body text-text-secondary">{t("upsell.description", { ns: "common" })}</p>
          <p className="text-caption text-text-secondary">{t("web.lockedNote")}</p>
          <Link
            to="/credits"
            className="inline-flex items-center justify-center rounded-xl bg-primary px-4 py-2.5 text-caption font-bold text-white shadow-sm"
          >
            {t("web.lockedCta")}
          </Link>
        </Card>
      </div>
    );
  }

  // ── Trạng thái 2: lỗi tải ─────────────────────────────────────────────────────────────
  if (isError) {
    return (
      <div className="flex flex-col gap-4">
        {header}
        <Card className="flex flex-col items-start gap-3">
          <p className="text-body text-text-secondary">{t("state.loadError")}</p>
          <button
            type="button"
            onClick={onRetry}
            className="rounded-xl bg-primary px-4 py-2.5 text-caption font-bold text-white shadow-sm"
          >
            {t("web.retry")}
          </button>
        </Card>
      </div>
    );
  }

  const rangeTabs = (
    <div className="flex gap-1.5">
      {RANGES.map((r) => (
        <button
          key={r}
          type="button"
          onClick={() => {
            onRangeChange(r);
          }}
          className={`rounded-lg px-3 py-1.5 text-caption font-semibold ${
            r === range ? "bg-primary-dark text-white" : "bg-background-alt text-text-secondary"
          }`}
        >
          {t(`range.${r}`)}
        </button>
      ))}
    </div>
  );

  const logRows = points
    .filter((p) => (logBandFilter === "ALL" ? true : bandForPoint(p)?.code === logBandFilter))
    .slice()
    .reverse();

  return (
    <div className="flex flex-col gap-4">
      {header}

      {/* 4 KPI — số liệu thật từ `stats` */}
      <div className="grid grid-cols-2 gap-4 xl:grid-cols-4">
        <Card className="flex flex-col gap-2">
          <div className="flex items-start justify-between gap-2">
            <p className="text-overline font-bold tracking-[0.4px] text-text-secondary">{t("web.kpiAvgLabel")}</p>
            <span className="flex size-7 shrink-0 items-center justify-center rounded-lg bg-chip-bg text-primary-dark">
              <Droplet className="size-3.5" aria-hidden="true" />
            </span>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-[26px] font-bold leading-8 text-primary-dark">
              {isPending ? dash : formatPh(stats?.median, dash)}
            </span>
            {medianBand ? (
              <span
                className={cn(
                  "rounded-full px-2 py-0.5 text-[11px] font-semibold",
                  phTokenStyle(medianBand.colorToken).bg,
                  phTokenStyle(medianBand.colorToken).text,
                )}
              >
                {medianBand.label}
              </span>
            ) : null}
          </div>
          <div className="mt-auto border-t border-border pt-2">
            <p className="text-[11px] leading-snug text-text-secondary">{t("web.kpiAvgFootReal")}</p>
          </div>
        </Card>

        <Card className="flex flex-col gap-2">
          <div className="flex items-start justify-between gap-2">
            <p className="text-overline font-bold tracking-[0.4px] text-text-secondary">{t("web.kpiPassLabel")}</p>
            <span className="flex size-7 shrink-0 items-center justify-center rounded-lg bg-chip-bg text-primary-dark">
              <CheckCircle2 className="size-3.5" aria-hidden="true" />
            </span>
          </div>
          <span className="text-[26px] font-bold leading-8 text-success-text">
            {passPercent === null ? dash : `${String(passPercent)}%`}
          </span>
          <div className="mt-auto border-t border-border pt-2">
            <p className="text-[11px] leading-snug text-text-secondary">
              {t("web.kpiPassFootReal", {
                inRange: stats?.inRangeCount ?? 0,
                count: stats?.count ?? 0,
              })}
            </p>
          </div>
        </Card>

        <Card className="flex flex-col gap-2">
          <div className="flex items-start justify-between gap-2">
            <p className="text-overline font-bold tracking-[0.4px] text-text-secondary">{t("web.kpiScansLabel")}</p>
            <span className="flex size-7 shrink-0 items-center justify-center rounded-lg bg-chip-bg text-primary-dark">
              <ScanLine className="size-3.5" aria-hidden="true" />
            </span>
          </div>
          <span className="text-[26px] font-bold leading-8 text-primary-dark">{stats?.count ?? 0}</span>
          <div className="mt-auto border-t border-border pt-2">
            <p className="text-[11px] leading-snug text-text-secondary">{t("web.kpiScansFoot")}</p>
          </div>
        </Card>

        <Card className="flex flex-col gap-2">
          <div className="flex items-start justify-between gap-2">
            <p className="text-overline font-bold tracking-[0.4px] text-text-secondary">{t("web.kpiLowConfLabel")}</p>
            <span className="flex size-7 shrink-0 items-center justify-center rounded-lg bg-chip-bg text-primary-dark">
              <AlertTriangle className="size-3.5" aria-hidden="true" />
            </span>
          </div>
          <span className="text-[26px] font-bold leading-8 text-primary-dark">{stats?.lowConfidenceCount ?? 0}</span>
          <div className="mt-auto border-t border-border pt-2">
            <p className="text-[11px] leading-snug text-text-secondary">{t("web.kpiLowConfFoot")}</p>
          </div>
        </Card>
      </div>

      {/* Biểu đồ + cột phải */}
      <div className="grid grid-cols-12 gap-4">
        <Card className="col-span-12 flex flex-col gap-3 xl:col-span-8">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <p className="text-[18px] font-bold leading-6 text-primary-dark">{t("web.chartTitle")}</p>
              <p className="pt-1 text-caption text-text-secondary">{t("web.chartSubtitle")}</p>
            </div>
            <div className="flex flex-wrap items-center gap-3 rounded-xl bg-background-alt px-3 py-2">
              {legendBands.map((band) => (
                <span key={band.code} className="inline-flex items-center gap-1.5 text-[11px] text-text-secondary">
                  <span className={`size-2 rounded-full ${phTokenStyle(band.colorToken).solid}`} aria-hidden="true" />
                  {band.label} {bandRangeLabel(band)}
                </span>
              ))}
            </div>
          </div>

          {rangeTabs}

          {points.length === 0 ? (
            <div className="flex h-52 flex-col items-center justify-center gap-1 rounded-xl bg-background-alt/60 p-3 text-center">
              <p className="text-body font-semibold text-text-primary">{t("state.emptyTitle")}</p>
              <p className="text-caption text-text-secondary">{t("web.emptyHint")}</p>
            </div>
          ) : (
            <PhTrendChart
              points={points
                .filter((p): p is CatTrendPoint & { phValue: number } => p.phValue !== null)
                .map((p) => ({ date: p.capturedAt, phValue: p.phValue }))}
              bands={bands}
              referenceBand={referenceBand}
              emptyLabel={t("chart.empty")}
              dateFormat={t("chart.dateFormat")}
              tooltipDateFormat={t("chart.tooltipDateFormat")}
              todayLabel={t("chart.today")}
              phLabel={t("web.phUnit")}
              height={260}
            />
          )}

          {/* Chân biểu đồ — số liệu THẬT: độ tin cậy trung bình + phiên bản bảng màu từ D13. */}
          <div className="flex flex-wrap items-center justify-between gap-2 rounded-xl bg-background-alt p-3">
            <p className="max-w-[260px] text-[11px] font-semibold leading-snug text-text-primary">
              {t("web.chartFootTitle")}
            </p>
            <div className="flex flex-wrap gap-x-4 gap-y-1 text-[11px] text-text-secondary">
              <span>
                {avgConfidence === null
                  ? t("web.chartConfidenceEmpty")
                  : t("web.chartConfidenceReal", { percent: avgConfidence })}
              </span>
              {chartVersions.length > 0 ? (
                <span>{t("web.chartVersions", { versions: chartVersions.join(", ") })}</span>
              ) : null}
            </div>
          </div>

          <PhGaugeBar bands={bands} value={stats?.median ?? null} />
        </Card>

        <div className="col-span-12 flex flex-col gap-4 xl:col-span-4">
          <Card className="flex flex-col gap-3">
            <div className="flex items-start gap-2">
              <span className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-chip-bg text-primary-dark">
                <BarChart3 className="size-4" aria-hidden="true" />
              </span>
              <div className="min-w-0 flex-1">
                <p className="text-body font-bold leading-snug text-primary-dark">{t("web.freqTitle")}</p>
                <p className="pt-0.5 text-[11px] text-text-secondary">{t("web.freqSubtitle")}</p>
              </div>
            </div>
            <p>
              <span className="text-[26px] font-bold leading-8 text-primary-dark">{avgPerDay}</span>{" "}
              <span className="text-caption text-text-secondary">{t("web.freqUnit")}</span>
            </p>
            {points.length === 0 ? (
              <p className="text-caption text-text-secondary">{t("web.logEmpty")}</p>
            ) : (
              <div className="flex items-end gap-1.5">
                {weekdayBars.map((bar) => (
                  <div key={bar.day} className="flex flex-1 flex-col items-center gap-1">
                    <div className="flex h-20 w-full items-end">
                      <div
                        className={cn("w-full rounded-t-md", bar.count > 0 ? "bg-primary-dark" : "bg-border")}
                        style={{ height: `${String(Math.max(bar.height, 6))}%` }}
                      />
                    </div>
                    <span className="text-[10px] text-text-secondary">{weekdayLabel(bar.day)}</span>
                  </div>
                ))}
              </div>
            )}
          </Card>

          {/* Tóm tắt kỳ — toàn bộ số liệu lấy từ `stats` của D13, không có khối "AI Vet"
              nào trong MVP (không có backend, và câu chữ mẫu nhắc tới phát hiện máu —
              trái quyết định #8 "Chỉ số đo: Chỉ pH. Không phát hiện máu"). */}
          <Card className="flex flex-col gap-3 bg-background-alt">
            <div className="flex items-start gap-2">
              <span className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-primary-dark text-white">
                <Sparkles className="size-4" aria-hidden="true" />
              </span>
              <div className="min-w-0 flex-1">
                <p className="text-body font-bold text-primary-dark">{t("web.summaryTitle")}</p>
                <p className="text-[11px] text-text-secondary">{t("web.summarySubtitle")}</p>
              </div>
            </div>
            <dl className="grid grid-cols-2 gap-x-3 gap-y-2">
              <div>
                <dt className="text-[11px] text-text-secondary">{t("web.summaryMin")}</dt>
                <dd className="text-body font-bold text-primary-dark">{formatPh(stats?.min, dash)}</dd>
              </div>
              <div>
                <dt className="text-[11px] text-text-secondary">{t("web.summaryMax")}</dt>
                <dd className="text-body font-bold text-primary-dark">{formatPh(stats?.max, dash)}</dd>
              </div>
              <div>
                <dt className="text-[11px] text-text-secondary">{t("web.summaryMedian")}</dt>
                <dd className="text-body font-bold text-primary-dark">{formatPh(stats?.median, dash)}</dd>
              </div>
              <div>
                <dt className="text-[11px] text-text-secondary">{t("web.summaryInRange")}</dt>
                <dd className="text-body font-bold text-primary-dark">
                  {t("web.summaryInRangeValue", {
                    inRange: stats?.inRangeCount ?? 0,
                    count: stats?.count ?? 0,
                  })}
                </dd>
              </div>
            </dl>
          </Card>
        </div>
      </div>

      {/* Nhật ký — mỗi dòng là một điểm quét thật */}
      <Card className="flex flex-col gap-3">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="flex items-start gap-2">
            <ClipboardList className="mt-1 size-5 shrink-0 text-primary-dark" aria-hidden="true" />
            <div>
              <p className="text-[18px] font-bold leading-6 text-primary-dark">{t("web.logTitle")}</p>
              <p className="pt-1 text-caption text-text-secondary">{t("web.logSubtitle")}</p>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <select
              aria-label={t("web.logFilterLabel")}
              value={logBandFilter}
              onChange={(event) => {
                setLogBandFilter(event.target.value);
              }}
              className="rounded-xl border border-border bg-surface px-3 py-2 text-[11px] font-semibold text-text-secondary"
            >
              <option value="ALL">{t("web.logFilter")}</option>
              {bands.map((band) => (
                <option key={band.code} value={band.code}>
                  {band.label}
                </option>
              ))}
            </select>
            <button
              type="button"
              onClick={onRetry}
              className="inline-flex items-center gap-1.5 rounded-xl border border-border bg-surface px-3 py-2 text-[11px] font-semibold text-text-secondary"
            >
              <RefreshCw className="size-3.5" aria-hidden="true" />
              {t("web.logRefresh")}
            </button>
          </div>
        </div>

        {logRows.length === 0 ? (
          <p className="text-caption text-text-secondary">{t("web.logEmpty")}</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[720px] border-collapse text-left">
              <thead>
                {/* Mockup: hàng tiêu đề bảng có nền nhạt, không trùng nền thẻ. */}
                <tr className="border-b border-border bg-background-alt">
                  {["web.colTime", "web.colSample", "web.colPh", "web.colStatus"].map((k) => (
                    <th key={k} className="px-3 py-2.5 text-[10px] font-bold tracking-[0.4px] text-text-secondary">
                      {t(k)}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {logRows.map((p, i) => {
                  const band = bandForPoint(p);
                  const style = phTokenStyle(band?.colorToken ?? "color-ph-unknown");
                  const captured = new Date(p.capturedAt);
                  return (
                    // Mockup tô nền cả hàng cho lần quét "cần lưu ý" — dùng cờ `triggersAlert`
                    // THẬT của dải (API), không tự đặt ngưỡng.
                    <tr key={p.capturedAt} className={cn("border-b border-border", band?.triggersAlert && style.bg)}>
                      <td className="px-3 py-3">
                        <p className="text-caption font-bold text-text-primary">
                          {new Intl.DateTimeFormat("vi-VN", {
                            day: "2-digit",
                            month: "2-digit",
                            hour: "2-digit",
                            minute: "2-digit",
                          }).format(captured)}
                        </p>
                        <p className="text-[11px] text-text-secondary">
                          {p.nearBoundary ? t("web.logNearBoundary") : t("web.logAutoScan")}
                        </p>
                      </td>
                      <td className="px-3 py-3">
                        <span className="flex items-center gap-2">
                          <img
                            src={DESIGN_MOCK_BEADS[i % DESIGN_MOCK_BEADS.length]}
                            alt=""
                            className="size-9 shrink-0 rounded-full object-cover"
                          />
                          <span className="text-[11px] text-text-secondary">
                            {p.confidence === null
                              ? dash
                              : t("web.logConfidence", {
                                  percent: Math.round(p.confidence * 100),
                                })}
                          </span>
                        </span>
                      </td>
                      <td className="px-3 py-3">
                        <span className="text-body font-bold text-primary-dark">{formatPh(p.phValue, dash)}</span>{" "}
                        <span className="text-[11px] text-text-secondary">{t("web.phUnit")}</span>
                      </td>
                      <td className="px-3 py-3">
                        {/* `PhBadge` (entities) đã kèm icon theo `band.iconName` như mockup —
                            a11y không dựa màu đơn thuần. */}
                        {band ? (
                          <PhBadge band={band} className="text-[11px]" />
                        ) : (
                          <span className="text-[11px] text-text-secondary">{dash}</span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        <p className="text-[11px] text-text-secondary">
          {t("web.logFooterReal", { shown: logRows.length, total: points.length })}
        </p>
      </Card>
    </div>
  );
}
