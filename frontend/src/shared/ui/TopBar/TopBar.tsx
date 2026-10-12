import type { ReactNode } from "react";
import { ArrowLeft } from "lucide-react";
import { Link, useNavigate } from "react-router";
import { useTranslation } from "react-i18next";
import { cn } from "@/shared/lib/cn";
import appLogo from "@/shared/assets/images/web-auth/logo.png";

export interface TopBarProps {
  /** app-level: logo + wordmark (trang chủ, khu vực đã đăng nhập). task-level: back + tiêu đề (p10 §5.2). */
  variant: "app-level" | "task-level";
  /** Bắt buộc với task-level, bỏ qua ở app-level (dùng wordmark thay). */
  title?: string;
  /** task-level: gọi khi bấm back; mặc định `navigate(-1)` (hoặc về `/` nếu không có lịch sử). */
  onBack?: () => void;
  /** Khu vực hành động bên phải — mặc định app-level là bell+avatar, task-level là rỗng. */
  actions?: ReactNode;
}

// `rgba(250,248,255,.8)` chính là `--color-background` ở 80% và `0 1px 8px rgba(0,0,0,.04)`
// nằm trong họ shadow trung tính của p10 §2.5 — dùng token thay vì viết lại giá trị.
const HEADER_SHELL = "sticky top-0 z-[var(--z-sticky-header)] w-full bg-background/80 shadow-xs backdrop-blur-md";

/**
 * Thanh trên cùng dùng chung — khớp Figma (CatCheck-Demo 26mOVF2zdu4cI1EPz2Syxw): 2 biến thể
 * app-level (node 1:2217, Trang chủ) và task-level (node header 01b/01c-1, back+tiêu đề+avatar).
 */
export function TopBar({ variant, title, onBack, actions }: TopBarProps) {
  const { t } = useTranslation("common");
  const navigate = useNavigate();

  const handleBack = () => {
    if (onBack) {
      onBack();
      return;
    }
    const state = typeof window !== "undefined" ? (window.history.state as { idx?: number } | null) : null;
    const canGoBack = typeof state?.idx === "number" && state.idx > 0;

    if (canGoBack) {
      void navigate(-1);
    } else {
      void navigate("/");
    }
  };

  if (variant === "app-level") {
    return (
      <header className={HEADER_SHELL}>
        <div className="flex h-14 items-center justify-between px-4">
          <Link to="/" className="inline-flex shrink-0 items-center" aria-label={t("app.name")}>
            <img src={appLogo} alt={t("app.name")} className="h-11 w-[122px] object-contain object-left" />
          </Link>
          <div className="flex items-center gap-2">{actions}</div>
        </div>
      </header>
    );
  }

  return (
    <header className={cn(HEADER_SHELL, "border-b border-primary/10")}>
      <div className="mx-auto flex h-14 max-w-[480px] items-center justify-between px-2 sm:px-3">
        <div className="flex min-w-0 items-center gap-1.5 text-primary-dark">
          <button
            type="button"
            onClick={handleBack}
            aria-label={t("actions.back")}
            className="flex size-10 shrink-0 items-center justify-center rounded-full text-text-primary transition-colors hover:bg-background-alt"
          >
            <ArrowLeft size={20} aria-hidden="true" />
          </button>
          <Link to="/" className="inline-flex shrink-0 items-center" aria-label={t("app.name")}>
            <img src={appLogo} alt={t("app.name")} className="h-10 w-[112px] object-contain object-left" />
          </Link>
          {title ? (
            <span className="truncate border-l border-border pl-2 text-xs font-semibold text-text-tertiary">
              {title}
            </span>
          ) : null}
        </div>
        {actions ? (
          <div className="flex size-10 items-center justify-center">{actions}</div>
        ) : (
          <span className="size-10" />
        )}
      </div>
    </header>
  );
}
