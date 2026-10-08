import { Check } from "lucide-react";
import { LogoPawIcon } from "@/shared/assets/icons/AppIcons";
import { cn } from "../cn";

export interface StepperProps {
  /** Bước hiện tại (1-based). */
  current: number;
  total: number;
  /** Nhãn bước, vd "Bước 1/4: Thông tin chủ nuôi" — truyền sẵn chuỗi đã ghép i18n. */
  label: string;
  className?: string;
}

/**
 * Thanh tiến trình nhiều bước dùng chung (p10 §5.2 "RegistrationStepper: dot 4 bước + % +
 * progress") — khớp Figma 01c-1..4: icon paw + nhãn bước bên trái, phần trăm bên phải (đổi
 * thành dấu tick xanh khi hoàn tất 100%), thanh progress bo tròn bên dưới.
 */
export function Stepper({ current, total, label, className }: StepperProps) {
  const percent = Math.round((current / total) * 100);
  const isComplete = percent >= 100;

  return (
    <div className={cn("w-full", className)}>
      <div className="mb-2 flex items-center justify-between gap-2">
        <p className="flex items-center gap-1.5 text-caption font-semibold text-text-secondary">
          <LogoPawIcon size={13} className="shrink-0 text-primary-dark" />
          <span className="truncate">{label}</span>
        </p>
        {isComplete ? (
          <span className="flex shrink-0 items-center gap-1 rounded-full bg-success-bg px-2 py-0.5 text-overline text-success-text">
            <Check size={11} strokeWidth={3} aria-hidden="true" />
            {percent}%
          </span>
        ) : (
          <span className="shrink-0 text-caption text-text-tertiary">{percent}%</span>
        )}
      </div>
      <div
        className="h-1.5 w-full overflow-hidden rounded-full bg-chip-bg"
        role="progressbar"
        aria-valuenow={current}
        aria-valuemin={1}
        aria-valuemax={total}
        aria-label={label}
      >
        <div
          className="h-full rounded-full bg-primary-dark transition-[width] duration-[var(--duration-base)] ease-[var(--ease-standard)]"
          style={{ width: `${String(percent)}%` }}
        />
      </div>
    </div>
  );
}
