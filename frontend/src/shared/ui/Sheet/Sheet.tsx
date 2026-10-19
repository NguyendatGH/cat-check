import { forwardRef, type ComponentPropsWithoutRef, type ComponentRef } from "react";
import * as RadixDialog from "@radix-ui/react-dialog";
import { cn } from "../cn";
import { DialogOverlay } from "../Dialog/Dialog";

export const Sheet = RadixDialog.Root;
export const SheetTrigger = RadixDialog.Trigger;
export const SheetClose = RadixDialog.Close;

export interface SheetContentProps extends ComponentPropsWithoutRef<typeof RadixDialog.Content> {
  side?: "bottom" | "left" | "right" | "top";
}

export const SheetContent = forwardRef<
  ComponentRef<typeof RadixDialog.Content>,
  SheetContentProps
>(({ className, children, side = "bottom", ...props }, ref) => {

  const sideClasses = {
    bottom: "inset-x-0 bottom-0 max-h-[85vh] rounded-t-2xl border-t border-border bg-surface p-6 shadow-top",
    left: "inset-y-0 left-0 h-dvh w-[86vw] max-w-sm rounded-r-2xl border-r border-border bg-surface p-6 shadow-2xl",
    right: "inset-y-0 right-0 h-dvh w-[86vw] max-w-sm rounded-l-2xl border-l border-border bg-surface p-6 shadow-2xl",
    top: "inset-x-0 top-0 max-h-[85vh] rounded-b-2xl border-b border-border bg-surface p-6 shadow-bottom",
  };

  return (
    <RadixDialog.Portal>
      <DialogOverlay />
      <RadixDialog.Content
        ref={ref}
        data-side={side}
        className={cn(
          "ui-sheet fixed z-[var(--z-modal)] overflow-y-auto",
          sideClasses[side],
          "focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]",
          className,
        )}
        {...props}
      >
        {side === "bottom" && (
          <div className="mx-auto mb-4 h-1 w-10 shrink-0 rounded-full bg-border" aria-hidden="true" />
        )}
        {children}
      </RadixDialog.Content>
    </RadixDialog.Portal>
  );
});
SheetContent.displayName = "SheetContent";

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
