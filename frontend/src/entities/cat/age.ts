import { useCallback } from "react";
import { useTranslation } from "react-i18next";

/**
 * Tuổi mèo dạng "năm + tháng" (design `Web - 06 & 07`: "2T 3Th", mobile `06`: "2 tuổi").
 *
 * Nguồn DUY NHẤT là `ageMonths` do backend tính sẵn (`CatResponse.ageMonths`, từ `birthDate`
 * HOẶC `approxAgeMonths`) — FE chỉ đổi đơn vị hiển thị, không tự tính lại tuổi từ ngày sinh.
 * Trước đây mọi màn in thẳng "52 tháng tuổi", khó đọc với mèo trưởng thành.
 */
export function splitCatAge(ageMonths: number): { years: number; months: number } {
  const whole = Math.max(0, Math.floor(ageMonths));
  return { years: Math.floor(whole / 12), months: whole % 12 };
}

/** Trả hàm định dạng tuổi; `null` khi hồ sơ không có tuổi. Chuỗi nằm ở `cat:age.*`. */
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
