import { forwardRef, type ComponentPropsWithoutRef, type ComponentRef } from "react";
import * as RadixDialog from "@radix-ui/react-dialog";
import { X } from "lucide-react";
import { cn } from "../cn";

export const Dialog = RadixDialog.Root;
export const DialogTrigger = RadixDialog.Trigger;
export const DialogClose = RadixDialog.Close;

export const DialogOverlay = forwardRef<
  ComponentRef<typeof RadixDialog.Overlay>,
  ComponentPropsWithoutRef<typeof RadixDialog.Overlay>
>(({ className, ...props }, ref) => {
  return (
    <RadixDialog.Overlay
      ref={ref}
      className={cn("ui-overlay fixed inset-0 z-[var(--z-overlay)] bg-primary-darker/40", className)}
      {...props}
    />
  );
});
DialogOverlay.displayName = "DialogOverlay";

export const DialogContent = forwardRef<
  ComponentRef<typeof RadixDialog.Content>,
  ComponentPropsWithoutRef<typeof RadixDialog.Content> & { hideCloseButton?: boolean }
>(({ className, children, hideCloseButton, ...props }, ref) => {
  return (
  <RadixDialog.Portal>
    <DialogOverlay />
    <RadixDialog.Content
      ref={ref}
      className={cn(
        "ui-dialog fixed left-1/2 top-1/2 z-[var(--z-modal)] w-[calc(100vw-2rem)] max-w-md -translate-x-1/2 -translate-y-1/2",
        "rounded-2xl border border-border bg-surface p-6 shadow-brand-xl",
        "focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]",
        className,
      )}
      {...props}
    >
      {children}
      {hideCloseButton ? null : (
        <RadixDialog.Close
          className="absolute right-3 top-3 flex size-11 items-center justify-center rounded-lg text-text-tertiary hover:bg-background-alt"
          aria-label="Đóng"
        >
          <X size={18} aria-hidden="true" />
        </RadixDialog.Close>
      )}
    </RadixDialog.Content>
  </RadixDialog.Portal>
  );
});
DialogContent.displayName = "DialogContent";

export const DialogTitle = forwardRef<
  ComponentRef<typeof RadixDialog.Title>,
  ComponentPropsWithoutRef<typeof RadixDialog.Title>
>(({ className, ...props }, ref) => (
  <RadixDialog.Title ref={ref} className={cn("text-h3 text-text-primary", className)} {...props} />
));
DialogTitle.displayName = "DialogTitle";

export const DialogDescription = forwardRef<
  ComponentRef<typeof RadixDialog.Description>,
  ComponentPropsWithoutRef<typeof RadixDialog.Description>
>(({ className, ...props }, ref) => (
  <RadixDialog.Description ref={ref} className={cn("text-caption text-text-secondary", className)} {...props} />
));
DialogDescription.displayName = "DialogDescription";
