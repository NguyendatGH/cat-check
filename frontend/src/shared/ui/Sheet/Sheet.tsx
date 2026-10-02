import { forwardRef, type ComponentPropsWithoutRef, type ComponentRef } from "react";
import * as RadixDialog from "@radix-ui/react-dialog";
import { cn } from "@/shared/lib/cn";
import { DialogOverlay } from "../Dialog/Dialog";

/** Sheet — bottom sheet mobile, bọc @radix-ui/react-dialog + biến thể trượt từ dưới lên. */
export const Sheet = RadixDialog.Root;
export const SheetTrigger = RadixDialog.Trigger;
export const SheetClose = RadixDialog.Close;

export const SheetContent = forwardRef<
  ComponentRef<typeof RadixDialog.Content>,
  ComponentPropsWithoutRef<typeof RadixDialog.Content>
>(({ className, children, ...props }, ref) => (
  <RadixDialog.Portal>
    <DialogOverlay />
    <RadixDialog.Content
      ref={ref}
      className={cn(
        "fixed inset-x-0 bottom-0 z-[var(--z-modal)] max-h-[85vh] overflow-y-auto",
        "rounded-t-2xl border-t border-border bg-surface p-6 shadow-top",
        "transition-transform duration-[var(--duration-slow)] ease-[var(--ease-standard)]",
        "data-[state=closed]:translate-y-full data-[state=open]:translate-y-0",
        "focus-visible:outline-none",
        className,
      )}
      {...props}
    >
      <div className="mx-auto mb-4 h-1 w-10 shrink-0 rounded-full bg-border" aria-hidden="true" />
      {children}
    </RadixDialog.Content>
  </RadixDialog.Portal>
));
SheetContent.displayName = "SheetContent";

export const SheetTitle = RadixDialog.Title;
export const SheetDescription = RadixDialog.Description;
