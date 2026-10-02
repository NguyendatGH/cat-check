import { useTranslation } from "react-i18next";
import { Link, useLocation, useNavigate } from "react-router";
import { Camera, Cat, ChevronRight, Compass, LineChart } from "lucide-react";
import { Button } from "@/shared/ui";

/**
 * `/*` — 404.
 *
 * Layout: `FullscreenLayout` (xem `app/router/router.tsx`), KHÔNG phải `PublicLayout` hay
 * `AppLayout`. `FullscreenLayout` chỉ là `min-h-dvh bg-background-alt` — không header, không
 * padding, không hộp nội dung — nên trang tự khai báo hộp của mình, cùng cách
 * `pages/legal/shell.tsx` xử lý cho `PublicLayout`.
 *
 * Route `*` nằm NGOÀI `RequireAuth`: trang này hiện được cả khi chưa đăng nhập. Vì vậy CTA
 * chính là `/dashboard` (xem được ở chế độ khách); các lối tắt còn lại cần phiên nên sẽ bị
 * `RequireAuth` đưa về đăng nhập — đó là hành vi đúng, không phải ngõ cụt.
 *
 * Hiện lại đường dẫn vừa mở để người dùng tự thấy chỗ gõ sai: 404 ở đây hay đến từ liên kết
 * cũ đã đổi tên (p9 §9.4.3 R-F03/R-F04 đổi `/scan/:scanId/result` → `/scan/result/:scanId`,
 * `/history/:scanId` → `/scans/:scanId`, `/scan/:scanId/assign-cat` → `.../reassign-cat`).
 */

interface Shortcut {
  to: string;
  labelKey: string;
  icon: typeof Cat;
}

const SHORTCUTS: Shortcut[] = [
  { to: "/cats", labelKey: "errors:notFound.shortcutCats", icon: Cat },
  { to: "/history", labelKey: "errors:notFound.shortcutHistory", icon: LineChart },
  { to: "/scan", labelKey: "errors:notFound.shortcutScan", icon: Camera },
];

export function NotFoundPage() {
  const { t } = useTranslation(["errors", "common"]);
  const location = useLocation();
  const navigate = useNavigate();

  return (
    <div className="mx-auto flex min-h-dvh w-full max-w-md flex-col justify-center gap-6 px-4 py-10">
      <header className="flex flex-col items-center gap-3 text-center">
        <span
          className="flex size-14 items-center justify-center rounded-2xl bg-chip-bg text-primary-dark"
          aria-hidden="true"
        >
          <Compass size={26} />
        </span>
        <h1 className="text-h2 font-bold text-text-primary">{t("common:pages.notFound.title")}</h1>
        <p className="text-body text-text-secondary">{t("errors:notFound.lead")}</p>
      </header>

      <div className="flex flex-col gap-1.5 rounded-2xl bg-surface p-4 shadow-brand-md">
        <p className="text-[11px] font-semibold uppercase tracking-wide text-text-tertiary">
          {t("errors:notFound.pathLabel")}
        </p>
        <p className="break-all font-mono text-caption text-text-primary">{location.pathname}</p>
      </div>

      <div className="flex flex-col gap-2">
        <Button
          type="button"
          size="lg"
          onClick={() => {
            void navigate("/dashboard");
          }}
        >
          {t("errors:notFound.homeCta")}
        </Button>
        <Button
          type="button"
          variant="tertiary"
          size="md"
          onClick={() => {
            void navigate(-1);
          }}
        >
          {t("errors:notFound.backCta")}
        </Button>
      </div>

      <nav
        aria-label={t("errors:notFound.shortcutsTitle")}
        className="flex flex-col gap-2 rounded-2xl bg-surface p-4 shadow-brand-md"
      >
        <p className="text-[11px] font-semibold uppercase tracking-wide text-text-tertiary">
          {t("errors:notFound.shortcutsTitle")}
        </p>
        <ul className="flex flex-col">
          {SHORTCUTS.map(({ to, labelKey, icon: Icon }) => (
            <li key={to}>
              <Link
                to={to}
                className="flex min-h-[var(--touch-target-min)] items-center gap-3 rounded-xl px-1 py-2 text-body text-text-primary hover:bg-background-alt"
              >
                <Icon size={18} className="shrink-0 text-primary-dark" aria-hidden="true" />
                <span className="min-w-0 flex-1">{t(labelKey)}</span>
                <ChevronRight size={16} className="shrink-0 text-text-tertiary" aria-hidden="true" />
              </Link>
            </li>
          ))}
        </ul>
      </nav>
    </div>
  );
}
