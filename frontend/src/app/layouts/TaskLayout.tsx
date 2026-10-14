import { useEffect, useRef, useState } from "react";
import { Outlet, useLocation, useNavigate } from "react-router";
import { useTranslation } from "react-i18next";
import { ArrowLeft } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { DesktopAside } from "./shell/DesktopAside";
import { DesktopHeader } from "./shell/DesktopHeader";
import { OfflineBar } from "./shell/OfflineBar";

// The page's own <h1> text becomes the header title on mobile (the in-page h1 is then visually hidden).
function usePageHeading(mainRef: React.RefObject<HTMLElement | null>) {
  const { pathname } = useLocation();
  const [title, setTitle] = useState("");

  useEffect(() => {
    const main = mainRef.current;
    if (!main) return;
    const read = () => {
      setTitle(main.querySelector("h1")?.textContent.trim() ?? "");
    };
    read();
    const mo = new MutationObserver(read);
    mo.observe(main, { childList: true, subtree: true });
    return () => {
      mo.disconnect();
    };
  }, [mainRef, pathname]);

  return title;
}

function useScrolled() {
  const [scrolled, setScrolled] = useState(false);
  useEffect(() => {
    const onScroll = () => {
      setScrolled(window.scrollY > 4);
    };
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => {
      window.removeEventListener("scroll", onScroll);
    };
  }, []);
  return scrolled;
}

export function TaskLayout() {
  const { t } = useTranslation("common");
  const navigate = useNavigate();
  const mainRef = useRef<HTMLElement>(null);
  const title = usePageHeading(mainRef);
  const scrolled = useScrolled();

  return (
    <div className="flex min-h-dvh bg-background">
      <DesktopAside />
      <div className="flex min-w-0 flex-1 flex-col">
        <header
          className={cn(
            "sticky top-0 z-[var(--z-sticky-header)] bg-background px-2 transition-shadow duration-200 lg:hidden",
            scrolled && "shadow-[0_1px_0_var(--color-border)]",
          )}
        >
          <div className="mx-auto flex h-14 max-w-[480px] items-center gap-1">
            <button
              type="button"
              onClick={() => {
                void navigate(-1);
              }}
              aria-label={t("actions.back")}
              className="flex size-10 shrink-0 items-center justify-center rounded-full text-text-primary transition-colors hover:bg-surface active:bg-chip-bg"
            >
              <ArrowLeft size={20} aria-hidden="true" />
            </button>
            {title ? (
              <p aria-hidden="true" className="content-enter min-w-0 truncate text-[17px] font-bold text-text-primary">
                {title}
              </p>
            ) : null}
          </div>
        </header>
        <DesktopHeader showBack />
        <OfflineBar className="top-14 lg:top-16" />
        <main
          ref={mainRef}
          className={cn(
            "w-full flex-1 px-4 pb-6 pt-1 lg:mx-auto lg:max-w-[992px] lg:px-6 lg:py-6",
            title && "max-lg:[&_h1]:sr-only",
          )}
        >
          <Outlet />
        </main>
      </div>
    </div>
  );
}
