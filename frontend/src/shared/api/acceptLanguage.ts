import i18n from "@/shared/i18n/i18n";
import { DEFAULT_LOCALE } from "@/shared/config/constants";

export function currentAcceptLanguage(): string {
  return i18n.language || DEFAULT_LOCALE;
}
