import { Toaster as SonnerToaster, toast } from "sonner";
import type { ComponentProps } from "react";

export type ToastProps = ComponentProps<typeof SonnerToaster>;

export function Toast(props: ToastProps) {
  return (
    <SonnerToaster
      position="top-center"
      toastOptions={{
        classNames: {
          toast: "!rounded-xl !border !border-border !bg-surface !text-text-primary !shadow-brand-lg",
          title: "!text-body !font-semibold",
          description: "!text-caption !text-text-secondary",
        },
        style: { zIndex: "var(--z-toast)" },
      }}
      {...props}
    />
  );
}

export { toast };
