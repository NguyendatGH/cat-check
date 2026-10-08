import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from "react";
import { cn } from "../cn";
import { buttonVariants, type ButtonVariantProps } from "./Button.variants";

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement>, ButtonVariantProps {
  leftIcon?: ReactNode;
}

/** Button nền tảng — variant primary/secondary/tertiary, size, leftIcon, loading, disabled. */
export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, loading, leftIcon, disabled, children, ...props }, ref) => {
    return (
      <button
        ref={ref}
        className={cn(buttonVariants({ variant, size, loading }), className)}
        disabled={Boolean(disabled) || Boolean(loading)}
        aria-busy={loading ? true : undefined}
        {...props}
      >
        {loading ? (
          <span
            className="absolute inline-flex size-4 animate-spin rounded-full border-2 border-current border-t-transparent"
            aria-hidden="true"
          />
        ) : null}
        {leftIcon ? (
          <span className="inline-flex shrink-0" aria-hidden="true">
            {leftIcon}
          </span>
        ) : null}
        {children}
      </button>
    );
  },
);

Button.displayName = "Button";
