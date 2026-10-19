import type { ReactNode } from "react";
import { I18nProvider } from "./I18nProvider";
import { QueryProvider } from "./QueryProvider";
import { SessionProvider } from "./SessionProvider";
import { ToastProvider } from "./ToastProvider";
import { SplashHost } from "@/shared/ui/BrandSplash";
import { CelebrationHost } from "@/shared/ui/Celebration";

export function AppProviders({ children }: { children: ReactNode }) {
  return (
    <I18nProvider>
      <QueryProvider>
        <SessionProvider>
          <ToastProvider>
            {children}
            <SplashHost />
            <CelebrationHost />
          </ToastProvider>
        </SessionProvider>
      </QueryProvider>
    </I18nProvider>
  );
}
