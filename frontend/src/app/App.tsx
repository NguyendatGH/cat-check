import { RouterProvider } from "react-router";
import { useEffect, useRef } from "react";
import { AppProviders } from "./providers/AppProviders";
import { router } from "./router/router";
import { preloadPagesFor } from "./router/lazy";
import { hasNonAdminRole, useSessionStore } from "@/entities/user";
import { useMicroInteractions } from "@/shared/lib/motion/usePageReveal";

function usePagePreload() {
  const status = useSessionStore((s) => s.status);
  const user = useSessionStore((s) => s.user);
  const audience =
    status === "authenticated" ? (hasNonAdminRole(user) ? "buyer" : "staff") : status === "anonymous" ? "anonymous" : null;
  const done = useRef(new Set<string>());

  useEffect(() => {
    if (!audience || done.current.has(audience)) return;
    done.current.add(audience);
    preloadPagesFor(audience);
  }, [audience]);
}

function MotionRoot() {
  const rootRef = useRef<HTMLDivElement>(null);
  useMicroInteractions(rootRef);
  usePagePreload();
  return (
    <div ref={rootRef} className="min-h-dvh">
      <RouterProvider router={router} />
    </div>
  );
}

export function App() {
  return (
    <AppProviders>
      <MotionRoot />
    </AppProviders>
  );
}
