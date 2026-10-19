import type { ReactNode } from "react";
import { cn } from "@/shared/lib/cn";

export function AuthCard({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div
      className={cn(
        "flex flex-col gap-6 lg:mx-auto lg:mt-6 lg:w-full lg:max-w-[480px] lg:rounded-3xl lg:bg-surface lg:p-10 lg:shadow-[0px_25px_50px_-12px_rgba(0,0,0,0.12)]",
        className,
      )}
    >
      {children}
    </div>
  );
}
