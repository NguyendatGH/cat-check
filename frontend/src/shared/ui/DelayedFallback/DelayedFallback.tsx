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
 */
export function DelayedFallback({ children, fallback, delay = 250 }: DelayedFallbackProps) {
  const [showFallback, setShowFallback] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => {
      setShowFallback(true);
    }, delay);

    return () => {
      clearTimeout(timer);
    };
  }, [delay]);

  if (showFallback) {
    return <div className="fade-swap">{fallback}</div>;
  }

  return <>{children}</>;
}
