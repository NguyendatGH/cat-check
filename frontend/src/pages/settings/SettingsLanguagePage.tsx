import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, Check, Languages } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { SUPPORTED_LOCALES } from "@/shared/config/constants";
import { useProfile, useUpdateProfile } from "@/features/auth";

/**
 * `/settings/language` — ngôn ngữ hiển thị.
 *
 * Đổi ngôn ngữ làm HAI việc, không chỉ một:
 *   1. `i18n.changeLanguage` — đổi ngay giao diện (i18next tự lưu vào localStorage theo
 *      cấu hình `detection.caches` ở `shared/i18n/i18n.ts`);
 *   2. `PATCH /users/me { locale }` — để email/thông báo server gửi đi cũng đúng ngôn ngữ.
 *      Cột `app_user.locale` có CHECK chỉ nhận 'vi' | 'en'.
 * Nếu bước 2 lỗi thì giao diện vẫn đổi và báo rõ là mới chỉ đổi trên thiết bị này.
 *
 * Trang nằm trong `TaskLayout`: layout cấp `px-4 py-6` + hộp 944px, trang không tự thêm.
 */
export function SettingsLanguagePage() {
  const { t, i18n } = useTranslation("settings");
  const queryClient = useQueryClient();
  const { data: profile } = useProfile();
  const updateProfile = useUpdateProfile();

  const active = i18n.resolvedLanguage ?? profile?.locale ?? SUPPORTED_LOCALES[0];

  const choose = (locale: string) => {
    void i18n.changeLanguage(locale);
    updateProfile.mutate(
      { locale },
      {
        onSuccess: (updated) => {
          queryClient.setQueryData(["auth", "profile"], updated);
        },
      },
    );
  };

  return (
    <div className="flex flex-col gap-5">
      <header>
        <h1 className="flex items-center gap-2.5 text-h2 font-bold text-text-primary lg:text-h1">
          <Languages size={22} className="text-primary-dark" aria-hidden="true" />
          {t("pages.language.title")}
        </h1>
        <p className="pt-1 text-body text-text-secondary">{t("language.intro")}</p>
      </header>

      <fieldset className="flex max-w-[560px] flex-col gap-2.5 rounded-2xl bg-surface p-5 shadow-brand-md">
        <legend className="sr-only">{t("pages.language.title")}</legend>
        {SUPPORTED_LOCALES.map((locale) => {
          const selected = active === locale;
          return (
            <label
              key={locale}
              className={cn(
                "flex cursor-pointer items-center gap-3 rounded-xl border p-3.5 transition-colors",
                selected
                  ? "border-primary bg-chip-bg/40"
                  : "border-border bg-background-alt/50 hover:border-border-strong",
              )}
            >
              <input
                type="radio"
                name="locale"
                value={locale}
                checked={selected}
                onChange={() => {
                  choose(locale);
                }}
                className="sr-only"
              />
              <span className="min-w-0 flex-1">
                <span className="block text-body font-bold text-text-primary">
                  {locale === "vi" ? t("language.optionVi") : t("language.optionEn")}
                </span>
                <span className="block text-caption text-text-secondary">
                  {locale === "vi" ? t("language.optionViNote") : t("language.optionEnNote")}
                </span>
              </span>
              {selected ? (
                <span className="flex shrink-0 items-center gap-1.5 rounded-full bg-success-bg px-3 py-1 text-caption font-semibold text-success-text">
                  <Check size={13} aria-hidden="true" />
                  {t("language.current")}
                </span>
              ) : null}
            </label>
          );
        })}

        {/* Vùng thông báo chỉ chiếm chỗ khi có nội dung — để trống thì thẻ không bị dư đáy. */}
        <p aria-live="polite" className="text-caption empty:hidden">
          {updateProfile.isError ? (
            <span className="flex items-start gap-1.5 text-warning-text">
              <AlertTriangle size={14} className="mt-0.5 shrink-0" aria-hidden="true" />
              {t("language.savedLocalOnly")} {t("language.saveFailed")}
            </span>
          ) : updateProfile.isSuccess ? (
            <span className="flex items-center gap-1.5 text-success-text">
              <Check size={14} className="shrink-0" aria-hidden="true" />
              {t("language.saved")}
            </span>
          ) : null}
        </p>
      </fieldset>
    </div>
  );
}
