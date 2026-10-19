import { useEffect, useRef, useState } from "react";
import { PartyPopper } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { useMediaQuery } from "@/shared/lib/hooks/useMediaQuery";
import { DEFAULT_CELEBRATION_MS, useCelebrationStore } from "@/shared/lib/motion/celebration";
import { readBrandPalette, runFireworks } from "./fireworks";

export interface FireworksProps {
  durationMs?: number;
  onDone?: () => void;
  className?: string;
}

/** Canvas pháo hoa toàn khung cha; tự dọn rAF khi unmount. Không tự kiểm tra reduced-motion. */
export function Fireworks({ durationMs = DEFAULT_CELEBRATION_MS, onDone, className }: FireworksProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const onDoneRef = useRef(onDone);

  useEffect(() => {
    onDoneRef.current = onDone;
  }, [onDone]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    return runFireworks(canvas, {
      durationMs,
      colors: readBrandPalette(),
      onDone: () => onDoneRef.current?.(),
    });
  }, [durationMs]);

  return <canvas ref={canvasRef} aria-hidden="true" className={cn("pointer-events-none size-full", className)} />;
}

const FADE_OUT_MS = 400;

/**
 * Gắn MỘT lần trong AppProviders. Nghe store `celebrate()`; phủ toàn màn hình, không chặn thao tác
 * (pointer-events none), tự tắt sau ~3 giây. prefers-reduced-motion: bỏ pháo hoa, chỉ hiện thẻ lời
 * chào rồi mờ dần.
 */
export function CelebrationHost() {
  const current = useCelebrationStore((s) => s.current);
  const finish = useCelebrationStore((s) => s.finish);
  const reducedMotion = useMediaQuery("(prefers-reduced-motion: reduce)");
  const [leavingId, setLeavingId] = useState<number | null>(null);

  const id = current?.id ?? null;
  const durationMs = current?.durationMs ?? DEFAULT_CELEBRATION_MS;

  useEffect(() => {
    if (id === null) return;
    const fadeTimer = window.setTimeout(() => {
      setLeavingId(id);
    }, Math.max(0, durationMs - FADE_OUT_MS));
    const doneTimer = window.setTimeout(() => {
      finish(id);
    }, durationMs);
    return () => {
      window.clearTimeout(fadeTimer);
      window.clearTimeout(doneTimer);
    };
  }, [id, durationMs, finish]);

  if (!current) return null;
  const hasMessage = Boolean(current.title ?? current.message);
  const leaving = leavingId === current.id;

  return (
    <div
      data-testid="celebration-overlay"
      className="pointer-events-none fixed inset-0 z-[var(--z-toast)] flex items-center justify-center p-4"
    >
      {reducedMotion ? null : <Fireworks key={current.id} durationMs={current.durationMs} className="absolute inset-0" />}
      {hasMessage ? (
        <div
          key={current.id}
          role="status"
          aria-live="polite"
          className={cn(
            "relative flex max-w-sm flex-col items-center gap-2 rounded-2xl border border-secondary/60 bg-surface/95 px-6 py-5 text-center shadow-brand-lg backdrop-blur-sm transition-opacity duration-300",
            reducedMotion ? null : "animate-pop-spring",
            leaving ? "opacity-0" : "opacity-100",
          )}
        >
          <span className="flex size-12 items-center justify-center rounded-full bg-secondary-light">
            <PartyPopper className="size-6 text-secondary-text-on" aria-hidden="true" />
          </span>
          {current.title ? <p className="text-h3 font-bold text-text-primary">{current.title}</p> : null}
          {current.message ? <p className="text-body font-semibold text-primary">{current.message}</p> : null}
        </div>
      ) : null}
    </div>
  );
}
