import { cn } from "@/shared/lib/cn";
import { Button } from "../Button/Button";

export interface ErrorStateProps {
  title: string;
  description?: string;
  onRetry?: () => void;
  retryLabel?: string;
  className?: string;
}

/** ErrorState — title/description/onRetry, dùng khi query lỗi. */
export function ErrorState({ title, description, onRetry, retryLabel, className }: ErrorStateProps) {
  return (
    <div className={cn("flex flex-col items-center gap-3 px-6 py-12 text-center", className)} role="alert">
      <p className="text-h3 text-danger-text">{title}</p>
      {description ? <p className="max-w-sm text-caption text-text-secondary">{description}</p> : null}
      {onRetry ? (
        <Button variant="tertiary" size="sm" onClick={onRetry} className="mt-2">
          {retryLabel}
        </Button>
      ) : null}
    </div>
  );
}
