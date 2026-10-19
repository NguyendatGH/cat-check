import { useEffect, useState } from "react";
import { useLocation } from "react-router";
import { useTranslation } from "react-i18next";
import { useSessionStore } from "@/entities/user";
import { celebrate, useCelebrationStore } from "@/shared/lib/motion/celebration";
import { useSplashStore } from "@/shared/lib/motion/splash";
import { useCreditBalance } from "./hooks";
import { batchValidityDays, findWelcomeBatch, markWelcomeCelebrated, shouldCheckWelcome } from "./welcome";

const HOLD_KEY = "welcome-bonus";

/**
 * Pháo hoa chào mừng quà đăng ký (lô WELCOME) — một lần mỗi tài khoản, ở Trang chủ sau onboarding và
 * sau brand splash. Giữ khoá "sắp ăn mừng" trong lúc tải số dư để product tour đợi tới sau pháo hoa.
 */
export function WelcomeBonusCelebration() {
  const { t } = useTranslation("credit");
  const user = useSessionStore((s) => s.user);
  const { pathname } = useLocation();
  const splashPhase = useSplashStore((s) => s.phase);
  const [checkedFor, setCheckedFor] = useState<string | null>(null);

  const eligible = shouldCheckWelcome({ user, pathname, splashPhase }) && checkedFor !== user?.id;
  const balance = useCreditBalance(eligible);

  useEffect(() => {
    if (!eligible) return;
    const { hold, release } = useCelebrationStore.getState();
    hold(HOLD_KEY);
    return () => {
      release(HOLD_KEY);
    };
  }, [eligible]);

  const userId = user?.id ?? null;
  const { data, isError, isSuccess } = balance;

  useEffect(() => {
    if (!eligible || userId === null) return;
    if (isError) {
      setCheckedFor(userId);
      return;
    }
    if (!isSuccess) return;
    const batch = findWelcomeBatch(data);
    setCheckedFor(userId);
    if (!batch) return;
    markWelcomeCelebrated(userId);
    celebrate({
      title: t("welcome.title"),
      message: t("welcome.message", { credits: batch.initialAmount, days: batchValidityDays(batch) }),
      durationMs: 3200,
    });
  }, [eligible, userId, data, isError, isSuccess, t]);

  return null;
}
