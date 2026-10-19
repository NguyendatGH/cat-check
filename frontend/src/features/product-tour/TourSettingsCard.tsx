import { useNavigate } from "react-router";
import { useTranslation } from "react-i18next";
import { Compass } from "lucide-react";
import { hasNonAdminRole, useSessionStore } from "@/entities/user";
import { LANDING_PATHS } from "@/shared/auth/landing";
import { Switch } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import { useTourStatus, writeTourStatus } from "./storage";

/** Bật/tắt hướng dẫn tính năng và nút xem lại (đưa về Trang chủ để chạy tour). */
export function TourSettingsCard({
  className,
  tone = "inset",
}: {
  className?: string;
  /** inset: nằm trong 1 card trắng (nền xám nhạt) · card: tự là 1 card trắng. */
  tone?: "inset" | "card";
}) {
  const innerBg = tone === "card" ? "bg-background-alt" : "bg-surface";
  const { t } = useTranslation("tour");
  const navigate = useNavigate();
  const user = useSessionStore((s) => s.user);
  const userId = user?.id ?? null;
  const status = useTourStatus(userId);

  // Tour chỉ dành cho người mua; staff không có Trang chủ /dashboard.
  if (!userId || !hasNonAdminRole(user)) return null;

  const replay = () => {
    writeTourStatus(userId, "pending");
    void navigate(LANDING_PATHS.buyer);
  };

  return (
    <div
      className={cn(
        "flex flex-col gap-3 p-4",
        tone === "card" ? "rounded-2xl bg-surface" : "rounded-xl bg-background-alt",
        className,
      )}
    >
      <div className="flex items-start gap-3">
        <span className={cn("flex size-9 shrink-0 items-center justify-center rounded-lg text-primary-dark", innerBg)}>
          <Compass size={17} aria-hidden="true" />
        </span>
        <div className="min-w-0 flex-1">
          <p className="text-body font-bold text-text-primary">{t("settings.title")}</p>
          <p className="pt-0.5 text-caption text-text-secondary">{t("settings.note")}</p>
        </div>
        <Switch
          checked={status === "pending"}
          onCheckedChange={(checked) => {
            writeTourStatus(userId, checked ? "pending" : "disabled");
          }}
          aria-label={t("settings.toggle")}
        />
      </div>
      <button
        type="button"
        onClick={replay}
        className={cn(
          "flex min-h-[var(--touch-target-min)] items-center justify-center rounded-xl px-3.5 py-2 text-caption font-semibold text-primary hover:bg-chip-bg",
          innerBg,
        )}
      >
        {t("settings.replay")}
      </button>
    </div>
  );
}
