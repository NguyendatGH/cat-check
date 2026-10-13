import { SkeletonLoader } from "../SkeletonLoader/SkeletonLoader";

export interface PageSkeletonProps {
  /**
   * Show header section (title + subtitle)
   * @default true
   */
  showHeader?: boolean;

  /**
   * Number of content lines/cards to show
   * @default 3
   */
  lines?: number;

  /**
   * Height of each line (if different from default card height)
   */
  lineHeight?: string;
}

/**
 * Page-level skeleton that matches typical page structure.
 * Prevents layout shift by providing a better approximation of actual content.
 *
 * Usage:
 * ```tsx
 * <Suspense fallback={<PageSkeleton />}>
 *   <YourPage />
 * </Suspense>
 * ```
 */
export function PageSkeleton({
  showHeader = true,
  lines = 3,
  lineHeight = "h-16",
}: PageSkeletonProps) {
  return (
    <div className="flex flex-col gap-4 px-4 py-4 lg:px-0 lg:py-5">
      {showHeader && (
        <header className="flex flex-col gap-2">
          <SkeletonLoader shape="text" className="h-7 w-2/3" />
          <SkeletonLoader shape="text" className="h-4 w-1/2 opacity-60" />
        </header>
      )}

      <div className="flex flex-col gap-3">
        {Array.from({ length: lines }).map((_, i) => (
          <SkeletonLoader key={i} shape="card" className={`w-full ${lineHeight}`} />
        ))}
      </div>
    </div>
  );
}
