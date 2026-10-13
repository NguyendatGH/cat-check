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
    <div className="flex flex-col gap-5 px-4 py-5 lg:px-0">
      {showHeader && (
        <header className="flex flex-col gap-2">
          <SkeletonLoader shape="text" className="h-8 w-3/4" />
          <SkeletonLoader shape="text" className="h-4 w-1/2" />
        </header>
      )}

      <div className="flex flex-col gap-4">
        {Array.from({ length: lines }).map((_, i) => (
          <SkeletonLoader key={i} shape="card" className={`w-full ${lineHeight}`} />
        ))}
      </div>
    </div>
  );
}
