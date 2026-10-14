import { ChevronLeft, ChevronRight, Loader2 } from "lucide-react";
import { cn } from "../cn";

export interface PaginationProps {
  page: number;
  pageCount: number;
  hasMore?: boolean;
  loading?: boolean;
  onPageChange: (page: number) => void;
  labels: { previous: string; next: string; page: (page: number) => string; nav: string };
  className?: string;
}

function pageWindow(page: number, count: number): (number | "gap")[] {
  if (count <= 5) return Array.from({ length: count }, (_, i) => i);
  const keep = new Set([0, count - 1, page - 1, page, page + 1].filter((p) => p >= 0 && p < count));
  const sorted = [...keep].sort((a, b) => a - b);
  const out: (number | "gap")[] = [];
  sorted.forEach((p, i) => {
    if (i > 0 && p - sorted[i - 1] > 1) out.push("gap");
    out.push(p);
  });
  return out;
}

export function Pagination({ page, pageCount, hasMore = false, loading = false, onPageChange, labels, className }: PaginationProps) {
  if (pageCount <= 1 && !hasMore) return null;
  const canNext = page < pageCount - 1 || hasMore;
  const arrow =
    "flex size-10 items-center justify-center rounded-xl text-text-secondary transition-colors hover:bg-background-alt hover:text-text-primary disabled:pointer-events-none disabled:opacity-35";

  return (
    <nav aria-label={labels.nav} className={cn("flex items-center justify-center gap-1", className)}>
      <button
        type="button"
        aria-label={labels.previous}
        disabled={page === 0 || loading}
        onClick={() => {
          onPageChange(page - 1);
        }}
        className={arrow}
      >
        <ChevronLeft size={18} aria-hidden="true" />
      </button>
      {pageWindow(page, pageCount).map((p, i) =>
        p === "gap" ? (
          <span key={`gap-${String(i)}`} className="w-6 text-center text-caption text-text-tertiary" aria-hidden="true">
            …
          </span>
        ) : (
          <button
            key={p}
            type="button"
            aria-label={labels.page(p + 1)}
            aria-current={p === page ? "page" : undefined}
            onClick={() => {
              onPageChange(p);
            }}
            className={cn(
              "flex size-10 items-center justify-center rounded-xl text-caption font-semibold tabular-nums transition-colors",
              p === page ? "bg-primary-dark text-white" : "text-text-secondary hover:bg-background-alt hover:text-text-primary",
            )}
          >
            {p + 1}
          </button>
        ),
      )}
      {hasMore ? (
        <span className="w-6 text-center text-caption text-text-tertiary" aria-hidden="true">
          …
        </span>
      ) : null}
      <button
        type="button"
        aria-label={labels.next}
        disabled={!canNext || loading}
        onClick={() => {
          onPageChange(page + 1);
        }}
        className={arrow}
      >
        {loading ? <Loader2 size={18} className="animate-spin" aria-hidden="true" /> : <ChevronRight size={18} aria-hidden="true" />}
      </button>
    </nav>
  );
}
