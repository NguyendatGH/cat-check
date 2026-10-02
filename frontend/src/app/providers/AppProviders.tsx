import type { ReactNode } from "react";
import { I18nProvider } from "./I18nProvider";
import { QueryProvider } from "./QueryProvider";
import { SessionProvider } from "./SessionProvider";
import { ToastProvider } from "./ToastProvider";

/** Gộp toàn bộ provider gốc, thứ tự: i18n -> query -> session (chặn render tới khi biết phiên) -> toast. */
export function AppProviders({ children }: { children: ReactNode }) {
  return (
    <I18nProvider>
      <QueryProvider>
        <SessionProvider>
          <ToastProvider>{children}</ToastProvider>
        </SessionProvider>
      </QueryProvider>
    </I18nProvider>
  );
}
