import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import LanguageDetector from "i18next-browser-languagedetector";
import HttpBackend from "i18next-http-backend";
import { NAMESPACES } from "./namespaces";
import { DEFAULT_LOCALE } from "@/shared/config/constants";

const LOCALE_STORAGE_KEY = "catcheck.locale";

// Chỉ trang Cài đặt ngôn ngữ ghi khoá này; detector không tự cache vì init sẽ ghi cả mặc định "vi",
// khiến không còn phân biệt được thiết bị chưa có lựa chọn.
export function getStoredLocale(): string | null {
  try {
    return localStorage.getItem(LOCALE_STORAGE_KEY);
  } catch {
    return null;
  }
}

export function setStoredLocale(locale: string): void {
  try {
    localStorage.setItem(LOCALE_STORAGE_KEY, locale);
  } catch {
    // Chế độ riêng tư: vẫn đổi ngôn ngữ cho phiên hiện tại.
  }
}

// `index.html` cố định lang="vi"; `languageChanged` phát cả lúc init xong lẫn khi đổi ngôn ngữ.
i18n.on("languageChanged", (lng) => {
  document.documentElement.lang = lng;
});

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
    // Mặc định tiếng Việt; chỉ dùng tiếng Anh khi người dùng tự chọn ở Cài đặt. Không đoán theo
    // trình duyệt, và dùng khoá mới để bỏ giá trị "en" mà khoá cũ đã tự lưu theo navigator.
    detection: {
      order: ["localStorage"],
      lookupLocalStorage: LOCALE_STORAGE_KEY,
      caches: [],
    },
    returnNull: false,
  });

export default i18n;
