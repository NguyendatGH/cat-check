import { useTranslation } from "react-i18next";
import { AlertTriangle, CheckCircle2, ChevronRight, LineChart } from "lucide-react";
import { CatAvatar } from "@/entities/cat";
import { DisclaimerBanner } from "@/entities/disclaimer";
import { PhGaugeBar, findBandForPh, phTokenStyle, type PhBand } from "@/entities/ph-bands";
import { displayableScanCount } from "@/entities/scan-result";
import type { ScanListItem, ScanSummary } from "@/entities/scan-result";
import { PhBandLegendRow, type HistoryFilterKey } from "@/features/history";

import tipScanFrequency from "@/shared/assets/images/web-history/tip-scan-frequency.jpg";

/**
 * Bố cục DESKTOP (>= lg) cho màn Lịch sử Sức khoẻ — dựng từ bản export gốc của Figma
 * `Web - 06 & 07. Hồ sơ Bé Mèo & Sổ khám Y tế (Cat Medical Profile)`, phần panel
 * "Dòng thời gian Y tế & Quét chỉ thị sinh học" (đích của link "Xem toàn bộ lịch sử"),
 * ghép với các khối của mockup mobile `07. Lịch sử Sức khỏe (Health History)`.
 *
 * KHÔNG tự thêm padding ở `lg` — `AppLayout` đã cấp khung nội dung responsive kèm padding
 * desktop. Nhờ vậy màn rộng có thể dùng đủ không gian mà không tạo khoảng trống hai bên.
 *
 * DỮ LIỆU THẬT: E2 `GET /scans` (timeline, cursor-paging) + E3 `GET /scans/summary` (KPI,
 * trung vị, khoảng pH) + `GET /reference/ph-bands` (nhãn/màu/ngưỡng). Tuyệt đối không
 * hard-code ngưỡng pH ở đây — mọi nhãn dải đều tra từ `bands`.
 *
 * KHÁC VỚI `pages/cat/CatMedicalPanels.tsx`: panel ở hồ sơ mèo là bản xem trước vài mốc gần
 * nhất (quét + ghi chú); màn này là bản đầy đủ, có lọc, chạy bằng dữ liệu quét thật.
 */

/**
 * CÒN LÀ MOCK THEO THIẾT KẾ — API không trả mục này:
 * - thẻ mẹo "Bao lâu nên quét một lần?" (nội dung tĩnh lấy nguyên văn từ mockup `07`).
 *
 * ĐÃ BỎ: ảnh mẫu hạt chỉ thị trong timeline (`timeline-scan-sample.jpg`). E2 chỉ trả
 * `thumbnailHex`, không trả URL ảnh, nên mọi bản ghi thiếu màu đều rơi về CÙNG MỘT tấm ảnh
 * khay cát chung — nhìn như từng lần quét có ảnh riêng trong khi không bản ghi nào có ảnh
 * cả. Nay ô đầu mốc là màu đo được nếu có, không thì biểu tượng tô theo màu dải pH thật.
 */

function Panel({ children, className = "" }: { children: React.ReactNode; className?: string }) {
  return (
    <div className={`rounded-3xl bg-surface p-6 shadow-[0px_8px_24px_-12px_rgba(47,79,178,0.22)] ${className}`}>
      {children}
    </div>
  );
}

const FILTERS: HistoryFilterKey[] = ["ALL", "ABNORMAL", "DISPUTED"];

export interface WebHistoryScreenProps {
  catName: string | null;
  catAvatarUrl: string | null;
  catBreedName: string | null;
  bands: PhBand[];
  summary: ScanSummary | undefined;
  /** `[monthKey, scans][]` đã gom nhóm ở trang cha (dùng chung với bản mobile). */
  groups: [string, ScanListItem[]][];
  totalLoaded: number;
  /** Tổng lượt quét hiển thị được của bé (E3 trừ INCONCLUSIVE); `null` khi chưa có summary. */
  totalRecords: number | null;
  filter: HistoryFilterKey;
  onFilterChange: (filter: HistoryFilterKey) => void;
  isPending: boolean;
  isError: boolean;
  onRetry: () => void;
  hasNextPage: boolean;
  isFetchingNextPage: boolean;
  onLoadMore: () => void;
  onOpenScan: (scanId: string) => void;
  onStartScan: () => void;
  onExport: () => void;
  onOpenTrends: () => void;
  /** `t("monthGroup.label", {...})` đã dựng sẵn ở trang cha để không lặp logic parse key. */
  monthLabel: (key: string) => string;
  timestampLabel: (iso: string) => string;
}

export function WebHistoryScreen({
  catName,
  catAvatarUrl,
  catBreedName,
  bands,
  summary,
  groups,
  totalLoaded,
  totalRecords,
  filter,
  onFilterChange,
  isPending,
  isError,
  onRetry,
  hasNextPage,
  isFetchingNextPage,
  onLoadMore,
  onOpenScan,
  onStartScan,
  onExport,
  onOpenTrends,
  monthLabel,
  timestampLabel,
}: WebHistoryScreenProps) {
  const { t } = useTranslation(["history", "common"]);
  const dash = t("web.valueUnavailable");

  const header = (
    <div className="flex flex-col gap-6 xl:flex-row xl:items-start xl:justify-between">
      <div className="max-w-[680px]">
        <p className="text-small font-bold tracking-[0.55px] text-primary-dark">{t("web.eyebrow")}</p>
        <h1 className="pt-1 text-[32px] font-bold leading-10 tracking-[-0.8px] text-primary-dark">{t("web.title")}</h1>
        <p className="pt-2 text-body leading-relaxed text-text-secondary">
          {catName ? t("web.subtitleNamed", { name: catName }) : t("web.subtitle")}
        </p>
      </div>
      <button
        type="button"
        onClick={onExport}
        className="inline-flex min-h-12 shrink-0 items-center justify-center gap-2 rounded-2xl bg-primary px-6 py-3 text-body font-bold text-white shadow-[0px_8px_16px_-8px_rgba(47,79,178,0.55)] transition-transform hover:-translate-y-0.5"
      >
        {t("export.cta")}
      </button>
    </div>
  );

  if (isError) {
    return (
      <div className="flex flex-col gap-4">
        {header}
        <Panel className="flex flex-col items-start gap-3">
          <p className="text-body text-text-secondary">{t("state.loadError")}</p>
          <button
            type="button"
            onClick={onRetry}
            className="rounded-xl bg-primary px-4 py-2.5 text-caption font-bold text-white shadow-sm"
          >
            {t("actions.retry", { ns: "common" })}
          </button>
        </Panel>
      </div>
    );
  }

  const rangeText =
    summary && typeof summary.min === "number" && typeof summary.max === "number"
      ? t("web.kpiRangeValue", { min: summary.min.toFixed(1), max: summary.max.toFixed(1) })
      : dash;

  return (
    <div className="flex flex-col gap-4">
      {header}

      {/* 4 KPI — toàn bộ lấy từ E3 `GET /scans/summary` */}
      <div className="grid grid-cols-2 gap-5 xl:grid-cols-4">
        <Panel className="flex min-h-[156px] flex-col gap-2">
          <p className="text-small font-bold tracking-[0.45px] text-text-secondary">{t("web.kpiTotal")}</p>
          {/* Số lượt quét HIỂN THỊ ĐƯỢC, không phải `summary.count` thô: E3 đếm cả bản ghi
              INCONCLUSIVE mà E2 không bao giờ trả về, nên dùng `count` thì KPI luôn lớn hơn
              số dòng dòng thời gian ngay bên cạnh (bug "20 vs 19"). Số bị trừ ra đã được
              nói rõ ở chân thẻ "ĐỘ TIN CẬY THẤP" (`kpiLowConfFoot`). */}
          <span className="text-[34px] font-bold leading-10 text-primary-dark">
            {isPending || !summary ? dash : displayableScanCount(summary)}
          </span>
          <p className="mt-auto border-t border-border pt-3 text-caption leading-snug text-text-secondary">
            {t("web.kpiTotalFoot")}
          </p>
        </Panel>

        <Panel className="flex min-h-[156px] flex-col gap-2">
          <p className="text-small font-bold tracking-[0.45px] text-text-secondary">{t("web.kpiMedian")}</p>
          <span className="text-[34px] font-bold leading-10 text-primary-dark">
            {summary?.median != null ? summary.median.toFixed(2) : dash}
          </span>
          <p className="mt-auto border-t border-border pt-3 text-caption leading-snug text-text-secondary">
            {t("web.kpiMedianFoot")}
          </p>
        </Panel>

        <Panel className="flex min-h-[156px] flex-col gap-2">
          <p className="text-small font-bold tracking-[0.45px] text-text-secondary">{t("web.kpiRange")}</p>
          <span className="text-[34px] font-bold leading-10 text-primary-dark">{rangeText}</span>
          <p className="mt-auto border-t border-border pt-3 text-caption leading-snug text-text-secondary">
            {t("web.kpiRangeFoot")}
          </p>
        </Panel>

        <Panel className="flex min-h-[156px] flex-col gap-2">
          <p className="text-small font-bold tracking-[0.45px] text-text-secondary">{t("web.kpiLowConf")}</p>
          <span className="text-[34px] font-bold leading-10 text-primary-dark">{summary?.lowConfidenceCount ?? 0}</span>
          <p className="mt-auto border-t border-border pt-3 text-caption leading-snug text-text-secondary">
            {t("web.kpiLowConfFoot", { count: summary?.inconclusiveCount ?? 0 })}
          </p>
        </Panel>
      </div>

      {/*
        BỐ CỤC: hàng tóm tắt (hồ sơ bé + phổ pH rộng | mẹo + lối sang xu hướng hẹp) rồi dòng
        thời gian trọn bề ngang. Bộ lọc nằm NGAY TRÊN dòng thời gian nó lọc — trước đây nó bị
        nhét vào cột 4/12 hẹp nên "Bất thường" / "Đã tranh chấp" vỡ hai dòng.
      */}
      <div className="grid grid-cols-12 gap-5">
        <Panel className="col-span-12 flex flex-col gap-4 xl:col-span-8">
          <div className="flex flex-wrap items-center gap-4">
            <CatAvatar src={catAvatarUrl} name={catName ?? t("web.unknownCat")} size="lg" />
            <div className="min-w-0 flex-1">
              <p className="truncate text-h3 font-bold text-text-primary">{catName ?? t("web.unknownCat")}</p>
              {catBreedName ? <p className="truncate text-body text-text-secondary">{catBreedName}</p> : null}
            </div>
            {summary?.median != null ? (
              <span className="shrink-0 rounded-full bg-chip-bg px-4 py-2 text-caption font-semibold text-primary-dark">
                {t("spectrum.average", { value: summary.median.toFixed(1) })}
              </span>
            ) : null}
          </div>
          <div className="border-t border-border pt-4">
            <p className="text-body font-bold text-text-primary">{t("spectrum.title")}</p>
            <div className="flex flex-col gap-2 pt-3">
              <PhGaugeBar bands={bands} value={summary?.median ?? null} />
              <PhBandLegendRow bands={bands} />
            </div>
          </div>
        </Panel>

        <Panel className="col-span-12 flex flex-col gap-4 xl:col-span-4">
          {/* Thẻ mẹo — nội dung tĩnh theo mockup `07`, không có API */}
          <div className="flex gap-3">
            <img src={tipScanFrequency} alt="" className="size-14 shrink-0 rounded-xl object-cover" />
            <div className="min-w-0">
              <p className="text-caption font-bold text-primary-dark">{t("web.tipTitle")}</p>
              <p className="pt-1 text-small leading-relaxed text-text-secondary">{t("web.tipBody")}</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onOpenTrends}
            className="mt-auto flex min-h-11 items-center justify-between gap-2 rounded-xl bg-background-alt px-4 py-2.5 text-left text-caption font-semibold text-primary-dark hover:bg-chip-bg"
          >
            <span className="flex items-center gap-2">
              <LineChart className="size-4 shrink-0" aria-hidden="true" />
              {t("web.trendsCta")}
            </span>
            <ChevronRight className="size-4 shrink-0" aria-hidden="true" />
          </button>
        </Panel>

        {/* ── Dòng thời gian: trọn bề ngang ───────────────────────────────────────────── */}
        <div className="col-span-12">
          <Panel className="flex flex-col gap-6">
            <div className="flex flex-wrap items-start justify-between gap-4">
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-3">
                  <p className="text-h3 font-bold leading-7 text-primary-dark">{t("web.timelineTitle")}</p>
                  {/* "N bản ghi" từng là `totalLoaded` — số dòng ĐÃ TẢI của trang đầu, trình bày
                      như thể là tổng. Nay: còn trang chưa tải thì nói rõ đang hiển thị bao nhiêu
                      trên tổng, đã tải hết thì hai số bằng nhau nên in một con số. */}
                  <span className="shrink-0 rounded-full bg-chip-bg px-3 py-1 text-small font-bold text-primary-dark">
                    {totalRecords !== null && totalLoaded < totalRecords
                      ? t("web.recordCountPartial", { loaded: totalLoaded, total: totalRecords })
                      : t("web.recordCount", { count: totalRecords ?? totalLoaded })}
                  </span>
                </div>
                <p className="pt-1 text-body text-text-secondary">{t("web.timelineSubtitle")}</p>
              </div>
              <div className="flex shrink-0 gap-1 rounded-xl bg-background-alt p-1" role="tablist" aria-label={t("web.filterLabel")}>
                {FILTERS.map((option) => (
                  <button
                    key={option}
                    type="button"
                    role="tab"
                    aria-selected={filter === option}
                    onClick={() => {
                      onFilterChange(option);
                    }}
                    className={`min-h-10 whitespace-nowrap rounded-lg px-4 text-caption font-semibold transition-colors ${
                      filter === option ? "bg-primary-dark text-white shadow-xs" : "text-text-secondary hover:bg-surface"
                    }`}
                  >
                    {t(`filters.${option}`)}
                  </button>
                ))}
              </div>
            </div>

            {isPending ? (
              <div className="flex flex-col gap-4">
                {[0, 1, 2].map((i) => (
                  <div key={i} className="h-20 animate-pulse rounded-2xl bg-background-alt" />
                ))}
              </div>
            ) : groups.length === 0 ? (
              <div className="flex flex-col items-start gap-3 rounded-2xl bg-background-alt p-8">
                <p className="text-h3 font-bold text-text-primary">{t("state.emptyTitle")}</p>
                <p className="text-body text-text-secondary">{t("state.emptyDescription")}</p>
                <button
                  type="button"
                  onClick={onStartScan}
                  className="mt-1 min-h-12 rounded-2xl bg-primary px-5 py-3 text-body font-bold text-white shadow-sm"
                >
                  {t("state.emptyCta")}
                </button>
              </div>
            ) : (
              <div className="flex flex-col gap-7">
                {groups.map(([key, group]) => (
                  <section key={key} className="flex flex-col gap-3">
                    <div className="flex items-baseline justify-between gap-2">
                      <h2 className="text-body font-bold text-text-primary">{monthLabel(key)}</h2>
                      <span className="text-small text-text-tertiary">
                        {t("monthGroup.count", { count: group.length })}
                      </span>
                    </div>

                    <ol className="relative flex flex-col gap-3 border-l-2 border-primary/15 pl-7">
                      {group.map((scan) => {
                        const band =
                          bands.find((b) => b.code === scan.bandCode) ??
                          findBandForPh(bands, scan.phValue ?? Number.NaN);
                        const style = phTokenStyle(band?.colorToken ?? "color-ph-unknown");
                        const StatusIcon = band?.severity === "NORMAL" ? CheckCircle2 : AlertTriangle;
                        return (
                          <li key={scan.scanId} className="relative">
                            <span
                              className={`absolute -left-[36px] top-1/2 size-4 -translate-y-1/2 rounded-full border-[3px] border-surface ${style.solid}`}
                              aria-hidden="true"
                            />
                            <button
                              type="button"
                              onClick={() => {
                                onOpenScan(scan.scanId);
                              }}
                              className="grid w-full grid-cols-[auto_minmax(0,1fr)_auto_auto] items-center gap-4 rounded-2xl bg-background-alt/50 px-4 py-3 text-left transition-colors hover:bg-background-alt"
                            >
                              {/* Màu hạt đo được nếu E2 có `thumbnailHex`; không thì biểu tượng
                                  tô theo màu dải pH thật — không ảnh minh hoạ. */}
                              {scan.thumbnailHex ? (
                                <span
                                  className="size-11 rounded-xl border border-border"
                                  style={{ backgroundColor: scan.thumbnailHex }}
                                  aria-hidden="true"
                                />
                              ) : (
                                <span
                                  className={`flex size-11 items-center justify-center rounded-xl ${style.bg}`}
                                  aria-hidden="true"
                                >
                                  <StatusIcon className={`size-5 ${style.text}`} />
                                </span>
                              )}

                              <span className="flex min-w-0 flex-col gap-1">
                                <span className="text-body font-bold text-text-primary">
                                  {timestampLabel(scan.capturedAt)}
                                </span>
                                <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
                                  {scan.confidence !== null ? (
                                    <span className="text-small text-text-secondary">
                                      {t("web.confidence", { value: Math.round(scan.confidence * 100) })}
                                    </span>
                                  ) : null}
                                  {scan.nearBoundary ? (
                                    <span className="rounded-full bg-surface px-2.5 py-0.5 text-small font-semibold text-text-secondary">
                                      {t("web.nearBoundary")}
                                    </span>
                                  ) : null}
                                  {scan.disputed ? (
                                    <span className="rounded-full bg-surface px-2.5 py-0.5 text-small font-semibold text-text-secondary">
                                      {t("item.disputedBadge")}
                                    </span>
                                  ) : null}
                                  {scan.hasNote ? (
                                    <span className="rounded-full bg-surface px-2.5 py-0.5 text-small font-semibold text-text-secondary">
                                      {t("web.hasNote")}
                                    </span>
                                  ) : null}
                                </span>
                              </span>

                              <span className="flex flex-col items-end gap-1">
                                <span className="text-h3 font-bold text-primary-dark">
                                  {scan.phValue != null ? t("web.phValue", { value: scan.phValue.toFixed(1) }) : dash}
                                </span>
                                <span
                                  className={`whitespace-nowrap rounded-full px-3 py-1 text-small font-semibold ${style.bg} ${style.text}`}
                                >
                                  {band?.label ?? scan.classification}
                                </span>
                              </span>

                              <span className="flex items-center gap-1 text-caption font-semibold text-primary-dark">
                                {t("item.detailLink")}
                                <ChevronRight className="size-4" aria-hidden="true" />
                              </span>
                            </button>
                          </li>
                        );
                      })}
                    </ol>
                  </section>
                ))}

                {hasNextPage ? (
                  <button
                    type="button"
                    onClick={onLoadMore}
                    disabled={isFetchingNextPage}
                    className="self-start min-h-11 rounded-xl bg-background-alt px-5 py-3 text-body font-semibold text-primary-dark disabled:opacity-60"
                  >
                    {t("state.loadMore")}
                  </button>
                ) : null}
              </div>
            )}
          </Panel>
        </div>
      </div>

      {/* CTA xuất hồ sơ — nguyên văn mockup `07` */}
      <Panel className="flex flex-wrap items-center justify-between gap-5">
        <div className="max-w-[600px]">
          <p className="text-h3 font-bold text-text-primary">{t("export.title")}</p>
          <p className="pt-1 text-body leading-relaxed text-text-secondary">{t("export.description")}</p>
        </div>
        <button
          type="button"
          onClick={onExport}
          className="min-h-12 shrink-0 rounded-2xl bg-primary px-6 py-3 text-body font-bold text-white shadow-sm"
        >
          {t("export.cta")}
        </button>
      </Panel>

      <DisclaimerBanner variant="short" />
    </div>
  );
}
