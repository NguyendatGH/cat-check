import type { ReactNode } from "react";
import { ArrowLeft } from "lucide-react";
import { useNavigate } from "react-router";
import { useTranslation } from "react-i18next";
import { LogoPawIcon } from "@/shared/assets/icons/AppIcons";

export interface TopBarProps {
  /** app-level: logo + wordmark (trang chủ, khu vực đã đăng nhập). task-level: back + tiêu đề (p10 §5.2). */
  variant: "app-level" | "task-level";
  /** Bắt buộc với task-level, bỏ qua ở app-level (dùng wordmark thay). */
  title?: string;
  /** task-level: gọi khi bấm back; mặc định `navigate(-1)`. */
  onBack?: () => void;
  /** Khu vực hành động bên phải — mặc định app-level là bell+avatar, task-level là rỗng. */
  actions?: ReactNode;
}

const HEADER_SHELL =
  "sticky top-0 z-[var(--z-sticky-header)] w-full bg-[rgba(250,248,255,0.8)] shadow-[0px_1px_8px_0px_rgba(0,0,0,0.04)] backdrop-blur-md";

/**
 * Thanh trên cùng dùng chung — khớp Figma (CatCheck-Demo 26mOVF2zdu4cI1EPz2Syxw): 2 biến thể
 * app-level (node 1:2217, Trang chủ) và task-level (node header 01b/01c-1, back+tiêu đề+avatar).
 */
export function TopBar({ variant, title, onBack, actions }: TopBarProps) {
  const { t } = useTranslation("common");
  const navigate = useNavigate();

  if (variant === "app-level") {
    return (
      <header className={HEADER_SHELL}>
        <div className="flex h-14 items-center justify-between px-4">
          <div className="flex items-center gap-2">
            <div className="flex size-9 shrink-0 items-center justify-center rounded-full bg-primary text-white shadow-[0px_2px_4px_rgba(47,79,178,0.15)]">
              <LogoPawIcon size={17} />
            </div>
            <h1 className="text-[18px] font-bold tracking-[-0.45px] text-primary-dark">CATCHECK</h1>
          </div>
          <div className="flex items-center gap-2">{actions}</div>
        </div>
      </header>
    );
  }

  return (
    <header className={HEADER_SHELL}>
      <div className="relative flex h-14 items-center justify-center px-2">
        <button
          type="button"
          onClick={() => {
            if (onBack) {
              onBack();
            } else {
              void navigate(-1);
            }
          }}
          aria-label={t("actions.back")}
          className="absolute left-2 flex size-11 items-center justify-center rounded-full text-text-primary hover:bg-background-alt"
        >
          <ArrowLeft size={20} aria-hidden="true" />
        </button>
        <h1 className="truncate px-14 text-[17px] font-semibold text-text-primary">{title}</h1>
        {actions ? <div className="absolute right-2 flex items-center">{actions}</div> : null}
      </div>
    </header>
  );
}
