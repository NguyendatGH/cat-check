import type { Middleware } from "openapi-fetch";
import i18n from "@/shared/i18n/i18n";

/** Đính Accept-Language theo ngôn ngữ hiện tại của i18next cho mọi request. */
export const localeMiddleware: Middleware = {
  onRequest({ request }) {
    request.headers.set("Accept-Language", i18n.language || "vi");
    return request;
  },
};
