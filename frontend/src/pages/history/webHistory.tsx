import { useTranslation } from "react-i18next";
import { CatAvatar } from "@/entities/cat";
import { DisclaimerBanner } from "@/entities/disclaimer";
import { PhGaugeBar, findBandForPh, phTokenStyle, type PhBand } from "@/entities/ph-bands";
import type { ScanListItem, ScanSummary } from "@/entities/scan-result";
import { PhBandLegendRow, type HistoryFilterKey } from "@/features/history";

import tipScanFrequency from "@/shared/assets/images/web-history/tip-scan-frequency.jpg";
import timelineScanSample from "@/shared/assets/images/web-history/timeline-scan-sample.jpg";

/**
 * Bố cục DESKTOP (>= lg) cho màn Lịch sử Sức khoẻ — dựng từ bản export gốc của Figma
 * `Web - 06 & 07. Hồ sơ Bé Mèo & Sổ khám Y tế (Cat Medical Profile)`, phần panel
 * "Dòng thời gian Y tế & Quét chỉ thị sinh học" (đích của link "Xem toàn bộ lịch sử"),
 * ghép với các khối của mockup mobile `07. Lịch sử Sức khỏe (Health History)`.
 *
 * KHÔNG tự thêm padding ở `lg` — `AppLayout` đã cấp khung nội dung 944px kèm `lg:px-6 lg:py-6`.
 *
 * DỮ LIỆU THẬT: E2 `GET /scans` (timeline, cursor-paging) + E3 `GET /scans/summary` (KPI,
 * trung vị, khoảng pH) + `GET /reference/ph-bands` (nhãn/màu/ngưỡng). Tuyệt đối không
 * hard-code ngưỡng pH ở đây — mọi nhãn dải đều tra từ `bands`.
 *
 * KHÁC VỚI `pages/cat/CatMedicalPanels.tsx`: panel ở hồ sơ mèo là bản xem trước 3 sự kiện
 * DỮ LIỆU MẪU (bệnh án/vaccine — backend không có bảng); màn này là bản đầy đủ chạy bằng
 * dữ liệu quét thật. Hai nơi bổ sung cho nhau, không trùng nội dung.
 */

/**
 * CÒN LÀ MOCK THEO THIẾT KẾ — API không trả các mục này:
 * - ảnh mẫu hạt chỉ thị trong timeline (E2 chỉ trả `thumbnailHex`, không trả URL ảnh);
 * - thẻ mẹo "Bao lâu nên quét một lần?" (nội dung tĩnh lấy nguyên văn từ mockup `07`).
 */
const DESIGN_MOCK_SAMPLE_IMAGE = timelineScanSample;

function Panel({ children, className = "" }: { children: React.ReactNode; className?: string }) {
  return <div className={`rounded-2xl bg-surface p-5 shadow-xs ${className}`}>{children}</div>;
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
  monthLabel,
  timestampLabel,
}: WebHistoryScreenProps) {
  const { t } = useTranslation(["history", "common"]);
  const dash = t("web.valueUnavailable");

  const header = (
    <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
      <div className="max-w-[600px]">
        <p className="text-overline font-bold tracking-[0.4px] text-primary-dark">{t("web.eyebrow")}</p>
        <h1 className="pt-1 text-[26px] font-bold leading-8 tracking-[-0.5px] text-primary-dark">{t("web.title")}</h1>
        <p className="pt-2 text-caption leading-relaxed text-text-secondary">
          {catName ? t("web.subtitleNamed", { name: catName }) : t("web.subtitle")}
        </p>
      </div>
      <button
        type="button"
        onClick={onExport}
        className="inline-flex shrink-0 items-center justify-center gap-2 rounded-xl bg-primary px-4 py-2.5 text-caption font-bold text-white shadow-sm"
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
    summary && summary.min !== null && summary.max !== null
      ? t("web.kpiRangeValue", { min: summary.min.toFixed(1), max: summary.max.toFixed(1) })
      : dash;

  return (
    <div className="flex flex-col gap-4">
      {header}

      {/* 4 KPI — toàn bộ lấy từ E3 `GET /scans/summary` */}
      <div className="grid grid-cols-2 gap-4 xl:grid-cols-4">
        <Panel className="flex flex-col gap-1.5">
          <p className="text-overline font-bold tracking-[0.4px] text-text-secondary">{t("web.kpiTotal")}</p>
          <span className="text-[26px] font-bold leading-8 text-primary-dark">
            {isPending ? dash : (summary?.count ?? 0)}
          </span>
          <p className="mt-auto border-t border-border pt-2 text-[11px] leading-snug text-text-secondary">
            {t("web.kpiTotalFoot")}
          </p>
        </Panel>

        <Panel className="flex flex-col gap-1.5">
          <p className="text-overline font-bold tracking-[0.4px] text-text-secondary">{t("web.kpiMedian")}</p>
          <span className="text-[26px] font-bold leading-8 text-primary-dark">
            {summary?.median != null ? summary.median.toFixed(2) : dash}
          </span>
          <p className="mt-auto border-t border-border pt-2 text-[11px] leading-snug text-text-secondary">
            {t("web.kpiMedianFoot")}
          </p>
        </Panel>

        <Panel className="flex flex-col gap-1.5">
          <p className="text-overline font-bold tracking-[0.4px] text-text-secondary">{t("web.kpiRange")}</p>
          <span className="text-[26px] font-bold leading-8 text-primary-dark">{rangeText}</span>
          <p className="mt-auto border-t border-border pt-2 text-[11px] leading-snug text-text-secondary">
            {t("web.kpiRangeFoot")}
          </p>
        </Panel>

        <Panel className="flex flex-col gap-1.5">
          <p className="text-overline font-bold tracking-[0.4px] text-text-secondary">{t("web.kpiLowConf")}</p>
          <span className="text-[26px] font-bold leading-8 text-primary-dark">{summary?.lowConfidenceCount ?? 0}</span>
          <p className="mt-auto border-t border-border pt-2 text-[11px] leading-snug text-text-secondary">
            {t("web.kpiLowConfFoot", { count: summary?.inconclusiveCount ?? 0 })}
          </p>
        </Panel>
      </div>

      <div className="grid grid-cols-12 gap-4">
        {/* ── Cột trái: nhận diện mèo, phổ pH, bộ lọc, mẹo ───────────────────────────── */}
        <div className="col-span-12 flex flex-col gap-4 xl:col-span-4">
          <Panel className="flex flex-col gap-3">
            <div className="flex items-center gap-3">
              <CatAvatar src={catAvatarUrl} name={catName ?? t("web.unknownCat")} size="md" />
              <div className="min-w-0">
                <p className="truncate text-body font-bold text-text-primary">{catName ?? t("web.unknownCat")}</p>
                {catBreedName ? <p className="truncate text-caption text-text-secondary">{catBreedName}</p> : null}
              </div>
            </div>
            <div className="border-t border-border pt-3">
              <div className="flex items-baseline justify-between gap-2">
                <p className="text-caption font-semibold text-text-primary">{t("spectrum.title")}</p>
                {summary?.median != null ? (
                  <span className="text-[11px] text-text-secondary">
                    {t("spectrum.average", { value: summary.median.toFixed(1) })}
                  </span>
                ) : null}
              </div>
              <div className="flex flex-col gap-1.5 pt-2">
                <PhGaugeBar bands={bands} value={summary?.median ?? null} />
                <PhBandLegendRow bands={bands} />
              </div>
            </div>
          </Panel>

          <Panel className="flex flex-col gap-2">
            <p className="text-overline font-bold tracking-[0.4px] text-text-secondary">{t("web.filterLabel")}</p>
            <div className="flex flex-col gap-1.5" role="tablist" aria-label={t("web.filterLabel")}>
              {FILTERS.map((option) => (
                <button
                  key={option}
                  type="button"
                  role="tab"
                  aria-selected={filter === option}
                  onClick={() => {
                    onFilterChange(option);
                  }}
                  className={`rounded-xl px-3 py-2 text-left text-caption font-semibold transition-colors ${
                    filter === option
                      ? "bg-primary-dark text-white"
                      : "bg-background-alt text-text-secondary hover:bg-chip-bg"
                  }`}
                >
                  {t(`filters.${option}`)}
                </button>
              ))}
            </div>
          </Panel>

          {/* Thẻ mẹo — nội dung tĩnh theo mockup `07`, không có API */}
          <Panel className="flex gap-3 bg-background-alt">
            <img src={tipScanFrequency} alt="" className="size-14 shrink-0 rounded-xl object-cover" />
            <div className="min-w-0">
              <p className="text-caption font-bold text-primary-dark">{t("web.tipTitle")}</p>
              <p className="pt-1 text-[11px] leading-relaxed text-text-secondary">{t("web.tipBody")}</p>
            </div>
          </Panel>
        </div>

        {/* ── Cột phải: dòng thời gian ────────────────────────────────────────────────── */}
        <div className="col-span-12 xl:col-span-8">
          <Panel className="flex flex-col gap-4">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p className="text-[18px] font-bold leading-6 text-primary-dark">{t("web.timelineTitle")}</p>
                <p className="pt-1 text-caption text-text-secondary">{t("web.timelineSubtitle")}</p>
              </div>
              <span className="shrink-0 rounded-full bg-chip-bg px-3 py-1 text-[11px] font-semibold text-primary-dark">
                {t("web.recordCount", { count: totalLoaded })}
              </span>
            </div>

            {isPending ? (
              <div className="flex flex-col gap-3">
                {[0, 1, 2].map((i) => (
                  <div key={i} className="h-20 animate-pulse rounded-xl bg-background-alt" />
                ))}
              </div>
            ) : groups.length === 0 ? (
              <div className="flex flex-col items-start gap-2 rounded-xl bg-background-alt p-6">
                <p className="text-body font-semibold text-text-primary">{t("state.emptyTitle")}</p>
                <p className="text-caption text-text-secondary">{t("state.emptyDescription")}</p>
                <button
                  type="button"
                  onClick={onStartScan}
                  className="mt-1 rounded-xl bg-primary px-4 py-2.5 text-caption font-bold text-white shadow-sm"
                >
                  {t("state.emptyCta")}
                </button>
              </div>
            ) : (
              <div className="flex flex-col gap-5">
                {groups.map(([key, group]) => (
                  <section key={key} className="flex flex-col gap-3">
                    <div className="flex items-baseline justify-between gap-2">
                      <h2 className="text-caption font-bold text-text-primary">{monthLabel(key)}</h2>
                      <span className="text-[11px] text-text-tertiary">
                        {t("monthGroup.count", { count: group.length })}
                      </span>
                    </div>

                    <ol className="relative flex flex-col gap-5 border-l border-border pl-6">
                      {group.map((scan) => {
                        const band =
                          bands.find((b) => b.code === scan.bandCode) ??
                          findBandForPh(bands, scan.phValue ?? Number.NaN);
                        const style = phTokenStyle(band?.colorToken ?? "color-ph-unknown");
                        return (
                          <li key={scan.scanId} className="relative">
                            <span
                              className={`absolute -left-[31px] top-1.5 size-3 rounded-full border-2 border-surface ${style.solid}`}
                              aria-hidden="true"
                            />
                            <div className="flex items-start gap-3">
                              {scan.thumbnailHex ? (
                                <span
                                  className="size-14 shrink-0 rounded-xl border border-border"
                                  style={{ backgroundColor: scan.thumbnailHex }}
                                  aria-hidden="true"
                                />
                              ) : (
                                <img
                                  src={DESIGN_MOCK_SAMPLE_IMAGE}
                                  alt=""
                                  className="size-14 shrink-0 rounded-xl object-cover"
                                />
                              )}

                              <div className="min-w-0 flex-1">
                                <div className="flex flex-wrap items-center gap-2">
                                  <span
                                    className={`inline-flex items-center rounded-full px-2.5 py-1 text-[11px] font-semibold ${style.bg} ${style.text}`}
                                  >
                                    {band?.label ?? scan.classification}
                                  </span>
                                  <span className="text-body font-bold text-primary-dark">
                                    {scan.phValue != null
                                      ? t("web.phValue", { value: scan.phValue.toFixed(1) })
                                      : dash}
                                  </span>
                                  <span className="text-[11px] text-text-tertiary">
                                    {timestampLabel(scan.capturedAt)}
                                  </span>
                                </div>

                                <div className="flex flex-wrap items-center gap-x-3 gap-y-1 pt-2">
                                  {scan.confidence !== null ? (
                                    <span className="text-[11px] text-text-secondary">
                                      {t("web.confidence", {
                                        value: Math.round(scan.confidence * 100),
                                      })}
                                    </span>
                                  ) : null}
                                  {scan.nearBoundary ? (
                                    <span className="rounded-full bg-background-alt px-2 py-0.5 text-[11px] font-semibold text-text-secondary">
                                      {t("web.nearBoundary")}
                                    </span>
                                  ) : null}
                                  {scan.disputed ? (
                                    <span className="rounded-full bg-background-alt px-2 py-0.5 text-[11px] font-semibold text-text-secondary">
                                      {t("item.disputedBadge")}
                                    </span>
                                  ) : null}
                                  {scan.hasNote ? (
                                    <span className="rounded-full bg-background-alt px-2 py-0.5 text-[11px] font-semibold text-text-secondary">
                                      {t("web.hasNote")}
                                    </span>
                                  ) : null}
                                  <button
                                    type="button"
                                    onClick={() => {
                                      onOpenScan(scan.scanId);
                                    }}
                                    className="ml-auto text-caption font-semibold text-primary-dark hover:underline"
                                  >
                                    {t("item.detailLink")}
                                  </button>
                                </div>
                              </div>
                            </div>
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
                    className="self-start rounded-xl bg-background-alt px-4 py-2.5 text-caption font-semibold text-primary-dark disabled:opacity-60"
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
      <Panel className="flex flex-wrap items-center justify-between gap-4">
        <div className="max-w-[560px]">
          <p className="text-body font-bold text-text-primary">{t("export.title")}</p>
          <p className="pt-1 text-caption leading-relaxed text-text-secondary">{t("export.description")}</p>
        </div>
        <button
          type="button"
          onClick={onExport}
          className="shrink-0 rounded-xl bg-primary px-5 py-3 text-caption font-bold text-white shadow-sm"
        >
          {t("export.cta")}
        </button>
      </Panel>

      <DisclaimerBanner variant="short" />
    </div>
  );
}
