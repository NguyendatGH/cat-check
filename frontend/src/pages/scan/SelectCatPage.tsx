import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { ChevronDown, CircleHelp, Lightbulb, Plus, ShieldCheck, UserPlus } from "lucide-react";
import { Button, EmptyState, ErrorState, SkeletonLoader } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import { useListReveal } from "@/shared/lib/motion/usePageReveal";
import { usePhBands } from "@/entities/ph-bands";
import { useActiveCatsForCapture, useLatestScanByCat, useScanCaptureStore } from "@/features/scan";
import { CatChoice, SharedChoice } from "./selectCatParts";

function InfoDisclosure({ icon, title, body }: { icon: React.ReactNode; title: string; body: string }) {
  return (
    <details className="group rounded-xl bg-background-alt/70 px-3 py-2 [&_summary::-webkit-details-marker]:hidden">
      <summary className="flex min-h-9 cursor-pointer list-none items-center gap-2 text-small font-semibold text-text-secondary">
        <span className="shrink-0 text-primary">{icon}</span>
        <span className="min-w-0 flex-1 truncate">{title}</span>
        <ChevronDown
          className="size-4 shrink-0 text-text-tertiary transition-transform duration-200 group-open:rotate-180"
          aria-hidden="true"
        />
      </summary>
      <p className="content-enter pb-1 pl-6 text-small leading-relaxed text-text-secondary">{body}</p>
    </details>
  );
}

export function SelectCatPage() {
  const { t } = useTranslation(["scan", "common"]);
  const navigate = useNavigate();
  const { data: cats, isPending, isError, refetch } = useActiveCatsForCapture();
  const { data: bands } = usePhBands();
  const setSelectedCat = useScanCaptureStore((s) => s.setSelectedCat);
  const setSharedUnknown = useScanCaptureStore((s) => s.setSharedUnknown);

  const [selection, setSelection] = useState<{ id: string; name: string } | "SHARED" | null>(null);
  const latestByCat = useLatestScanByCat((cats ?? []).map((cat) => cat.id));
  const listRef = useRef<HTMLDivElement>(null);
  useListReveal(listRef, cats?.length ?? 0);

  const handleConfirm = () => {
    if (selection === "SHARED") {
      setSharedUnknown();
    } else if (selection) {
      setSelectedCat(selection);
    } else {
      return;
    }
    void navigate("/scan");
  };

  if (isPending) {
    return (
      <div className="flex flex-col gap-3 px-4 pt-4 lg:px-0 lg:pt-0">
        <SkeletonLoader shape="card" className="h-16" />
        <SkeletonLoader shape="card" className="h-16" />
        <SkeletonLoader shape="card" className="h-16" />
      </div>
    );
  }

  if (isError) {
    return (
      <ErrorState
        title={t("selectCat.loadError")}
        onRetry={() => {
          void refetch();
        }}
        retryLabel={t("actions.retry", { ns: "common" })}
      />
    );
  }

  if (cats.length === 0) {
    return (
      <EmptyState
        icon={<UserPlus className="size-6" aria-hidden="true" />}
        title={t("selectCat.emptyTitle")}
        description={t("selectCat.emptyDescription")}
        action={
          <Button
            variant="primary"
            onClick={() => {
              void navigate("/cats/new");
            }}
          >
            {t("selectCat.goAddCat")}
          </Button>
        }
      />
    );
  }

  const confirmLabel =
    selection === "SHARED"
      ? t("selectCat.confirmCtaShared")
      : selection
        ? t("selectCat.confirmCtaNamed", { name: selection.name })
        : t("selectCat.confirmCtaGeneric");

  const confirmButton = (
    <Button variant="primary" size="lg" className="w-full" disabled={!selection} onClick={handleConfirm}>
      <span className="truncate">{confirmLabel}</span>
    </Button>
  );

  return (
    <div className="mx-auto flex w-full max-w-[480px] flex-col gap-4 px-4 pb-24 pt-4 lg:max-w-none lg:px-0 lg:pb-0 lg:pt-0">
      <header data-reveal className="flex flex-col gap-1">
        <h1 className="text-h3 font-bold leading-tight text-text-primary lg:text-h2">{t("selectCat.heading")}</h1>
        <p className="text-small text-text-secondary lg:text-caption">{t("selectCat.description")}</p>
      </header>

      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:gap-6">
        <div className="flex min-w-0 flex-1 flex-col gap-3">
          <div data-reveal className="lg:hidden">
            <InfoDisclosure
              icon={<ShieldCheck className="size-4" aria-hidden="true" />}
              title={t("selectCat.privacyNoteTitle")}
              body={t("selectCat.privacyNoteBody")}
            />
          </div>

          <div ref={listRef} role="radiogroup" aria-label={t("selectCat.heading")} className="flex flex-col gap-2">
            {cats.map((cat) => (
              <CatChoice
                key={cat.id}
                cat={cat}
                bands={bands ?? []}
                latestScan={latestByCat[cat.id]}
                selected={selection !== "SHARED" && selection?.id === cat.id}
                onSelect={() => {
                  setSelection({ id: cat.id, name: cat.name });
                }}
              />
            ))}
            <SharedChoice
              selected={selection === "SHARED"}
              onSelect={() => {
                setSelection("SHARED");
              }}
              icon={<CircleHelp className="size-5" aria-hidden="true" />}
            />
            <button
              type="button"
              data-reveal-item
              onClick={() => {
                void navigate("/cats/new");
              }}
              className="flex min-h-12 w-full items-center justify-center gap-2 rounded-2xl border border-dashed border-border px-3 text-small font-semibold text-primary-dark transition-colors hover:border-primary hover:bg-chip-bg/50"
            >
              <Plus className="size-4" aria-hidden="true" />
              {t("selectCat.addAnotherCat")}
            </button>
          </div>

          <div data-reveal className="lg:hidden">
            <InfoDisclosure
              icon={<Lightbulb className="size-4" aria-hidden="true" />}
              title={t("selectCat.multiCatTipTitle")}
              body={t("selectCat.multiCatTipBody")}
            />
          </div>
        </div>

        <aside data-reveal className="hidden w-[340px] shrink-0 flex-col gap-3 lg:sticky lg:top-6 lg:flex">
          <div className="flex items-start gap-3 rounded-2xl bg-surface p-4 shadow-xs">
            <ShieldCheck className="mt-0.5 size-5 shrink-0 text-primary" aria-hidden="true" />
            <span className="flex min-w-0 flex-col gap-0.5">
              <span className="text-caption font-bold text-text-primary">{t("selectCat.privacyNoteTitle")}</span>
              <span className="text-caption leading-snug text-text-secondary">{t("selectCat.privacyNoteBody")}</span>
            </span>
          </div>
          <div className="flex items-start gap-3 rounded-2xl bg-surface p-4 shadow-xs">
            <Lightbulb className="mt-0.5 size-5 shrink-0 text-secondary-text-on" aria-hidden="true" />
            <span className="flex min-w-0 flex-col gap-0.5">
              <span className="text-caption font-bold text-text-primary">{t("selectCat.multiCatTipTitle")}</span>
              <span className="text-caption leading-snug text-text-secondary">{t("selectCat.multiCatTipBody")}</span>
            </span>
          </div>
          {confirmButton}
          <Button
            variant="tertiary"
            className="w-full border-transparent"
            onClick={() => {
              void navigate(-1);
            }}
          >
            {t("selectCat.cancel")}
          </Button>
        </aside>
      </div>

      {/* Sits above the bottom nav and its raised scan button. */}
      <div
        className={cn(
          "fixed inset-x-0 bottom-[calc(92px+env(safe-area-inset-bottom))] z-[var(--z-sticky-header)] mx-auto w-full max-w-[480px] px-4 transition-[transform,opacity] duration-300 ease-out motion-reduce:transition-none lg:hidden",
          selection ? "translate-y-0 opacity-100" : "pointer-events-none translate-y-4 opacity-0",
        )}
        aria-hidden={!selection}
      >
        <div className="rounded-2xl bg-surface/95 p-2 shadow-brand-md">{confirmButton}</div>
      </div>
    </div>
  );
}
