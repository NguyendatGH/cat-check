import { useCallback } from "react";
import { useTranslation } from "react-i18next";

export function splitCatAge(ageMonths: number): { years: number; months: number } {
  const whole = Math.max(0, Math.floor(ageMonths));
  return { years: Math.floor(whole / 12), months: whole % 12 };
}

export function useFormatCatAge(): (ageMonths: number | null | undefined) => string | null {
  const { t } = useTranslation("cat");
  return useCallback(
    (ageMonths) => {
      if (typeof ageMonths !== "number" || !Number.isFinite(ageMonths) || ageMonths < 0) return null;
      const { years, months } = splitCatAge(ageMonths);
      if (years === 0) return t("age.months", { count: months });
      if (months === 0) return t("age.years", { count: years });
      return t("age.yearsMonths", { years, months });
    },
    [t],
  );
}
