import { useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router";
import welcomeLogo from "@/shared/assets/images/welcome-logo-lockup.png";
import welcomeCat from "@/shared/assets/images/welcome-hero-cat.png";
import iconShield from "@/shared/assets/icons/welcome/shield.svg";
import iconScan from "@/shared/assets/icons/welcome/feature-scan.svg";
import iconPh from "@/shared/assets/icons/welcome/feature-ph.svg";
import iconJournal from "@/shared/assets/icons/welcome/feature-journal.svg";
import decoSparkle from "@/shared/assets/icons/welcome/deco-sparkle.svg";
import decoPaw from "@/shared/assets/icons/welcome/deco-paw.svg";
import decoHeart from "@/shared/assets/icons/welcome/deco-heart.svg";
import arrowRight from "@/shared/assets/icons/welcome/arrow-right.svg";

/**
 * `/` — Màn hình Chào mừng (Splash / Welcome).
 *
 * Dựng từ design context THẬT của Figma qua MCP (file `26mOVF2zdu4cI1EPz2Syxw`,
 * node `16:7774`, khung 390×912.25) — KHÔNG phải từ ảnh PNG export. Bản trước dựng theo
 * PNG 406×986 nên sai tỉ lệ, sai cỡ chữ và thiếu hẳn lớp trang trí (2 nền bo tròn xoay nhẹ
 * sau ảnh hero, 3 huy hiệu nổi, 2 vầng sáng blur).
 *
 * 10 asset (logo, ảnh mèo, 8 icon SVG) tải thẳng từ Figma và dùng nguyên vẹn — không thay
 * bằng icon lucide tương đương, theo yêu cầu của skill figma-design-to-code.
 *
 * Bố cục gốc dùng absolute trong stack cao 896.25px; ở đây chuyển sang flow (padding/gap
 * giữ đúng số đo Figma) để co giãn được, vì đây là web responsive chứ không phải khung cố định.
 * Câu chữ khớp `spec/reference/screens/M1-mobile-auth-home-scan.md` §1.
 */
export function HomePage() {
  const { t } = useTranslation("common");
  const navigate = useNavigate();

  return (
    <div className="relative min-h-dvh overflow-hidden bg-background px-4 pb-4 lg:flex lg:items-center lg:justify-center lg:px-8 lg:py-8">
      {/* Vầng sáng nền (Figma 16:7778 / 16:7777) */}
      <div
        className="pointer-events-none absolute -right-[5px] top-[157px] size-64 rounded-full bg-info/35 blur-[32px]"
        aria-hidden="true"
      />
      <div
        className="pointer-events-none absolute left-[19px] top-[20px] size-56 rounded-full bg-secondary/20 blur-[32px]"
        aria-hidden="true"
      />

      <div className="relative mx-auto flex w-full max-w-[390px] flex-col pt-2 lg:grid lg:max-w-[1200px] lg:grid-cols-[0.85fr_1.15fr] lg:gap-x-12 lg:gap-y-5 lg:rounded-[32px] lg:bg-surface lg:p-12 lg:pt-12 lg:shadow-[0px_25px_60px_-24px_rgba(47,79,178,0.35)]">
        {/* Status / Compliance Pill (16:7781) */}
        <div className="flex w-fit items-center gap-1.5 rounded-full bg-chip-bg px-3.5 py-1 shadow-xs lg:col-start-1 lg:row-start-1 lg:self-end">
          <img src={iconShield} alt="" className="h-[11.667px] w-[9.333px] shrink-0" />
          <p className="text-center text-[11px] font-semibold leading-[14px] tracking-[0.4px] text-primary-dark">
            {t("welcome.disclaimer")}
          </p>
        </div>

        {/* Brand Logo Lockup (16:7787) */}
        <div className="flex flex-col items-center pt-3 lg:col-start-1 lg:row-start-2 lg:items-start lg:pt-0">
          <img src={welcomeLogo} alt={t("app.name")} className="h-16 w-[96.09px] object-contain" />
          <div className="flex items-center justify-center gap-1 pt-2 opacity-90 lg:justify-start">
            <span className="text-[11px] font-semibold uppercase leading-[14px] tracking-[0.55px] text-primary-dark">
              {t("welcome.sloganLeft")}
            </span>
            <span className="text-[12px] uppercase leading-4 tracking-[0.55px] text-danger">❤</span>
            <span className="text-[11px] font-semibold uppercase leading-[14px] tracking-[0.55px] text-primary-dark">
              {t("welcome.sloganRight")}
            </span>
          </div>
          <p className="pt-0.5 text-[11px] font-semibold italic leading-[14px] text-text-secondary">
            {t("welcome.sloganVi")}
          </p>
        </div>

        {/* Cat Hero Stage with Soft Organic Framing (16:7829) */}
        <div className="relative mx-auto mt-4 flex w-full max-w-[320px] items-center justify-center py-[15px] lg:col-start-2 lg:row-span-6 lg:row-start-1 lg:mt-0 lg:h-full lg:max-w-none lg:py-0">
          <div
            className="pointer-events-none absolute inset-x-[-2.56px] inset-y-[-2.77px] -rotate-1 rounded-[40px] bg-deco-backdrop shadow-[inset_0px_2px_4px_0px_rgba(0,0,0,0.05)]"
            aria-hidden="true"
          />
          <div
            className="pointer-events-none absolute inset-[2.78px_3.21px] rotate-2 rounded-[35.2px] bg-secondary-light/30"
            aria-hidden="true"
          />

          {/* Hero Cat Image Frame (16:7843) */}
          <div className="relative aspect-[294.39/266.39] w-full max-w-[294.39px] overflow-hidden rounded-[32px] bg-surface shadow-[0px_10px_15px_-3px_rgba(0,0,0,0.1),0px_4px_6px_-4px_rgba(0,0,0,0.1)] lg:aspect-[1/0.9] lg:max-w-[540px] lg:rounded-[40px]">
            <img src={welcomeCat} alt="" className="size-full object-cover" />
            <div
              className="absolute inset-x-0 bottom-0 h-12 bg-gradient-to-t from-primary-dark/10 to-transparent"
              aria-hidden="true"
            />
          </div>

          {/* 3 huy hiệu nổi (16:7832 / 16:7835 / 16:7839) */}
          <span
            className="absolute -top-1 left-4 flex size-7 items-center justify-center rounded-full bg-secondary/80 shadow-sm"
            aria-hidden="true"
          >
            <img src={decoSparkle} alt="" className="h-[13.333px] w-[7.85px]" />
          </span>
          <span
            className="absolute -right-2 top-10 flex size-8 items-center justify-center rounded-full bg-primary shadow-[0px_4px_6px_-1px_rgba(0,0,0,0.1),0px_2px_4px_-2px_rgba(0,0,0,0.1)]"
            aria-hidden="true"
          >
            <img src={decoPaw} alt="" className="h-[14.25px] w-[15px]" />
          </span>
          <span
            className="absolute -bottom-2 left-6 flex size-9 items-center justify-center rounded-2xl bg-surface shadow-[0px_4px_6px_-1px_rgba(0,0,0,0.1),0px_2px_4px_-2px_rgba(0,0,0,0.1)]"
            aria-hidden="true"
          >
            <img src={decoHeart} alt="" className="h-[15.292px] w-[16.667px]" />
          </span>
        </div>

        {/* Value Proposition Copy Block (16:7802) */}
        <div className="flex flex-col items-center px-4 pb-4 pt-3 lg:col-start-1 lg:row-start-3 lg:items-start lg:px-0 lg:pb-0 lg:pt-0">
          <h1 className="text-center text-[20px] font-bold leading-7 tracking-[-0.5px] text-text-primary lg:text-left lg:text-[34px] lg:leading-[1.18] lg:tracking-[-0.8px]">
            {t("welcome.slideTitle")}
          </h1>
          <p className="max-w-[320px] pt-[4.75px] text-center text-[14px] leading-[22.75px] text-text-secondary lg:max-w-[440px] lg:text-left lg:text-[16px] lg:leading-7">
            {t("welcome.slideBody")}
          </p>
        </div>

        {/* Quick Highlights Bento Badges (16:7809) */}
        <div className="mx-auto flex w-full max-w-[320px] justify-center gap-2 px-1 pb-6 lg:col-start-1 lg:row-start-4 lg:mx-0 lg:grid lg:max-w-[440px] lg:grid-cols-3 lg:gap-3 lg:px-0 lg:pb-0">
          {[
            { icon: iconScan, label: t("welcome.features.scan"), w: "16.667px", h: "15px" },
            { icon: iconPh, label: t("welcome.features.ph"), w: "15px", h: "15px" },
            { icon: iconJournal, label: t("welcome.features.journal"), w: "16.667px", h: "15px" },
          ].map((feature) => (
            <div
              key={feature.label}
              className="flex w-[98.66px] flex-col items-center justify-center rounded-xl bg-surface px-2 py-[15px] shadow-xs lg:w-auto lg:items-start lg:justify-start lg:bg-background-alt lg:px-3 lg:py-4 lg:text-left lg:shadow-none"
            >
              <img src={feature.icon} alt="" style={{ width: feature.w, height: feature.h }} className="mb-1" />
              <span className="text-center text-[11px] font-bold leading-[14px] tracking-[0.4px] text-text-primary lg:whitespace-nowrap lg:text-left lg:text-[12px] lg:leading-4">
                {feature.label}
              </span>
            </div>
          ))}
        </div>

        {/* Action Buttons Stack (16:7846) */}
        <div className="mx-auto flex w-full max-w-[320px] flex-col gap-2.5 lg:col-start-1 lg:row-start-5 lg:mx-0 lg:max-w-[440px] lg:gap-3">
          <button
            type="button"
            onClick={() => {
              void navigate("/auth/register");
            }}
            className="flex h-[50px] w-full items-center justify-center gap-2 rounded-2xl bg-primary text-[16px] font-bold leading-6 text-white shadow-[0px_4px_6px_-1px_rgba(0,0,0,0.1),0px_2px_4px_-2px_rgba(0,0,0,0.1)] transition-colors hover:bg-primary-dark"
          >
            {t("welcome.ctaPrimary")}
            <img src={arrowRight} alt="" className="size-[13.333px]" />
          </button>
          <button
            type="button"
            onClick={() => {
              void navigate("/auth/login");
            }}
            className="flex h-[46px] w-full items-center justify-center rounded-2xl bg-surface text-[16px] font-semibold leading-6 text-primary-dark shadow-xs transition-colors hover:bg-background-alt"
          >
            {t("welcome.ctaSecondary")}
          </button>
        </div>

        {/* Footnote & Safe iOS Home Indicator (16:7856) */}
        <div className="flex flex-col items-center gap-2 pt-4 lg:col-start-1 lg:row-start-6 lg:items-start lg:pt-0">
          <p className="text-center text-[11px] font-semibold leading-[14px] tracking-[0.4px] text-nav-inactive">
            {t("welcome.legalPrefix")}{" "}
            <Link to="/legal/terms" className="underline-offset-2 hover:underline">
              {t("welcome.legalTerms")}
            </Link>{" "}
            &amp;{" "}
            <Link to="/legal/privacy" className="underline-offset-2 hover:underline">
              {t("welcome.legalPrivacy")}
            </Link>{" "}
            {t("welcome.legalSuffix")}
          </p>
          <div className="h-1 w-32 rounded-full bg-border/60 lg:hidden" aria-hidden="true" />
        </div>
      </div>
    </div>
  );
}
