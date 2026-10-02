import { Cat as CatIcon, Crown } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { Badge } from "@/shared/ui";
import type { Cat } from "./model";

/**
 * UI dùng chung cho mèo (p10 §5.2 `CatAvatar`/`CatCard`, p9 §9.2.4 `entities/cat/ui`).
 * Dùng bởi `features/cat` và các feature khác cần hiển thị mèo (`scan`, `history`, `trends`).
 * Component thuần hiển thị — không gọi API, không đọc store.
 */

const AVATAR_SIZE_CLASS = {
  sm: "size-8 text-caption",
  md: "size-12 text-body",
  lg: "size-20 text-h3",
  xl: "size-28 text-h2",
} as const;

export interface CatAvatarProps {
  src?: string | null;
  name: string;
  size?: keyof typeof AVATAR_SIZE_CLASS;
  className?: string;
}

/** Ảnh đại diện mèo — fallback chữ cái đầu tên khi chưa có ảnh (không có ảnh mặc định giả). */
export function CatAvatar({ src, name, size = "md", className }: CatAvatarProps) {
  const initial = name.trim().charAt(0).toUpperCase();
  return (
    <span
      className={cn(
        "inline-flex shrink-0 items-center justify-center overflow-hidden rounded-full bg-chip-bg font-bold text-primary",
        AVATAR_SIZE_CLASS[size],
        className,
      )}
    >
      {src ? (
        <img src={src} alt="" className="size-full object-cover" />
      ) : initial ? (
        <span aria-hidden="true">{initial}</span>
      ) : (
        <CatIcon className="size-1/2" aria-hidden="true" />
      )}
    </span>
  );
}

export interface CatCardProps {
  cat: Cat;
  selected?: boolean;
  variant?: "compact" | "detailed";
  primaryLabel?: string;
  archivedLabel?: string;
  ageLabel?: string;
  onSelect?: (cat: Cat) => void;
  className?: string;
}

/** Thẻ tóm tắt 1 con mèo — dùng cho `/cats`, Select Cat, dashboard. */
export function CatCard({
  cat,
  selected = false,
  variant = "detailed",
  primaryLabel,
  archivedLabel,
  ageLabel,
  onSelect,
  className,
}: CatCardProps) {
  const isArchived = cat.status === "ARCHIVED";
  return (
    <button
      type="button"
      onClick={() => { onSelect?.(cat); }}
      aria-pressed={onSelect ? selected : undefined}
      className={cn(
        "flex min-h-[var(--touch-target-min)] w-full items-center gap-3 rounded-xl border-2 bg-surface p-3 text-left transition-colors",
        selected ? "border-primary bg-chip-bg" : "border-border hover:border-border-strong",
        isArchived && "opacity-70",
        className,
      )}
    >
      <CatAvatar src={cat.avatarUrl} name={cat.name} size={variant === "compact" ? "sm" : "md"} />
      <span className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span className="flex items-center gap-1.5">
          <span className="truncate text-body font-semibold text-text-primary">{cat.name}</span>
          {cat.isPrimary && primaryLabel ? (
            <Crown className="size-3.5 shrink-0 text-secondary-text-on" aria-label={primaryLabel} />
          ) : null}
        </span>
        {variant === "detailed" ? (
          <span className="truncate text-caption text-text-secondary">
            {[cat.breedName, ageLabel].filter(Boolean).join(" · ")}
          </span>
        ) : null}
        {isArchived && archivedLabel ? (
          <Badge tone="neutral" className="mt-1 w-fit">
            {archivedLabel}
          </Badge>
        ) : null}
      </span>
    </button>
  );
}
