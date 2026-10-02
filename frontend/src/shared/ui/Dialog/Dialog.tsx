import { forwardRef, type ComponentPropsWithoutRef, type ComponentRef } from "react";
import * as RadixDialog from "@radix-ui/react-dialog";
import { X } from "lucide-react";
import { cn } from "@/shared/lib/cn";

/** Dialog/Modal — bọc @radix-ui/react-dialog. */
export const Dialog = RadixDialog.Root;
export const DialogTrigger = RadixDialog.Trigger;
export const DialogClose = RadixDialog.Close;

export const DialogOverlay = forwardRef<
  ComponentRef<typeof RadixDialog.Overlay>,
  ComponentPropsWithoutRef<typeof RadixDialog.Overlay>
>(({ className, ...props }, ref) => (
  <RadixDialog.Overlay
    ref={ref}
    className={cn("fixed inset-0 z-[var(--z-overlay)] bg-primary-darker/40", className)}
    {...props}
  />
));
DialogOverlay.displayName = "DialogOverlay";

export const DialogContent = forwardRef<
  ComponentRef<typeof RadixDialog.Content>,
  ComponentPropsWithoutRef<typeof RadixDialog.Content> & { hideCloseButton?: boolean }
>(({ className, children, hideCloseButton, ...props }, ref) => (
  <RadixDialog.Portal>
    <DialogOverlay />
    <RadixDialog.Content
      ref={ref}
      className={cn(
        "fixed left-1/2 top-1/2 z-[var(--z-modal)] w-[calc(100vw-2rem)] max-w-md -translate-x-1/2 -translate-y-1/2",
        "rounded-2xl border border-border bg-surface p-6 shadow-brand-xl",
        "focus-visible:outline-none",
        className,
      )}
      {...props}
    >
      {children}
      {hideCloseButton ? null : (
        <RadixDialog.Close
          className="absolute right-4 top-4 rounded-md p-1 text-text-tertiary hover:bg-background-alt"
          aria-label="Đóng"
        >
          <X size={18} aria-hidden="true" />
        </RadixDialog.Close>
      )}
    </RadixDialog.Content>
  </RadixDialog.Portal>
));
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
