import type { ReactNode } from "react";
import { Toast } from "@/shared/ui";

export function ToastProvider({ children }: { children: ReactNode }) {
  return (
    <>
      {children}
      <Toast />
    </>
  );
}
