import { useRef, useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { AlertTriangle, ChevronDown, ShieldCheck } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import type { DisclaimerVariant } from "./model";

/**
 * `DisclaimerBanner` — component nền dùng lại KHẮP app (p10 §5.2), sống ở
 * `entities/disclaimer` vì đây là entity dùng chung, không riêng của legal hay auth.
 * Nội dung lấy từ i18n namespace `legal` (p15 §15.7.3) theo đúng `variant` — component
 * KHÔNG chứa câu chữ y tế, chỉ vẽ khung + hành vi (thu gọn/bung EMERGENCY).
 *
 * Bản nháp câu chữ (p15 §15.7.3) chưa qua luật sư duyệt — `legal.json` giữ nguyên văn
 * bản nháp, KHÔNG được chỉnh sửa nội dung y tế ở đây khi dùng lại.
 */
export interface DisclaimerBannerProps {
  variant: DisclaimerVariant;
  /** EMERGENCY mặc định thu gọn (p10 §5.2); bỏ qua với 4 biến thể còn lại. */
  defaultCollapsed?: boolean;
  /** EMERGENCY tự bung khi `warning_flags` khác rỗng (p15 §15.7.2 P3) — module scan truyền vào. */
  hasWarningFlags?: boolean;
  className?: string;
}

export function DisclaimerBanner({
  variant,
  defaultCollapsed = variant === "emergency",
  hasWarningFlags = false,
  className,
}: DisclaimerBannerProps) {
  const { t } = useTranslation("legal");
  const [collapsed, setCollapsed] = useState(defaultCollapsed && !hasWarningFlags);

  if (variant === "footerLine") {
    return (
      <p className={cn("text-caption text-text-secondary", className)}>
        {t("disclaimer.footerLine.text")}{" "}
        <Link to="/legal/medical-disclaimer" className="font-semibold text-primary hover:underline">
          {t("disclaimer.footerLine.linkLabel")}
        </Link>
      </p>
    );
  }

  if (variant === "short") {
    return (
      <div
        className={cn(
          "flex items-start gap-2 rounded-lg border border-border bg-background-alt p-3 text-caption text-text-secondary",
          className,
        )}
      >
        <ShieldCheck className="mt-0.5 size-4 shrink-0 text-text-tertiary" aria-hidden="true" />
        <p>{t("disclaimer.short.text")}</p>
      </div>
    );
  }

  if (variant === "medium") {
    return (
      <div
        className={cn(
          "flex flex-col gap-2 rounded-lg border border-border bg-background-alt p-4 text-caption text-text-secondary",
          className,
        )}
      >
        <div className="flex items-center gap-2 font-semibold text-text-primary">
          <ShieldCheck className="size-4 shrink-0" aria-hidden="true" />
          <span>{t("disclaimer.medium.title")}</span>
        </div>
        <p>{t("disclaimer.medium.text")}</p>
      </div>
    );
  }

  if (variant === "emergency") {
    const bullets = t("disclaimer.emergency.bullets", { returnObjects: true }) as string[];
    return (
      <div className={cn("overflow-hidden rounded-xl border-2 border-danger bg-danger-bg", className)} role="alert">
        <button
          type="button"
          onClick={() => {
            setCollapsed((v) => !v);
          }}
          aria-expanded={!collapsed}
          className="flex w-full min-h-11 items-center justify-between gap-2 p-3 text-left font-bold text-danger-text"
        >
          <span className="flex items-center gap-2">
            <AlertTriangle className="size-5 shrink-0" aria-hidden="true" />
            {t("disclaimer.emergency.title")}
          </span>
          <ChevronDown
            className={cn("size-5 shrink-0 transition-transform", !collapsed && "rotate-180")}
            aria-hidden="true"
          />
        </button>
        {!collapsed ? (
          <div className="flex flex-col gap-3 border-t border-danger/30 p-4 text-small text-danger-text">
            <p>{t("disclaimer.emergency.intro")}</p>
            <ul className="list-disc space-y-1 pl-5">
              {bullets.map((bullet) => (
                <li key={bullet}>{bullet}</li>
              ))}
            </ul>
            <p className="font-semibold">{t("disclaimer.emergency.closing")}</p>
          </div>
        ) : null}
      </div>
    );
  }

  // variant === "long"
  const longBullets = t("disclaimer.long.bullets", { returnObjects: true }) as string[];
  return (
    <div className={cn("flex flex-col gap-4 rounded-xl border border-border bg-surface p-5", className)}>
      <div className="flex items-center gap-2">
        <ShieldCheck className="size-5 shrink-0 text-primary" aria-hidden="true" />
        <h2 className="text-h3 font-bold text-text-primary">{t("disclaimer.long.title")}</h2>
      </div>
      <p className="text-body text-text-secondary">{t("disclaimer.long.intro")}</p>
      <ul className="list-disc space-y-2 pl-5 text-body text-text-secondary">
        {longBullets.map((bullet) => (
          <li key={bullet}>{bullet}</li>
        ))}
      </ul>
      <p className="text-body text-text-secondary">{t("disclaimer.long.closing")}</p>
    </div>
  );
}

/* ---------------- DisclaimerScroll ---------------- */

const BOTTOM_THRESHOLD_PX = 8;

export interface DisclaimerScrollProps {
  children: ReactNode;
  onReachBottom: () => void;
  /** Nhãn a11y cho vùng cuộn — truyền theo ngữ cảnh dùng (ví dụ tiêu đề D-LONG). */
  label: string;
  hintText: string;
  doneText: string;
  className?: string;
}

/**
 * Khung "cuộn-đến-cuối mới cho tương tác tiếp" dùng lại cho mọi màn cần ép đọc hết D-LONG
 * trước khi tick "Tôi đã đọc và hiểu" (p15 §15.3.3 P1). Bản tổng quát hoá của
 * `features/onboarding/components.tsx` `DisclaimerScroll` (A7 viết cục bộ trước khi entity
 * này có nội dung thật) — nơi khác nên dùng bản này thay vì tự viết lại.
 */
export function DisclaimerScroll({
  children,
  onReachBottom,
  label,
  hintText,
  doneText,
  className,
}: DisclaimerScrollProps) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const [reachedBottom, setReachedBottom] = useState(false);

  const handleScroll = () => {
    const el = scrollRef.current;
    if (!el) return;
    const distanceToBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    if (distanceToBottom <= BOTTOM_THRESHOLD_PX && !reachedBottom) {
      setReachedBottom(true);
      onReachBottom();
    }
  };

  return (
    <div className={cn("flex flex-col gap-3", className)}>
      <div
        ref={scrollRef}
        onScroll={handleScroll}
        className="max-h-[50dvh] overflow-y-auto rounded-xl border border-border bg-surface p-4"
        role="region"
        aria-label={label}
      >
        {children}
      </div>
      <p className={cn("text-caption", reachedBottom ? "text-ph-normal-text" : "text-text-tertiary")}>
        {reachedBottom ? doneText : hintText}
      </p>
    </div>
  );
}
