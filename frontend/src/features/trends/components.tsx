import { format } from "date-fns";
import {
  Area,
  CartesianGrid,
  ComposedChart,
  Line,
  ReferenceArea,
  ReferenceDot,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { cn } from "@/shared/lib/cn";
import type { PhBand } from "@/entities/ph-bands";
import { phTokenStyle } from "@/entities/ph-bands";
import type { DistributionBucket, TrendRange } from "./types";

/**
 * `phTokenStyle` trả class Tailwind (dùng cho `className`), nhưng recharts (SVG) cần GIÁ TRỊ màu
 * thật cho `stroke`/`fill`. Ánh xạ riêng sang chính biến CSS `--color-ph-*` mà `phTokenStyle`
 * cũng trỏ vào (không hard-code hex, không lệch nguồn với `entities/ph-bands`).
 */
const PH_CSS_VAR: Record<string, string> = {
  "color-ph-normal": "var(--color-ph-normal)",
  "color-ph-mild": "var(--color-ph-mild)",
  "color-ph-abnormal": "var(--color-ph-abnormal)",
  "color-ph-unknown": "var(--color-ph-unknown)",
};

function phCssVar(colorToken: string | undefined): string {
  return PH_CSS_VAR[colorToken ?? ""] ?? PH_CSS_VAR["color-ph-unknown"];
}

/**
 * UI cho `features/trends` — recharts CHỈ được import ở đây (eslint override cho
 * `features/trends`/`features/export`, xem `eslint.config.js` mục 5). Ghép `entities/ph-bands`
 * (vùng mục tiêu/màu) + chuỗi điểm pH do page truyền vào (E2 ở mobile, D13 ở desktop).
 */

/* ---------------- Tiện ích dải pH ---------------- */

/**
 * BẪY ĐÃ GẶP THẬT: dải mở đầu/cuối (`LOW`, `HIGH`, `INCONCLUSIVE`) KHÔNG có `phMin`/`phMax`
 * và server BỎ HẲN key đó khỏi JSON thay vì trả `null`. Lọc bằng `!== null` thì `undefined`
 * lọt qua, `Math.min(…, undefined)` ra `NaN` và toàn bộ toạ độ biểu đồ thành `NaN` (đường cong
 * biến mất không báo lỗi — đúng triệu chứng của bản desktop trước đây). Luôn lọc bằng guard này.
 */
function isFiniteNumber(value: number | null | undefined): value is number {
  return typeof value === "number" && Number.isFinite(value);
}

/** Toàn bộ biên hữu hạn của các dải pH, đã sắp tăng dần — dùng làm vạch chia trục Y. */
export function phBandBoundaries(bands: PhBand[]): number[] {
  const values = [...bands.map((b) => b.phMin), ...bands.map((b) => b.phMax)].filter(isFiniteNumber);
  return [...new Set(values)].sort((a, b) => a - b);
}

/** Lấy tối đa `max` mốc trên trục X, luôn giữ mốc đầu và mốc cuối. */
function pickTicks(values: number[], max: number): number[] {
  if (values.length <= max) return values;
  const step = (values.length - 1) / (max - 1);
  const picked = new Set<number>();
  for (let i = 0; i < max; i += 1) {
    picked.add(values[Math.round(i * step)]);
  }
  picked.add(values[values.length - 1]);
  return [...picked].sort((a, b) => a - b);
}

/* ---------------- RangeSegmentedControl ---------------- */

export interface RangeSegmentedControlProps {
  value: TrendRange;
  onChange: (value: TrendRange) => void;
  labels: Record<TrendRange, string>;
  className?: string;
}

/** Bộ chọn khoảng thời gian — 3 tab chia đều bề ngang, tab đang chọn nền `primary-dark`. */
export function RangeSegmentedControl({ value, onChange, labels, className }: RangeSegmentedControlProps) {
  const options: TrendRange[] = ["7D", "30D", "90D"];
  return (
    <div className={cn("flex w-full items-center gap-1 rounded-xl bg-background-alt p-1", className)} role="tablist">
      {options.map((option) => (
        <button
          key={option}
          type="button"
          role="tab"
          aria-selected={value === option}
          onClick={() => {
            onChange(option);
          }}
          className={cn(
            "min-h-[var(--touch-target-min)] flex-1 rounded-lg px-2 py-1.5 text-caption font-semibold transition-colors",
            value === option ? "bg-primary-dark text-white shadow-xs" : "text-text-secondary",
          )}
        >
          {labels[option]}
        </button>
      ))}
    </div>
  );
}

/* ---------------- PhTrendChart ---------------- */

/** Hình dạng tối thiểu để vẽ — mobile map từ `TrendPoint` (E2), desktop từ `CatTrendPoint` (D13). */
export interface PhSeriesPoint {
  /** ISO-8601. */
  date: string;
  phValue: number;
}

export interface PhTrendChartProps {
  points: PhSeriesPoint[];
  /** Nguồn DUY NHẤT của ngưỡng/màu — `GET /reference/ph-bands`. */
  bands: PhBand[];
  /** Dải được tô nền làm mốc tham chiếu (thường là dải `severity === "NORMAL"`). */
  referenceBand?: PhBand | undefined;
  emptyLabel: string;
  /** Pattern date-fns cho nhãn trục X (đặt ở i18n để không nhúng chữ Việt vào .tsx). */
  dateFormat: string;
  /** Pattern date-fns cho nhãn trong tooltip. */
  tooltipDateFormat: string;
  /** Nhãn thay cho mốc cuối cùng trên trục X. */
  todayLabel: string;
  /** Tên series hiện trong tooltip. */
  phLabel: string;
  height?: number;
  className?: string;
}

const AREA_FILL_ID = "ph-trend-area-fill";

/**
 * Đường cong pH ĐẦY ĐỦ TRỤC theo mockup `08`: vạch chia trục Y đặt đúng các biên dải pH thật,
 * nhãn ngày trên trục X (mốc cuối là "hôm nay"), vùng tham chiếu tô nền + 2 đường đứt ở biên,
 * lưới ngang, mảng gradient dưới đường, điểm dữ liệu rõ và điểm gần nhất được làm nổi.
 *
 * Mọi con số pH đều suy từ `bands` (API) và chính dữ liệu — KHÔNG hard-code ngưỡng nào.
 */
export function PhTrendChart({
  points,
  bands,
  referenceBand,
  emptyLabel,
  dateFormat,
  tooltipDateFormat,
  todayLabel,
  phLabel,
  height = 224,
  className,
}: PhTrendChartProps) {
  if (points.length === 0) {
    return (
      <div
        className={cn(
          "flex items-center justify-center rounded-xl bg-background-alt/60 px-4 text-center text-caption text-text-tertiary",
          className,
        )}
        style={{ height }}
      >
        {emptyLabel}
      </div>
    );
  }

  const data = points.map((p) => ({ x: new Date(p.date).getTime(), ph: p.phValue })).sort((a, b) => a.x - b.x);
  const last = data[data.length - 1];
  const phValues = data.map((d) => d.ph);
  const boundaries = phBandBoundaries(bands);

  const lo = Math.min(...phValues, ...boundaries);
  const hi = Math.max(...phValues, ...boundaries);
  const pad = Math.max((hi - lo) * 0.12, 0.1);
  const domainMin = Math.floor((lo - pad) * 10) / 10;
  const domainMax = Math.ceil((hi + pad) * 10) / 10;

  const yTicks = [...new Set([domainMin, ...boundaries.filter((b) => b > domainMin && b < domainMax), domainMax])];
  const xTicks = pickTicks(
    data.map((d) => d.x),
    5,
  );
  // Một điểm duy nhất ⇒ miền thời gian rộng 0, recharts không vẽ được: nới ra ±1 ngày.
  const oneDayMs = 24 * 60 * 60 * 1000;
  const xDomain: [number, number] =
    data.length === 1 ? [data[0].x - oneDayMs, data[0].x + oneDayMs] : [data[0].x, last.x];

  const refMin = isFiniteNumber(referenceBand?.phMin) ? referenceBand.phMin : null;
  const refMax = isFiniteNumber(referenceBand?.phMax) ? referenceBand.phMax : null;
  const refColor = phCssVar(referenceBand?.colorToken);

  return (
    <div className={cn("w-full", className)} style={{ height }} aria-hidden="true">
      <ResponsiveContainer width="100%" height="100%">
        <ComposedChart data={data} margin={{ top: 10, right: 10, bottom: 0, left: 0 }}>
          <defs>
            <linearGradient id={AREA_FILL_ID} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="var(--color-primary)" stopOpacity={0.22} />
              <stop offset="100%" stopColor="var(--color-primary)" stopOpacity={0} />
            </linearGradient>
          </defs>

          <CartesianGrid vertical={false} strokeDasharray="4 4" stroke="var(--color-border)" />

          {refMin !== null && refMax !== null ? (
            <ReferenceArea y1={refMin} y2={refMax} fill={refColor} fillOpacity={0.18} stroke="none" />
          ) : null}
          {refMin !== null ? (
            <ReferenceLine y={refMin} stroke={refColor} strokeDasharray="5 4" strokeOpacity={0.9} />
          ) : null}
          {refMax !== null ? (
            <ReferenceLine y={refMax} stroke={refColor} strokeDasharray="5 4" strokeOpacity={0.9} />
          ) : null}

          <XAxis
            dataKey="x"
            type="number"
            scale="time"
            domain={xDomain}
            ticks={xTicks}
            tickFormatter={(value: number) => (value === last.x ? todayLabel : format(new Date(value), dateFormat))}
            tickLine={false}
            axisLine={{ stroke: "var(--color-border)" }}
            stroke="var(--color-text-tertiary)"
            tick={{ fontSize: 11 }}
            tickMargin={8}
            minTickGap={4}
          />
          <YAxis
            type="number"
            domain={[domainMin, domainMax]}
            ticks={yTicks}
            tickFormatter={(value: number) => value.toFixed(1)}
            tickLine={false}
            axisLine={false}
            stroke="var(--color-text-tertiary)"
            tick={{ fontSize: 11 }}
            width={34}
          />
          <Tooltip
            labelFormatter={(value) => format(new Date(value as number), tooltipDateFormat)}
            formatter={(value) => [Number(value).toFixed(2), phLabel]}
            contentStyle={{
              borderRadius: 12,
              border: "1px solid var(--color-border)",
              fontSize: 12,
            }}
          />

          <Area
            type="monotone"
            dataKey="ph"
            stroke="none"
            fill={`url(#${AREA_FILL_ID})`}
            isAnimationActive={false}
            tooltipType="none"
          />
          <Line
            type="monotone"
            dataKey="ph"
            stroke="var(--color-primary)"
            strokeWidth={2.5}
            dot={{ r: 4, fill: "var(--color-surface)", stroke: "var(--color-primary)", strokeWidth: 2 }}
            activeDot={{ r: 6 }}
            isAnimationActive={false}
          />
          <ReferenceDot
            x={last.x}
            y={last.ph}
            r={6}
            fill="var(--color-primary-dark)"
            stroke="var(--color-surface)"
            strokeWidth={3}
          />
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  );
}

/* ---------------- DistributionBar ---------------- */

export interface DistributionBarProps {
  buckets: DistributionBucket[];
  bands: PhBand[];
  /** VD "30 lần". */
  countLabel: (bucket: DistributionBucket) => string;
  /** VD "88%". */
  percentLabel: (bucket: DistributionBucket) => string;
  /** VD "(pH 6.3 – 6.6)" — trả `null` cho dải mở không có đủ hai biên. */
  rangeLabel?: (band: PhBand) => string | null;
  className?: string;
}

/**
 * `band.description` của một số dải là TEMPLATE của backend (chứa `{0}` để điền giá trị pH của
 * MỘT lần quét). Ở đây là thống kê nhiều lần quét nên không có giá trị để điền — bỏ qua mô tả
 * dạng template thay vì in ra "{0}".
 */
function plainDescription(description: string | undefined): string | null {
  if (!description || description.includes("{0}")) return null;
  return description;
}

export function DistributionBar({
  buckets,
  bands,
  countLabel,
  percentLabel,
  rangeLabel,
  className,
}: DistributionBarProps) {
  if (buckets.length === 0) return null;
  const sorted = [...buckets].sort((a, b) => b.count - a.count);
  return (
    <div className={cn("flex flex-col gap-3", className)}>
      <div className="flex h-3 w-full overflow-hidden rounded-full bg-background-alt" role="img" aria-hidden="true">
        {sorted.map((bucket) => {
          const band = bands.find((b) => b.code === bucket.classification);
          const style = phTokenStyle(band?.colorToken ?? "color-ph-unknown");
          return (
            <span key={bucket.classification} style={{ width: `${String(bucket.percent)}%` }} className={style.solid} />
          );
        })}
      </div>
      <ul className="flex flex-col divide-y divide-border">
        {sorted.map((bucket) => {
          const band = bands.find((b) => b.code === bucket.classification);
          const style = phTokenStyle(band?.colorToken ?? "color-ph-unknown");
          const range = band && rangeLabel ? rangeLabel(band) : null;
          const description = plainDescription(band?.description);
          return (
            <li key={bucket.classification} className="flex items-start gap-2.5 py-2.5">
              <span aria-hidden="true" className={cn("mt-1.5 size-2.5 shrink-0 rounded-full", style.solid)} />
              <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                <span className="text-caption font-bold text-text-primary">
                  {band?.label ?? bucket.classification}
                  {range ? <span className="font-semibold text-text-secondary"> {range}</span> : null}
                </span>
                {description ? (
                  <span className="text-small leading-snug text-text-secondary">{description}</span>
                ) : null}
              </span>
              <span className="flex shrink-0 flex-col items-end">
                <span className={cn("text-body font-bold", style.text)}>{percentLabel(bucket)}</span>
                <span className="text-small text-text-tertiary">{countLabel(bucket)}</span>
              </span>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
