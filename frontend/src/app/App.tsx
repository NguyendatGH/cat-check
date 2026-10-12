import { RouterProvider } from "react-router";
import { useRef } from "react";
import { AppProviders } from "./providers/AppProviders";
import { router } from "./router/router";
import { useMicroInteractions } from "@/shared/lib/motion/usePageReveal";

function MotionRoot() {
  const rootRef = useRef<HTMLDivElement>(null);
  useMicroInteractions(rootRef);
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
