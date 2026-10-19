import { z } from "zod";
import i18n from "./i18n";

export function applyZodErrorMap(): void {
  z.config({
    customError: (iss) => {
      switch (iss.code) {
        case "too_small":
          return i18n.t("errors:validation.tooSmall", { ns: "errors" });
        case "too_big":
          return i18n.t("errors:validation.tooBig", { ns: "errors" });
        case "invalid_type":
          return i18n.t("errors:validation.required", { ns: "errors" });
        default:
          return i18n.t("errors:validation.generic", { ns: "errors" });
      }
    },
  });
}
