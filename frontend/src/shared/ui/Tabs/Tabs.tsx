import { forwardRef, type ComponentPropsWithoutRef, type ComponentRef } from "react";
import * as RadixTabs from "@radix-ui/react-tabs";
import { cn } from "../cn";

export const Tabs = RadixTabs.Root;

export const TabsList = forwardRef<
  ComponentRef<typeof RadixTabs.List>,
  ComponentPropsWithoutRef<typeof RadixTabs.List>
>(({ className, ...props }, ref) => (
  <RadixTabs.List
    ref={ref}
    className={cn(
      "inline-flex items-center gap-1 rounded-2xl border border-border/40 bg-background-alt/80 p-1 shadow-xs backdrop-blur-xs",
      className,
    )}
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
      "tab-pill-animated cursor-pointer min-h-[var(--touch-target-min)] rounded-xl px-3.5 py-1.5 text-caption font-semibold text-text-secondary transition-all duration-200 active:scale-[0.97]",
      "data-[state=active]:bg-surface data-[state=active]:text-primary-dark data-[state=active]:font-bold data-[state=active]:shadow-[0_2px_8px_rgba(13,54,154,0.12),inset_0_1px_0_rgba(255,255,255,0.8)]",
      "hover:text-text-primary hover:bg-surface/50",
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
  <RadixTabs.Content
    ref={ref}
    className={cn(
      "tab-content-enter pt-4 focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]",
      className,
    )}
    {...props}
  />
));
TabsContent.displayName = "TabsContent";
