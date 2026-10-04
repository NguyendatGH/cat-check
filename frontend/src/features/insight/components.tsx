import { useState } from "react";
import { useTranslation } from "react-i18next";
import { AlertTriangle, Bell, Check, ChevronDown, ChevronUp, Info } from "lucide-react";
import { Button, EmptyState, ErrorState, SkeletonLoader } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import { useAcknowledgeHealthFlag, useHealthFlags } from "./hooks";
import type { HealthFlagSeverity, HealthFlagView } from "./types";

/**
 * UI của module Cảnh báo. Đặt trong feature (không ở `pages/`) vì CẢ hai trang Trang chủ và
 * Hồ sơ mèo đều dùng, mà `boundaries` cấm page import page.
 *
 * Nội dung mô tả một dấu hiệu LUÔN là `explanationVi` do server render và lưu lại
 * (`health_flag.explanation_vi`) — client không tự ghép câu từ `messageParams`: bản đã gửi
 * qua push/PDF phải trùng chữ với bản hiện trên màn hình. Tiêu đề lấy từ i18n theo
 * `ruleCode`, rơi về `ruleCode` thô nếu rule mới chưa có khoá dịch.
 */

/** `Partial`: backend có thể thêm mức mới, rơi về `ATTENTION` thay vì vỡ render. */
const SEVERITY_STYLE: Partial<Record<HealthFlagSeverity, { chip: string; icon: typeof Info }>> = {
  INFO: { chip: "bg-info text-info-text", icon: Info },
  ATTENTION: { chip: "bg-warning-bg text-warning-text", icon: AlertTriangle },
  URGENT: { chip: "bg-danger-bg text-danger-text", icon: AlertTriangle },
};

const SEVERITY_FALLBACK = { chip: "bg-warning-bg text-warning-text", icon: AlertTriangle };

function severityStyle(severity: HealthFlagSeverity) {
  return SEVERITY_STYLE[severity] ?? SEVERITY_FALLBACK;
}

export interface HealthFlagRowProps {
  flag: HealthFlagView;
  onAcknowledge: (flagId: string) => void;
  acknowledging: boolean;
}

export function HealthFlagRow({ flag, onAcknowledge, acknowledging }: HealthFlagRowProps) {
  const { t, i18n } = useTranslation("common");
  const { chip, icon: Icon } = severityStyle(flag.severity);

  return (
    <li className="flex flex-col gap-2 rounded-xl border border-border bg-surface p-3">
      <div className="flex flex-wrap items-center gap-2">
        <span className={cn("inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-small font-semibold", chip)}>
          <Icon size={12} aria-hidden="true" />
          {t(`healthFlags.severity.${flag.severity}`, { defaultValue: flag.severity })}
        </span>
        <span className="min-w-0 flex-1 text-caption font-semibold text-text-primary">
          {t(`healthFlags.rule.${flag.ruleCode}`, { defaultValue: flag.ruleCode })}
        </span>
        <span className="shrink-0 text-small text-text-tertiary">
          {new Date(flag.triggeredAt).toLocaleDateString(i18n.language)}
        </span>
      </div>

      {flag.explanationVi ? (
        <p className="text-caption leading-relaxed text-text-secondary">{flag.explanationVi}</p>
      ) : null}

      <div className="flex items-center justify-between gap-2">
        <span className="text-small text-text-tertiary">
          {t("healthFlags.window", {
            from: new Date(flag.windowFrom).toLocaleDateString(i18n.language),
            to: new Date(flag.windowTo).toLocaleDateString(i18n.language),
          })}
        </span>
        {flag.acknowledged ? (
          <span className="inline-flex shrink-0 items-center gap-1 text-small font-semibold text-success-text">
            <Check size={12} aria-hidden="true" />
            {t("healthFlags.acknowledged")}
          </span>
        ) : (
          <Button
            type="button"
            variant="tertiary"
            size="sm"
            loading={acknowledging}
            onClick={() => {
              onAcknowledge(flag.flagId);
            }}
          >
            {t("healthFlags.acknowledge")}
          </Button>
        )}
      </div>
    </li>
  );
}

export interface HealthFlagListProps {
  /** Giới hạn theo một bé mèo; bỏ trống = mọi bé của tài khoản. */
  catId?: string | undefined;
  /** `false` khi chưa đăng nhập — endpoint cần phiên, gọi sẽ 403. */
  enabled?: boolean;
  /** Chỉ lấy mục chưa xác nhận (mặc định) hay lấy tất cả. */
  unacknowledgedOnly?: boolean;
  limit?: number;
  className?: string;
}

/** G1 + G3 — danh sách dấu hiệu theo dõi kèm nút "Đã hiểu" cho từng mục. */
export function HealthFlagList({
  catId,
  enabled = true,
  unacknowledgedOnly = true,
  limit = 10,
  className,
}: HealthFlagListProps) {
  const { t } = useTranslation("common");
  const query = useHealthFlags({ catId, acknowledged: unacknowledgedOnly ? false : undefined, limit }, enabled);
  const acknowledge = useAcknowledgeHealthFlag();

  if (!enabled) return null;

  if (query.isPending) {
    return <SkeletonLoader shape="card" className={cn("h-24", className)} />;
  }

  if (query.isError) {
    return (
      <ErrorState
        title={t("healthFlags.loadError")}
        onRetry={() => {
          void query.refetch();
        }}
        retryLabel={t("actions.retry")}
        className={className}
      />
    );
  }

  if (query.data.items.length === 0) {
    return (
      <EmptyState
        title={t("healthFlags.emptyTitle")}
        description={t("healthFlags.emptyDescription")}
        className={className}
      />
    );
  }

  return (
    <div className={cn("flex flex-col gap-2", className)}>
      <ul className="flex flex-col gap-2">
        {query.data.items.map((flag) => (
          <HealthFlagRow
            key={flag.flagId}
            flag={flag}
            acknowledging={acknowledge.isPending && acknowledge.variables === flag.flagId}
            onAcknowledge={(flagId) => {
              acknowledge.mutate(flagId);
            }}
          />
        ))}
      </ul>
      {query.data.hasMore ? <p className="text-small text-text-tertiary">{t("healthFlags.hasMore")}</p> : null}
      <p className="text-small leading-relaxed text-text-tertiary">{t("healthFlags.disclaimer")}</p>
    </div>
  );
}

export interface HealthFlagDisclosureProps {
  catId?: string | undefined;
  /** `cat.unacknowledgedFlagCount` / `summary.unacknowledgedFlagCount` do server tính. */
  count: number;
  enabled?: boolean;
  className?: string;
}

/**
 * Con số `unacknowledgedFlagCount` dưới dạng NÚT: bấm vào bung danh sách thật (G1) thay vì
 * một badge chết. Không có cảnh báo nào thì không render gì — không chiếm chỗ vô ích.
 */
export function HealthFlagDisclosure({ catId, count, enabled = true, className }: HealthFlagDisclosureProps) {
  const { t } = useTranslation("common");
  const [open, setOpen] = useState(false);

  if (count <= 0) return null;

  return (
    <div className={cn("flex flex-col gap-2", className)}>
      <button
        type="button"
        aria-expanded={open}
        onClick={() => {
          setOpen((value) => !value);
        }}
        className="flex items-center gap-2 rounded-xl bg-warning-bg px-3 py-2.5 text-left text-caption font-semibold text-warning-text"
      >
        <Bell size={16} className="shrink-0" aria-hidden="true" />
        <span className="min-w-0 flex-1">{t("healthFlags.countCta", { count })}</span>
        {open ? (
          <ChevronUp size={16} className="shrink-0" aria-hidden="true" />
        ) : (
          <ChevronDown size={16} className="shrink-0" aria-hidden="true" />
        )}
      </button>
      {open ? <HealthFlagList catId={catId} enabled={enabled} /> : null}
    </div>
  );
}
