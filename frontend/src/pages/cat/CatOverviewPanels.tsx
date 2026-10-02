import { useTranslation } from "react-i18next";
import {
  BellRing,
  ChevronRight,
  Droplet,
  History,
  PencilLine,
  ScanLine,
  ShieldCheck,
  type LucideIcon,
} from "lucide-react";
import { Button, SkeletonLoader } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import { findBandForPh, PhGaugeBar, phTokenStyle, type PhBand } from "@/entities/ph-bands";
import type { ScanSummary } from "@/entities/scan-result";
import type { CatSummaryResponse } from "@/features/cat";
import { useScanHistory } from "@/features/history";

/**
 * Các khối TỔNG QUAN của `/cats/:catId` ở MOBILE — dựng theo mockup
 * `06. Hồ sơ Bé Mèo (Cat Profile)`: thẻ trạng thái chỉ thị sinh học, lưới 4 chỉ số,
 * danh sách lần quét gần đây, hai nút hành động chính.
 *
 * TOÀN BỘ SỐ LIỆU LÀ THẬT:
 *  - `GET /cats/{id}/summary` → số lần quét 30 ngày, tỉ lệ trong ngưỡng, lần quét gần nhất;
 *  - `GET /scans/summary`     → tổng số lần quét, trung vị, khoảng pH;
 *  - `GET /scans`             → 2 lần quét gần nhất;
 *  - `GET /reference/ph-bands`→ nhãn/màu/ngưỡng của dải.
 *
 * KHÁC MOCKUP CÓ CHỦ ĐÍCH: ô "Chuỗi theo dõi (14 ngày)" của mockup không có endpoint nào
 * tính streak → thay bằng "Tỉ lệ trong ngưỡng" (số thật). Ô "Lần nhắc tiếp theo" giữ đúng
 * khung nhưng `nextReminderAt` luôn `null` ở MVP (nhắc lịch thuộc M5) nên hiển thị trạng
 * thái rỗng thật, không bịa ngày.
 */

const isBound = (v: number | null | undefined): v is number => typeof v === "number" && Number.isFinite(v);

/* ================================================================== Biomarker status */

export function CatBiomarkerCard({
  bands,
  summary,
}: {
  bands: PhBand[];
  summary: CatSummaryResponse | undefined;
}) {
  const { t } = useTranslation("cat");
  const lastScan = summary?.lastScan ?? null;
  const phValue = lastScan?.phValue ?? null;
  // `classification` của lần quét gần nhất CHÍNH LÀ mã dải (IN_RANGE / SLIGHTLY_HIGH...),
  // tra thẳng theo code trước rồi mới rơi về dò theo giá trị pH.
  const band =
    (lastScan ? bands.find((b) => b.code === lastScan.classification) : undefined) ??
    (phValue !== null ? findBandForPh(bands, phValue) : undefined) ??
    null;
  const style = phTokenStyle(band?.colorToken ?? "color-ph-unknown");

  const rangeLabel = band
    ? isBound(band.phMin) && isBound(band.phMax)
      ? t("overview.bandRange", { label: band.label, min: band.phMin.toFixed(1), max: band.phMax.toFixed(1) })
      : isBound(band.phMax)
        ? t("overview.bandBelow", { label: band.label, max: band.phMax.toFixed(1) })
        : isBound(band.phMin)
          ? t("overview.bandAbove", { label: band.label, min: band.phMin.toFixed(1) })
          : band.label
    : null;

  return (
    <section className="rounded-2xl bg-surface p-4 shadow-xs">
      <div className="flex items-start justify-between gap-3">
        <p className="flex items-start gap-2 text-overline font-bold uppercase tracking-[0.4px] text-text-tertiary">
          <ShieldCheck className="mt-px size-4 shrink-0 text-primary" aria-hidden="true" />
          {t("overview.biomarkerEyebrow")}
        </p>
        <span
          className={cn("shrink-0 rounded-full px-2.5 py-1 text-small font-semibold", style.bg, style.text)}
        >
          {band?.severity === "NORMAL" ? t("overview.biomarkerStable") : t("overview.biomarkerWatch")}
        </span>
      </div>

      {phValue === null ? (
        <>
          <p className="pt-2 text-h3 font-bold text-text-primary">{t("overview.biomarkerNoDataTitle")}</p>
          <p className="pt-1 text-caption leading-relaxed text-text-secondary">
            {t("overview.biomarkerNoDataBody")}
          </p>
        </>
      ) : (
        <>
          <p className="pt-2 text-h3 font-bold text-text-primary">{band?.label ?? lastScan?.classification}</p>
          <p className="pt-1 text-caption leading-relaxed text-text-secondary">
            {t("overview.biomarkerBody", { count: summary?.scanCount30d ?? 0 })}
          </p>

          <div className="flex flex-wrap items-baseline justify-between gap-2 pt-4">
            <span className="text-small text-text-tertiary">
              {t("overview.biomarkerCurrent", { value: phValue.toFixed(1) })}
            </span>
            {rangeLabel ? (
              <span className={cn("text-small font-semibold", style.text)}>{rangeLabel}</span>
            ) : null}
          </div>
          <PhGaugeBar bands={bands} value={phValue} className="pt-1.5" />
        </>
      )}
    </section>
  );
}

/* ================================================================== 4 chỉ số */

function StatTile({
  label,
  icon: Icon,
  value,
  unit,
  foot,
  tone = "default",
}: {
  label: string;
  icon: LucideIcon;
  value: string;
  unit?: string;
  foot: string;
  tone?: "default" | "normal";
}) {
  return (
    <div className="flex flex-col rounded-2xl bg-surface p-4 shadow-xs">
      <div className="flex items-start justify-between gap-2">
        <p className="text-caption text-text-secondary">{label}</p>
        <span className="flex size-7 shrink-0 items-center justify-center rounded-lg bg-background-alt">
          <Icon className="size-4 text-primary" aria-hidden="true" />
        </span>
      </div>
      <p className="pt-2">
        <span className={cn("text-h2 font-bold", tone === "normal" ? "text-ph-normal-text" : "text-text-primary")}>
          {value}
        </span>
        {unit ? <span className="pl-1 text-caption text-text-secondary">{unit}</span> : null}
      </p>
      <p className="pt-0.5 text-small text-text-tertiary">{foot}</p>
    </div>
  );
}

export function CatStatGrid({
  summary,
  scanSummary,
}: {
  summary: CatSummaryResponse | undefined;
  scanSummary: ScanSummary | undefined;
}) {
  const { t } = useTranslation("cat");
  const dash = "—";

  const inRange =
    summary?.inRangeRatio30d != null ? `${String(Math.round(summary.inRangeRatio30d * 100))}%` : dash;
  const median = scanSummary?.median != null ? scanSummary.median.toFixed(1) : dash;
  const rangeFoot =
    scanSummary?.min != null && scanSummary.max != null
      ? t("overview.statMedianFoot", { min: scanSummary.min.toFixed(1), max: scanSummary.max.toFixed(1) })
      : t("overview.statMedianFootEmpty");

  return (
    <div className="grid grid-cols-2 gap-3">
      <StatTile
        label={t("overview.statScanTotal")}
        icon={ScanLine}
        value={String(scanSummary?.count ?? 0)}
        unit={t("overview.statScanTotalUnit")}
        foot={t("overview.statScanTotalFoot", { count: summary?.scanCount30d ?? 0 })}
      />
      <StatTile
        label={t("overview.statInRange")}
        icon={ShieldCheck}
        value={inRange}
        tone="normal"
        foot={t("overview.statInRangeFoot")}
      />
      <StatTile
        label={t("overview.statMedian")}
        icon={Droplet}
        value={median}
        foot={rangeFoot}
      />
      <StatTile
        label={t("overview.statReminder")}
        icon={BellRing}
        value={t("overview.statReminderNone")}
        foot={t("overview.statReminderFoot")}
      />
    </div>
  );
}

/* ================================================================== Lần quét gần đây */

const RECENT_LIMIT = 2;

export function CatRecentScansCard({
  catId,
  bands,
  totalCount,
  timestampLabel,
  onOpenScan,
  onViewAll,
}: {
  catId: string;
  bands: PhBand[];
  totalCount: number;
  timestampLabel: (iso: string) => string;
  onOpenScan: (scanId: string) => void;
  onViewAll: () => void;
}) {
  const { t } = useTranslation("cat");
  const { data, isPending, isError } = useScanHistory(catId, "ALL");
  const items = (data?.pages.flatMap((p) => p.items) ?? []).slice(0, RECENT_LIMIT);

  return (
    <section className="rounded-2xl bg-surface p-4 shadow-xs">
      <div className="flex items-center justify-between gap-3">
        <h2 className="flex items-center gap-2 text-h3 font-bold text-text-primary">
          <History className="size-5 shrink-0 text-primary" aria-hidden="true" />
          {t("overview.recentTitle")}
        </h2>
        <span className="shrink-0 text-small text-text-tertiary">
          {t("overview.recentCount", { shown: items.length, total: totalCount })}
        </span>
      </div>

      <div className="flex flex-col gap-2 pt-3">
        {isPending ? (
          <SkeletonLoader shape="card" className="h-16" />
        ) : isError ? (
          <p className="text-caption text-text-secondary">{t("overview.recentError")}</p>
        ) : items.length === 0 ? (
          <p className="text-caption text-text-secondary">{t("overview.recentEmpty")}</p>
        ) : (
          items.map((scan) => {
            const band = bands.find((b) => b.code === scan.bandCode);
            const style = phTokenStyle(band?.colorToken ?? "color-ph-unknown");
            return (
              <button
                key={scan.scanId}
                type="button"
                onClick={() => {
                  onOpenScan(scan.scanId);
                }}
                className="flex min-h-[var(--touch-target-min)] items-center gap-3 rounded-xl bg-background-alt p-3 text-left"
              >
                <span
                  className={cn("flex size-8 shrink-0 items-center justify-center rounded-full", style.bg)}
                  aria-hidden="true"
                >
                  <Droplet className={cn("size-4", style.text)} />
                </span>
                <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                  <span className="flex flex-wrap items-center gap-2">
                    <span className="text-body font-bold text-text-primary">
                      {scan.phValue !== null
                        ? t("web.profile.phValueShort", { value: scan.phValue.toFixed(1) })
                        : "—"}
                    </span>
                    {band ? (
                      <span className={cn("rounded-full px-2 py-0.5 text-small font-semibold", style.bg, style.text)}>
                        {band.label}
                      </span>
                    ) : null}
                  </span>
                  <span className="truncate text-small text-text-tertiary">{timestampLabel(scan.capturedAt)}</span>
                </span>
                <ChevronRight className="size-4 shrink-0 text-text-tertiary" aria-hidden="true" />
              </button>
            );
          })
        )}
      </div>

      <button
        type="button"
        onClick={onViewAll}
        className="mt-3 flex min-h-[var(--touch-target-min)] w-full items-center justify-center gap-1.5 text-caption font-semibold text-primary-dark"
      >
        {totalCount > 0 ? t("overview.recentAll", { count: totalCount }) : t("overview.recentAllShort")}
        <ChevronRight className="size-4" aria-hidden="true" />
      </button>
    </section>
  );
}

/* ================================================================== Hành động chính */

export function CatProfileActions({
  name,
  onScan,
  onEdit,
}: {
  name: string;
  onScan: () => void;
  onEdit: () => void;
}) {
  const { t } = useTranslation("cat");
  return (
    <div className="flex flex-col gap-3">
      <Button type="button" size="lg" leftIcon={<ScanLine className="size-5" aria-hidden="true" />} onClick={onScan}>
        {t("overview.scanCta", { name })}
      </Button>
      <Button
        type="button"
        variant="tertiary"
        size="lg"
        leftIcon={<PencilLine className="size-5" aria-hidden="true" />}
        onClick={onEdit}
      >
        {t("overview.editCta")}
      </Button>
    </div>
  );
}
