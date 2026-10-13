import { useEffect, useState, type ReactNode } from "react";

export interface DelayedFallbackProps {
  children: ReactNode;
  fallback: ReactNode;
  delay?: number;
}

/**
 * Delays showing the fallback component to avoid flashing skeletons on fast loads.
 * On typical network speeds (<250ms), nothing is shown.
 * On slow loads (>delay), the fallback appears smoothly.
 *
 * PERF: Uses requestIdleCallback to defer timer setup, avoiding main thread blocking.
 */
export function DelayedFallback({ children, fallback, delay = 250 }: DelayedFallbackProps) {
  const [showFallback, setShowFallback] = useState(false);

  useEffect(() => {
    let timer: ReturnType<typeof setTimeout> | undefined;
    let idleHandle: number | undefined;

    // Defer setting up the timeout until the browser is idle
    // This prevents blocking the main thread during critical initial render
    if (typeof requestIdleCallback !== "undefined") {
      idleHandle = requestIdleCallback(() => {
        timer = setTimeout(() => {
          setShowFallback(true);
        }, delay);
      });
    } else {
      // Fallback for browsers without requestIdleCallback
      timer = setTimeout(() => {
        setShowFallback(true);
      }, delay);
    }

    return () => {
      if (timer !== undefined) clearTimeout(timer);
      if (idleHandle !== undefined && typeof cancelIdleCallback !== "undefined") {
        cancelIdleCallback(idleHandle);
      }
    };
  }, [delay]);

  if (showFallback) {
    return <div className="fade-swap">{fallback}</div>;
  }

  return <>{children}</>;
}
