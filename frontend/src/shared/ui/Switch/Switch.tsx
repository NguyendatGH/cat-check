import { forwardRef, useId, type ComponentPropsWithoutRef, type ComponentRef } from "react";
import * as RadixSwitch from "@radix-ui/react-switch";
import { cn } from "../cn";

export interface SwitchProps extends ComponentPropsWithoutRef<typeof RadixSwitch.Root> {
  label?: string;
}

export const Switch = forwardRef<ComponentRef<typeof RadixSwitch.Root>, SwitchProps>(
  ({ className, label, id, ...props }, ref) => {
    const generatedId = useId();
    const switchId = id ?? generatedId;
    const control = (
      <RadixSwitch.Root
        ref={ref}
        id={switchId}
        className={cn(
          "relative inline-flex h-6 w-11 shrink-0 cursor-pointer items-center rounded-full bg-border-strong transition-colors duration-[var(--duration-base)]",
          "before:absolute before:inset-x-0 before:-inset-y-2.5 before:content-['']",
          "data-[state=checked]:bg-primary-dark",
          "focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]",
          "disabled:cursor-not-allowed disabled:opacity-50",
          className,
        )}
        {...props}
      >
        <RadixSwitch.Thumb className="block size-5 translate-x-0.5 rounded-full bg-surface shadow-xs transition-transform duration-[var(--duration-base)] data-[state=checked]:translate-x-[22px]" />
      </RadixSwitch.Root>
    );

    if (!label) return control;

    return (
      <label htmlFor={switchId} className="flex min-h-11 cursor-pointer items-center gap-3">
        {control}
        <span className="text-caption text-text-secondary">{label}</span>
      </label>
    );
  },
);

Switch.displayName = "Switch";
