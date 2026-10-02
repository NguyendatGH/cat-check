import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { PhGaugeBar, type PhBand } from "@/entities/ph-bands";

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
import iconVetAdvice from "@/shared/assets/icons/web-scan/imgContainer14.svg";
import iconSave from "@/shared/assets/icons/web-scan/imgContainer15.svg";
import iconPdf from "@/shared/assets/icons/web-scan/imgContainer16.svg";
import iconAskVet from "@/shared/assets/icons/web-scan/imgContainer17.svg";
import iconTrendStrip from "@/shared/assets/icons/web-scan/imgContainer18.svg";
import sparkline from "@/shared/assets/icons/web-scan/imgSvg.svg";
import iconMetricTile from "@/shared/assets/icons/web-scan/imgContainer19.svg";
import viewportPhoto from "@/shared/assets/images/web-scan/viewport-litter.png";
import catPhoto from "@/shared/assets/images/web-scan/cat-luna.png";

/**
 * Bố cục DESKTOP (>= lg) cho màn Quét & Kết quả — dựng từ design context thật của Figma
 * (file 26mOVF2zdu4cI1EPz2Syxw, node `16:6333`, khung 1280×1736; lưới 2 cột `16:6368`,
 * trái `16:6369` 5/12, phải `16:6508` 7/12). Asset tải về `shared/assets/icons/web-scan` và
 * `shared/assets/images/web-scan`.
 *
 * Mobile KHÔNG dùng file này — các page chỉ render nó từ breakpoint `lg` trở lên.
 *
 * ⚠️ DỮ LIỆU GIẢ: backend hiện chưa trả được kết quả có nghĩa (bảng `color_chart` mới chỉ có
 * 1 dòng placeholder ⇒ mọi lần quét đều `INCONCLUSIVE` + `scanId: null`; `/cats/{id}/trends`
 * trả 501). Các hằng `WEB_DEMO_*` bên dưới CHỈ để dựng hình đúng Figma, không phải dữ liệu
 * thật của người dùng — thay bằng dữ liệu API ngay khi backend sẵn sàng.
 */

interface DemoPathologyRow {
  key: string;
  title: string;
  verdict: string;
  body: string;
  /** Bề rộng thanh mức độ, 0–100. */
  level: number;
}

const WEB_DEMO_PATHOLOGY: DemoPathologyRow[] = [
  {
    key: "flutd",
    title: "Viêm đường tiết niệu (FLUTD)",
    verdict: "Thấp (0.2%)",
    body: "Không phát hiện triệu chứng tiểu buốt, pH đồng nhất trên toàn bộ hạt.",
    level: 8,
  },
  {
    key: "struvite",
    title: "Sỏi khoáng Struvite (MgNH₄PO₄)",
    verdict: "Rất Thấp",
    body: "Nước tiểu không bị kiềm lắng đọng tinh thể.",
    level: 6,
  },
  {
    key: "oxalate",
    title: "Sỏi Canxi Oxalate",
    verdict: "Bình thường",
    body: "Nồng độ toan hóa thấp, không có dấu hiệu acid niệu kéo dài.",
    level: 24,
  },
  {
    key: "blood",
    title: "Vi máu / Hemoglobin (Blood Trace)",
    verdict: "Âm tính (-)",
    body: "Cảm biến màu không phát hiện sắc tố đỏ/nâu đậm do hồng cầu vỡ.",
    level: 4,
  },
];

const WEB_DEMO_VET_QUOTE =
  "“Tình trạng nước tiểu của bé Luna hiện tại rất ổn định và lý tưởng. Tiếp tục duy trì lượng nước uống khoảng 150ml/ngày (thông qua đài phun nước hoặc bổ sung pate mềm) và duy trì hạt ăn cân bằng dinh dưỡng hiện tại. Nên quét định kỳ 3 ngày/lần.”";

const WEB_DEMO_RESULT = {
  headline: "KẾT QUẢ: BÌNH THƯỜNG (pH 6.8)",
  statusChip: "Khỏe mạnh",
  reading: "6.8 pH",
  referenceNote: "Ngưỡng chuẩn (6.5 - 7.2)",
  body: "Nồng độ ion H+ trong nước tiểu ở mức cân bằng lý tưởng cho mèo nuôi nhà.",
  /** Chỉ để đặt kim trên PhGaugeBar khi chưa có kết quả thật. */
  gaugeValue: 6.8,
};

const WEB_DEMO_CAT = {
  name: "Bé Luna",
  chip: "Cái • Triệt sản",
  meta: "Mèo Anh lông ngắn • 2.5 tuổi • Cân nặng: 4.2 kg",
};

const WEB_DEMO_PREVIOUS = [
  { key: "p1", label: "Lần trước (12/10)", value: "pH 6.7 • Bình thường" },
  { key: "p2", label: "Lần trước (09/10)", value: "pH 6.9 • Bình thường" },
];

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
}

/** Cột TRÁI desktop: hồ sơ mèo, khung ngắm AI, cân chỉnh sáng, CTA, mẹo chụp (`16:6369`). */
export function WebCapturePanel({ previewUrl, onPickFile, onSubmit, submitDisabled }: WebCapturePanelProps) {
  const { t } = useTranslation("scan");
  return (
    <div className="flex flex-col gap-4">
      <Card className="flex flex-col gap-3">
        <p className="inline-flex items-center gap-1.5 text-overline font-bold tracking-[0.4px] text-text-secondary">
          <img src={iconCatLabel} alt="" className="size-3.5" />
          {t("web.catSectionLabel")}
        </p>
        <div className="flex items-center gap-3 rounded-xl bg-background-alt p-2">
          <img src={catPhoto} alt="" className="size-12 shrink-0 rounded-lg object-cover" />
          <div className="min-w-0 flex-1">
            <p className="flex flex-wrap items-center gap-2">
              <span className="text-body font-bold text-text-primary">{WEB_DEMO_CAT.name}</span>
              <span className="rounded-full bg-secondary-light px-2 py-0.5 text-overline font-semibold text-secondary-text-on">
                {WEB_DEMO_CAT.chip}
              </span>
            </p>
            <p className="truncate text-caption text-text-secondary">{WEB_DEMO_CAT.meta}</p>
          </div>
        </div>
      </Card>

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
  /** pH thật nếu có; chưa có thì dùng số mẫu để dựng hình. */
  phValue?: number | null;
}

/** Cột PHẢI desktop: kết quả, thang pH, ma trận bệnh lý, lời khuyên, hành động (`16:6508`). */
export function WebResultPanel({ bands, phValue }: WebResultPanelProps) {
  const { t } = useTranslation("scan");
  const gauge = phValue ?? WEB_DEMO_RESULT.gaugeValue;

  return (
    <div className="flex flex-col gap-4">
      <div className="rounded-2xl bg-success-bg p-4 shadow-xs">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="flex items-start gap-3">
            <span className="flex size-10 shrink-0 items-center justify-center rounded-2xl bg-success-text">
              <img src={iconResultBadge} alt="" className="size-5" />
            </span>
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-overline font-bold tracking-[0.4px] text-success-text">
                  {t("web.overallLabel")}
                </span>
                <span className="rounded-full bg-success-bg px-2 py-0.5 text-overline font-semibold text-success-text">
                  {WEB_DEMO_RESULT.statusChip}
                </span>
              </div>
              <p className="pt-1 text-[22px] font-bold leading-7 text-success-text">{WEB_DEMO_RESULT.headline}</p>
              <p className="max-w-[340px] pt-1 text-caption text-text-secondary">{WEB_DEMO_RESULT.body}</p>
            </div>
          </div>
          <div className="text-right">
            <p className="text-[22px] font-bold leading-7 text-text-primary">{WEB_DEMO_RESULT.reading}</p>
            <p className="text-[11px] leading-tight text-text-secondary">{WEB_DEMO_RESULT.referenceNote}</p>
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
        <PhGaugeBar bands={bands} value={gauge} />
      </Card>

      <Card className="flex flex-col gap-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p className="text-body font-bold text-text-primary">{t("web.pathologyTitle")}</p>
          <span className="text-[11px] text-text-secondary">{t("web.pathologyBadge")}</span>
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          {WEB_DEMO_PATHOLOGY.map((row) => (
            <div key={row.key} className="flex flex-col gap-1.5 rounded-xl bg-background-alt p-3">
              <div className="flex items-start justify-between gap-2">
                <p className="text-caption font-bold leading-snug text-text-primary">{row.title}</p>
                <span className="shrink-0 rounded-full bg-success-bg px-2 py-0.5 text-[10px] font-semibold text-success-text">
                  {row.verdict}
                </span>
              </div>
              <p className="text-[11px] leading-snug text-text-secondary">{row.body}</p>
              <div className="h-1 overflow-hidden rounded-full bg-info" aria-hidden="true">
                <div className="h-full rounded-full bg-success" style={{ width: `${String(row.level)}%` }} />
              </div>
            </div>
          ))}
        </div>
      </Card>

      <Card className="flex gap-3">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-secondary">
          <img src={iconVetAdvice} alt="" className="size-5" />
        </span>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-start justify-between gap-2">
            <p className="text-caption font-bold text-text-primary">{t("web.vetAdviceTitle")}</p>
            <span className="text-[11px] text-text-secondary">{t("web.vetAdviceBadge")}</span>
          </div>
          <p className="pt-1 text-caption italic leading-relaxed text-text-secondary">{WEB_DEMO_VET_QUOTE}</p>
        </div>
      </Card>

      <div className="flex flex-wrap gap-2">
        <button
          type="button"
          className="inline-flex flex-1 items-center justify-center gap-2 rounded-xl bg-primary px-4 py-3 text-caption font-bold text-white shadow-sm"
        >
          <img src={iconSave} alt="" className="size-4" />
          {t("web.saveToHistory")}
        </button>
        <button
          type="button"
          className="inline-flex items-center justify-center gap-2 rounded-xl bg-deco-backdrop px-4 py-3 text-caption font-semibold text-primary-dark"
        >
          <img src={iconPdf} alt="" className="size-4" />
          {t("web.exportPdf")}
        </button>
        <button
          type="button"
          className="inline-flex items-center justify-center gap-2 rounded-xl bg-secondary px-4 py-3 text-caption font-semibold text-secondary-text-on"
        >
          <img src={iconAskVet} alt="" className="size-4" />
          {t("web.askVet")}
        </button>
      </div>
    </div>
  );
}

/** Dải xu hướng 14 ngày ở đáy trang (`16:6654`). Sparkline là asset SVG của thiết kế. */
export function WebTrendStrip() {
  const { t } = useTranslation("scan");
  return (
    <Card className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="inline-flex items-center gap-2 text-caption font-bold text-text-primary">
          <img src={iconTrendStrip} alt="" className="size-4" />
          {t("web.trendStripTitle")}
        </p>
        <span className="rounded-full bg-success-bg px-2.5 py-1 text-overline font-semibold text-success-text">
          {t("web.trendStripBadge")}
        </span>
      </div>
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
        <img src={sparkline} alt="" className="h-12 w-full flex-1 object-contain" />
        <div className="flex gap-2">
          {WEB_DEMO_PREVIOUS.map((row) => (
            <div key={row.key} className="flex items-center gap-2 rounded-xl bg-background-alt px-3 py-2">
              <img src={iconMetricTile} alt="" className="size-4 shrink-0" />
              <div className="leading-tight">
                <p className="text-[11px] text-text-secondary">{row.label}</p>
                <p className="text-caption font-bold text-text-primary">{row.value}</p>
              </div>
            </div>
          ))}
        </div>
      </div>
    </Card>
  );
}
