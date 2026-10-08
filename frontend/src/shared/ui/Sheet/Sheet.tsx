import { forwardRef, type ComponentPropsWithoutRef, type ComponentRef } from "react";
import * as RadixDialog from "@radix-ui/react-dialog";
import { cn } from "../cn";
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
        // Xem ghi chú cùng loại ở Dialog: `outline-none` huỷ fallback focus ring toàn cục.
        "focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]",
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

/**
 * Bọc lại title/description của Radix với token typography — bản trước export thẳng
 * primitive nên `<h2>`/`<p>` rơi về cỡ chữ mặc định của trình duyệt, lệch hẳn so với
 * `DialogTitle`/`DialogDescription` vốn đã có `text-h3`/`text-caption`.
 */
export const SheetTitle = forwardRef<
  ComponentRef<typeof RadixDialog.Title>,
  ComponentPropsWithoutRef<typeof RadixDialog.Title>
>(({ className, ...props }, ref) => (
  <RadixDialog.Title ref={ref} className={cn("text-h3 font-semibold text-text-primary", className)} {...props} />
));
SheetTitle.displayName = "SheetTitle";

export const SheetDescription = forwardRef<
  ComponentRef<typeof RadixDialog.Description>,
  ComponentPropsWithoutRef<typeof RadixDialog.Description>
>(({ className, ...props }, ref) => (
  <RadixDialog.Description ref={ref} className={cn("text-caption text-text-secondary", className)} {...props} />
));
SheetDescription.displayName = "SheetDescription";
