import { forwardRef, useId, type ComponentPropsWithoutRef, type ComponentRef } from "react";
import * as RadixSwitch from "@radix-ui/react-switch";
import { cn } from "@/shared/lib/cn";

export interface SwitchProps extends ComponentPropsWithoutRef<typeof RadixSwitch.Root> {
  /** Nhãn hiển thị bên phải công tắc (Ghi nhớ đăng nhập, bật lịch nhắc...). */
  label?: string;
}

/**
 * Bật/tắt 1 thiết lập — bọc `@radix-ui/react-switch`, khớp Figma (track xanh đậm khi bật,
 * nút tròn trắng trượt phải) dùng ở "Ghi nhớ đăng nhập" (M1 01b) và cài đặt thông báo.
 */
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
          "data-[state=checked]:bg-primary-dark",
          "focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]",
          "disabled:cursor-not-allowed disabled:opacity-50",
          className,
        )}
        {...props}
      >
        <RadixSwitch.Thumb className="block size-5 translate-x-0.5 rounded-full bg-white shadow-xs transition-transform duration-[var(--duration-base)] data-[state=checked]:translate-x-[22px]" />
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
