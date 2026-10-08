import { useTranslation } from "react-i18next";
import { Navigation, Star } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { formatNumber } from "@/shared/lib/format/formatNumber";
import { KIND_CHIP_CLASS, KIND_ICON, distanceText, isAroundTheClockBadge, type PlaceKind } from "./placeView";

/** Chip loại cơ sở — suy từ `place.kind` của API, không phải nhãn tự đặt. */
export function KindChip({ kind, size = "sm" }: { kind: PlaceKind; size?: "sm" | "md" }) {
  const { t } = useTranslation("map");
  const Icon = KIND_ICON[kind];
  return (
    <span
      className={cn(
        "inline-flex shrink-0 items-center gap-1 whitespace-nowrap rounded-md font-bold",
        size === "md" ? "px-2.5 py-1 text-[11px]" : "px-2 py-0.5 text-[10px]",
        KIND_CHIP_CLASS[kind],
      )}
    >
      <Icon size={size === "md" ? 12 : 11} aria-hidden="true" />
      {t(`kind.${kind}`)}
    </span>
  );
}

/** Chip nguồn cho cơ sở lấy từ OpenStreetMap — để người dùng biết CatCheck không tổng hợp cơ sở này. */
export function OsmSourceChip({ size = "sm" }: { size?: "sm" | "md" }) {
  const { t } = useTranslation("map");
  return (
    <span
      className={cn(
        "inline-flex shrink-0 items-center whitespace-nowrap rounded-md border border-border font-semibold text-text-secondary",
        size === "md" ? "px-2.5 py-1 text-[11px]" : "px-2 py-0.5 text-[10px]",
      )}
      title={t("source.osmNote")}
    >
      {t("source.osm")}
    </span>
  );
}

/** Badge do cơ sở công bố (API `badges[]`). Không tự thêm badge nào. */
export function PlaceBadges({ badges, size = "sm" }: { badges: string[]; size?: "sm" | "md" }) {
  if (badges.length === 0) return null;
  return (
    <>
      {badges.map((badge) => (
        <span
          key={badge}
          className={cn(
            "inline-flex max-w-full items-center whitespace-nowrap rounded-md font-semibold",
            size === "md" ? "px-2.5 py-1 text-[11px]" : "px-2 py-0.5 text-[10px]",
            isAroundTheClockBadge(badge) ? "bg-danger-bg text-danger-text" : "bg-background-alt text-text-secondary",
          )}
        >
          <span className="truncate">{badge}</span>
        </span>
      ))}
    </>
  );
}

export function Stars({ value, size = 11 }: { value: number; size?: number }) {
  return (
    <span className="flex shrink-0 items-center gap-0.5" aria-hidden="true">
      {[1, 2, 3, 4, 5].map((i) => (
        <Star
          key={i}
          size={size}
          className={i <= Math.round(value) ? "text-secondary" : "text-border"}
          fill="currentColor"
        />
      ))}
    </span>
  );
}

/**
 * Điểm trung bình chỉ hiện khi `reviewCount > 0`. API trả `rating: 0` khi chưa có đánh giá —
 * in "0.0" là con số vô nghĩa, nên lúc đó chỉ có một dòng trung tính (hoặc ẩn hẳn nếu `hideEmpty`).
 */
export function RatingLine({
  rating,
  reviewCount,
  size = "sm",
  hideEmpty = false,
}: {
  rating: number;
  reviewCount: number;
  size?: "sm" | "md";
  hideEmpty?: boolean;
}) {
  const { t } = useTranslation("map");
  if (reviewCount <= 0) {
    return hideEmpty ? null : (
      <span className={cn("text-text-tertiary", size === "md" ? "text-[12px]" : "text-[11px]")}>
        {t("rating.empty")}
      </span>
    );
  }
  return (
    <span className="inline-flex items-center gap-1.5 whitespace-nowrap">
      <span
        className={cn(
          "inline-flex items-center gap-1 font-bold text-text-primary",
          size === "md" ? "text-[14px]" : "text-[12px]",
        )}
      >
        <Star size={size === "md" ? 14 : 12} className="text-secondary" fill="currentColor" aria-hidden="true" />
        {formatNumber(rating, { minimumFractionDigits: 1, maximumFractionDigits: 1 })}
      </span>
      <span className="text-[11px] text-text-secondary">{t("rating.count", { count: reviewCount })}</span>
    </span>
  );
}

/** Khoảng cách tính từ vị trí người dùng đã cấp quyền; không có vị trí thì không vẽ gì. */
export function DistanceLabel({ km, className }: { km: number | null; className?: string }) {
  const { t } = useTranslation("map");
  if (km === null) return null;
  const { key, value } = distanceText(km);
  const label = t(key, { value });
  return (
    <span className={cn("inline-flex items-center gap-1 whitespace-nowrap text-[11px] font-semibold", className)}>
      <Navigation size={11} aria-hidden="true" />
      {label}
    </span>
  );
}
