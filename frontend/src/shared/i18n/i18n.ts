import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import LanguageDetector from "i18next-browser-languagedetector";
import HttpBackend from "i18next-http-backend";
import { NAMESPACES } from "./namespaces";
import { DEFAULT_LOCALE } from "@/shared/config/constants";

/**
 * i18next-http-backend nạp file JSON tĩnh từ /locales/{{lng}}/{{ns}}.json (public/locales),
 * được đồng bộ từ src/shared/i18n/locales/** lúc build/dev (xem vite.config.ts, hàm
 * syncLocalesToPublic) — nguồn sự thật duy nhất vẫn là src/shared/i18n/locales/**.
 * Chỉ "vi" có nội dung ở M0 — "en" để thư mục rỗng (chờ dịch), fallbackLng: "vi".
 */
void i18n
  .use(HttpBackend)
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    ns: NAMESPACES,
    defaultNS: "common",
    fallbackLng: DEFAULT_LOCALE,
    supportedLngs: ["vi", "en"],
    load: "languageOnly",
    interpolation: { escapeValue: false },
    backend: {
      loadPath: "/locales/{{lng}}/{{ns}}.json",
    },
    detection: {
      order: ["localStorage", "navigator"],
      caches: ["localStorage"],
    },
    returnNull: false,
  });

export default i18n;
