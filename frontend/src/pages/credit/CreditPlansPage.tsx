import "./credit.css";
import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import type { TFunction } from "i18next";
import { Link, useNavigate } from "react-router";
import { useGSAP } from "@gsap/react";
import { gsap } from "gsap";
import {
  ArrowLeft,
  ArrowRight,
  BellRing,
  Cat,
  Check,
  FileDown,
  Gift,
  Leaf,
  ShoppingBag,
  Sparkles,
  Sprout,
  TrendingUp,
  X,
  type LucideIcon,
} from "lucide-react";
import { Badge, Button, EmptyState, ErrorState, SkeletonLoader } from "@/shared/ui";
import { featureFlags } from "@/shared/config/featureFlags";
import { cn } from "@/shared/lib/cn";
import { useEntitlement, usePackageCatalog, type PackagePlan } from "@/features/credit";

const PLAN_ICONS: Partial<Record<string, LucideIcon>> = {
  MINI: Sprout,
  DAILY: Leaf,
  PLUS: Sparkles,
  MULTI: Cat,
  CARE_BOX: Gift,
};

function reducedMotion() {
  return typeof window.matchMedia === "function" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

function scrollToPlan(track: HTMLElement | null, index: number, behavior: ScrollBehavior) {
  const card = track?.children[index] as HTMLElement | undefined;
  if (!track || !card) return;
  track.scrollTo({
    left: card.offsetLeft - (track.clientWidth - card.clientWidth) / 2,
    behavior: reducedMotion() ? "auto" : behavior,
  });
}

function planFeatures(plan: PackagePlan, t: TFunction<"credit">) {
  return [
    {
      label:
        // The API omits maxCatProfiles when the plan has no limit.
        typeof plan.maxCatProfiles === "number"
          ? t("activate.perks.cats", { count: plan.maxCatProfiles })
          : t("activate.perks.catsUnlimited"),
      included: true,
    },
    {
      label: plan.historyLevel === "ADVANCED" ? t("activate.perks.historyAdvanced") : t("activate.perks.historyBasic"),
      included: plan.historyLevel !== "NONE",
    },
    { label: t("activate.perks.storeImage"), included: plan.storeImage },
    { label: t("activate.perks.trend"), included: plan.hasTrend },
    { label: t("activate.perks.reminder"), included: plan.hasReminder },
    { label: t("activate.perks.export"), included: plan.hasExport },
  ];
}

export function CreditPlansPage() {
  const { t } = useTranslation(["credit", "common"]);
  const navigate = useNavigate();
  const packagesQuery = usePackageCatalog();
  const entitlementQuery = useEntitlement();
  const plans = useMemo(() => packagesQuery.data ?? [], [packagesQuery.data]);
  const currentCode = entitlementQuery.data?.currentPackage ?? null;

  const rootRef = useRef<HTMLDivElement>(null);
  const trackRef = useRef<HTMLUListElement>(null);
  const [active, setActive] = useState(0);
  const centeredOnCurrent = useRef(false);

  // Open on the user's current plan, once.
  useEffect(() => {
    if (centeredOnCurrent.current || plans.length === 0 || entitlementQuery.isPending) return;
    centeredOnCurrent.current = true;
    const index = plans.findIndex((plan) => plan.code === currentCode);
    if (index > 0) {
      scrollToPlan(trackRef.current, index, "auto");
      setActive(index);
    }
  }, [plans, currentCode, entitlementQuery.isPending]);

  // The card closest to the carousel's centre is the active one.
  useEffect(() => {
    const track = trackRef.current;
    if (!track || plans.length === 0) return;
    let frame = 0;
    const update = () => {
      frame = 0;
      const center = track.scrollLeft + track.clientWidth / 2;
      let best = 0;
      let bestDistance = Infinity;
      Array.from(track.children).forEach((card, index) => {
        const el = card as HTMLElement;
        const distance = Math.abs(el.offsetLeft + el.clientWidth / 2 - center);
        if (distance < bestDistance) {
          best = index;
          bestDistance = distance;
        }
      });
      setActive(best);
    };
    const onScroll = () => {
      if (frame === 0) frame = requestAnimationFrame(update);
    };
    track.addEventListener("scroll", onScroll, { passive: true });
    return () => {
      track.removeEventListener("scroll", onScroll);
      cancelAnimationFrame(frame);
    };
  }, [plans.length]);

  const ready = !packagesQuery.isPending;

  // Entrance: copy rises, perk icons pop, cards slide in from the right.
  useGSAP(
    () => {
      if (!ready) return;
      const media = gsap.matchMedia();
      media.add("(prefers-reduced-motion: no-preference)", () => {
        const tl = gsap.timeline({ defaults: { ease: "power3.out" } });
        const step = (selector: string, vars: gsap.TweenVars, position?: string) => {
          const targets = gsap.utils.toArray<HTMLElement>(selector, rootRef.current);
          if (targets.length > 0) tl.from(targets, vars, position);
        };
        step("[data-plans-intro]", { autoAlpha: 0, y: 14, duration: 0.5, stagger: 0.07 });
        step(
          "[data-plans-perk]",
          { autoAlpha: 0, y: 6, scale: 0.6, duration: 0.45, stagger: 0.07, ease: "back.out(1.8)" },
          "-=0.3",
        );
        step("[data-plan-card]", { autoAlpha: 0, x: 56, rotation: 2, duration: 0.6, stagger: 0.08 }, "-=0.25");
        step("[data-plans-footer]", { autoAlpha: 0, y: 8, duration: 0.35 }, "-=0.35");
      });
      return () => {
        media.revert();
      };
    },
    { scope: rootRef, dependencies: [ready] },
  );

  // Active plan: background glow shifts to its hue, its icon spins in, its checklist ticks through.
  useGSAP(
    () => {
      const root = rootRef.current;
      const track = trackRef.current;
      if (!root || !track) return;
      const cards = gsap.utils.toArray<HTMLElement>("[data-plan-card]", track);
      const activeCard = cards.at(active);
      if (!activeCard) return;
      const glow = getComputedStyle(activeCard).getPropertyValue("--plan-soft").trim();
      const shells = cards.map((card) => card.querySelector("[data-plan-shell]"));

      if (reducedMotion()) {
        gsap.set(root, { "--plans-glow": glow });
        return;
      }
      gsap.to(root, { "--plans-glow": glow, duration: 0.6, ease: "sine.out", overwrite: "auto" });
      shells.forEach((shell, index) => {
        if (!shell) return;
        gsap.to(shell, {
          scale: index === active ? 1 : 0.94,
          opacity: index === active ? 1 : 0.7,
          duration: 0.45,
          ease: "power3.out",
          overwrite: "auto",
        });
      });
      gsap.fromTo(
        activeCard.querySelector("[data-plan-orb]"),
        { scale: 0.6, rotation: -40 },
        { scale: 1, rotation: 0, duration: 0.7, ease: "back.out(2.2)", overwrite: "auto" },
      );
      gsap.fromTo(
        activeCard.querySelectorAll("[data-plan-feature]"),
        { x: -8, autoAlpha: 0.35 },
        { x: 0, autoAlpha: 1, duration: 0.3, stagger: 0.04, ease: "power2.out", overwrite: "auto" },
      );
    },
    { scope: rootRef, dependencies: [active, plans.length] },
  );

  const perks = [
    { icon: TrendingUp, label: t("activate.perks.trend") },
    { icon: BellRing, label: t("activate.perks.reminder") },
    { icon: FileDown, label: t("activate.perks.export") },
  ];

  return (
    <div
      ref={rootRef}
      data-motion-scene
      className="plans-stage mx-auto flex w-full max-w-5xl flex-col gap-7 overflow-x-clip px-4 pb-10 pt-4 lg:px-0 lg:pt-0"
    >
      <Link
        to="/credits"
        data-plans-intro
        className="inline-flex min-h-11 w-fit items-center gap-1.5 rounded-full text-caption font-semibold text-text-secondary transition-colors hover:text-text-primary"
      >
        <ArrowLeft className="size-4" aria-hidden="true" />
        {t("pages.credits.title")}
      </Link>

      <header className="-mt-3 flex flex-col items-center gap-3 text-center">
        <span
          data-plans-intro
          className="rounded-full bg-surface/80 px-3 py-1 text-small font-semibold tracking-wide text-text-secondary shadow-xs ring-1 ring-border/60"
        >
          {t("plans.eyebrow")}
        </span>
        <h1
          data-plans-intro
          className="max-w-[20ch] text-balance text-h1 font-extrabold tracking-tight text-text-primary lg:text-[2.25rem] lg:leading-[2.75rem]"
        >
          {t("plans.title")}
        </h1>
        <p data-plans-intro className="max-w-md text-pretty text-caption text-text-secondary">
          {t("plans.subtitle")}
        </p>
      </header>

      <ul
        className="mx-auto grid w-full max-w-md grid-cols-3 divide-x divide-border/70"
        aria-label={t("plans.perksLabel")}
      >
        {perks.map(({ icon: Icon, label }) => (
          <li key={label} data-plans-perk className="flex flex-col items-center gap-2 px-2 text-center">
            <span className="flex size-11 items-center justify-center rounded-2xl bg-surface text-primary-dark shadow-brand-md ring-1 ring-border/50">
              <Icon className="size-5" aria-hidden="true" />
            </span>
            <span className="text-small font-semibold text-text-secondary">{label}</span>
          </li>
        ))}
      </ul>

      {packagesQuery.isPending ? (
        <div className="flex justify-center gap-4">
          <SkeletonLoader shape="card" className="h-[460px] w-[min(80vw,320px)] rounded-[30px]" />
        </div>
      ) : packagesQuery.isError ? (
        <ErrorState
          title={t("catalog.errorTitle")}
          onRetry={() => {
            void packagesQuery.refetch();
          }}
          retryLabel={t("actions.retry", { ns: "common" })}
        />
      ) : plans.length === 0 ? (
        <EmptyState title={t("catalog.emptyTitle")} description={t("catalog.emptyDescription")} />
      ) : (
        <>
          <ul
            ref={trackRef}
            aria-label={t("plans.listLabel")}
            className="plans-track relative -mx-4 flex snap-x snap-mandatory items-stretch gap-4 overflow-x-auto py-4 lg:mx-0"
          >
            {plans.map((plan, index) => {
              const Icon = PLAN_ICONS[plan.code] ?? Sparkles;
              const isCurrent = plan.code === currentCode;
              return (
                <li
                  key={plan.code}
                  data-plan={plan.code}
                  data-plan-card
                  data-index={index}
                  className="plans-card shrink-0 snap-center"
                >
                  <div data-plan-shell className="plan-shell h-full">
                    <article className="plan-core flex h-full flex-col items-center gap-4 px-5 pb-5 pt-7 text-center">
                      {isCurrent ? (
                        <Badge tone="brand" className="absolute right-4 top-4">
                          {t("catalog.currentBadge")}
                        </Badge>
                      ) : null}
                      <span data-plan-orb className="plan-orb" aria-hidden="true">
                        <Icon className="size-8" strokeWidth={2.25} />
                      </span>
                      <div className="flex flex-col gap-1">
                        <h2 className="text-h2 font-extrabold tracking-tight text-text-primary">{plan.name}</h2>
                        <p className="text-caption text-text-secondary">
                          {t("catalog.weight", { value: plan.weightKg })}
                          {" · "}
                          {t("catalog.validity", { days: plan.creditValidityDays })}
                        </p>
                      </div>
                      <p className="flex items-baseline gap-1.5">
                        <span className="text-[2.75rem] font-extrabold leading-none tracking-tight text-text-primary tabular-nums">
                          {plan.creditAmount}
                        </span>
                        <span className="text-caption font-semibold text-text-secondary">{t("balance.unit")}</span>
                      </p>
                      <span className="plan-ornament w-full" aria-hidden="true">
                        <Icon className="size-3.5" />
                      </span>
                      <ul className="flex w-full flex-col gap-2.5 text-left">
                        {planFeatures(plan, t).map(({ label, included }) => (
                          <li key={label} data-plan-feature className="flex items-center gap-2.5">
                            <span
                              className={cn(
                                "flex size-5 shrink-0 items-center justify-center rounded-full",
                                included ? "bg-text-primary text-surface" : "bg-chip-bg text-text-tertiary",
                              )}
                              aria-hidden="true"
                            >
                              {included ? (
                                <Check className="size-3" strokeWidth={3} />
                              ) : (
                                <X className="size-3" strokeWidth={3} />
                              )}
                            </span>
                            <span
                              className={cn(
                                "text-caption",
                                included ? "font-semibold text-text-primary" : "text-text-tertiary",
                              )}
                            >
                              {label}
                              {included ? null : <span className="sr-only"> ({t("plans.notIncluded")})</span>}
                            </span>
                          </li>
                        ))}
                      </ul>
                      <Button
                        type="button"
                        size="lg"
                        className="mt-auto w-full rounded-full bg-text-primary hover:bg-primary-dark"
                        onClick={() => {
                          void navigate("/credits/activate");
                        }}
                      >
                        {t("plans.cta", { name: plan.name })}
                      </Button>
                    </article>
                  </div>
                </li>
              );
            })}
          </ul>

          <div data-plans-footer className="flex justify-center gap-1">
            {plans.map((plan, index) => (
              <button
                key={plan.code}
                type="button"
                data-plan={plan.code}
                aria-label={t("plans.showPlan", { name: plan.name })}
                aria-current={index === active ? "true" : undefined}
                className="flex size-8 items-center justify-center rounded-full"
                onClick={() => {
                  scrollToPlan(trackRef.current, index, "smooth");
                }}
              >
                <span className="plans-dot" data-active={index === active} />
              </button>
            ))}
          </div>
        </>
      )}

      {featureFlags.shop ? (
        <p
          data-plans-footer
          className="flex flex-wrap items-center justify-center gap-x-2 gap-y-1 text-center text-caption text-text-secondary"
        >
          <ShoppingBag className="size-4 text-text-tertiary" aria-hidden="true" />
          {t("activate.shopPrompt")}
          <Link
            to="/shop"
            className="inline-flex items-center gap-1 font-semibold text-primary underline-offset-4 hover:underline"
          >
            {t("activate.shopCta")}
            <ArrowRight className="size-3.5" aria-hidden="true" />
          </Link>
        </p>
      ) : null}
    </div>
  );
}
