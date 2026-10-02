import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from "react";
import { cn } from "@/shared/lib/cn";

export interface ListItemProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, "title"> {
  leading?: ReactNode;
  title: ReactNode;
  subtitle?: ReactNode;
  trailing?: ReactNode;
}

/** ListItem — leading/title/subtitle/trailing, dùng cho danh sách mèo/lịch sử/nhắc lịch... */
export const ListItem = forwardRef<HTMLButtonElement, ListItemProps>(
  ({ className, leading, title, subtitle, trailing, ...props }, ref) => {
    return (
      <button
        ref={ref}
        type="button"
        className={cn(
          "flex w-full min-h-[var(--touch-target-min)] items-center gap-3 rounded-lg px-3 py-2 text-left",
          "hover:bg-background-alt focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]",
          className,
        )}
        {...props}
      >
        {leading ? (
          <span className="flex shrink-0 items-center justify-center" aria-hidden="true">
            {leading}
          </span>
        ) : null}
        <span className="flex min-w-0 flex-1 flex-col">
          <span className="truncate text-body text-text-primary">{title}</span>
          {subtitle ? <span className="truncate text-caption text-text-secondary">{subtitle}</span> : null}
        </span>
        {trailing ? <span className="flex shrink-0 items-center">{trailing}</span> : null}
      </button>
    );
  },
);

ListItem.displayName = "ListItem";
