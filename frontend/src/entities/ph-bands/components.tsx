import {
  AlertTriangle,
  ArrowDownCircle,
  ArrowUpCircle,
  CheckCircle,
  HelpCircle,
  Info,
  type LucideIcon,
} from "lucide-react";
import { useTranslation } from "react-i18next";
import { cn } from "@/shared/lib/cn";
import { phTokenStyle } from "./colorToken";
import type { PhBand } from "./model";

/**
 * UI hiển thị pH dùng chung (p10 §5.2 `PhGaugeBar`/`ResultGauge` rút gọn, p9 §9.2.4
 * `entities/ph-bands/ui`). Mọi ngưỡng/nhãn/màu nhận qua props từ dữ liệu API
 * (`entities/ph-bands` `usePhBands()`), KHÔNG hard-code (p6 §6.7.3/§6.7.4).
 */

const ICON_MAP: Record<string, LucideIcon> = {
  "check-circle": CheckCircle,
  "arrow-down-circle": ArrowDownCircle,
  "arrow-up-circle": ArrowUpCircle,
  "alert-triangle": AlertTriangle,
  "help-circle": HelpCircle,
};

function resolveIcon(iconName: string): LucideIcon {
  return ICON_MAP[iconName] ?? Info;
}

export interface PhBadgeProps {
  band: PhBand;
  className?: string;
}

/**
 * `StatusPill` áp cho pH — BẮT BUỘC kèm icon + chữ, không chỉ màu (p10 mục 10, a11y không
 * dựa màu đơn thuần). `band.label` luôn tới từ API, component không có nhãn mặc định.
 */
export function PhBadge({ band, className }: PhBadgeProps) {
  const style = phTokenStyle(band.colorToken);
  const Icon = resolveIcon(band.iconName);
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-caption font-semibold",
        style.bg,
        style.text,
        className,
      )}
    >
      <Icon className="size-4 shrink-0" aria-hidden="true" />
      {band.label}
    </span>
  );
}

export interface PhGaugeBarProps {
  /** Toàn bộ dải, đã sắp `sortOrder` (kết quả `usePhBands()`). */
  bands: PhBand[];
  /** Giá trị pH đo được; `null`/`undefined` = chưa có điểm để đánh dấu. */
  value?: number | null;
  /** Nếu bỏ trống thì tự suy ra từ min/max của `bands` (p6 §6.7.3) — không hard-code 5.5–8.5. */
  domain?: { min: number; max: number };
  className?: string;
}

/**
 * Thanh gradient hiển thị vị trí pH trên thang các dải — nền cho `ResultGauge` (p10 §5.2).
 * Không tự suy diễn dải hiển thị cứng: domain = [min, max] của chính dữ liệu `bands`.
 */
export function PhGaugeBar({ bands, value, domain, className }: PhGaugeBarProps) {
  // Dải mở đầu/cuối KHÔNG có `phMin`/`phMax`, và server bỏ hẳn key đó khỏi JSON thay vì
  // trả `null` — nên phải lọc bằng `typeof === "number"`. Lọc bằng `!== null` thì
  // `undefined` lọt qua và `Math.min(..., undefined)` ra `NaN`, hỏng cả hai nhãn đầu thang.
  const isNumber = (v: number | null | undefined): v is number => typeof v === "number" && Number.isFinite(v);
  const lowerBounds = bands.map((b) => b.phMin).filter(isNumber);
  const upperBounds = bands.map((b) => b.phMax).filter(isNumber);
  const min = domain?.min ?? (lowerBounds.length ? Math.min(...lowerBounds) : 0);
  const max = domain?.max ?? (upperBounds.length ? Math.max(...upperBounds) : 14);
  const span = max - min || 1;

  const clampPercent = (ph: number) => Math.min(100, Math.max(0, ((ph - min) / span) * 100));

  return (
    <div className={cn("flex flex-col gap-1", className)}>
      <div className="relative h-3 w-full overflow-hidden rounded-full" role="img" aria-hidden="true">
        <div className="flex h-full w-full">
          {bands
            .slice()
            .sort((a, b) => a.sortOrder - b.sortOrder)
            .map((band) => {
              const segMin = band.phMin ?? min;
              const segMax = band.phMax ?? max;
              const widthPercent = Math.max(0, clampPercent(segMax) - clampPercent(segMin));
              if (widthPercent <= 0) return null;
              return (
                <span
                  key={band.code}
                  style={{ width: `${String(widthPercent)}%` }}
                  className={phTokenStyle(band.colorToken).solid}
                />
              );
            })}
        </div>
        {value !== null && value !== undefined ? (
          <span
            className="absolute top-1/2 size-3.5 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-surface bg-text-primary shadow-sm"
            style={{ left: `${String(clampPercent(value))}%` }}
          />
        ) : null}
      </div>
      <div className="flex justify-between text-small text-text-tertiary">
        <span>{min.toFixed(1)}</span>
        <span>{max.toFixed(1)}</span>
      </div>
    </div>
  );
}

export interface PhRangeLegendProps {
  bands: PhBand[];
  className?: string;
}

/** Chú giải các dải màu — dùng cạnh `PhGaugeBar`/chart xu hướng. */
export function PhRangeLegend({ bands, className }: PhRangeLegendProps) {
  return (
    <ul className={cn("flex flex-wrap gap-x-4 gap-y-1.5", className)}>
      {bands
        .slice()
        .sort((a, b) => a.sortOrder - b.sortOrder)
        .map((band) => (
          <li key={band.code} className="flex items-center gap-1.5 text-caption text-text-secondary">
            <span
              aria-hidden="true"
              className={cn("size-2.5 shrink-0 rounded-full", phTokenStyle(band.colorToken).solid)}
            />
            {band.label}
          </li>
        ))}
    </ul>
  );
}

/**
 * Ghi chú miễn trừ y tế ngắn (biến thể `D-SHORT`, p15 §15.7.3) đi kèm mọi hiển thị pH.
 *
 * Component `DisclaimerBanner` chuẩn hoá (p10 §5.2) đọc namespace i18n `legal` — namespace đó
 * do A2 sở hữu và tại thời điểm này CHƯA có key `disclaimer.*` (M0). Để không vi phạm ranh giới
 * file (`legal.json` không thuộc A3/A5) và vẫn đáp ứng yêu cầu "mọi bề mặt pH phải có disclaimer"
 * (ORCHESTRATOR §A3/A5), câu chữ D-SHORT (p15 §15.7.3, nguyên văn) được đặt cục bộ trong
 * `scan.json` → `phBands.disclaimer.*` (phần thuộc A5). Khi A2/W3 hoàn thiện `legal.json` +
 * `DisclaimerBanner` dùng chung, thay props/nội dung ở ĐÚNG MỘT chỗ này.
 */
export function PhDisclaimerNote({ className }: { className?: string }) {
  const { t } = useTranslation("scan");
  return (
    <p className={cn("text-small text-text-tertiary", className)} role="note">
      {t("phBands.disclaimer.short")}
    </p>
  );
}
