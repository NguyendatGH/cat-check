import type { Middleware } from "openapi-fetch";
import { CSRF_COOKIE_NAME, CSRF_HEADER_NAME } from "@/shared/config/constants";

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`));
  return match?.[1] ? decodeURIComponent(match[1]) : null;
}

const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);

export const csrfMiddleware: Middleware = {
  onRequest({ request }) {
    if (SAFE_METHODS.has(request.method)) {
      return request;
    }
    const token = readCookie(CSRF_COOKIE_NAME);
    if (token) {
      request.headers.set(CSRF_HEADER_NAME, token);
    }
    return request;
  },
};
