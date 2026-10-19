import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { cn } from "@/shared/lib/cn";
import type { PhBand } from "@/entities/ph-bands";
import { PhBadge } from "@/entities/ph-bands";

export interface PhGaugeCircularProps {
  bands: PhBand[];
  value: number | null;
  activeBand?: PhBand;
  markerHex?: string | null;
  showBandLegend?: boolean;
  className?: string;
  size?: "xs" | "sm" | "md" | "lg";
  variant?: "full" | "dial";
}

// SVG angles: 0° = right, 90° = bottom. The arc sweeps 270° clockwise from bottom-left
// to bottom-right, leaving the 90° gap centred at the bottom.
const ARC_START = 135;
const ARC_SWEEP = 270;
const CX = 50;
const CY = 50;
const R = 40;
const DISPLAY_MIN = 4;
const DISPLAY_MAX = 9;

// Universal-indicator colours per whole pH unit (see --ph-hue-* in index.css).
const HUE_STOPS: readonly [number, string][] = [3, 4, 5, 6, 7, 8, 9, 10, 11].map((ph) => [ph, `--ph-hue-${String(ph)}`]);

const isNum = (v: number | null | undefined): v is number => typeof v === "number" && Number.isFinite(v);

interface Scale {
  min: number;
  max: number;
  segments: { band: PhBand; from: number; to: number }[];
  ticks: number[];
}

function buildScale(bands: PhBand[]): Scale | null {
  const sorted = [...bands].filter((b) => isNum(b.phMin) || isNum(b.phMax)).sort((a, b) => a.sortOrder - b.sortOrder);
  const bounds = sorted.flatMap((b) => [b.phMin, b.phMax]).filter(isNum);
  if (sorted.length === 0 || bounds.length === 0) return null;

  const min = Math.min(DISPLAY_MIN, Math.floor(Math.min(...bounds) - 0.5));
  const max = Math.max(DISPLAY_MAX, Math.ceil(Math.max(...bounds) + 0.5));

  const segments = sorted.map((band) => ({
    band,
    from: isNum(band.phMin) ? band.phMin : min,
    to: isNum(band.phMax) ? band.phMax : max,
  }));
  const ticks = [...new Set(bounds)].sort((a, b) => a - b);
  return { min, max, segments, ticks };
}

function phToAngle(scale: Scale, ph: number): number {
  const t = Math.max(0, Math.min(1, (ph - scale.min) / (scale.max - scale.min || 1)));
  return ARC_START + t * ARC_SWEEP;
}

export function phSpectrumColor(ph: number): string {
  const first = HUE_STOPS[0];
  const last = HUE_STOPS[HUE_STOPS.length - 1];
  if (ph <= first[0]) return `var(${first[1]})`;
  if (ph >= last[0]) return `var(${last[1]})`;
  const upper = HUE_STOPS.findIndex(([stop]) => stop >= ph);
  const [loPh, loVar] = HUE_STOPS[upper - 1];
  const [hiPh, hiVar] = HUE_STOPS[upper];
  const weight = Math.round(((hiPh - ph) / (hiPh - loPh)) * 100);
  return `color-mix(in oklab, var(${loVar}) ${String(weight)}%, var(${hiVar}))`;
}

function polar(angle: number, r: number): [number, number] {
  const rad = (angle * Math.PI) / 180;
  return [CX + r * Math.cos(rad), CY + r * Math.sin(rad)];
}

function useAnimatedAngle(target: number): number {
  const [angle, setAngle] = useState(ARC_START);
  const current = useRef(ARC_START);

  useEffect(() => {
    const from = current.current;
    if (from === target) return;
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      current.current = target;
      setAngle(target);
      return;
    }
    const start = performance.now();
    const duration = 900;
    let frame = 0;
    const tick = (now: number) => {
      const p = Math.min(1, (now - start) / duration);
      const eased = 1 - Math.pow(1 - p, 3);
      current.current = from + (target - from) * eased;
      setAngle(current.current);
      if (p < 1) frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => {
      cancelAnimationFrame(frame);
    };
  }, [target]);

  return angle;
}

const SIZE_CLASS = {
  xs: "size-32",
  sm: "size-44",
  md: "size-64",
  lg: "size-80",
} as const;

const RING_STEPS = 90;

function buildRing(scale: Scale) {
  const step = ARC_SWEEP / RING_STEPS;
  return Array.from({ length: RING_STEPS }, (_, i) => {
    const from = ARC_START + i * step;
    const to = Math.min(ARC_START + ARC_SWEEP, from + step + 0.4);
    const [x1, y1] = polar(from, R);
    const [x2, y2] = polar(to, R);
    const ph = scale.min + ((i + 0.5) / RING_STEPS) * (scale.max - scale.min);
    return {
      key: i,
      d: `M ${x1.toFixed(2)} ${y1.toFixed(2)} A ${String(R)} ${String(R)} 0 0 1 ${x2.toFixed(2)} ${y2.toFixed(2)}`,
      color: phSpectrumColor(ph),
    };
  });
}

export function PhGaugeCircular({
  bands,
  value,
  activeBand,
  markerHex,
  showBandLegend,
  className,
  size = "md",
  variant = "full",
}: PhGaugeCircularProps) {
  const { t } = useTranslation("scan");
  const scale = useMemo(() => buildScale(bands), [bands]);
  const ringSegments = useMemo(() => (scale ? buildRing(scale) : []), [scale]);
  const target = scale && isNum(value) ? phToAngle(scale, value) : ARC_START;
  const needleAngle = useAnimatedAngle(target);

  if (!scale) {
    return <p className={cn("text-caption text-text-tertiary", className)}>{t("result.scale.unavailable")}</p>;
  }

  const measuredColor = markerHex && /^#[0-9a-f]{6}$/i.test(markerHex) ? markerHex : null;
  const [tipX, tipY] = polar(needleAngle, R - 7);
  const [tailX, tailY] = polar(needleAngle + 180, 6);
  const integers = Array.from({ length: scale.max - scale.min + 1 }, (_, i) => scale.min + i);
  const scaleLabels = size === "xs" ? integers.filter((ph) => ph === scale.min || ph === 7 || ph === scale.max) : integers;

  const dial = (
    <div className={cn("relative shrink-0", SIZE_CLASS[size])}>
      <svg viewBox="0 0 100 100" className="relative size-full overflow-visible" role="img" aria-hidden="true">
        {ringSegments.map((seg) => (
          <path key={seg.key} d={seg.d} stroke={seg.color} strokeWidth="9" fill="none" />
        ))}
        {scale.ticks.map((tick) => {
          const angle = phToAngle(scale, tick);
          const [x1, y1] = polar(angle, R - 4.5);
          const [x2, y2] = polar(angle, R + 4.5);
          return <line key={tick} x1={x1} y1={y1} x2={x2} y2={y2} stroke="var(--color-surface)" strokeWidth="0.9" />;
        })}
        {scaleLabels.map((ph) => {
          const [lx, ly] = polar(phToAngle(scale, ph), R - 12);
          return (
            <text
              key={ph}
              x={lx}
              y={ly}
              textAnchor="middle"
              dominantBaseline="central"
              fontSize={size === "xs" ? 7 : 5.5}
              fontWeight="600"
              fill="var(--color-text-tertiary)"
            >
              {ph}
            </text>
          );
        })}
        <line x1={tailX} y1={tailY} x2={tipX} y2={tipY} stroke="var(--color-text-primary)" strokeWidth="2.2" strokeLinecap="round" />
        <circle cx={CX} cy={CY} r="4.5" fill="var(--color-text-primary)" />
        <circle cx={CX} cy={CY} r="2" fill={measuredColor ?? "var(--color-surface)"} />
        {isNum(value) ? (
          <>
            <text x={CX} y="74" textAnchor="middle" fontSize={size === "xs" ? 15 : 13} fontWeight="700" fill="var(--color-text-primary)">
              {value.toFixed(1)}
            </text>
            <text x={CX} y="84" textAnchor="middle" fontSize="5.5" fill="var(--color-text-secondary)">
              {t("result.scale.currentPh")}
            </text>
          </>
        ) : null}
      </svg>
    </div>
  );

  if (variant === "dial") {
    return <div className={className}>{dial}</div>;
  }

  return (
    <div className={cn("flex flex-col items-center gap-4", className)}>
      {dial}

      {activeBand ? <PhBadge band={activeBand} /> : null}

      {measuredColor ? (
        <div className="flex w-full max-w-xs items-center gap-3 rounded-lg bg-deco-backdrop p-3">
          <span
            className="size-7 shrink-0 rounded-full border-[6px] border-surface shadow-sm"
            style={{ backgroundColor: measuredColor }}
            aria-hidden="true"
          />
          <span className="flex min-w-0 flex-1 flex-col">
            <span className="text-caption font-bold text-text-primary">{t("result.vetNoteTitle")}</span>
            <span className="truncate text-caption text-text-secondary">
              {t("result.grainColorName", { hex: measuredColor })}
            </span>
          </span>
        </div>
      ) : null}

      {showBandLegend ? (
        <ul className="grid w-full grid-cols-1 gap-2 sm:grid-cols-3">
          {scale.segments.map(({ band }) => {
            const isActive = activeBand?.code === band.code;
            const mid = ((isNum(band.phMin) ? band.phMin : scale.min) + (isNum(band.phMax) ? band.phMax : scale.max)) / 2;
            const rangeLabel =
              isNum(band.phMin) && isNum(band.phMax)
                ? t("result.scale.rangeBetween", { min: band.phMin.toFixed(1), max: band.phMax.toFixed(1) })
                : isNum(band.phMax)
                  ? t("result.scale.rangeBelow", { max: band.phMax.toFixed(1) })
                  : isNum(band.phMin)
                    ? t("result.scale.rangeAbove", { min: band.phMin.toFixed(1) })
                    : "";
            return (
              <li
                key={band.code}
                className={cn(
                  "flex items-start gap-2.5 rounded-lg px-3 py-2 transition-colors",
                  isActive ? "bg-chip-bg" : "hover:bg-background-alt",
                )}
              >
                <span
                  className="mt-1 size-3 shrink-0 rounded-full ring-2 ring-surface"
                  style={{ backgroundColor: phSpectrumColor(mid) }}
                  aria-hidden="true"
                />
                <span className="min-w-0">
                  <span className={cn("block text-small font-bold text-text-primary", isActive && "text-primary-dark")}>
                    {rangeLabel}
                  </span>
                  <span className="block text-small leading-snug text-text-secondary">{band.label}</span>
                </span>
              </li>
            );
          })}
        </ul>
      ) : null}
    </div>
  );
}
