import { useTranslation } from "react-i18next";
import { cn } from "@/shared/lib/cn";
import stepCheck from "@/shared/assets/icons/web-auth/step-check.svg";

/**
 * Thanh tiến trình 4 bước của luồng đăng ký — CHỈ dùng ở bản web (Figma 16:8430).
 * Bản mobile dùng `Stepper` (shared/ui) dạng 1 dòng + progress bar, khác hẳn bố cục.
 *
 * Đặt ở `pages/auth/` vì cả RegisterPage lẫn VerifyOtpPage đều cần, nhưng đây là bố cục
 * riêng của luồng đăng ký web nên chưa đủ phổ quát để nâng lên `shared/ui`.
 */
export function WebRegisterSteps({
  current,
  withHeader = false,
}: {
  current: 1 | 2 | 3 | 4;
  /**
   * Bước 1 (Figma Web-01c, thẻ 1184x228) có thêm hàng tiêu đề onboarding + chứng nhận và
   * thanh % phía dưới; các bước sau (Web-01c-2, thẻ 1184x104) chỉ có dải 4 bước.
   */
  withHeader?: boolean;
}) {
  const { t } = useTranslation("auth");
  const steps = [1, 2, 3, 4] as const;
  const percent = current * 25;

  return (
    <div className="hidden rounded-2xl bg-surface p-7 shadow-xs lg:block">
      {withHeader ? (
        <div className="mb-6 flex items-start justify-between gap-6">
          <div className="flex flex-col gap-2">
            <span className="flex w-fit items-center gap-2 rounded-full bg-chip-bg px-3 py-1">
              <span className="size-2 rounded-full bg-primary-dark" aria-hidden="true" />
              <span className="text-[11px] font-bold uppercase tracking-[0.55px] text-primary-dark">
                {t("web.steps.headerBadge")}
              </span>
            </span>
            <h2 className="text-[20px] font-extrabold leading-7 text-primary-dark">
              {t("web.steps.headerTitle")}
            </h2>
          </div>
          <span className="flex shrink-0 items-center gap-2 rounded-xl bg-background-alt px-3.5 py-2">
            <img src={stepCheck} alt="" className="h-[10.021px] w-[13.583px]" />
            <span className="text-[11px] font-semibold tracking-[0.4px] text-text-secondary">
              {t("web.steps.headerCert")}
            </span>
          </span>
        </div>
      ) : null}

      <div className="flex items-center justify-center gap-4">
        {steps.map((step) => {
          const done = step < current;
          const active = step === current;
          return (
            <div
              key={step}
              className={cn(
                "flex flex-1 items-center gap-4",
                active && "rounded-lg bg-deco-backdrop p-2",
                withHeader && "rounded-xl p-3",
                withHeader && active && "bg-chip-bg",
                withHeader && !active && "bg-background-alt",
                done && "opacity-90",
                !done && !active && !withHeader && "opacity-60",
              )}
            >
              <span
                className={cn(
                  "flex size-10 shrink-0 items-center justify-center rounded-full",
                  done && "bg-[rgb(0,101,43)] shadow-xs",
                  active && "bg-primary shadow-[0px_4px_6px_-1px_rgba(0,0,0,0.1)]",
                  !done && !active && "bg-chip-bg",
                )}
              >
                {done ? (
                  <img src={stepCheck} alt="" className="h-[10.021px] w-[13.583px]" />
                ) : (
                  <span
                    className={cn(
                      "text-[18px] font-bold leading-6",
                      active ? "text-white" : "text-nav-inactive",
                    )}
                  >
                    {step}
                  </span>
                )}
              </span>

              <span className="flex min-w-0 flex-col">
                <span className="flex items-center gap-1.5">
                  <span
                    className={cn(
                      "text-[11px] font-bold uppercase",
                      done && "tracking-[0.55px] text-verified-deep",
                      active && "tracking-[0.55px] text-primary-dark",
                      !done && !active && "font-semibold tracking-[0.4px] text-nav-inactive",
                    )}
                  >
                    {t(`web.steps.s${String(step)}Label`)}
                  </span>
                  {done ? (
                    <span className="rounded-full bg-[rgba(0,74,29,0.1)] px-2 py-0.5 text-[10px] leading-[15px] text-verified-deep">
                      {t("web.steps.done")}
                    </span>
                  ) : null}
                  {active ? (
                    <span className="rounded-full bg-primary-dark px-2 py-0.5 text-[10px] leading-[15px] text-white">
                      {t("web.steps.current")}
                    </span>
                  ) : null}
                </span>
                <span
                  className={cn(
                    "truncate text-[14px] font-bold leading-5 tracking-[0.2px]",
                    active ? "text-primary-dark" : "text-text-primary",
                  )}
                >
                  {t(`web.steps.s${String(step)}Name`)}
                </span>
              </span>
            </div>
          );
        })}
      </div>

      {withHeader ? (
        <div
          className="mt-6 h-2 w-full overflow-hidden rounded-full bg-chip-bg"
          role="progressbar"
          aria-valuenow={percent}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-label={t("web.steps.progressLabel")}
        >
          <div className="h-full rounded-full bg-primary" style={{ width: `${String(percent)}%` }} />
        </div>
      ) : null}
    </div>
  );
}
