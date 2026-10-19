import "./credit.css";
import { useRef } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router";
import { useGSAP } from "@gsap/react";
import { gsap } from "gsap";
import { ArrowUpRight, Clock, List as ListIcon, Package, Plus } from "lucide-react";
import { Badge, Button, EmptyState, ErrorState, SkeletonLoader } from "@/shared/ui";
import { useListReveal } from "@/shared/lib/motion/usePageReveal";
import { WalletCat } from "./WalletCat";
import {
  CreditBatchCard,
  EntitlementFeatureList,
  LedgerEntryRow,
  formatRemainingSeconds,
  useCreditBalance,
  useCreditLedger,
  useEntitlement,
  usePackageCatalog,
} from "@/features/credit";

export function CreditsPage() {
  const { t } = useTranslation(["credit", "common"]);
  const navigate = useNavigate();

  const balanceQuery = useCreditBalance();
  const ledgerQuery = useCreditLedger();
  const entitlementQuery = useEntitlement();
  const packagesQuery = usePackageCatalog();
  const planName = (code: string | null): string =>
    (code ? packagesQuery.data?.find((plan) => plan.code === code)?.name : undefined) ?? code ?? "";

  const rootRef = useRef<HTMLDivElement>(null);
  const balanceRef = useRef<HTMLSpanElement>(null);
  const ledgerRef = useRef<HTMLUListElement>(null);
  const shownBalance = useRef(0);

  const balance = balanceQuery.data;
  const available = balance?.availableBalance ?? 0;
  const nearest = balance?.batches[0];
  const ledgerEntries = ledgerQuery.data?.pages.flatMap((page) => page.entries) ?? [];

  const goActivate = () => {
    void navigate("/credits/activate");
  };

  // Entrance: wallet lifts in, then the other sections follow.
  useGSAP(
    () => {
      const media = gsap.matchMedia();
      media.add("(prefers-reduced-motion: no-preference)", () => {
        const tl = gsap.timeline({ defaults: { ease: "power3.out" } });
        tl.from("[data-credits-wallet]", { autoAlpha: 0, y: 18, scale: 0.98, duration: 0.55 });
        if (rootRef.current?.querySelector("[data-wallet-mascot]")) {
          tl.from(
            "[data-wallet-mascot]",
            { scale: 0.6, rotation: -14, duration: 0.9, ease: "elastic.out(1, 0.5)" },
            "-=0.35",
          ).to(
            "[data-wallet-mascot]",
            { rotation: 4, duration: 0.14, yoyo: true, repeat: 3, ease: "sine.inOut" },
            "-=0.2",
          );
        }
        tl.from("[data-credits-section]", { autoAlpha: 0, y: 14, duration: 0.45, stagger: 0.08 }, "-=0.3");
      });
      return () => {
        media.revert();
      };
    },
    { scope: rootRef },
  );

  // Balance counts up from the last shown value; batch bars and meter segments fill from the left.
  useGSAP(
    () => {
      if (!balance) return;
      const media = gsap.matchMedia();
      media.add("(prefers-reduced-motion: no-preference)", () => {
        const counter = { value: shownBalance.current };
        if (balanceRef.current) balanceRef.current.textContent = String(counter.value);
        gsap.to(counter, {
          value: available,
          duration: 0.9,
          ease: "power2.out",
          onUpdate: () => {
            if (balanceRef.current) balanceRef.current.textContent = String(Math.round(counter.value));
          },
        });
        const bars = gsap.utils.toArray<HTMLElement>(".batch-bar-fill", rootRef.current);
        if (bars.length > 0) {
          gsap.from(bars, { scaleX: 0, duration: 0.8, ease: "expo.out", stagger: 0.06, delay: 0.15 });
        }
      });
      shownBalance.current = available;
      return () => {
        media.revert();
      };
    },
    { scope: rootRef, dependencies: [available, balance?.batches.length] },
  );

  useListReveal(ledgerRef, ledgerEntries.length);

  return (
    <div
      ref={rootRef}
      data-motion-scene
      className="mx-auto flex w-full max-w-2xl flex-col gap-6 px-4 pb-8 pt-5 lg:max-w-none lg:p-0"
    >
      <h1 className="text-h2 font-bold text-text-primary lg:text-h1">{t("pages.credits.title")}</h1>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1.35fr)_minmax(0,1fr)] lg:items-start">
        <div className="flex min-w-0 flex-col gap-6">
          {/* Wallet */}
          <section data-credits-wallet className="wallet-shell" aria-labelledby="credits-wallet-label">
            <div className="wallet-core flex flex-col gap-5 p-5 lg:p-6">
              {balanceQuery.isPending ? (
                <SkeletonLoader shape="card" className="h-48" />
              ) : balanceQuery.isError ? (
                <ErrorState
                  title={t("balance.errorTitle")}
                  onRetry={() => {
                    void balanceQuery.refetch();
                  }}
                  retryLabel={t("actions.retry", { ns: "common" })}
                />
              ) : (
                <>
                  <div className="flex items-center gap-4 sm:gap-6">
                    <div data-wallet-mascot className="wallet-mascot shrink-0">
                      <WalletCat className="size-full" />
                    </div>
                    <div className="flex min-w-0 flex-col gap-1">
                      <p id="credits-wallet-label" className="text-caption font-semibold text-text-secondary">
                        {t("balance.availableLabel")}
                      </p>
                      <p className="flex items-baseline gap-1.5">
                        <span
                          ref={balanceRef}
                          className="text-[3rem] font-extrabold leading-none tracking-tight text-primary-dark tabular-nums"
                        >
                          {available}
                        </span>
                        <span className="text-body font-semibold text-text-secondary">{t("balance.unit")}</span>
                      </p>
                      <p className="text-small text-text-tertiary">{t("wallet.creditHint")}</p>
                      {balanceQuery.data.trialScansRemaining > 0 ? (
                        <span className="w-fit rounded-full bg-secondary-light px-2.5 py-0.5 text-small font-bold text-secondary-text-on">
                          {t("balance.trialRemaining", { count: balanceQuery.data.trialScansRemaining })}
                        </span>
                      ) : null}
                    </div>
                  </div>

                  {nearest && nearest.remainingSeconds > 0 ? (
                    <div className="flex flex-col gap-2 rounded-2xl bg-surface/80 p-3 ring-1 ring-border/50">
                      <div className="h-2 w-full overflow-hidden rounded-full bg-chip-bg" aria-hidden="true">
                        <div
                          className="batch-bar-fill wallet-bar h-full rounded-full"
                          style={{
                            width: `${String(Math.round((nearest.remainingAmount / Math.max(1, nearest.initialAmount)) * 100))}%`,
                          }}
                        />
                      </div>
                      <p className="flex items-center gap-1.5 text-small text-text-secondary">
                        <Clock className="size-3.5 shrink-0 text-warning" aria-hidden="true" />
                        {t("balance.nearestExpiring", {
                          amount: nearest.remainingAmount,
                          remaining: formatRemainingSeconds(nearest.remainingSeconds, t),
                        })}
                      </p>
                    </div>
                  ) : null}

                  <div className="grid gap-2 md:grid-cols-2">
                    <Button
                      type="button"
                      className="rounded-full px-3"
                      leftIcon={<Plus className="size-4" />}
                      onClick={goActivate}
                    >
                      {t("activateCta")}
                    </Button>
                    <Link
                      to="/credits/plans"
                      className="inline-flex min-h-[var(--touch-target-min)] items-center justify-center gap-1.5 rounded-full bg-surface px-3 text-body font-semibold text-primary-dark ring-1 ring-border transition-colors hover:bg-background-alt"
                    >
                      {t("wallet.plansCta")}
                      <ArrowUpRight className="size-4" aria-hidden="true" />
                    </Link>
                  </div>
                </>
              )}
            </div>
          </section>

          {/* Batches */}
          {balance ? (
            <section data-credits-section className="flex flex-col gap-3">
              <h2 className="text-h3 font-semibold text-text-primary">{t("batch.sectionTitle")}</h2>
              {balance.batches.length === 0 ? (
                <div className="rounded-3xl bg-surface ring-1 ring-border/60">
                  <EmptyState
                    icon={<Package className="size-6" aria-hidden="true" />}
                    title={t("batch.emptyTitle")}
                    description={t("batch.emptyDescription")}
                    action={
                      <Button type="button" size="sm" className="rounded-full" onClick={goActivate}>
                        {t("activateCta")}
                      </Button>
                    }
                  />
                </div>
              ) : (
                <ul className="flex flex-col gap-2.5">
                  {balance.batches.map((batch) => (
                    <li key={batch.batchId}>
                      <CreditBatchCard batch={batch} />
                    </li>
                  ))}
                </ul>
              )}
            </section>
          ) : null}
        </div>

        <div className="flex min-w-0 flex-col gap-6">
          {/* Current plan */}
          <section data-credits-section className="flex flex-col gap-3">
            <h2 className="text-h3 font-semibold text-text-primary">{t("entitlement.sectionTitle")}</h2>
            <div className="rounded-3xl bg-surface p-5 shadow-brand-md ring-1 ring-border/60">
              {entitlementQuery.isPending ? (
                <SkeletonLoader shape="text" />
              ) : entitlementQuery.isError ? (
                <ErrorState
                  title={t("entitlement.errorTitle")}
                  onRetry={() => {
                    void entitlementQuery.refetch();
                  }}
                  retryLabel={t("actions.retry", { ns: "common" })}
                />
              ) : (
                <div className="flex flex-col gap-4">
                  <div className="flex items-start justify-between gap-3">
                    <p className="text-caption text-text-secondary">
                      {entitlementQuery.data.currentPackage
                        ? t("entitlement.currentPackage", { name: planName(entitlementQuery.data.currentPackage) })
                        : t("entitlement.noPackage")}
                    </p>
                    {entitlementQuery.data.currentPackage ? (
                      <Badge tone="brand" className="shrink-0">
                        {t("catalog.currentBadge")}
                      </Badge>
                    ) : null}
                  </div>
                  <EntitlementFeatureList entitlement={entitlementQuery.data} />
                  {entitlementQuery.data.currentPackage ? (
                    <p className="text-caption text-text-secondary">
                      {typeof entitlementQuery.data.maxCatProfiles === "number"
                        ? t("catalog.catsLimit", { count: entitlementQuery.data.maxCatProfiles })
                        : t("catalog.catsUnlimited")}
                    </p>
                  ) : null}
                  <Link
                    to="/credits/plans"
                    className="group -mx-1 inline-flex min-h-11 items-center justify-between gap-2 rounded-2xl bg-background-alt px-4 text-caption font-semibold text-primary-dark transition-colors hover:bg-chip-bg"
                  >
                    {t("wallet.plansCta")}
                    <ArrowUpRight
                      className="size-4 transition-transform duration-[var(--duration-base)] group-hover:-translate-y-px group-hover:translate-x-0.5"
                      aria-hidden="true"
                    />
                  </Link>
                </div>
              )}
            </div>
          </section>

          {/* Activity */}
          <section data-credits-section className="flex flex-col gap-3">
            <h2 className="text-h3 font-semibold text-text-primary">{t("ledger.sectionTitle")}</h2>
            {ledgerQuery.isPending ? (
              <SkeletonLoader shape="card" />
            ) : ledgerQuery.isError ? (
              <ErrorState
                title={t("ledger.errorTitle")}
                onRetry={() => {
                  void ledgerQuery.refetch();
                }}
                retryLabel={t("actions.retry", { ns: "common" })}
              />
            ) : ledgerEntries.length === 0 ? (
              <div className="rounded-3xl bg-surface ring-1 ring-border/60">
                <EmptyState
                  icon={<ListIcon className="size-6" aria-hidden="true" />}
                  title={t("ledger.emptyTitle")}
                  description={t("ledger.emptyDescription")}
                />
              </div>
            ) : (
              <div className="flex flex-col gap-2 rounded-3xl bg-surface p-2 ring-1 ring-border/60">
                <ul ref={ledgerRef} className="flex flex-col divide-y divide-border/60">
                  {ledgerEntries.map((entry) => (
                    <li key={entry.id}>
                      <LedgerEntryRow entry={entry} />
                    </li>
                  ))}
                </ul>
                {ledgerQuery.hasNextPage ? (
                  <Button
                    type="button"
                    variant="tertiary"
                    className="rounded-full border-0"
                    loading={ledgerQuery.isFetchingNextPage}
                    onClick={() => {
                      void ledgerQuery.fetchNextPage();
                    }}
                  >
                    {t("ledger.loadMore")}
                  </Button>
                ) : null}
              </div>
            )}
          </section>
        </div>
      </div>
    </div>
  );
}
