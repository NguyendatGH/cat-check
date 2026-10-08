import { useRef, useState, type ChangeEvent, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import {
  AlertTriangle,
  Check,
  Clock,
  Droplets,
  HelpCircle,
  Images,
  Lightbulb,
  Mars,
  RotateCcw,
  ScanLine,
  TrendingUp,
  Venus,
} from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { Badge, Button, Card } from "@/shared/ui";
import type { Cat } from "@/entities/cat";
import { CatAvatar } from "@/entities/cat";
import type { PhBand } from "@/entities/ph-bands";
import { PhBadge, phTokenStyle } from "@/entities/ph-bands";
import type { CaptureSource, QualityFlag, ScanListItem, ScanResult, TriggeredFlag } from "@/entities/scan-result";

/**
 * UI cho `features/scan` — component nghiệp vụ ghép `entities/ph-bands` (màu/nhãn pH) +
 * `entities/scan-result` (dữ liệu quét) + `entities/cat` (hiển thị mèo). Không đặt ở entity
 * vì 1 entity không được import entity khác (eslint boundaries).
 *
 * Hình dạng màn hình bám mockup `03` (camera), `04` (kết quả) và `05` (chọn mèo). MỌI nhãn/
 * ngưỡng pH đều tới từ API `GET /reference/ph-bands` — không hard-code ở đây (p6 §6.7.3/4).
 */

/* ---------------- CaptureTrigger ---------------- */

export interface CaptureTriggerProps {
  previewUrl: string | null;
  /** `source` cho biết ảnh tới từ camera hay thư viện — gửi đúng `captureSource` lên E1. */
  onFileSelected: (file: File | null, source?: CaptureSource) => void;
  guideText: string;
  retakeLabel: string;
  cameraLabel: string;
  chooseFromGalleryLabel: string;
  disabled?: boolean;
  className?: string;
}

/** Góc khung quét (mockup `03`) — 4 chữ L trắng ở 4 góc vùng ngắm. */
const VIEWFINDER_CORNERS = [
  "left-0 top-0 rounded-tl-xl border-l-4 border-t-4",
  "right-0 top-0 rounded-tr-xl border-r-4 border-t-4",
  "left-0 bottom-0 rounded-bl-xl border-b-4 border-l-4",
  "right-0 bottom-0 rounded-br-xl border-b-4 border-r-4",
] as const;

/**
 * Bước chụp — Tier 2 (input `capture` HTML chuẩn mở camera gốc thiết bị) thay vì Tier 1 (live
 * camera stream `getUserMedia` + canvas). Quyết định phạm vi (xem `docs/handovers/A6.md`):
 * pipeline server đã tự chấm điểm chất lượng ảnh (E12 `precheckThresholds` + BLOCKING quality
 * flags trả về sau khi nộp), nên không bắt buộc preview trực tiếp.
 *
 * Phần NHÌN bám mockup `03`: khung ngắm tối + 4 góc định vị + pill hướng dẫn + thanh điều
 * khiển (Album ảnh · nút chụp · Mẹo quét). Khung hiển thị ảnh người dùng vừa chọn khi đã có —
 * KHÔNG dựng ảnh mẫu giả khi chưa chụp.
 */
export function CaptureTrigger({
  previewUrl,
  onFileSelected,
  guideText,
  retakeLabel,
  cameraLabel,
  chooseFromGalleryLabel,
  disabled,
  className,
}: CaptureTriggerProps) {
  const { t } = useTranslation("scan");
  const cameraInputRef = useRef<HTMLInputElement>(null);
  const galleryInputRef = useRef<HTMLInputElement>(null);
  const [tipsOpen, setTipsOpen] = useState(false);

  const handleChange = (source: CaptureSource) => (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0] ?? null;
    onFileSelected(file, source);
    e.target.value = "";
  };

  return (
    <div className={cn("flex flex-col gap-3", className)}>
      <div className="relative aspect-[3/5] w-full overflow-hidden rounded-3xl bg-text-primary">
        {previewUrl ? (
          <img src={previewUrl} alt={t("result.imageAlt")} className="absolute inset-0 size-full object-cover" />
        ) : (
          <span className="absolute inset-0 bg-primary-darker" aria-hidden="true" />
        )}

        {/* Vùng ngắm: 4 góc định vị + vạch quét ngang (mockup `03`). */}
        <div className="absolute inset-x-[9%] top-[20%] aspect-square" aria-hidden="true">
          <span className="absolute inset-0 rounded-xl bg-surface/10" />
          {VIEWFINDER_CORNERS.map((corner) => (
            <span key={corner} className={cn("absolute size-9 border-surface", corner)} />
          ))}
          <span className="absolute inset-x-3 bottom-4 h-0.5 rounded-full bg-secondary/80" />
        </div>

        <p className="absolute inset-x-[10%] bottom-[22%] flex items-center justify-center gap-2 rounded-full bg-surface/15 px-3 py-2 text-center text-small font-semibold text-white">
          <ScanLine className="size-4 shrink-0 text-secondary" aria-hidden="true" />
          {t("capture.frameHint")}
        </p>

        {/* Thanh điều khiển: Album ảnh · nút chụp · Mẹo quét. */}
        <div className="absolute inset-x-0 bottom-0 flex items-end justify-around gap-2 bg-gradient-to-t from-primary-darker to-transparent px-4 pb-5 pt-10">
          <button
            type="button"
            disabled={disabled}
            onClick={() => {
              galleryInputRef.current?.click();
            }}
            className="flex w-20 flex-col items-center gap-1.5 text-small text-white disabled:opacity-50"
          >
            <span className="flex size-12 items-center justify-center rounded-full bg-surface/20">
              <Images className="size-5" aria-hidden="true" />
            </span>
            {t("capture.albumCta")}
          </button>

          <button
            type="button"
            disabled={disabled}
            aria-label={cameraLabel}
            onClick={() => {
              cameraInputRef.current?.click();
            }}
            className="flex size-20 shrink-0 items-center justify-center rounded-full bg-surface/30 p-1.5 shadow-brand-xl disabled:opacity-50"
          >
            <span className="flex size-full items-center justify-center rounded-full bg-surface p-1.5">
              <span className="flex size-full items-center justify-center rounded-full bg-primary-dark">
                <ScanLine className="size-7 text-secondary" aria-hidden="true" />
              </span>
            </span>
          </button>

          <button
            type="button"
            aria-expanded={tipsOpen}
            onClick={() => {
              setTipsOpen((v) => !v);
            }}
            className="flex w-20 flex-col items-center gap-1.5 text-small text-white"
          >
            <span className="flex size-12 items-center justify-center rounded-full bg-surface/20">
              <HelpCircle className="size-5" aria-hidden="true" />
            </span>
            {t("capture.tipsCta")}
          </button>
        </div>
      </div>

      {tipsOpen ? (
        <Card padding="sm" className="flex flex-col gap-2">
          <p className="flex items-center gap-2 text-caption font-bold text-text-primary">
            <Lightbulb className="size-4 shrink-0 text-secondary-text-on" aria-hidden="true" />
            {t("web.tipsTitle")}
          </p>
          <ul className="flex flex-col gap-1.5 text-caption text-text-secondary">
            {[guideText, t("web.tip1"), t("web.tip2")].map((tip) => (
              <li key={tip} className="flex gap-2">
                <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-border-strong" aria-hidden="true" />
                {tip}
              </li>
            ))}
          </ul>
        </Card>
      ) : null}

      {previewUrl ? (
        <Button
          type="button"
          variant="tertiary"
          leftIcon={<RotateCcw className="size-4" aria-hidden="true" />}
          onClick={() => {
            onFileSelected(null);
          }}
          disabled={disabled}
        >
          {retakeLabel}
        </Button>
      ) : (
        <Button
          type="button"
          variant="tertiary"
          leftIcon={<Images className="size-4" aria-hidden="true" />}
          onClick={() => {
            galleryInputRef.current?.click();
          }}
          disabled={disabled}
        >
          {chooseFromGalleryLabel}
        </Button>
      )}

      <input
        ref={cameraInputRef}
        type="file"
        accept="image/*"
        capture="environment"
        className="sr-only"
        onChange={handleChange("CAMERA")}
      />
      <input
        ref={galleryInputRef}
        type="file"
        accept="image/*"
        className="sr-only"
        onChange={handleChange("GALLERY")}
      />
    </div>
  );
}

/* ---------------- AnalyzingState ---------------- */

export interface AnalyzingStateProps {
  text: string;
  /** Ảnh vừa gửi (Web/M-03a) — phủ mờ phía sau vòng quay để người dùng biết ảnh nào đang xử lý. */
  previewUrl?: string | null;
  /** Dòng phụ dưới câu chính, VD "Mỗi lần phân tích dùng 1 lượt quét…". */
  note?: string;
  className?: string;
}

/** Trạng thái đang phân tích (Web/M-03a, biến thể spinner không xác định — `fetch` không báo tiến độ tải lên). */
export function AnalyzingState({ text, previewUrl, note, className }: AnalyzingStateProps) {
  return (
    <div
      className={cn("flex flex-col items-center gap-4 px-6 py-16 text-center", className)}
      role="status"
      aria-live="polite"
    >
      {previewUrl ? (
        <span className="relative size-40 overflow-hidden rounded-2xl bg-background-alt">
          <img src={previewUrl} alt="" className="size-full object-cover opacity-60" />
          <span className="absolute inset-0 flex items-center justify-center bg-surface/40">
            <span
              className="size-12 animate-spin rounded-full border-4 border-border border-t-primary"
              aria-hidden="true"
            />
          </span>
        </span>
      ) : (
        <span
          className="size-12 animate-spin rounded-full border-4 border-border border-t-primary"
          aria-hidden="true"
        />
      )}
      <p className="text-body text-text-secondary">{text}</p>
      {note ? <p className="max-w-sm text-caption text-text-tertiary">{note}</p> : null}
    </div>
  );
}

/**
 * BE trả `messageKey` dạng khoá phẳng kiểu Java properties CÓ tiền tố tên module, VD
 * `scan.quality.BLURRY` (`QualityFlag.messageKey()`, quyết định #15) — trong khi i18next dùng
 * `.` làm dấu phân tách NESTED KEY bên trong 1 namespace đã tách theo module. Namespace ở đây
 * đã là `"scan"` nên phải bỏ tiền tố `scan.` trước khi tra, nếu không i18next sẽ tìm nhầm
 * đường dẫn `scan.json → {scan: {quality: {...}}}` (lồng thêm 1 cấp `scan` thừa).
 */
function stripModulePrefix(key: string, moduleName: string): string {
  const prefix = `${moduleName}.`;
  return key.startsWith(prefix) ? key.slice(prefix.length) : key;
}

/* ---------------- QualityFlagList ---------------- */

export interface QualityFlagListProps {
  flags: QualityFlag[];
  className?: string;
}

export function QualityFlagList({ flags, className }: QualityFlagListProps) {
  const { t } = useTranslation("scan");
  if (flags.length === 0) return null;
  return (
    <ul className={cn("flex flex-col gap-2", className)}>
      {flags.map((flag) => (
        <li
          key={flag.code}
          className={cn(
            "flex items-start gap-2 rounded-lg p-2.5 text-caption",
            flag.severity === "BLOCKING" ? "bg-danger-bg text-danger-text" : "bg-warning-bg text-warning-text",
          )}
        >
          <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          <span>{t(stripModulePrefix(flag.messageKey, "scan"), { defaultValue: flag.code })}</span>
        </li>
      ))}
    </ul>
  );
}

/* ---------------- TriggeredFlagList ---------------- */

/**
 * `rule.<CODE>.message` — module `insight` KHÔNG có namespace i18n riêng ở FE (không có route/
 * trang riêng, chỉ hiển thị lồng trong kết quả quét) nên khoá này sống trong `scan.json` (không
 * có tiền tố `insight.` cần bỏ, khác `QualityFlag` ở trên — xem `docs/handovers/A6.md`).
 */
export function TriggeredFlagList({ flags }: { flags: TriggeredFlag[] }) {
  const { t } = useTranslation("scan");
  if (flags.length === 0) return null;
  return (
    <ul className="flex flex-wrap gap-2">
      {flags.map((flag) => (
        <li key={flag.flagId}>
          <Badge tone="neutral">{t(flag.messageKey, { defaultValue: flag.ruleCode })}</Badge>
        </li>
      ))}
    </ul>
  );
}

/* ---------------- Thang pH (PhScale) ---------------- */

const isFiniteNumber = (v: number | null | undefined): v is number => typeof v === "number" && Number.isFinite(v);

/** Phần thang đo nằm NGOÀI dải hữu hạn, để 2 dải mở đầu/cuối vẫn nhìn thấy được. */
const OPEN_END_RATIO = 0.3;

interface ScaleGeometry {
  /** Đầu/cuối thang vẽ (đã chừa chỗ cho dải mở). */
  min: number;
  max: number;
  /** Ngưỡng hữu hạn nhỏ nhất/lớn nhất do API khai báo. */
  innerMin: number;
  innerMax: number;
  ticks: number[];
  segments: { code: string; colorToken: string; left: number; width: number }[];
}

/**
 * Hình học thang pH suy TỪ DỮ LIỆU `bands` (không hằng số ngưỡng trong code). Dải không có
 * cả `phMin` lẫn `phMax` (VD `INCONCLUSIVE`) KHÔNG phải một đoạn trên trục nên bị loại khỏi
 * dải màu — nếu vẽ, nó phủ toàn bộ thang và che mọi dải khác.
 */
function buildScaleGeometry(bands: PhBand[]): ScaleGeometry | null {
  const bounds = [...bands.map((b) => b.phMin), ...bands.map((b) => b.phMax)].filter(isFiniteNumber);
  if (bounds.length === 0) return null;

  const innerMin = Math.min(...bounds);
  const innerMax = Math.max(...bounds);
  const innerSpan = innerMax - innerMin || 1;
  const pad = innerSpan * OPEN_END_RATIO;
  const min = innerMin - pad;
  const max = innerMax + pad;
  const span = max - min;
  const percent = (v: number) => Math.min(100, Math.max(0, ((v - min) / span) * 100));

  const segments = bands
    .slice()
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .filter((band) => isFiniteNumber(band.phMin) || isFiniteNumber(band.phMax))
    .map((band) => {
      const left = percent(band.phMin ?? min);
      const right = percent(band.phMax ?? max);
      return { code: band.code, colorToken: band.colorToken, left, width: Math.max(0, right - left) };
    })
    .filter((segment) => segment.width > 0);

  const ticks = [...new Set([min, ...bounds, max])].sort((a, b) => a - b);

  return { min, max, innerMin, innerMax, ticks, segments };
}

function percentOf(geometry: ScaleGeometry, value: number): number {
  const span = geometry.max - geometry.min || 1;
  return Math.min(100, Math.max(0, ((value - geometry.min) / span) * 100));
}

export interface PhScaleProps {
  bands: PhBand[];
  value: number | null;
  /** Dải đang khớp giá trị đo — dùng để tô màu con trỏ và làm nổi chú giải. */
  activeBand?: PhBand;
  /** Hiện bảng chú giải khoảng pH của từng dải (bố cục desktop, mockup web `03 & 04`). */
  showBandLegend?: boolean;
  className?: string;
}

/**
 * Thang pH đầy đủ theo mockup `04`: nhãn 2 đầu (Axit/Kiềm), dải màu, con trỏ giá trị đo và
 * hàng mốc số. Mọi con số đều lấy từ `bands` (API) — component không biết ngưỡng nào cả.
 */
export function PhScale({ bands, value, activeBand, showBandLegend, className }: PhScaleProps) {
  const { t } = useTranslation("scan");
  const geometry = buildScaleGeometry(bands);

  if (!geometry) {
    return <p className={cn("text-caption text-text-tertiary", className)}>{t("result.scale.unavailable")}</p>;
  }

  const markerPercent = isFiniteNumber(value) ? percentOf(geometry, value) : null;
  const markerStyle = phTokenStyle(activeBand?.colorToken ?? "color-ph-unknown");

  const rangeLabel = (band: PhBand): string => {
    if (isFiniteNumber(band.phMin) && isFiniteNumber(band.phMax)) {
      return t("result.scale.rangeBetween", { min: band.phMin.toFixed(1), max: band.phMax.toFixed(1) });
    }
    if (isFiniteNumber(band.phMax)) return t("result.scale.rangeBelow", { max: band.phMax.toFixed(1) });
    if (isFiniteNumber(band.phMin)) return t("result.scale.rangeAbove", { min: band.phMin.toFixed(1) });
    return "";
  };

  return (
    <div className={cn("flex flex-col gap-2", className)}>
      <div className="flex items-baseline justify-between gap-2 text-small text-text-secondary">
        <span>{t("result.scale.acidSide", { value: geometry.innerMin.toFixed(1) })}</span>
        <span>{t("result.scale.alkalineSide", { value: geometry.innerMax.toFixed(1) })}</span>
      </div>

      {/* Con trỏ giá trị đo — đặt theo % vị trí thật trên thang. */}
      <div className="relative h-7">
        {markerPercent !== null && isFiniteNumber(value) ? (
          <span
            className={cn(
              "absolute -translate-x-1/2 whitespace-nowrap rounded-lg px-2 py-1 text-small font-bold",
              markerStyle.bg,
              markerStyle.text,
            )}
            style={{ left: `${String(markerPercent)}%` }}
          >
            {t("result.scale.marker", { value: value.toFixed(1) })}
          </span>
        ) : null}
      </div>

      <div className="relative h-3 w-full rounded-full bg-background-alt" role="img" aria-hidden="true">
        {geometry.segments.map((segment) => (
          <span
            key={segment.code}
            className={cn(
              "absolute inset-y-0 first:rounded-l-full last:rounded-r-full",
              phTokenStyle(segment.colorToken).solid,
            )}
            style={{ left: `${String(segment.left)}%`, width: `${String(segment.width)}%` }}
          />
        ))}
        {markerPercent !== null ? (
          <span
            className="absolute top-1/2 size-4 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-surface bg-text-primary shadow-sm"
            style={{ left: `${String(markerPercent)}%` }}
          />
        ) : null}
      </div>

      <div className="relative h-5">
        {geometry.ticks.map((tick) => (
          <span
            key={tick}
            className="absolute -translate-x-1/2 text-small text-text-tertiary"
            style={{ left: `${String(percentOf(geometry, tick))}%` }}
          >
            {tick.toFixed(1)}
          </span>
        ))}
      </div>

      {showBandLegend ? (
        <ul className="grid gap-2 sm:grid-cols-3">
          {bands
            .slice()
            .sort((a, b) => a.sortOrder - b.sortOrder)
            .filter((band) => isFiniteNumber(band.phMin) || isFiniteNumber(band.phMax))
            .map((band) => {
              const style = phTokenStyle(band.colorToken);
              const isActive = activeBand?.code === band.code;
              return (
                <li key={band.code} className={cn("rounded-lg px-2 py-1.5", isActive ? style.bg : "bg-transparent")}>
                  <p className={cn("text-small font-bold", style.text)}>{rangeLabel(band)}</p>
                  <p className="text-small leading-snug text-text-secondary">{band.label}</p>
                </li>
              );
            })}
        </ul>
      ) : null}
    </div>
  );
}

/* ---------------- Helpers dùng chung cho màn kết quả ---------------- */

/**
 * `description` của dải là chuỗi MessageFormat từ server, VD `pH {0} nằm trong khoảng tham
 * chiếu 6,3–6,6.` — `{0}` là chỗ điền giá trị pH đo được. Không thay thì người dùng thấy
 * nguyên `{0}` trên màn hình.
 */
export function formatBandDescription(description: string, phValue: number | null): string {
  return description.replace(/\{0\}/g, isFiniteNumber(phValue) ? phValue.toFixed(1) : "—");
}

/** Dải khớp `bandCode` của kết quả — nguồn nhãn/màu duy nhất (không tự phân loại lại). */
export function findResultBand(bands: PhBand[], result: ScanResult): PhBand | undefined {
  return bands.find((band) => band.code === result.bandCode);
}

/* ---------------- ScanResultSummary ---------------- */

export interface ScanResultSummaryProps {
  result: ScanResult;
  bands: PhBand[];
  matchLabel?: string;
  className?: string;
}

/**
 * Khối kết quả chính theo mockup `04`: trạng thái → tiêu đề pH → mô tả dải → thang pH →
 * "Màu hạt ghi nhận" (+ % khớp) → cờ chất lượng. Dùng ở CẢ `/scan/result/:id` và
 * `/scans/:id` để hai màn luôn đọc giống nhau.
 */
export function ScanResultSummary({ result, bands, matchLabel, className }: ScanResultSummaryProps) {
  const { t } = useTranslation("scan");
  const band = findResultBand(bands, result);
  /**
   * BE lược MỌI field `null` khỏi JSON (xem `GET /scans/{id}`: không có `displayHex`/
   * `matchPercent` khi chưa đo được), nên so sánh `!== null` vẫn đúng với `undefined` và
   * hàng "Màu hạt ghi nhận" từng hiện rỗng. Chuẩn hoá về `null` một lần tại đây.
   */
  const displayHex = result.displayHex ?? null;
  const matchPercent = result.matchPercent ?? null;

  return (
    <Card padding="md" className={cn("flex flex-col gap-4", className)}>
      <div className="flex flex-col items-center gap-2 text-center">
        {band ? <PhBadge band={band} /> : null}
        <h2 className="text-h3 font-bold text-text-primary">
          {isFiniteNumber(result.phValue)
            ? t("result.phHeading", { value: result.phValue.toFixed(1) })
            : (band?.label ?? t("result.inconclusive.title"))}
        </h2>
        {band ? (
          <p className="text-caption text-text-secondary">{formatBandDescription(band.description, result.phValue)}</p>
        ) : null}
      </div>

      <div className="rounded-xl bg-background-alt p-4">
        <PhScale bands={bands} value={result.phValue} activeBand={band} />
      </div>

      {displayHex !== null || matchPercent !== null ? (
        <div className="flex items-center gap-3 rounded-lg bg-deco-backdrop p-3">
          {displayHex !== null ? (
            <span
              className="size-7 shrink-0 rounded-full border-[6px] border-surface shadow-sm"
              style={{ backgroundColor: displayHex }}
              aria-hidden="true"
            />
          ) : null}
          <span className="flex min-w-0 flex-1 flex-col">
            <span className="text-caption font-bold text-text-primary">{t("result.vetNoteTitle")}</span>
            {displayHex !== null ? (
              <span className="truncate text-caption text-text-secondary">
                {t("result.grainColorName", { hex: displayHex })}
              </span>
            ) : null}
          </span>
          {matchPercent !== null ? (
            <span className="shrink-0 rounded-full bg-surface px-3 py-1 text-caption font-bold text-primary-dark">
              {matchLabel ?? t("result.matchLabel")} {matchPercent}%
            </span>
          ) : null}
        </div>
      ) : null}

      <QualityFlagList flags={result.qualityFlags} />
      <TriggeredFlagList flags={result.triggeredFlags} />
    </Card>
  );
}

/* ---------------- ScanAdviceCard ---------------- */

function AdviceRow({ icon, title, body }: { icon: ReactNode; title: string; body: string }) {
  return (
    <li className="flex gap-2.5 rounded-xl bg-background-alt p-3">
      <span className="mt-0.5 shrink-0" aria-hidden="true">
        {icon}
      </span>
      <span className="flex min-w-0 flex-col gap-0.5">
        <span className="text-caption font-bold text-text-primary">{title}</span>
        <span className="text-caption leading-snug text-text-secondary">{body}</span>
      </span>
    </li>
  );
}

/**
 * "Bạn nên làm gì?" (mockup `04`). Chỉ gồm: nhắc dùng app + chăm sóc thường ngày, và — khi
 * luật xu hướng thật sự bắn cờ — chính câu chữ của cờ đó. KHÔNG suy diễn bệnh, không bịa số
 * ngày/chuỗi kết quả (quyết định #8: chỉ đo pH).
 */
export function ScanAdviceCard({ result, className }: { result: ScanResult; className?: string }) {
  const { t } = useTranslation("scan");
  const watchMessages = result.triggeredFlags.map((flag) => t(flag.messageKey, { defaultValue: flag.ruleCode }));

  return (
    <Card padding="md" className={cn("flex flex-col gap-3", className)}>
      <p className="flex items-center gap-2 text-body font-bold text-text-primary">
        <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-secondary">
          <Lightbulb className="size-4 text-secondary-text-on" aria-hidden="true" />
        </span>
        {t("result.advice.title")}
      </p>
      <ul className="flex flex-col gap-2">
        <AdviceRow
          icon={<TrendingUp className="size-4 text-primary" aria-hidden="true" />}
          title={t("result.advice.routineTitle")}
          body={t("result.advice.routineBody")}
        />
        <AdviceRow
          icon={<Droplets className="size-4 text-primary" aria-hidden="true" />}
          title={t("result.advice.waterTitle")}
          body={t("result.advice.waterBody")}
        />
        {watchMessages.length > 0 ? (
          <AdviceRow
            icon={<AlertTriangle className="size-4 text-warning-text" aria-hidden="true" />}
            title={t("result.advice.watchTitle")}
            body={watchMessages.join(" ")}
          />
        ) : null}
      </ul>
    </Card>
  );
}

/* ---------------- InconclusiveNotice ---------------- */

export interface InconclusiveNoticeProps {
  title: string;
  description: string;
  retryHint?: string;
  retryLabel: string;
  onRetry: () => void;
  /** Dải `INCONCLUSIVE` của API — vẽ pill nhãn đúng như Web/M-03b (nhãn từ server, không viết cứng). */
  band?: PhBand;
  /** Cờ chất lượng server trả về; BLOCKING xếp lên đầu. */
  flags?: QualityFlag[];
  /** Dòng cuối, VD "Số dư giữ nguyên: 5 credit." — chỉ truyền khi có số thật. */
  footnote?: string;
  className?: string;
}

/** Màn "Chưa đủ dữ liệu" (Web/M-03b). Không trừ credit — câu chữ nói rõ điều đó. */
export function InconclusiveNotice({
  title,
  description,
  retryHint,
  retryLabel,
  onRetry,
  band,
  flags = [],
  footnote,
  className,
}: InconclusiveNoticeProps) {
  const sortedFlags = flags
    .slice()
    .sort((a, b) => Number(b.severity === "BLOCKING") - Number(a.severity === "BLOCKING"));
  return (
    <div className={cn("flex flex-col items-center gap-3 px-4 py-10 text-center", className)}>
      <HelpCircle className="size-12 text-text-tertiary" aria-hidden="true" />
      {band ? <PhBadge band={band} /> : null}
      <div className="flex flex-col gap-1">
        <p className="text-h3 font-bold text-text-primary">{title}</p>
        <p className="max-w-md text-caption text-text-secondary">{description}</p>
      </div>
      {sortedFlags.length > 0 ? <QualityFlagList flags={sortedFlags} className="w-full max-w-md text-left" /> : null}
      {retryHint ? <p className="max-w-md text-caption text-text-tertiary">{retryHint}</p> : null}
      <Button type="button" variant="primary" className="w-full max-w-xs" onClick={onRetry}>
        {retryLabel}
      </Button>
      {footnote ? <p className="text-caption text-text-tertiary">{footnote}</p> : null}
    </div>
  );
}

/* ---------------- Chọn mèo (mockup `05`) ---------------- */

/** "Hôm nay 08:42" / "Hôm qua 21:40" / "28/09 07:05" — chân thẻ chọn mèo (mockup `05`). */
function relativeScanTime(
  date: Date,
  locale: string,
  t: (key: string, options?: Record<string, unknown>) => string,
): string {
  const time = new Intl.DateTimeFormat(locale, { hour: "2-digit", minute: "2-digit" }).format(date);
  const startOfToday = new Date();
  startOfToday.setHours(0, 0, 0, 0);
  const dayMs = 24 * 60 * 60 * 1000;
  if (date.getTime() >= startOfToday.getTime()) return t("selectCat.lastScanToday", { time });
  if (date.getTime() >= startOfToday.getTime() - dayMs) return t("selectCat.lastScanYesterday", { time });
  const day = new Intl.DateTimeFormat(locale, { day: "2-digit", month: "2-digit" }).format(date);
  return t("selectCat.lastScanAt", { date: `${day} ${time}` });
}

/** Vòng tròn chọn ở mép phải mỗi thẻ — tick đậm khi đang chọn (mockup `05`). */
function SelectionDot({ selected }: { selected: boolean }) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        "flex size-7 shrink-0 items-center justify-center rounded-full",
        selected ? "bg-primary-dark text-white" : "bg-chip-bg text-chip-bg",
      )}
    >
      <Check className="size-4" />
    </span>
  );
}

export interface SelectCatOptionProps {
  cat: Cat;
  selected: boolean;
  onSelect: () => void;
  /** Dải pH (API) — để đổi `lastClassification` thành nhãn/màu thật, không tự đặt tên. */
  bands: PhBand[];
  /**
   * Lượt quét gần nhất của bé (E2 `GET /scans?catId=…&limit=1`) — `GET /cats` KHÔNG trả
   * `lastScanAt`, nên thiếu prop này thẻ từng ghi "Chưa có lượt quét nào" cho cả bé đã quét
   * hàng chục lần. `undefined` = chưa biết (đang tải/lỗi) ⇒ không khẳng định gì; `null` = chưa
   * từng quét.
   */
  latestScan?: ScanListItem | null;
  className?: string;
}

/**
 * Thẻ chọn mèo đầy đủ theo mockup `05`: ảnh + tên + chip giới tính/tuổi, dòng giống • cân
 * nặng, và chân thẻ hiển thị kết quả quét gần nhất. Mọi trường đều có thể vắng trong dữ liệu
 * thật (`Cat` cho phép `null`) — thiếu thì bỏ dòng đó, KHÔNG điền giá trị mẫu.
 */
export function SelectCatOption({ cat, selected, onSelect, bands, latestScan, className }: SelectCatOptionProps) {
  const { t, i18n } = useTranslation("scan");

  const sexLabel =
    cat.sex === "FEMALE"
      ? t("selectCat.sexFemale")
      : cat.sex === "MALE"
        ? t("selectCat.sexMale")
        : t("selectCat.sexUnknown");
  const SexIcon = cat.sex === "MALE" ? Mars : Venus;

  const ageLabel = !isFiniteNumber(cat.ageMonths)
    ? null
    : cat.ageMonths >= 12
      ? t("selectCat.ageYears", { count: Math.floor(cat.ageMonths / 12) })
      : t("selectCat.ageMonthsShort", { count: cat.ageMonths });

  const metaLine = [cat.breedName, isFiniteNumber(cat.weightKg) ? t("selectCat.weight", { value: cat.weightKg }) : null]
    .filter((part): part is string => Boolean(part))
    .join(" • ");

  const lastScanAt = latestScan?.capturedAt ?? cat.lastScanAt ?? null;
  const lastBand = bands.find((band) => band.code === (latestScan?.bandCode ?? cat.lastClassification));
  const lastPh = latestScan && isFiniteNumber(latestScan.phValue) ? latestScan.phValue : null;
  const lastScanLabel = lastScanAt
    ? relativeScanTime(new Date(lastScanAt), i18n.language, t)
    : latestScan === null
      ? t("selectCat.noScanYet")
      : null;

  return (
    <button
      type="button"
      onClick={onSelect}
      aria-pressed={selected}
      className={cn(
        "flex w-full flex-col gap-2.5 rounded-2xl border-2 p-3 text-left transition-colors",
        selected ? "border-primary-dark bg-background-alt" : "border-transparent bg-surface hover:border-border",
        className,
      )}
    >
      <span className="flex items-center gap-3">
        {/* 56px theo Figma `05` (rect 56×56 rx28) — lớn hơn preset `md` của CatAvatar. */}
        <CatAvatar src={cat.avatarUrl} name={cat.name} size="md" className="size-14" />
        <span className="flex min-w-0 flex-1 flex-col gap-0.5">
          <span className="flex flex-wrap items-center gap-2">
            <span className="truncate text-body font-bold text-text-primary">{cat.name}</span>
            <span
              className={cn(
                "inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-small font-semibold",
                selected ? "bg-secondary text-secondary-text-on" : "bg-chip-bg text-text-secondary",
              )}
            >
              <SexIcon className="size-3" aria-hidden="true" />
              {[sexLabel, ageLabel].filter(Boolean).join(" • ")}
            </span>
          </span>
          {metaLine ? <span className="truncate text-caption text-text-secondary">{metaLine}</span> : null}
        </span>
        <SelectionDot selected={selected} />
      </span>

      {lastScanLabel || lastBand ? (
        <span className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 rounded-xl bg-background-alt px-3 py-2">
          {lastBand ? (
            <span className="inline-flex min-w-0 items-center gap-1.5 text-small text-text-secondary">
              <span
                className={cn("size-2.5 shrink-0 rounded-full", phTokenStyle(lastBand.colorToken).solid)}
                aria-hidden="true"
              />
              <span className="truncate">
                {lastPh !== null
                  ? t("selectCat.lastResult", { label: lastBand.label, value: lastPh.toFixed(1) })
                  : lastBand.label}
              </span>
            </span>
          ) : null}
          {lastScanLabel ? (
            <span className="inline-flex shrink-0 items-center gap-1.5 text-small text-text-secondary">
              <Clock className="size-3.5 shrink-0" aria-hidden="true" />
              {lastScanLabel}
            </span>
          ) : null}
        </span>
      ) : null}
    </button>
  );
}

/* ---------------- SharedTrayOption ---------------- */

export interface SharedTrayOptionProps {
  title: string;
  description: string;
  selected: boolean;
  onSelect: () => void;
  className?: string;
}

/** Lựa chọn đặc biệt "Chưa rõ / Dùng chung khay" trong danh sách chọn mèo (mockup `05`). */
export function SharedTrayOption({ title, description, selected, onSelect, className }: SharedTrayOptionProps) {
  return (
    <button
      type="button"
      onClick={onSelect}
      aria-pressed={selected}
      className={cn(
        "flex min-h-[var(--touch-target-min)] w-full items-center gap-3 rounded-2xl border-2 p-3 text-left transition-colors",
        selected ? "border-primary-dark bg-background-alt" : "border-transparent bg-surface hover:border-border",
        className,
      )}
    >
      <span className="flex size-14 shrink-0 items-center justify-center rounded-full bg-secondary-light text-secondary-text-on">
        <HelpCircle className="size-6" aria-hidden="true" />
      </span>
      <span className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span className="truncate text-body font-bold text-text-primary">{title}</span>
        <span className="text-caption leading-snug text-text-secondary">{description}</span>
      </span>
      <SelectionDot selected={selected} />
    </button>
  );
}

/* ---------------- MultiCatTip ---------------- */

/** Thẻ mẹo "N+1 khay cát" cuối màn chọn mèo (mockup `05`). */
export function MultiCatTip({ className }: { className?: string }) {
  const { t } = useTranslation("scan");
  return (
    <div className={cn("flex items-start gap-3 rounded-2xl bg-surface p-3", className)}>
      <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-secondary">
        <Lightbulb className="size-4 text-secondary-text-on" aria-hidden="true" />
      </span>
      <span className="flex min-w-0 flex-col gap-0.5">
        <span className="text-caption font-bold text-text-primary">{t("selectCat.multiCatTipTitle")}</span>
        <span className="text-caption leading-snug text-text-secondary">{t("selectCat.multiCatTipBody")}</span>
      </span>
    </div>
  );
}
