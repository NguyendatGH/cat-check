import type { ReactNode } from "react";
import { cn } from "@/shared/ui/cn";

export interface ChatBubbleProps {
  variant: "user" | "assistant";
  children: ReactNode;
  className?: string;
}

export function ChatBubble({ variant, children, className }: ChatBubbleProps) {
  const baseClasses =
    "max-w-[92%] rounded-2xl px-4 py-3 animate-none transition-all duration-300 ease-out";

  const variantClasses = {
    user: "ml-auto max-w-[85%] rounded-br-sm bg-primary text-white",
    assistant:
      "rounded-bl-sm bg-gradient-to-br from-background-alt via-background-alt to-on-primary-subtle text-text-primary shadow-xs",
  };

  return (
    <div className={cn(baseClasses, variantClasses[variant], className)}>
      {children}
    </div>
  );
}
