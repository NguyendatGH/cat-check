import type { ReactNode } from "react";
import { cn } from "@/shared/lib/cn";

export interface EmptyStateProps {
  icon?: ReactNode;
  title: string;
  description?: string;
  action?: ReactNode;
  className?: string;
}

/** EmptyState — icon/title/description/action. Dùng cho danh sách rỗng và placeholder trang M0. */
export function EmptyState({ icon, title, description, action, className }: EmptyStateProps) {
  return (
    <div className={cn("flex flex-col items-center gap-3 px-6 py-12 text-center", className)}>
      {icon ? (
        <div
          className="flex size-12 items-center justify-center rounded-full bg-background-alt text-text-tertiary"
          aria-hidden="true"
        >
          {icon}
        </div>
      ) : null}
      <p className="text-h3 text-text-primary">{title}</p>
      {description ? <p className="max-w-sm text-caption text-text-secondary">{description}</p> : null}
      {action ? <div className="mt-2">{action}</div> : null}
    </div>
  );
}
