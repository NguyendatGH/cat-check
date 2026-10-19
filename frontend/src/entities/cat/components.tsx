import { Cat as CatIcon, Crown } from "lucide-react";
import { useEffect, useState } from "react";
import { cn } from "@/shared/lib/cn";
import { Badge } from "@/shared/ui";
import type { Cat } from "./model";


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

export function CatAvatar({ src, name, size = "md", className }: CatAvatarProps) {
  const initial = name.trim().charAt(0).toUpperCase();
  const [imageFailed, setImageFailed] = useState(false);

  useEffect(() => {
    setImageFailed(false);
  }, [src]);

  const canShowImage = Boolean(src) && !imageFailed;
  return (
    <span
      className={cn(
        "inline-flex shrink-0 items-center justify-center overflow-hidden rounded-full bg-chip-bg font-bold text-primary",
        AVATAR_SIZE_CLASS[size],
        className,
      )}
    >
      {canShowImage ? (
        <img
          src={src ?? undefined}
          alt=""
          className="size-full object-cover"
          onError={() => {
            setImageFailed(true);
          }}
        />
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
  roleLabel?: string;
  onSelect?: (cat: Cat) => void;
  className?: string;
}

export function CatCard({
  cat,
  selected = false,
  variant = "detailed",
  primaryLabel,
  archivedLabel,
  ageLabel,
  roleLabel,
  onSelect,
  className,
}: CatCardProps) {
  const isArchived = cat.status === "ARCHIVED";
  return (
    <button
      type="button"
      onClick={() => {
        onSelect?.(cat);
      }}
      aria-pressed={onSelect ? selected : undefined}
      className={cn(
        "flex min-h-[var(--touch-target-min)] w-full items-center gap-3 rounded-xl border-2 bg-surface p-3 text-left transition-colors",
        selected
          ? "border-primary bg-chip-bg"
          : "border-border max-lg:border-transparent max-lg:shadow-xs hover:border-border-strong",
        isArchived && "opacity-70",
        className,
      )}
    >
      <CatAvatar
        src={cat.avatarUrl}
        name={cat.name}
        size={variant === "compact" ? "sm" : "md"}
        className={selected ? "bg-surface" : undefined}
      />
      <span className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span className="flex items-center gap-1.5">
          <span className="truncate text-body font-semibold text-text-primary">{cat.name}</span>
          {cat.isPrimary && primaryLabel ? (
            <Crown className="size-3.5 shrink-0 text-secondary-text-on" aria-label={primaryLabel} />
          ) : null}
        </span>
        {variant === "detailed" && (cat.breedName ?? ageLabel) ? (
          <span className="truncate text-caption text-text-secondary">
            {[cat.breedName, ageLabel].filter(Boolean).join(" · ")}
          </span>
        ) : null}
        {roleLabel ? (
          <Badge tone={cat.isPrimary ? "brand" : "neutral"} className="mt-1 w-fit">
            {roleLabel}
          </Badge>
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
