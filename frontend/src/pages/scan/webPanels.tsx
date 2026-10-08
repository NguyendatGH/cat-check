import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { AlertTriangle, ArrowRight, Camera, ChevronRight, ImageOff, Info, Lock, ScanLine } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { findBandForPh, phTokenStyle, type PhBand } from "@/entities/ph-bands";
import type { ScanListItem, ScanResult } from "@/entities/scan-result";
import { PhScale, QualityFlagList, TriggeredFlagList, useRecentCatScans } from "@/features/scan";

import iconBanner from "@/shared/assets/icons/web-scan/imgContainer.svg";
import iconCatLabel from "@/shared/assets/icons/web-scan/imgContainer3.svg";
import iconViewport from "@/shared/assets/icons/web-scan/imgContainer4.svg";
import iconUpload from "@/shared/assets/icons/web-scan/imgContainer6.svg";
import iconTipsTitle from "@/shared/assets/icons/web-scan/imgContainer11.svg";
import iconTip1 from "@/shared/assets/icons/web-scan/imgMargin.svg";
import iconTip2 from "@/shared/assets/icons/web-scan/imgMargin1.svg";
import iconResultBadge from "@/shared/assets/icons/web-scan/imgContainer12.svg";
import iconPdf from "@/shared/assets/icons/web-scan/imgContainer16.svg";

/**
 * Bố cục DESKTOP (>= lg) cho màn Quét & Kết quả — khung lấy từ `Web - 03 & 04. Quét & Kết
 * quả phân tích cát` (lưới 2 cột: trái 5/12 ảnh & bé mèo, phải 7/12 kết quả) cùng các biến thể
 * `Web - 03a` (đang phân tích), `Web - 03b` (INCONCLUSIVE) và `Web - Sys - 06` (hết credit).
 *
 * Mobile KHÔNG dùng file này — các page chỉ render nó từ breakpoint `lg` trở lên.
 *
 * MỌI con số trên màn đều truy được về một field API. Những khối của bản thiết kế KHÔNG có
 * nguồn dữ liệu đã bị bỏ hẳn (không thay bằng chữ mẫu):
 *  - badge "AI LAB DIAGNOSTIC V3.4", "Quang phổ kế sẵn sàng", pill "Chứng chỉ thú y ISO/Vet" —
 *    claim không có bằng chứng; thay bằng hai pill dữ liệu thật do page truyền vào (lần quét gần
 *    nhất của bé — E2; số dư credit — C3).
 *  - ảnh cát mẫu + hai điểm đo "RGB #8FE3AA", "482 điểm hạt (độ tin cậy 99.4%)" — ảnh stock và
 *    số bịa; khung ảnh giờ chỉ hiện ảnh người dùng chọn hoặc ảnh đã lưu của lần quét.
 *  - thẻ "Cân chỉnh ánh sáng (+12% / Chuẩn / 5400K)" và dòng "hoàn tất 100% trong 0.8s" — số
 *    bịa; thay bằng thẻ "Thông tin lần quét" đọc từ `ScanResultResponse`.
 *  - ma trận bệnh lý (FLUTD, sỏi, "vi máu") và "Lời khuyên bác sĩ" — chẩn đoán, trái quyết định
 *    "không chẩn đoán"; vị trí đó nay là khối "Bạn nên làm gì?" dùng chung với mobile.
 *  - nút "Lưu vào lịch sử" (E1 đã lưu ngay khi phân tích) và "Hỏi bác sĩ" (không có tính năng).
 */

function Panel({ children, className = "" }: { children: ReactNode; className?: string }) {
  return <div className={cn("rounded-2xl bg-surface p-4 shadow-xs", className)}>{children}</div>;
}

/* ---------------- Đầu trang ---------------- */

export interface HeaderPill {
  key: string;
  icon: ReactNode;
  label: string;
  value: string;
}

/** Banner đầu trang (Figma `16:6337`): icon + tiêu đề + tối đa 2 pill dữ liệu THẬT. */
export function WebScanHeader({
  title,
  subtitle,
  pills = [],
}: {
  title: string;
  subtitle?: string;
  pills?: HeaderPill[];
}) {
  return (
    <Panel className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
      <div className="flex min-w-0 items-start gap-3">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-chip-bg">
          <img src={iconBanner} alt="" className="size-5" />
        </span>
        <div className="min-w-0">
          <h1 className="text-[20px] font-bold leading-7 tracking-[-0.5px] text-text-primary">{title}</h1>
          {subtitle ? <p className="text-caption text-text-secondary">{subtitle}</p> : null}
        </div>
      </div>
      {pills.length > 0 ? (
        <div className="flex flex-wrap gap-2 xl:shrink-0 xl:flex-nowrap">
          {pills.map((pill) => (
            <div key={pill.key} className="flex items-center gap-2 rounded-xl bg-background-alt px-3 py-2">
              <span className="shrink-0 text-primary-dark" aria-hidden="true">
                {pill.icon}
              </span>
              <div className="leading-tight">
                <p className="whitespace-nowrap text-[11px] tracking-[0.4px] text-text-secondary">{pill.label}</p>
                <p className="whitespace-nowrap text-caption font-bold text-text-primary">{pill.value}</p>
              </div>
            </div>
          ))}
        </div>
      ) : null}
    </Panel>
  );
}

/* ---------------- Thẻ bé mèo ---------------- */

export interface WebCatCardProps {
  /** `null` = khay dùng chung (không có hồ sơ). */
  name: string | null;
  meta?: string | null;
  avatarUrl?: string | null;
  action?: ReactNode;
}

/** "Bé mèo đang quét" (Figma `16:6370`) — chỉ hiện dữ liệu hồ sơ thật, thiếu field thì bỏ dòng. */
export function WebCatCard({ name, meta, avatarUrl, action }: WebCatCardProps) {
  const { t } = useTranslation("scan");
  return (
    <Panel className="flex flex-col gap-3">
      <p className="inline-flex items-center gap-1.5 text-overline font-bold tracking-[0.4px] text-text-secondary">
        <img src={iconCatLabel} alt="" className="size-3.5" />
        {t("web.catSectionLabel")}
      </p>
      <div className="flex items-center gap-3 rounded-xl bg-background-alt p-2">
        {name === null ? (
          <span className="flex size-12 shrink-0 items-center justify-center rounded-lg bg-secondary-light text-h3 font-bold text-secondary-text-on">
            ?
          </span>
        ) : avatarUrl ? (
          <img src={avatarUrl} alt="" className="size-12 shrink-0 rounded-lg object-cover" />
        ) : (
          <span className="flex size-12 shrink-0 items-center justify-center rounded-lg bg-chip-bg text-h3 font-bold text-primary">
            {name.charAt(0).toUpperCase()}
          </span>
        )}
        <div className="min-w-0 flex-1">
          <p className="truncate text-body font-bold text-text-primary">{name ?? t("web.sharedTrayTitle")}</p>
          {name === null ? (
            <p className="text-caption text-text-secondary">{t("web.sharedTrayHint")}</p>
          ) : meta ? (
            <p className="truncate text-caption text-text-secondary">{meta}</p>
          ) : null}
        </div>
        {action}
      </div>
    </Panel>
  );
}

/* ---------------- Cột trái màn chụp ---------------- */

export interface CaptureErrorMessage {
  tone: "danger" | "warning";
  text: string;
}

interface WebCapturePanelProps {
  catCard: ReactNode;
  /** Ảnh người dùng vừa chọn — chưa có thì khung trống (KHÔNG dựng ảnh mẫu). */
  previewUrl?: string | null;
  onPickFile: (file: File, source: "CAMERA" | "GALLERY") => void;
  onSubmit: () => void;
  submitDisabled?: boolean;
  /** Hết credit (Sys-06): nút phân tích bị khoá và hiện biểu tượng khoá. */
  locked?: boolean;
  /** Đang gửi/phân tích — khoá cả hai nút chọn ảnh. */
  busy?: boolean;
  error?: CaptureErrorMessage | null;
}

/** Góc khung ngắm — 4 chữ L ở 4 góc (giống khung mobile `03`). */
const FRAME_CORNERS = [
  "left-3 top-3 rounded-tl-lg border-l-[3px] border-t-[3px]",
  "right-3 top-3 rounded-tr-lg border-r-[3px] border-t-[3px]",
  "left-3 bottom-3 rounded-bl-lg border-b-[3px] border-l-[3px]",
  "right-3 bottom-3 rounded-br-lg border-b-[3px] border-r-[3px]",
] as const;

function FilePickButton({
  label,
  icon,
  capture,
  disabled = false,
  onPick,
}: {
  label: string;
  icon: ReactNode;
  capture?: boolean;
  disabled?: boolean;
  onPick: (file: File) => void;
}) {
  return (
    <label
      className={cn(
        "inline-flex min-h-9 items-center gap-1.5 rounded-lg bg-deco-backdrop px-3 py-1.5 text-caption font-semibold text-primary-dark focus-within:outline focus-within:outline-[var(--focus-ring-width)] focus-within:outline-[var(--focus-ring-color)]",
        disabled ? "cursor-not-allowed opacity-50" : "cursor-pointer hover:bg-chip-bg",
      )}
    >
      {icon}
      {label}
      <input
        type="file"
        accept="image/jpeg,image/png,image/webp"
        capture={capture ? "environment" : undefined}
        disabled={disabled}
        className="sr-only"
        onChange={(event) => {
          const file = event.target.files?.[0];
          if (file) onPick(file);
          event.target.value = "";
        }}
      />
    </label>
  );
}

/** Cột TRÁI desktop `/scan`: bé mèo, khung ảnh, CTA phân tích, mẹo chụp (Figma `16:6369`). */
export function WebCapturePanel({
  catCard,
  previewUrl,
  onPickFile,
  onSubmit,
  submitDisabled,
  locked = false,
  busy = false,
  error,
}: WebCapturePanelProps) {
  const { t } = useTranslation("scan");
  return (
    <div className="flex flex-col gap-4">
      {catCard}

      <Panel className="flex flex-col gap-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p className="inline-flex items-center gap-2 text-body font-bold text-text-primary">
            <img src={iconViewport} alt="" className="size-4" />
            {t("web.viewportTitle")}
          </p>
          <div className="flex flex-wrap gap-2">
            <FilePickButton
              label={t("web.takePhoto")}
              icon={<Camera className="size-3.5" aria-hidden="true" />}
              capture
              disabled={busy}
              onPick={(file) => {
                onPickFile(file, "CAMERA");
              }}
            />
            <FilePickButton
              label={t("web.uploadNew")}
              icon={<img src={iconUpload} alt="" className="size-3.5" />}
              disabled={busy}
              onPick={(file) => {
                onPickFile(file, "GALLERY");
              }}
            />
          </div>
        </div>

        <div className="relative aspect-[4/3] overflow-hidden rounded-xl bg-primary-darker">
          {previewUrl ? (
            <img src={previewUrl} alt={t("result.imageAlt")} className="size-full object-cover" />
          ) : (
            <div className="flex size-full flex-col items-center justify-center gap-3 px-8 text-center">
              <ScanLine className="size-8 text-secondary" aria-hidden="true" />
              <p className="text-caption font-semibold text-white/90">{t("capture.frameHint")}</p>
              <p className="text-small text-white/70">{t("web.frameEmpty")}</p>
            </div>
          )}
          {FRAME_CORNERS.map((corner) => (
            <span key={corner} className={cn("absolute size-8 border-surface", corner)} aria-hidden="true" />
          ))}
        </div>
      </Panel>

      <div className="flex flex-col gap-2">
        <button
          type="button"
          onClick={onSubmit}
          disabled={submitDisabled || locked}
          className="inline-flex min-h-12 w-full items-center justify-center gap-2 rounded-xl bg-primary-dark px-6 py-3 text-body font-bold text-white shadow-brand-lg transition-colors hover:bg-primary disabled:cursor-not-allowed disabled:opacity-50"
        >
          {locked ? <Lock className="size-4" aria-hidden="true" /> : <ScanLine className="size-4" aria-hidden="true" />}
          {t("web.analyzeCta")}
        </button>
        <p className="text-small text-text-tertiary">{t("capture.creditNote")}</p>
        {error ? (
          <p
            role="alert"
            className={cn(
              "flex items-start gap-2 rounded-xl p-3 text-caption",
              error.tone === "danger" ? "bg-danger-bg text-danger-text" : "bg-warning-bg text-warning-text",
            )}
          >
            <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
            {error.text}
          </p>
        ) : null}
      </div>

      <TipsCard />
    </div>
  );
}

/** "Mẹo chụp màu cát" (Figma `16:6489`) — nội dung hướng dẫn tĩnh, không phải dữ liệu. */
export function TipsCard() {
  const { t } = useTranslation("scan");
  return (
    <Panel className="flex flex-col gap-3">
      <p className="inline-flex items-center gap-2 text-caption font-bold text-text-primary">
        <img src={iconTipsTitle} alt="" className="size-4" />
        {t("web.tipsTitle")}
      </p>
      <div className="grid gap-3 xl:grid-cols-2">
        {[
          { icon: iconTip1, text: t("web.tip1") },
          { icon: iconTip2, text: t("web.tip2") },
        ].map((tip) => (
          <div key={tip.text} className="flex gap-2 rounded-xl bg-background-alt p-2.5">
            <img src={tip.icon} alt="" className="mt-0.5 size-4 shrink-0" />
            <p className="text-small leading-snug text-text-secondary">{tip.text}</p>
          </div>
        ))}
      </div>
    </Panel>
  );
}

/* ---------------- Cột phải: kết quả ---------------- */

/** `band.description` của server là TEMPLATE chứa `{0}` để điền chính giá trị pH đó. */
function bandSentence(band: PhBand | undefined, phValue: number | null): string | null {
  if (!band?.description) return null;
  if (!band.description.includes("{0}")) return band.description;
  return phValue == null ? null : band.description.replace(/\{0\}/g, phValue.toFixed(1));
}

function finite(value: number | null | undefined): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

interface WebResultPanelProps {
  bands: PhBand[];
  /** pH THẬT của lần quét; `null`/bỏ trống = chưa có kết quả kết luận được. */
  phValue?: number | null;
  /** `classification` server đã gán cho lần quét — ưu tiên hơn việc suy lại từ con số pH. */
  bandCode?: string | null;
  /** Lần quét đầy đủ (màn kết quả) — có thì hiện khoảng ước lượng, cờ chất lượng, cờ xu hướng. */
  result?: ScanResult;
  /** Khối chèn giữa thang pH và hàng hành động (VD "Bạn nên làm gì?"). */
  children?: ReactNode;
  /** Hàng hành động cuối cột; bỏ trống ở màn chụp (chưa có gì để xuất). */
  actions?: ReactNode;
}

/**
 * Cột PHẢI desktop: kết quả + thang pH (Figma `16:6508`).
 *
 * Nhãn phân loại LUÔN là `band.label` do `GET /reference/ph-bands` trả (đi từ `label_key`
 * của server) — không có chuỗi "Khỏe mạnh"/"BÌNH THƯỜNG" nào viết cứng ở client, và khoảng
 * tham chiếu in ra cũng lấy từ dải `severity === "NORMAL"` chứ không phải số trong Figma.
 */
export function WebResultPanel({
  bands,
  phValue = null,
  bandCode = null,
  result,
  children,
  actions,
}: WebResultPanelProps) {
  const { t } = useTranslation("scan");
  const value = finite(phValue);
  const band = (bandCode !== null ? bands.find((b) => b.code === bandCode) : undefined) ?? findBandForPh(bands, value);
  const referenceBand = bands.find((b) => b.severity === "NORMAL");
  const refMin = finite(referenceBand?.phMin);
  const refMax = finite(referenceBand?.phMax);
  const sentence = bandSentence(band, value);
  const hasResult = value !== null || band !== undefined;
  const low = finite(result?.phLow);
  const high = finite(result?.phHigh);
  const style = band ? phTokenStyle(band.colorToken) : null;

  return (
    <div className="flex flex-col gap-4">
      <Panel className="flex flex-col gap-3">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="flex min-w-0 flex-1 items-start gap-3">
            <span
              className={cn(
                "flex size-10 shrink-0 items-center justify-center rounded-2xl",
                style ? style.bg : "bg-chip-bg",
              )}
            >
              <img src={iconResultBadge} alt="" className="size-5" />
            </span>
            <div className="min-w-0">
              <span className="text-overline font-bold tracking-[0.4px] text-text-secondary">
                {t("web.overallLabel")}
              </span>
              {/* Nhãn dải (`band.label` từ API) là TIÊU ĐỀ, tô màu theo `colorToken` của dải —
                  không lặp thêm một pill cùng chữ ngay phía trên như bản trước. */}
              <p className={cn("pt-1 text-[22px] font-bold leading-7", style ? style.text : "text-text-primary")}>
                {hasResult ? (band?.label ?? t("web.resultUnknown")) : t("web.awaitingTitle")}
              </p>
              {sentence ? <p className="pt-1 text-caption text-text-secondary">{sentence}</p> : null}
              {!hasResult ? <p className="pt-1 text-caption text-text-secondary">{t("web.awaitingBody")}</p> : null}
            </div>
          </div>
          <div className="shrink-0 text-right">
            {hasResult ? (
              <p className="text-[26px] font-bold leading-8 text-text-primary">
                {value !== null ? t("web.phReading", { value: value.toFixed(1) }) : t("web.valueUnavailable")}
              </p>
            ) : null}
            {refMin !== null && refMax !== null ? (
              <p className="text-[11px] leading-tight text-text-secondary">
                {t("web.referenceRange", { range: `${refMin.toFixed(1)} – ${refMax.toFixed(1)}` })}
              </p>
            ) : null}
          </div>
        </div>
        {low !== null && high !== null ? (
          <p className="text-caption text-text-secondary">
            {t("web.estimateRange", { low: low.toFixed(1), high: high.toFixed(1) })}
          </p>
        ) : null}
        {result?.nearBoundary ? (
          <p className="flex items-start gap-2 rounded-lg bg-background-alt p-2.5 text-caption text-text-secondary">
            <Info className="mt-0.5 size-4 shrink-0 text-info-text" aria-hidden="true" />
            {t("web.nearBoundary")}
          </p>
        ) : null}
        {result ? <QualityFlagList flags={result.qualityFlags} /> : null}
        {result ? <TriggeredFlagList flags={result.triggeredFlags} /> : null}
      </Panel>

      <Panel className="flex flex-col gap-3">
        <div>
          <p className="text-body font-bold text-text-primary">{t("web.spectrumTitle")}</p>
          <p className="text-caption text-text-secondary">
            {hasResult ? t("web.spectrumSubtitle") : t("web.spectrumSubtitleAwaiting")}
          </p>
        </div>
        {/* Dải màu + nhãn ngưỡng lấy TỪ API (entities/ph-bands) — không hard-code ngưỡng pH. */}
        <PhScale bands={bands} value={value} activeBand={band} showBandLegend />
      </Panel>

      {children}

      {actions ? <div className="flex flex-wrap gap-2">{actions}</div> : null}
    </div>
  );
}

/** Nút xuất PDF (Figma "Xuất phiếu PDF") — trang `/export` tự xử lý quyền theo gói. */
export function ExportPdfLink({ className }: { className?: string }) {
  const { t } = useTranslation("scan");
  return (
    <Link
      to="/export"
      className={cn(
        "inline-flex min-h-11 flex-1 items-center justify-center gap-2 rounded-xl bg-deco-backdrop px-4 py-2.5 text-caption font-semibold text-primary-dark hover:bg-chip-bg",
        className,
      )}
    >
      <img src={iconPdf} alt="" className="size-4" />
      {t("web.exportPdf")}
    </Link>
  );
}

/* ---------------- Ảnh + thông tin lần quét ---------------- */

/** Ảnh đã lưu của lần quét; không có ảnh thì nói rõ LÝ DO server trả (`storeImageReason`). */
export function ScanImageCard({ result }: { result: ScanResult }) {
  const { t } = useTranslation("scan");
  const reason = result.storeImageReason ?? null;
  return (
    <Panel className="flex flex-col gap-3">
      <p className="inline-flex items-center gap-2 text-body font-bold text-text-primary">
        <img src={iconViewport} alt="" className="size-4" />
        {t("web.imageTitle")}
      </p>
      {result.imageUrl ? (
        <img src={result.imageUrl} alt={t("web.imageAlt")} className="aspect-[4/3] w-full rounded-xl object-cover" />
      ) : (
        <div className="flex min-h-48 w-full flex-col items-center justify-center gap-2 rounded-xl bg-background-alt px-6 py-8 text-center">
          {result.displayHex ? (
            <span
              className="size-14 rounded-full border-[6px] border-surface shadow-sm"
              style={{ backgroundColor: result.displayHex }}
              aria-hidden="true"
            />
          ) : (
            <ImageOff className="size-8 text-text-tertiary" aria-hidden="true" />
          )}
          <p className="text-caption font-semibold text-text-secondary">{t("web.imageNotStored")}</p>
          {reason ? (
            <p className="text-small text-text-tertiary">{t(`imageReason.${reason}`, { defaultValue: "" })}</p>
          ) : null}
        </div>
      )}
    </Panel>
  );
}

function MetaRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-start justify-between gap-3 py-1.5">
      <dt className="text-caption text-text-secondary">{label}</dt>
      <dd className="text-right text-caption font-semibold text-text-primary">{value}</dd>
    </div>
  );
}

export function formatScanTime(iso: string): string {
  return new Date(iso).toLocaleString("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

/** "Thông tin lần quét" — chỉ các field `ScanResultResponse` thật sự có; vắng thì bỏ dòng. */
export function ScanMetaCard({ result, title }: { result: ScanResult; title?: string }) {
  const { t } = useTranslation("scan");
  const confidence = finite(result.confidence);
  const rows: { key: string; label: string; value: string }[] = [
    { key: "capturedAt", label: t("meta.capturedAt"), value: formatScanTime(result.capturedAt) },
  ];
  if (result.captureSource) {
    rows.push({ key: "source", label: t("meta.source"), value: t(`meta.source${result.captureSource}`) });
  }
  if (confidence !== null) {
    rows.push({
      key: "confidence",
      label: t("meta.confidence"),
      value: t("meta.confidenceValue", { value: Math.round(confidence * 100) }),
    });
  }
  if (result.calibrationMethod) {
    rows.push({
      key: "calibration",
      label: t("meta.calibration"),
      value: t(`meta.calibration${result.calibrationMethod}`),
    });
  }
  if (result.chartVersion !== null) {
    rows.push({
      key: "chart",
      label: t("meta.chart"),
      value: result.chartIsPlaceholder
        ? t("meta.chartPlaceholder", { version: result.chartVersion })
        : t("meta.chartValue", { version: result.chartVersion }),
    });
  }
  rows.push({
    key: "credit",
    label: t("meta.credit"),
    value: result.isTrial
      ? t("meta.creditTrial")
      : result.creditCharged
        ? t("meta.creditCharged")
        : t("meta.creditFree"),
  });
  if (result.engineVersion) {
    rows.push({ key: "engine", label: t("meta.engine"), value: result.engineVersion });
  }

  return (
    <Panel className="flex flex-col gap-1">
      <p className="text-body font-bold text-text-primary">{title ?? t("scanDetail.infoTitle")}</p>
      <dl className="flex flex-col divide-y divide-border">
        {rows.map((row) => (
          <MetaRow key={row.key} label={row.label} value={row.value} />
        ))}
      </dl>
    </Panel>
  );
}

/* ---------------- Dải "các lần quét gần đây" ---------------- */

/**
 * Thay cho dải "Xu hướng pH 14 ngày" của Figma: ba lần quét THẬT gần nhất của cùng bé (E2),
 * mỗi ô dẫn sang chi tiết. Biểu đồ xu hướng đầy đủ đã có ở `/cats/:catId/trends`.
 */
export function RecentScansStrip({
  catId,
  catName,
  currentScanId,
  bands,
}: {
  catId: string;
  catName: string;
  currentScanId: string;
  bands: PhBand[];
}) {
  const { t } = useTranslation("scan");
  const { data, isPending, isError } = useRecentCatScans(catId, 4);
  if (isError) return null;
  const others: ScanListItem[] = (data ?? []).filter((item) => item.scanId !== currentScanId).slice(0, 3);

  return (
    <Panel className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-body font-bold text-text-primary">{t("web.recentTitle", { name: catName })}</p>
        <Link
          to={`/cats/${catId}/history`}
          className="inline-flex items-center gap-1 text-caption font-semibold text-primary-dark hover:underline"
        >
          {t("web.recentViewAll")}
          <ArrowRight className="size-3.5" aria-hidden="true" />
        </Link>
      </div>
      {isPending ? (
        <div className="grid gap-3 md:grid-cols-3">
          {[0, 1, 2].map((i) => (
            <span key={i} className="h-16 animate-pulse rounded-xl bg-background-alt" />
          ))}
        </div>
      ) : others.length === 0 ? (
        <p className="text-caption text-text-secondary">{t("web.recentEmpty", { name: catName })}</p>
      ) : (
        <ul className="grid gap-3 md:grid-cols-3">
          {others.map((item) => {
            const band = bands.find((b) => b.code === item.bandCode);
            const ph = finite(item.phValue);
            return (
              <li key={item.scanId}>
                <Link
                  to={`/scans/${item.scanId}`}
                  className="flex items-center gap-3 rounded-xl bg-background-alt px-3 py-2.5 hover:bg-chip-bg"
                >
                  <span
                    className={cn(
                      "size-2.5 shrink-0 rounded-full",
                      band ? phTokenStyle(band.colorToken).solid : "bg-border-strong",
                    )}
                    aria-hidden="true"
                  />
                  <span className="min-w-0 flex-1">
                    <span className="block text-small text-text-secondary">{formatScanTime(item.capturedAt)}</span>
                    <span className="block truncate text-caption font-bold text-text-primary">
                      {ph !== null ? `pH ${ph.toFixed(1)}` : t("web.valueUnavailable")}
                      {band ? ` · ${band.label}` : ""}
                    </span>
                  </span>
                  <ChevronRight className="size-4 shrink-0 text-text-tertiary" aria-hidden="true" />
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </Panel>
  );
}

/* ---------------- Hết credit (Sys-06) ---------------- */

/**
 * Trạng thái hết credit ngay trong luồng Quét (`Web - Sys - 06`). Số dư/lượt thử lấy từ
 * C3 `GET /credits/balance`; page chỉ render khối này khi CẢ HAI đều bằng 0.
 */
export function OutOfCreditPanel({
  availableBalance,
  trialScansRemaining,
  hasPackage,
}: {
  availableBalance: number;
  trialScansRemaining: number;
  hasPackage: boolean;
}) {
  const { t } = useTranslation("scan");
  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-start gap-2.5 rounded-2xl bg-warning-bg p-5 text-warning-text shadow-xs">
        <AlertTriangle className="mt-0.5 size-5 shrink-0" aria-hidden="true" />
        <div className="flex flex-col gap-1">
          <p className="text-body font-bold">{t("outOfCredit.title")}</p>
          <p className="text-caption">{t("outOfCredit.body")}</p>
        </div>
      </div>

      <Panel className="flex flex-col gap-2">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="text-caption text-text-secondary">{t("outOfCredit.balanceLabel")}</p>
            <p className="text-h2 font-bold text-text-primary">
              {t("outOfCredit.balanceValue", { count: availableBalance })}
            </p>
          </div>
          <span className="rounded-full bg-warning-bg px-2.5 py-0.5 text-overline text-warning-text">
            {t("outOfCredit.trialBadge", { count: trialScansRemaining })}
          </span>
        </div>
        {hasPackage ? null : <p className="text-caption text-text-secondary">{t("outOfCredit.noPackage")}</p>}
      </Panel>

      <div className="flex flex-col gap-2">
        <Link
          to="/credits/activate"
          className="inline-flex min-h-12 w-full items-center justify-center rounded-xl bg-primary-dark px-6 text-body font-bold text-white shadow-brand-lg hover:bg-primary"
        >
          {t("outOfCredit.activateCta")}
        </Link>
        <Link
          to="/credits"
          className="inline-flex min-h-11 w-full items-center justify-center rounded-xl border border-border bg-surface px-6 text-body font-semibold text-primary hover:bg-background-alt"
        >
          {t("outOfCredit.plansCta")}
        </Link>
        <p className="text-caption text-text-secondary">{t("outOfCredit.activateHint")}</p>
      </div>

      <div className="flex items-start gap-2 rounded-2xl bg-background-alt p-4">
        <Info className="mt-0.5 size-4 shrink-0 text-info-text" aria-hidden="true" />
        <p className="text-caption text-text-secondary">{t("outOfCredit.note")}</p>
      </div>
    </div>
  );
}
