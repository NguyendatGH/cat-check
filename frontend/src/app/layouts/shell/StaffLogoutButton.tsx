import { LogOut } from "lucide-react";
import { useTranslation } from "react-i18next";
import { useLogout } from "@/features/auth";
import { cn } from "@/shared/lib/cn";

interface StaffLogoutButtonProps {
  /** "icon" shows only the icon (label kept for screen readers); "full" shows icon + label. */
  variant?: "icon" | "full";
  className?: string;
}

/** Sign-out control for the admin and seller consoles. */
export function StaffLogoutButton({ variant = "full", className }: StaffLogoutButtonProps) {
  const { t } = useTranslation("common");
  const logout = useLogout();
  const label = t("actions.logout");

  return (
    <button
      type="button"
      disabled={logout.isPending}
      aria-label={label}
      title={label}
      onClick={() => {
        logout.mutate(undefined, {
          onSettled: () => {
            // Full reload drops every cached query and the session store.
            window.location.assign("/auth/login");
          },
        });
      }}
      className={cn(
        "flex min-h-11 items-center justify-center gap-2 rounded-md font-semibold transition-colors disabled:opacity-50",
        variant === "icon" ? "size-11" : "px-3",
        className,
      )}
    >
      <LogOut size={16} aria-hidden="true" />
      {variant === "full" ? <span>{label}</span> : null}
    </button>
  );
}
