import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Check, Crown } from "lucide-react";
import { Badge } from "@/shared/ui";
import { CatAvatar, useFormatCatAge, type Cat } from "@/entities/cat";
import { cn } from "@/shared/lib/cn";
import iconNeuteredCheck from "@/shared/assets/icons/web-cat/profile-neutered-check.svg";
import iconMicrochip from "@/shared/assets/icons/web-cat/profile-microchip.svg";
import pawPattern from "@/shared/assets/icons/web-cat/profile-paw-pattern.svg";


export interface CatProfileHeroProps {
  cat: Cat;
  menu?: ReactNode;
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
    cat.weightKg != null ? t("detail.weightLabel", { weight: cat.weightKg }) : null,
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
      value: cat.weightKg != null ? t("detail.weightLabel", { weight: cat.weightKg }) : "—",
      foot: cat.weightUpdatedAt
        ? t("web.profile.weightUpdatedAt", { date: new Date(cat.weightUpdatedAt).toLocaleDateString(i18n.language) })
        : null,
    },
  ];

  const chip = "inline-flex shrink-0 items-center gap-1.5 rounded-full px-2.5 py-1 text-small font-semibold";

  return (
    <section data-reveal className={cn("relative overflow-hidden rounded-2xl bg-surface shadow-xs", className)}>
      <div className="relative h-14 bg-primary" aria-hidden="true">
        <img src={pawPattern} alt="" className="absolute right-14 top-1 h-12 opacity-60" />
      </div>
      {menu ? <div className="absolute right-3 top-2.5">{menu}</div> : null}

      <div className="flex flex-col gap-3 px-4 pb-4">
        <div className="-mt-8 flex items-end gap-3">
          <span className="relative shrink-0">
            <CatAvatar src={cat.avatarUrl} name={cat.name} size="lg" className="border-4 border-surface shadow-xs" />
            <span
              className="absolute bottom-0.5 right-0.5 flex size-5 items-center justify-center rounded-full border-2 border-surface bg-success"
              aria-hidden="true"
            >
              <Check className="size-2.5 text-white" strokeWidth={3} />
            </span>
          </span>
          <div className="min-w-0 flex-1 pb-1">
            <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
              <h1 className="truncate text-h3 font-bold leading-tight text-text-primary">{cat.name}</h1>
              {cat.isPrimary ? (
                <Badge tone="brand" className="gap-1">
                  <Crown className="size-3" aria-hidden="true" /> {t("status.primary")}
                </Badge>
              ) : null}
              {cat.status === "ARCHIVED" ? <Badge tone="neutral">{t("status.archived")}</Badge> : null}
            </div>
            {identityLine ? <p className="truncate pt-0.5 text-caption text-text-secondary">{identityLine}</p> : null}
          </div>
        </div>

        <div className="-mx-4 flex gap-2 overflow-x-auto px-4 [scrollbar-width:none]">
          {birthLabel ? (
            <span className={cn(chip, "bg-background-alt text-text-secondary")}>
              <img src={iconNeuteredCheck} alt="" className="size-3" />
              {t("detail.birthChip", { date: birthLabel })}
            </span>
          ) : null}
          <span className={cn(chip, "bg-background-alt text-text-secondary")}>
            <img src={iconMicrochip} alt="" className="size-3" />
            {t("detail.codeChip", { code: cat.publicCode })}
          </span>
          {cat.neutered === true ? (
            <span className={cn(chip, "bg-success-bg text-success-text")}>
              <img src={iconNeuteredCheck} alt="" className="size-3" />
              {t("detail.neuteredYes")}
            </span>
          ) : null}
        </div>

        <div className="hidden w-full grid-cols-2 gap-3 pt-1 text-left lg:grid">
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
