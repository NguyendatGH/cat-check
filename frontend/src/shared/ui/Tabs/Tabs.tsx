import { forwardRef, type ComponentPropsWithoutRef, type ComponentRef } from "react";
import * as RadixTabs from "@radix-ui/react-tabs";
import { cn } from "@/shared/lib/cn";

/** Tabs — bọc @radix-ui/react-tabs. */
export const Tabs = RadixTabs.Root;

export const TabsList = forwardRef<
  ComponentRef<typeof RadixTabs.List>,
  ComponentPropsWithoutRef<typeof RadixTabs.List>
>(({ className, ...props }, ref) => (
  <RadixTabs.List
    ref={ref}
    className={cn("inline-flex items-center gap-1 rounded-lg bg-background-alt p-1", className)}
    {...props}
  />
));
TabsList.displayName = "TabsList";

export const TabsTrigger = forwardRef<
  ComponentRef<typeof RadixTabs.Trigger>,
  ComponentPropsWithoutRef<typeof RadixTabs.Trigger>
>(({ className, ...props }, ref) => (
  <RadixTabs.Trigger
    ref={ref}
    className={cn(
      "min-h-[var(--touch-target-min)] rounded-md px-3 py-1.5 text-caption font-semibold text-text-secondary",
      "data-[state=active]:bg-surface data-[state=active]:text-primary data-[state=active]:shadow-xs",
      "focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]",
      className,
    )}
    {...props}
  />
));
TabsTrigger.displayName = "TabsTrigger";

export const TabsContent = forwardRef<
  ComponentRef<typeof RadixTabs.Content>,
  ComponentPropsWithoutRef<typeof RadixTabs.Content>
>(({ className, ...props }, ref) => (
  <RadixTabs.Content ref={ref} className={cn("pt-4 focus-visible:outline-none", className)} {...props} />
));
TabsContent.displayName = "TabsContent";
