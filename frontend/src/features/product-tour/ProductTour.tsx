import { useCallback, useEffect, useMemo, useState } from "react";
import { useLocation } from "react-router";
import { useTranslation } from "react-i18next";
import { Button, Checkbox, ConfigProvider, Tour } from "antd";
import type { TourProps } from "antd";
import { useSessionStore } from "@/entities/user";
import { featureFlags } from "@/shared/config/featureFlags";
import { useBreakpoint } from "@/shared/lib/hooks/useBreakpoint";
import { useMediaQuery } from "@/shared/lib/hooks/useMediaQuery";
import { useCelebrationBusy } from "@/shared/lib/motion/celebration";
import { useSplashStore } from "@/shared/lib/motion/splash";
import { findTourTarget, resolveTourSteps, shouldAutoStartTour, statusAfterClose, type ResolvedTourStep } from "./logic";
import { TOUR_STEPS } from "./steps";
import { useTourStatus, writeTourStatus } from "./storage";
import { TOUR_Z_INDEX, tourTheme } from "./tourTheme";

/** Chờ trang Trang chủ dựng xong (lazy page + hiệu ứng vào nav) rồi mới đo phần tử. */
const START_DELAY_MS = 700;

const PANEL_STYLE = { width: "min(360px, calc(100vw - 32px))" } as const;

/** Đo lại mỗi lần hiện bước; phần tử biến mất => rc-tour tự đặt panel giữa màn hình. */
function targetGetter(target: string | null): TourStepTarget {
  if (target === null) return null;
  return (() => findTourTarget(target)) as () => HTMLElement;
}

type TourStepTarget = NonNullable<TourProps["steps"]>[number]["target"];

interface ActiveTour {
  isDesktop: boolean;
  steps: ResolvedTourStep[];
}

/**
 * Hướng dẫn tính năng lần đầu (coach marks) cho tài khoản người mua. Gắn trong AppLayout; tự bật ở
 * /dashboard khi trạng thái là "pending" và brand splash đã ẩn.
 */
export function ProductTour() {
  const { t } = useTranslation(["tour", "common"]);
  const user = useSessionStore((s) => s.user);
  const userId = user?.id ?? null;
  const { pathname } = useLocation();
  const splashPhase = useSplashStore((s) => s.phase);
  const celebrationBusy = useCelebrationBusy();
  const status = useTourStatus(userId);
  const isDesktop = useBreakpoint("lg");
  const reducedMotion = useMediaQuery("(prefers-reduced-motion: reduce)");

  const [active, setActive] = useState<ActiveTour | null>(null);
  const [current, setCurrent] = useState(0);
  const [dontShowAgain, setDontShowAgain] = useState(false);

  const eligible = shouldAutoStartTour({ user, pathname, splashPhase, status, celebrationBusy });
  const fresh = active !== null && active.isDesktop === isDesktop;

  useEffect(() => {
    if (!eligible || fresh) return;
    const timer = window.setTimeout(() => {
      const steps = resolveTourSteps(TOUR_STEPS, {
        isDesktop,
        flags: featureFlags,
        hasTarget: (target) => findTourTarget(target) !== null,
      });
      setCurrent(0);
      setDontShowAgain(false);
      setActive(steps.length > 0 ? { isDesktop, steps } : null);
    }, START_DELAY_MS);
    return () => {
      window.clearTimeout(timer);
    };
  }, [eligible, fresh, isDesktop]);

  const close = useCallback(() => {
    writeTourStatus(userId, statusAfterClose(dontShowAgain));
    setActive(null);
    setCurrent(0);
  }, [userId, dontShowAgain]);

  const steps = useMemo<NonNullable<TourProps["steps"]>>(() => {
    if (!active) return [];
    const total = active.steps.length;
    return active.steps.map((step, index) => ({
      title: t(step.titleKey),
      description: (
        <div className="flex flex-col gap-3 pt-1">
          <p className="text-caption leading-relaxed text-text-secondary">{t(step.bodyKey)}</p>
          <Checkbox
            checked={dontShowAgain}
            onChange={(event) => {
              setDontShowAgain(event.target.checked);
            }}
          >
            <span className="text-caption text-text-secondary">{t("tour:actions.dontShowAgain")}</span>
          </Checkbox>
        </div>
      ),
      target: targetGetter(step.target),
      placement: step.placement,
      style: PANEL_STYLE,
      nextButtonProps: { children: index === total - 1 ? t("tour:actions.finish") : t("tour:actions.next") },
      prevButtonProps: { children: t("tour:actions.back") },
    }));
  }, [active, dontShowAgain, t]);

  const open = eligible && fresh && steps.length > 0;
  if (!open) return null;

  return (
    <ConfigProvider theme={tourTheme(reducedMotion)}>
      <Tour
        open
        steps={steps}
        current={current}
        onChange={setCurrent}
        onClose={close}
        onFinish={close}
        zIndex={TOUR_Z_INDEX}
        animated={!reducedMotion}
        disabledInteraction
        scrollIntoViewOptions={reducedMotion ? { block: "nearest" } : { behavior: "smooth", block: "nearest" }}
        indicatorsRender={(index, total) => (
          <span className="text-caption font-semibold text-text-tertiary">
            {t("tour:progress", { current: index + 1, total })}
          </span>
        )}
        actionsRender={(originNode, info) => (
          <>
            {info.current < info.total - 1 ? (
              <Button size="small" type="text" onClick={close}>
                {t("tour:actions.skip")}
              </Button>
            ) : null}
            {originNode}
          </>
        )}
      />
    </ConfigProvider>
  );
}
