import { forwardRef, type HTMLAttributes } from "react";
import { cn } from "../cn";

export interface CardProps extends HTMLAttributes<HTMLDivElement> {
  padding?: "none" | "sm" | "md" | "lg";
  elevated?: boolean;
}

const PADDING_CLASS: Record<NonNullable<CardProps["padding"]>, string> = {
  none: "p-0",
  sm: "p-3",
  md: "p-4",
  lg: "p-6",
};

/** Card custom — surfaces có phân cấp, không mặc định nổi bằng shadow. */
export const Card = forwardRef<HTMLDivElement, CardProps>(
  ({ className, padding = "md", elevated = false, ...props }, ref) => {
    return (
      <div
        ref={ref}
        className={cn(
          "rounded-lg border border-border/80 bg-surface",
          PADDING_CLASS[padding],
          elevated && "shadow-brand-md",
          className,
        )}
        {...props}
      />
    );
  },
);

Card.displayName = "Card";
