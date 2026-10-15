import i18next from "i18next";
import type { Locale } from "date-fns";
import { enUS, vi } from "date-fns/locale";

const INTL_LOCALES: Record<string, string> = { vi: "vi-VN", en: "en-US" };
const DATE_FNS_LOCALES: Record<string, Locale> = { vi, en: enUS };

/** Base language ("vi" | "en") of the active i18n language; defaults to "vi". */
function activeLanguage(): string {
  const lng = i18next.resolvedLanguage ?? i18next.language;
  const base = typeof lng === "string" ? lng.split("-")[0]?.toLowerCase() : undefined;
  return base && base in INTL_LOCALES ? base : "vi";
}

/** BCP 47 tag for `Intl.*` formatters, derived from the active i18n language. */
export function intlLocale(): string {
  return INTL_LOCALES[activeLanguage()] ?? "vi-VN";
}

/** date-fns locale matching the active i18n language. */
export function dateFnsLocale(): Locale {
  return DATE_FNS_LOCALES[activeLanguage()] ?? vi;
}

/**
 * `YYYY-MM-DD` of a date in the user's LOCAL time zone (for `<input type="date">` values/max).
 * `toISOString()` would give the UTC date, which is "yesterday" in UTC+7 before 07:00.
 */
export function localIsoDate(date: Date = new Date(), offsetDays = 0): string {
  const d = new Date(date.getFullYear(), date.getMonth(), date.getDate() + offsetDays);
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  return `${String(d.getFullYear())}-${mm}-${dd}`;
}
