import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { findBandForPh, PhBadge, PhGaugeBar, type PhBand } from "@/entities/ph-bands";

import iconBanner from "@/shared/assets/icons/web-scan/imgContainer.svg";
import iconInfo1 from "@/shared/assets/icons/web-scan/imgContainer1.svg";
import iconInfo2 from "@/shared/assets/icons/web-scan/imgContainer2.svg";
import iconCatLabel from "@/shared/assets/icons/web-scan/imgContainer3.svg";
import iconViewport from "@/shared/assets/icons/web-scan/imgContainer4.svg";
import iconLiveCamera from "@/shared/assets/icons/web-scan/imgContainer5.svg";
import iconUpload from "@/shared/assets/icons/web-scan/imgContainer6.svg";
import iconSpectralTag from "@/shared/assets/icons/web-scan/imgContainer7.svg";
import iconCalibration from "@/shared/assets/icons/web-scan/imgContainer8.svg";
import iconAnalyze from "@/shared/assets/icons/web-scan/imgContainer9.svg";
import iconTipsTitle from "@/shared/assets/icons/web-scan/imgContainer11.svg";
import iconTip1 from "@/shared/assets/icons/web-scan/imgMargin.svg";
import iconTip2 from "@/shared/assets/icons/web-scan/imgMargin1.svg";
import iconResultBadge from "@/shared/assets/icons/web-scan/imgContainer12.svg";
import iconPdf from "@/shared/assets/icons/web-scan/imgContainer16.svg";
import viewportPhoto from "@/shared/assets/images/web-scan/viewport-litter.png";

/**
 * Bố cục DESKTOP (>= lg) cho màn Quét & Kết quả — dựng từ design context thật của Figma
 * (file 26mOVF2zdu4cI1EPz2Syxw, node `16:6333`, khung 1280×1736; lưới 2 cột `16:6368`,
 * trái `16:6369` 5/12, phải `16:6508` 7/12). Asset tải về `shared/assets/icons/web-scan` và
 * `shared/assets/images/web-scan`.
 *
 * Mobile KHÔNG dùng file này — các page chỉ render nó từ breakpoint `lg` trở lên.
 *
 * ĐÃ BỎ HẲN (W1-E) toàn bộ hằng `WEB_DEMO_*`:
 *  - **Ma trận bệnh lý** (`WEB_DEMO_PATHOLOGY`: FLUTD, sỏi struvite, oxalate, "vi máu /
 *    hemoglobin") — KHÔNG thay bằng API: đây là chẩn đoán phân biệt và phát hiện máu, trái
 *    quyết định #6 ("không chẩn đoán") và #8 ("chỉ pH, không phát hiện máu").
 *  - **Trích dẫn bác sĩ** (`WEB_DEMO_VET_QUOTE`) — lời khuyên y tế bịa, không có nguồn.
 *  - **Khối kết quả** (`WEB_DEMO_RESULT`) — nay suy từ `phValue` thật + dải pH của
 *    `GET /reference/ph-bands`; nhãn/mô tả lấy từ `band.label`/`band.description`, không
 *    hard-code ngưỡng "6.5 – 7.2" như bản thiết kế.
 *  - **Hồ sơ mèo** (`WEB_DEMO_CAT`) — nay là mèo THẬT đang được chọn, do page truyền vào.
 *  - **Dải xu hướng đáy trang** (`WEB_DEMO_PREVIOUS` + ảnh sparkline tĩnh) — bỏ cả component
 *    `WebTrendStrip`: không có endpoint nào trả "2 lần quét trước" ở màn này, và biểu đồ xu
 *    hướng thật đã có ở `/cats/:catId/trends` (D13).
 */

function Card({ children, className = "" }: { children: ReactNode; className?: string }) {
  return <div className={`rounded-2xl bg-surface p-4 shadow-xs ${className}`}>{children}</div>;
}

/** Banner đầu trang: badge AI LAB + tiêu đề + 2 pill thông tin (Figma `16:6337`…). */
export function WebScanHeader() {
  const { t } = useTranslation("scan");
  return (
    <Card className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
      <div className="flex items-start gap-3">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-chip-bg">
          <img src={iconBanner} alt="" className="size-5" />
        </span>
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <span className="rounded-full bg-chip-bg px-2 py-0.5 text-overline font-bold tracking-[0.4px] text-primary-dark">
              {t("web.labBadge")}
            </span>
            <span className="inline-flex items-center gap-1 text-overline font-semibold text-success-text">
              <span className="size-1.5 rounded-full bg-success" aria-hidden="true" />
              {t("web.spectrometerReady")}
            </span>
          </div>
          <h1 className="pt-1 text-[20px] font-bold leading-7 tracking-[-0.5px] text-text-primary">
            {t("web.pageTitle")}
          </h1>
        </div>
      </div>
      <div className="flex flex-col gap-2 sm:flex-row xl:shrink-0">
        {[
          { icon: iconInfo1, label: t("web.lastCheckLabel"), value: t("web.lastCheckValue") },
          { icon: iconInfo2, label: t("web.certLabel"), value: t("web.certValue") },
        ].map((pill) => (
          <div key={pill.label} className="flex items-center gap-2 rounded-xl bg-background-alt px-3 py-2">
            <img src={pill.icon} alt="" className="size-4 shrink-0" />
            <div className="leading-tight">
              <p className="text-[11px] tracking-[0.4px] text-text-secondary">{pill.label}</p>
              <p className="text-caption font-bold text-text-primary">{pill.value}</p>
            </div>
          </div>
        ))}
      </div>
    </Card>
  );
}

interface WebCapturePanelProps {
  /** Ảnh người dùng vừa chọn; chưa có thì hiện ảnh mẫu của thiết kế. */
  previewUrl?: string | null;
  onPickFile?: (file: File) => void;
  onSubmit?: () => void;
  submitDisabled?: boolean;
  /** Mèo đang được chọn (`GET /cats/{id}`); bỏ trống = khay dùng chung, không hiện thẻ hồ sơ. */
  catName?: string | null;
  catMeta?: string | null;
  catAvatarUrl?: string | null;
}

/** Cột TRÁI desktop: hồ sơ mèo, khung ngắm AI, cân chỉnh sáng, CTA, mẹo chụp (`16:6369`). */
export function WebCapturePanel({
  previewUrl,
  onPickFile,
  onSubmit,
  submitDisabled,
  catName,
  catMeta,
  catAvatarUrl,
}: WebCapturePanelProps) {
  const { t } = useTranslation("scan");
  return (
    <div className="flex flex-col gap-4">
      {/* Mèo THẬT đang được chọn ở `/scan/select-cat`. Khay dùng chung (`SHARED_UNKNOWN`)
          không có hồ sơ nào nên page không truyền `catName` và thẻ này không render. */}
      {catName ? (
        <Card className="flex flex-col gap-3">
          <p className="inline-flex items-center gap-1.5 text-overline font-bold tracking-[0.4px] text-text-secondary">
            <img src={iconCatLabel} alt="" className="size-3.5" />
            {t("web.catSectionLabel")}
          </p>
          <div className="flex items-center gap-3 rounded-xl bg-background-alt p-2">
            {catAvatarUrl ? (
              <img src={catAvatarUrl} alt="" className="size-12 shrink-0 rounded-lg object-cover" />
            ) : (
              <span className="flex size-12 shrink-0 items-center justify-center rounded-lg bg-chip-bg text-h3 font-bold text-primary">
                {catName.charAt(0).toUpperCase()}
              </span>
            )}
            <div className="min-w-0 flex-1">
              <p className="text-body font-bold text-text-primary">{catName}</p>
              {catMeta ? <p className="truncate text-caption text-text-secondary">{catMeta}</p> : null}
            </div>
          </div>
        </Card>
      ) : null}

      <Card className="flex flex-col gap-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p className="inline-flex items-center gap-2 text-body font-bold text-text-primary">
            <img src={iconViewport} alt="" className="size-4" />
            {t("web.viewportTitle")}
          </p>
          <div className="flex gap-2">
            <span className="inline-flex items-center gap-1.5 rounded-lg bg-deco-backdrop px-3 py-1.5 text-caption font-semibold text-primary-dark">
              <img src={iconLiveCamera} alt="" className="size-3.5" />
              {t("web.liveCamera")}
            </span>
            <label className="inline-flex cursor-pointer items-center gap-1.5 rounded-lg bg-deco-backdrop px-3 py-1.5 text-caption font-semibold text-primary-dark">
              <img src={iconUpload} alt="" className="size-3.5" />
              {t("web.uploadNew")}
              <input
                type="file"
                accept="image/jpeg,image/png,image/webp"
                className="sr-only"
                onChange={(event) => {
                  const file = event.target.files?.[0];
                  if (file && onPickFile) onPickFile(file);
                }}
              />
            </label>
          </div>
        </div>

        <div className="relative overflow-hidden rounded-xl bg-info">
          <img src={previewUrl ?? viewportPhoto} alt="" className="aspect-[4/3] w-full object-cover" />
          <div className="absolute inset-0 bg-primary-dark/5" aria-hidden="true" />
          <span
            className="absolute left-[18%] top-[34%] flex items-center gap-2 rounded-xl bg-surface/90 px-2 py-1.5 shadow-sm"
            aria-hidden="true"
          >
            <span className="size-2 shrink-0 rounded-full bg-success-strong" />
            <span className="leading-tight">
              <span className="block text-[10px] font-bold text-text-primary">{t("web.marker1Title")}</span>
              <span className="block text-[10px] text-text-secondary">{t("web.marker1Detail")}</span>
            </span>
          </span>
          <span
            className="absolute bottom-[26%] right-[14%] flex items-center gap-2 rounded-xl bg-surface/90 px-2 py-1.5 shadow-sm"
            aria-hidden="true"
          >
            <span className="size-2 shrink-0 rounded-full bg-success-strong" />
            <span className="leading-tight">
              <span className="block text-[10px] font-bold text-text-primary">{t("web.marker2Title")}</span>
              <span className="block text-[10px] text-text-secondary">{t("web.marker2Detail")}</span>
            </span>
          </span>
          <span className="absolute inset-x-3 bottom-3 inline-flex items-center gap-2 rounded-lg bg-text-primary/85 px-3 py-1.5 text-[11px] font-semibold text-white">
            <img src={iconSpectralTag} alt="" className="size-3.5" />
            {t("web.detectionTag")}
          </span>
        </div>
      </Card>

      <Card className="flex flex-col gap-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p className="inline-flex items-center gap-2 text-caption font-bold text-text-primary">
            <img src={iconCalibration} alt="" className="size-4" />
            {t("web.calibrationTitle")}
          </p>
          <span className="text-overline font-semibold text-primary-dark">{t("web.autoWhiteBalance")}</span>
        </div>
        <dl className="grid grid-cols-3 gap-3">
          {[
            { k: t("web.brightness"), v: t("web.brightnessValue") },
            { k: t("web.contrast"), v: t("web.contrastValue") },
            { k: t("web.colorTemp"), v: t("web.colorTempValue") },
          ].map((row) => (
            <div key={row.k} className="rounded-lg bg-background-alt p-2">
              <dt className="text-[11px] text-text-secondary">{row.k}</dt>
              <dd className="text-caption font-bold text-text-primary">{row.v}</dd>
              <div className="mt-1.5 h-1 rounded-full bg-info" aria-hidden="true" />
            </div>
          ))}
        </dl>
      </Card>

      <div className="flex flex-col gap-2">
        <button
          type="button"
          onClick={onSubmit}
          disabled={submitDisabled}
          className="inline-flex w-full items-center justify-center gap-2 rounded-xl bg-primary px-6 py-3.5 text-body font-bold text-white shadow-sm disabled:cursor-not-allowed disabled:opacity-50"
        >
          <img src={iconAnalyze} alt="" className="size-4" />
          {t("web.analyzeCta")}
        </button>
        <div className="h-1 overflow-hidden rounded-full bg-chip-bg" aria-hidden="true">
          <div className="h-full w-full rounded-full bg-gradient-to-r from-primary to-success-strong" />
        </div>
        <p className="text-[11px] text-text-secondary">
          {t("web.analyzeProgress")} <span className="font-semibold text-primary-dark">{t("web.algorithm")}</span>
        </p>
      </div>

      <Card className="flex flex-col gap-3">
        <p className="inline-flex items-center gap-2 text-caption font-bold text-text-primary">
          <img src={iconTipsTitle} alt="" className="size-4" />
          {t("web.tipsTitle")}
        </p>
        <div className="grid gap-3 sm:grid-cols-2">
          {[
            { icon: iconTip1, text: t("web.tip1") },
            { icon: iconTip2, text: t("web.tip2") },
          ].map((tip) => (
            <div key={tip.text} className="flex gap-2 rounded-xl bg-background-alt p-2">
              <img src={tip.icon} alt="" className="mt-0.5 size-4 shrink-0" />
              <p className="text-[11px] leading-snug text-text-secondary">{tip.text}</p>
            </div>
          ))}
        </div>
      </Card>
    </div>
  );
}

interface WebResultPanelProps {
  bands: PhBand[];
  /** pH THẬT của lần quét; `null`/bỏ trống = chưa có kết quả kết luận được. */
  phValue?: number | null;
  /** `classification` server đã gán cho lần quét — ưu tiên hơn việc suy lại từ con số pH. */
  bandCode?: string | null;
}

/** `band.description` của server là TEMPLATE chứa `{0}` để điền chính giá trị pH đó. */
function bandSentence(band: PhBand | undefined, phValue: number | null): string | null {
  if (!band?.description) return null;
  if (!band.description.includes("{0}")) return band.description;
  return phValue === null ? null : band.description.replace("{0}", phValue.toFixed(1));
}

function finiteBound(value: number | null | undefined): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

/**
 * Cột PHẢI desktop: kết quả, thang pH, hành động (`16:6508`).
 *
 * Nhãn phân loại LUÔN là `band.label` do `GET /reference/ph-bands` trả (đi từ `label_key`
 * của server) — không có chuỗi "Khỏe mạnh"/"BÌNH THƯỜNG" nào viết cứng ở client, và ngưỡng
 * tham chiếu in ra cũng lấy từ dải `severity === "NORMAL"` chứ không phải số trong Figma.
 */
export function WebResultPanel({ bands, phValue = null, bandCode = null }: WebResultPanelProps) {
  const { t } = useTranslation("scan");
  const band =
    (bandCode !== null ? bands.find((b) => b.code === bandCode) : undefined) ?? findBandForPh(bands, phValue);
  const referenceBand = bands.find((b) => b.severity === "NORMAL");
  const refMin = finiteBound(referenceBand?.phMin);
  const refMax = finiteBound(referenceBand?.phMax);
  const sentence = bandSentence(band, phValue);
  const hasResult = phValue !== null || band !== undefined;

  return (
    <div className="flex flex-col gap-4">
      <div className="rounded-2xl bg-surface p-4 shadow-xs">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="flex items-start gap-3">
            <span className="flex size-10 shrink-0 items-center justify-center rounded-2xl bg-chip-bg">
              <img src={iconResultBadge} alt="" className="size-5" />
            </span>
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-overline font-bold tracking-[0.4px] text-text-secondary">
                  {t("web.overallLabel")}
                </span>
                {band ? <PhBadge band={band} /> : null}
              </div>
              <p className="pt-1 text-[22px] font-bold leading-7 text-text-primary">
                {hasResult ? (band?.label ?? t("web.resultUnknown")) : t("web.awaitingTitle")}
              </p>
              {sentence ? <p className="max-w-[340px] pt-1 text-caption text-text-secondary">{sentence}</p> : null}
              {!hasResult ? (
                <p className="max-w-[340px] pt-1 text-caption text-text-secondary">{t("web.awaitingBody")}</p>
              ) : null}
            </div>
          </div>
          <div className="text-right">
            <p className="text-[22px] font-bold leading-7 text-text-primary">
              {phValue !== null ? t("web.phReading", { value: phValue.toFixed(1) }) : t("web.valueUnavailable")}
            </p>
            {refMin !== null && refMax !== null ? (
              <p className="text-[11px] leading-tight text-text-secondary">
                {t("web.referenceRange", { range: `${refMin.toFixed(1)} – ${refMax.toFixed(1)}` })}
              </p>
            ) : null}
          </div>
        </div>
      </div>

      <Card className="flex flex-col gap-3">
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div>
            <p className="text-body font-bold text-text-primary">{t("web.spectrumTitle")}</p>
            <p className="text-caption text-text-secondary">{t("web.spectrumSubtitle")}</p>
          </div>
          <span className="rounded-full bg-deco-backdrop px-2.5 py-1 text-overline font-semibold text-primary-dark">
            {t("web.spectrumScaleBadge")}
          </span>
        </div>
        {/* Dải màu + nhãn ngưỡng lấy TỪ API (entities/ph-bands) — không hard-code ngưỡng pH. */}
        <PhGaugeBar bands={bands} value={phValue} />
      </Card>

      {/* Chỉ còn hành động CÓ THẬT. "Lưu vào lịch sử" bỏ đi vì `POST /scans` đã lưu ngay khi
          phân tích xong (không có endpoint lưu riêng), "Hỏi bác sĩ" bỏ vì không có tính năng
          nào phía sau nó. */}
      <div className="flex flex-wrap gap-2">
        <Link
          to="/export"
          className="inline-flex flex-1 items-center justify-center gap-2 rounded-xl bg-deco-backdrop px-4 py-3 text-caption font-semibold text-primary-dark"
        >
          <img src={iconPdf} alt="" className="size-4" />
          {t("web.exportPdf")}
        </Link>
      </div>
    </div>
  );
}
