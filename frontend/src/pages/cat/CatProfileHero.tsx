import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Check, Crown } from "lucide-react";
import { Badge } from "@/shared/ui";
import { CatAvatar, useFormatCatAge, type Cat } from "@/entities/cat";
import { cn } from "@/shared/lib/cn";
import iconNeuteredCheck from "@/shared/assets/icons/web-cat/profile-neutered-check.svg";
import iconMicrochip from "@/shared/assets/icons/web-cat/profile-microchip.svg";
import pawPattern from "@/shared/assets/icons/web-cat/profile-paw-pattern.svg";

/**
 * Thẻ nhận diện bé mèo ở đầu `/cats/:catId` — dùng CHUNG cho mobile (mockup
 * `06. Hồ sơ Bé Mèo`) và desktop (Figma `Web - 06 & 07`, cột trái `16:4300`):
 * dải nền thương hiệu, ảnh đại diện tràn mép, tên, dòng nhận diện, hai chip
 * (ngày sinh / mã hồ sơ), rồi lưới ô thông tin (chỉ hiện từ `lg`).
 *
 * Toàn bộ nội dung là DỮ LIỆU THẬT từ `GET /cats/{id}` — không có ảnh mặc định giả:
 * bé chưa có ảnh thì `CatAvatar` hiển thị chữ cái đầu như mọi nơi khác.
 */

export interface CatProfileHeroProps {
  cat: Cat;
  /** Nút "thao tác khác" (kebab) do trang cha dựng — đặt tuyệt đối góc phải trên. */
  menu?: ReactNode;
  /** Các ô thông tin phụ dưới lưới (nhóm máu / dị ứng ở desktop). */
  extraTiles?: ReactNode;
  className?: string;
}

export function CatProfileHero({ cat, menu, extraTiles, className }: CatProfileHeroProps) {
  const { t, i18n } = useTranslation("cat");
  const formatAge = useFormatCatAge();
  const ageLabel = formatAge(cat.ageMonths);

  const sexLabel = t(`form.sex.${cat.sex === "MALE" ? "male" : cat.sex === "FEMALE" ? "female" : "unknown"}`);
  const identityLine = [
    ageLabel,
    sexLabel,
    cat.breedName ?? cat.breedOther,
    cat.weightKg !== null ? t("detail.weightLabel", { weight: cat.weightKg }) : null,
  ]
    .filter(Boolean)
    .join(" • ");

  const birthLabel = cat.birthDate ? new Date(cat.birthDate).toLocaleDateString(i18n.language) : null;

  const tiles: { label: string; value: string; foot: string | null }[] = [
    {
      label: t("web.profile.sexAndAge"),
      value: [sexLabel, ageLabel].filter(Boolean).join(" • "),
      foot: birthLabel ? t("web.profile.bornAt", { date: birthLabel }) : null,
    },
    {
      label: t("web.profile.weightLabel"),
      value: cat.weightKg !== null ? t("detail.weightLabel", { weight: cat.weightKg }) : "—",
      foot: cat.weightUpdatedAt
        ? t("web.profile.weightUpdatedAt", { date: new Date(cat.weightUpdatedAt).toLocaleDateString(i18n.language) })
        : null,
    },
  ];

  return (
    <section className={cn("relative overflow-hidden rounded-2xl bg-surface shadow-xs", className)}>
      <div className="relative h-24 bg-primary">
        {/* Hoạ tiết dấu chân trang trí của Figma — SVG đã mang sẵn opacity 0.1 màu trắng. */}
        <img src={pawPattern} alt="" className="absolute right-4 top-1 h-20" aria-hidden="true" />
        <img src={pawPattern} alt="" className="absolute left-6 top-6 h-12" aria-hidden="true" />
      </div>
      {menu ? <div className="absolute right-3 top-3">{menu}</div> : null}

      <div className="-mt-12 flex flex-col items-center px-4 pb-5 text-center">
        <span className="relative">
          <CatAvatar src={cat.avatarUrl} name={cat.name} size="xl" className="border-4 border-surface" />
          <span
            className="absolute bottom-1 right-1 flex size-6 items-center justify-center rounded-full border-2 border-surface bg-success"
            aria-hidden="true"
          >
            <Check className="size-3 text-white" strokeWidth={3} />
          </span>
        </span>

        <div className="flex flex-wrap items-center justify-center gap-2 pt-3">
          <h1 className="text-h2 font-bold text-text-primary">{cat.name}</h1>
          {cat.isPrimary ? (
            <Badge tone="brand" className="gap-1">
              <Crown className="size-3" aria-hidden="true" /> {t("status.primary")}
            </Badge>
          ) : null}
          {cat.status === "ARCHIVED" ? <Badge tone="neutral">{t("status.archived")}</Badge> : null}
        </div>

        {identityLine ? <p className="pt-1 text-caption text-text-secondary">{identityLine}</p> : null}

        <div className="flex flex-wrap items-center justify-center gap-2 pt-3">
          {birthLabel ? (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-background-alt px-3 py-1 text-small font-semibold text-text-secondary">
              <img src={iconNeuteredCheck} alt="" className="size-3" />
              {t("detail.birthChip", { date: birthLabel })}
            </span>
          ) : null}
          <span className="inline-flex items-center gap-1.5 rounded-full bg-background-alt px-3 py-1 text-small font-semibold text-text-secondary">
            <img src={iconMicrochip} alt="" className="size-3" />
            {t("detail.codeChip", { code: cat.publicCode })}
          </span>
          {cat.neutered === true ? (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-success-bg px-3 py-1 text-small font-semibold text-success-text">
              <img src={iconNeuteredCheck} alt="" className="size-3" />
              {t("detail.neuteredYes")}
            </span>
          ) : null}
        </div>

        {/* Lưới ô thông tin — chỉ desktop, mobile đã có dòng nhận diện ở trên */}
        <div className="hidden w-full grid-cols-2 gap-3 pt-5 text-left lg:grid">
          {tiles.map((tile) => (
            <div key={tile.label} className="rounded-xl bg-background-alt p-3">
              <p className="text-small text-text-tertiary">{tile.label}</p>
              <p className="pt-0.5 text-caption font-bold text-text-primary">{tile.value}</p>
              {tile.foot ? <p className="text-small text-text-tertiary">{tile.foot}</p> : null}
            </div>
          ))}
          {extraTiles}
        </div>
      </div>
    </section>
  );
}
