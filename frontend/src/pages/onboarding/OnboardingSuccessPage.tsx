import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import {
  ArrowRight,
  BadgeCheck,
  Cat,
  Check,
  ClipboardCheck,
  Flag,
  FlaskConical,
  IdCard,
  ScanLine,
  ShieldCheck,
} from "lucide-react";
import { Badge, Button, Card, EmptyState, SkeletonLoader } from "@/shared/ui";
import { LogoPawIcon } from "@/shared/assets/icons/AppIcons";
import {
  OnboardingShell,
  PhBandBar,
  useCreditBalance,
  useOnboardingStore,
  usePhBands,
  usePrimaryCat,
} from "@/features/onboarding";

const ROADMAP_STEPS = ["step1", "step2", "step3"] as const;

/**
 * Cột phải desktop (W1 Web-01c-5) — "Lộ trình 3 bước tiếp theo".
 *
 * KHÔNG dựng khối QR tải app iOS/Android của mockup: sản phẩm là một web app responsive
 * (PWA), không có app store build — W1 §6 đã nêu đây là mâu thuẫn cần owner xác nhận.
 * Bước 2 cũng bỏ vế "cảnh báo hồng cầu vi thể" của mockup (mâu thuẫn quyết định #8).
 */
function SuccessRoadmap({ catName }: { catName: string }) {
  const { t } = useTranslation("onboarding");
  return (
    <div className="flex flex-col gap-4 rounded-2xl border border-border bg-surface p-5">
      <div className="flex items-center justify-between gap-3">
        <p className="flex items-center gap-2 text-body font-semibold text-text-primary">
          <Flag className="size-5 text-primary" aria-hidden="true" />
          {t("web.success.roadmap.title")}
        </p>
        <Badge tone="brand">{t("web.success.roadmap.badge")}</Badge>
      </div>
      <p className="text-caption text-text-secondary">{t("web.success.roadmap.intro", { catName })}</p>
      <ol className="flex flex-col gap-4">
        {ROADMAP_STEPS.map((key, index) => (
          <li key={key} className="flex gap-3">
            <span className="flex size-7 shrink-0 items-center justify-center rounded-full bg-primary text-caption font-bold text-white">
              {index + 1}
            </span>
            <span className="flex flex-col gap-0.5">
              <span className="text-caption font-semibold text-text-primary">
                {t(`web.success.roadmap.${key}.title`)}
              </span>
              <span className="text-caption text-text-secondary">{t(`web.success.roadmap.${key}.body`)}</span>
            </span>
          </li>
        ))}
      </ol>
    </div>
  );
}

/**
 * Bước 5/5 — Hoàn tất (M1 01d, W1 Web-01c-5; p2 US-E2-03). Tóm tắt hồ sơ + dải pH
 * tham chiếu lấy từ `GET /reference/ph-bands` (US-E2-03 AC2 — không hard-code số
 * trong design gốc). CTA về Dashboard.
 *
 * Desktop (`lg:`): hero chúc mừng chiếm trọn bề ngang, bên dưới là 2 cột ~7/5 — thẻ hồ sơ
 * + lượt quét bên trái, lộ trình 3 bước bên phải. Mobile giữ nguyên 1 cột.
 */
export function OnboardingSuccessPage() {
  const { t } = useTranslation(["onboarding", "common"]);
  const navigate = useNavigate();
  const { createdCat, activation, surveySkipped } = useOnboardingStore();
  const { data: bands } = usePhBands();
  const { data: balance } = useCreditBalance();

  // Nguồn chính là `createdCat` của store (vừa tạo ở bước 1). Store chỉ sống trong phiên
  // trang: tải lại / mở thẳng `/onboarding/success` thì store trống — khi đó đọc hồ sơ chính
  // từ `GET /cats` thay vì báo lỗi chung chung như trước.
  const primaryCat = usePrimaryCat(!createdCat);
  const cat = createdCat ?? primaryCat.data ?? null;
  // Trạng thái khảo sát chỉ biết chắc khi đi qua bước 2 trong phiên này.
  const surveyKnown = Boolean(createdCat);
  const normalBand = bands?.find((b) => b.severity === "NORMAL");
  const rangedBands = (bands ?? []).filter((b) => typeof b.phMin === "number" || typeof b.phMax === "number");

  if (!cat) {
    const loading = primaryCat.isPending;
    return (
      <OnboardingShell
        step={5}
        title={t("success.title")}
        footer={
          <Button
            type="button"
            size="lg"
            className="w-full"
            onClick={() => {
              void navigate("/dashboard");
            }}
          >
            {t("success.cta")}
          </Button>
        }
      >
        {loading ? (
          <div className="flex flex-col gap-3" aria-busy="true" aria-label={t("success.loadingCat")}>
            <SkeletonLoader className="h-24 w-full rounded-2xl" />
            <SkeletonLoader className="h-16 w-full rounded-2xl" />
          </div>
        ) : (
          <EmptyState
            title={primaryCat.isError ? t("errors.generic") : t("success.noCat")}
            action={
              <Button
                type="button"
                variant="secondary"
                onClick={() => {
                  void navigate("/onboarding/cat");
                }}
              >
                {t("success.noCatCta")}
              </Button>
            }
          />
        )}
      </OnboardingShell>
    );
  }

  const sexLabel = t(`success.summary.sex.${cat.sex.toLowerCase()}`);
  const ageText =
    cat.ageMonths != null
      ? cat.ageMonths >= 12
        ? t("success.summary.ageYears", {
            years: Math.floor(cat.ageMonths / 12),
            months: cat.ageMonths % 12,
          })
        : t("success.summary.ageMonthsOnly", { months: cat.ageMonths })
      : t("success.summary.unknown");

  return (
    <OnboardingShell
      step={5}
      title={t("success.title")}
      titleHidden
      hero={
        <div className="flex flex-col items-center gap-3 text-center lg:gap-4 lg:py-4">
          <span className="relative flex size-20 items-center justify-center rounded-full bg-primary shadow-[0px_8px_20px_-4px_rgba(47,79,178,0.35)] lg:size-24">
            <LogoPawIcon size={36} className="text-white" />
            <span className="absolute bottom-0 right-0 flex size-7 items-center justify-center rounded-full border-2 border-background bg-success text-white">
              <Check size={16} strokeWidth={3} aria-hidden="true" />
            </span>
          </span>
          <div className="flex flex-col items-center gap-1">
            <Badge tone="success" className="inline-flex items-center gap-1">
              <BadgeCheck size={13} aria-hidden="true" />
              {t("success.badge")}
            </Badge>
            <h2 className="pt-1 text-h1 font-bold text-text-primary lg:text-display">{t("success.welcome")}</h2>
            <p className="max-w-xl text-body text-text-secondary">{t("success.subtitle", { catName: cat.name })}</p>
          </div>
        </div>
      }
      aside={
        <>
          <SuccessRoadmap catName={cat.name} />
          <p className="text-small text-text-tertiary">{t("success.footer")}</p>
        </>
      }
      footer={
        <div className="flex flex-col gap-3">
          <Button
            type="button"
            size="lg"
            className="w-full"
            onClick={() => {
              void navigate("/dashboard");
            }}
          >
            {t("success.cta")}
            <ArrowRight className="size-5" aria-hidden="true" />
          </Button>
          <Button
            type="button"
            variant="secondary"
            size="md"
            className="w-full"
            leftIcon={<ScanLine className="size-5" aria-hidden="true" />}
            onClick={() => {
              void navigate("/scan/select-cat");
            }}
          >
            {t("success.ctaScan")}
          </Button>
        </div>
      }
    >
      {/* M1 01d: hàng nhận diện (ảnh + tên + cân nặng, dòng tuổi · giới tính) rồi tới
          BẢNG THÔNG SỐ nền nhạt, mỗi dòng có icon dẫn bên trái. */}
      <Card padding="lg" className="flex flex-col gap-4">
        <div className="flex items-center gap-4">
          <span className="relative shrink-0">
            {cat.avatarUrl ? (
              <img src={cat.avatarUrl} alt="" className="size-16 rounded-2xl object-cover" />
            ) : (
              <span className="flex size-16 items-center justify-center rounded-2xl bg-chip-bg text-h3 font-bold text-primary">
                {cat.name.charAt(0).toUpperCase()}
              </span>
            )}
            <span className="absolute -bottom-1 -right-1 flex size-6 items-center justify-center rounded-full border-2 border-surface bg-primary text-white">
              <ShieldCheck size={12} aria-hidden="true" />
            </span>
          </span>
          <div className="flex min-w-0 flex-col gap-0.5">
            <p className="flex flex-wrap items-center gap-2">
              <span className="text-h3 font-bold text-text-primary">{cat.name}</span>
              {cat.weightKg != null ? (
                <Badge tone="brand">{t("success.summary.weight", { weight: cat.weightKg })}</Badge>
              ) : null}
            </p>
            <p className="text-caption text-text-secondary">
              {ageText}
              {" • "}
              {sexLabel}
              {` (${cat.neutered ? t("success.summary.neutered") : t("success.summary.notNeutered")})`}
            </p>
          </div>
        </div>

        <div className="flex flex-col gap-3 rounded-xl bg-background-alt p-4">
          <dl className="flex flex-col gap-2.5 lg:grid lg:grid-cols-2 lg:gap-x-8 lg:gap-y-3">
            <div className="flex items-center justify-between gap-4">
              <dt className="flex items-center gap-2 text-caption text-text-secondary">
                <Cat className="size-4 shrink-0 text-primary" aria-hidden="true" />
                {t("success.summary.breed")}
              </dt>
              <dd className="text-caption font-bold text-text-primary">
                {cat.breedName || t("success.summary.unknown")}
              </dd>
            </div>
            {normalBand ? (
              <div className="flex items-center justify-between gap-4">
                <dt className="flex items-center gap-2 text-caption text-text-secondary">
                  <FlaskConical className="size-4 shrink-0 text-primary" aria-hidden="true" />
                  {t("success.summary.phRange")}
                </dt>
                <dd className="flex items-center gap-1.5 text-caption font-bold text-ph-normal-text">
                  <span className="size-2 shrink-0 rounded-full bg-ph-normal" aria-hidden="true" />
                  {normalBand.phMin}–{normalBand.phMax}
                </dd>
              </div>
            ) : null}
            {surveyKnown ? (
              <div className="flex items-center justify-between gap-4">
                <dt className="flex items-center gap-2 text-caption text-text-secondary">
                  <ClipboardCheck className="size-4 shrink-0 text-primary" aria-hidden="true" />
                  {t("success.summary.surveyLabel")}
                </dt>
                <dd className="text-caption font-bold text-text-primary">
                  {surveySkipped ? t("success.summary.surveySkipped") : t("success.summary.surveyDone")}
                </dd>
              </div>
            ) : null}
            <div className="flex items-center justify-between gap-4">
              <dt className="flex items-center gap-2 text-caption text-text-secondary">
                <IdCard className="size-4 shrink-0 text-primary" aria-hidden="true" />
                {t("success.summary.catId")}
              </dt>
              <dd className="rounded-md bg-chip-bg px-2 py-0.5 text-caption font-bold tracking-[0.5px] text-primary-dark">
                {cat.publicCode}
              </dd>
            </div>
          </dl>

          {/* W1 Web-01c-5 "Ngưỡng pH Nước tiểu Dự kiến": dải màu + nhãn 2 đầu, số lấy từ
              `GET /reference/ph-bands` nên không hard-code ngưỡng nào. */}
          {rangedBands.length > 0 ? (
            <div className="flex flex-col gap-1.5 border-t border-border pt-3">
              <PhBandBar bands={rangedBands} />
              <div className="flex items-center justify-between gap-2 text-small text-text-tertiary">
                <span className="truncate">{rangedBands.at(0)?.label}</span>
                {normalBand ? (
                  <span className="truncate font-semibold text-ph-normal-text">{normalBand.label}</span>
                ) : null}
                <span className="truncate">{rangedBands.at(-1)?.label}</span>
              </div>
            </div>
          ) : null}
        </div>
      </Card>

      <Card padding="md" className="flex items-center justify-between gap-4">
        <div className="flex flex-col">
          <p className="text-body font-semibold text-text-primary">{t("success.credit.title")}</p>
          <p className="text-caption text-text-secondary">
            {activation
              ? t("success.credit.activated", {
                  credits: activation.balanceAfter,
                  date: new Date(activation.expiresAt).toLocaleDateString("vi-VN"),
                })
              : (balance?.availableBalance ?? 0) > 0
                ? t("success.credit.available", { count: balance?.availableBalance })
                : (balance?.trialScansRemaining ?? 0) > 0
                  ? t("success.credit.trial", { count: balance?.trialScansRemaining })
                  : t("success.credit.none")}
          </p>
        </div>
        <Badge tone={activation || (balance?.availableBalance ?? 0) > 0 ? "brand" : "neutral"}>
          {activation
            ? activation.balanceAfter
            : (balance?.availableBalance ?? 0) > 0
              ? balance?.availableBalance
              : (balance?.trialScansRemaining ?? 0)}
        </Badge>
      </Card>

      {/* Desktop: disclaimer đã nằm cuối cột phải, tránh lặp 2 lần trên cùng màn. */}
      <p className="text-center text-small text-text-tertiary lg:hidden">{t("success.footer")}</p>
    </OnboardingShell>
  );
}
