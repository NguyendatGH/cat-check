import { useTranslation } from "react-i18next";
import { cn } from "@/shared/lib/cn";

export const LEGAL_SHELL = "mx-auto flex w-full max-w-2xl flex-col px-4 py-8 lg:max-w-[992px] lg:px-6 lg:py-10";

export const LEGAL_SPLIT = "flex flex-col gap-6 lg:flex-row lg:items-start lg:gap-6";

export const LEGAL_COLUMN = "flex min-w-0 flex-1 flex-col";

export const LEGAL_ASIDE =
  "hidden lg:sticky lg:top-[72px] lg:flex lg:w-[360px] lg:shrink-0 lg:flex-col lg:gap-4 lg:print:hidden";

export const LEGAL_ASIDE_CARD = "flex flex-col gap-2 rounded-2xl bg-surface p-5 shadow-brand-md";

export interface TocItem {
  id: string;
  label: string;
  level?: number;
}

export function DocumentToc({ items }: { items: TocItem[] }) {
  const { t } = useTranslation("legal");
  if (items.length === 0) return null;
  return (
    <nav aria-label={t("toc.title")} className={cn(LEGAL_ASIDE_CARD, "gap-3")}>
      <p className="text-[11px] font-semibold uppercase tracking-wide text-text-tertiary">{t("toc.title")}</p>
      <ul className="flex flex-col gap-1 overflow-y-auto">
        {items.map((item) => (
          <li key={item.id}>
            <a
              href={`#${item.id}`}
              className={cn(
                "block rounded-md py-1 text-caption text-text-secondary hover:text-primary hover:underline",
                (item.level ?? 2) >= 3 && "pl-3",
              )}
            >
              {item.label}
            </a>
          </li>
        ))}
      </ul>
    </nav>
  );
}
