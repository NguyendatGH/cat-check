import { forwardRef, useId, type InputHTMLAttributes, type ReactNode } from "react";
import { cn } from "@/shared/lib/cn";

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  helperText?: string;
  /** Icon trái, khớp pattern Input của Figma (CatCheck-Demo) — email/lock/search... */
  leftIcon?: ReactNode;
  /** Nội dung phải trong khung input (nút hiện/ẩn mật khẩu...), KHÔNG phải error/helper. */
  rightSlot?: ReactNode;
}

/**
 * Input nền tảng — label/error/helperText, aria-describedby đúng chuẩn a11y.
 * Bo góc/shadow/chiều cao khớp Figma (rounded-xl, shadow-xs thay vì viền, 48px) — nền
 * trắng nổi trên bg-background thay vì viền xám phẳng.
 */
export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ className, label, error, helperText, leftIcon, rightSlot, id, ...props }, ref) => {
    const generatedId = useId();
    const inputId = id ?? generatedId;
    const helperId = `${inputId}-helper`;
    const errorId = `${inputId}-error`;
    const describedBy = error ? errorId : helperText ? helperId : undefined;

    return (
      <div className="flex flex-col gap-1.5">
        {label ? (
          <label htmlFor={inputId} className="text-caption font-semibold text-text-secondary">
            {label}
          </label>
        ) : null}
        <div className="relative flex items-center">
          {leftIcon ? (
            <span
              className="pointer-events-none absolute left-3.5 flex items-center text-text-tertiary"
              aria-hidden="true"
            >
              {leftIcon}
            </span>
          ) : null}
          <input
            ref={ref}
            id={inputId}
            aria-invalid={error ? true : undefined}
            aria-describedby={describedBy}
            className={cn(
              "h-12 w-full rounded-xl bg-surface px-4 text-body text-text-primary shadow-xs",
              leftIcon && "pl-11",
              rightSlot && "pr-11",
              "placeholder:text-text-tertiary",
              "focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]",
              "disabled:cursor-not-allowed disabled:opacity-50",
              error && "outline outline-2 outline-danger",
              className,
            )}
            {...props}
          />
          {rightSlot ? <span className="absolute right-2 flex items-center">{rightSlot}</span> : null}
        </div>
        {error ? (
          <p id={errorId} className="text-small text-danger-text">
            {error}
          </p>
        ) : helperText ? (
          <p id={helperId} className="text-small text-text-tertiary">
            {helperText}
          </p>
        ) : null}
      </div>
    );
  },
);

Input.displayName = "Input";
