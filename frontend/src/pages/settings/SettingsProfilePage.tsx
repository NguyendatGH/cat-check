import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { AlertTriangle, Check, Link2, Loader2, LogOut, Mail, UserRound } from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";
import { Button, ErrorState, Input } from "@/shared/ui";
import { useSessionStore } from "@/entities/user";
import { AvatarUpload } from "@/features/cat";
import { GoogleGlyph } from "@/shared/assets/icons/GoogleGlyph";
import {
  MY_AVATAR_PATH,
  useConfirmEmailChange,
  useIdentities,
  useProfile,
  useRemoveMyAvatar,
  useRequestEmailChange,
  useUnlinkIdentity,
  useUpdateProfile,
  useUploadMyAvatar,
  useLogout,
} from "@/features/auth";

/**
 * `/settings/profile` — hồ sơ chủ nuôi.
 *
 * Nối API thật: `GET|PATCH /users/me` (B1/B2), `POST /account/email-change/request|confirm`
 * (B7/B8), `GET|DELETE /account/identities` (B9/B10). Không có frame Figma riêng cho màn
 * này — thiết kế gộp phần hồ sơ vào thẻ "Thông tin tài khoản chủ nuôi" của `Web - 16`, nên
 * trang này giữ đúng cụm trường và tone của thẻ đó ở dạng form chỉnh sửa.
 *
 * Trang nằm trong `TaskLayout` — layout đã cấp `px-4 py-6` và hộp nội dung desktop 944px,
 * trang KHÔNG tự thêm padding ngang.
 */

const PHONE_PATTERN = /^(0|\+84)\d{8,10}$/;

export function SettingsProfilePage() {
  const { t } = useTranslation(["settings", "common"]);
  const queryClient = useQueryClient();
  const { data: profile, isPending, isError, refetch } = useProfile();
  const updateProfile = useUpdateProfile();
  const { data: identities } = useIdentities();
  const unlinkIdentity = useUnlinkIdentity();
  const logout = useLogout();
  const requestEmailChange = useRequestEmailChange();
  const confirmEmailChange = useConfirmEmailChange();
  const uploadAvatar = useUploadMyAvatar();
  const removeAvatar = useRemoveMyAvatar();
  const sessionUser = useSessionStore((state) => state.user);
  const setSession = useSessionStore((state) => state.setSession);
  const [avatarMessage, setAvatarMessage] = useState<"saved" | "removed" | "failed" | null>(null);

  /** Cập nhật store phiên để header đổi ảnh ngay; `?v=` phá cache vì URL ảnh không đổi giữa các lần upload. */
  const syncSessionAvatar = (avatarUrl: string | null) => {
    if (sessionUser) setSession({ ...sessionUser, avatarUrl });
  };
  const onPickAvatar = (file: File | null) => {
    if (!file) return;
    setAvatarMessage(null);
    uploadAvatar.mutate(file, {
      onSuccess: () => {
        syncSessionAvatar(`${MY_AVATAR_PATH}?v=${String(Date.now())}`);
        setAvatarMessage("saved");
      },
      onError: () => {
        setAvatarMessage("failed");
      },
    });
  };
  const onRemoveAvatar = () => {
    setAvatarMessage(null);
    removeAvatar.mutate(undefined, {
      onSuccess: () => {
        syncSessionAvatar(null);
        setAvatarMessage("removed");
      },
      onError: () => {
        setAvatarMessage("failed");
      },
    });
  };

  const [emailStep, setEmailStep] = useState<"idle" | "request" | "confirm">("idle");
  const [newEmail, setNewEmail] = useState("");
  const [otp, setOtp] = useState("");

  const schema = z.object({
    fullName: z
      .string()
      .trim()
      .min(1, t("profile.validation.fullNameRequired"))
      .max(120, t("profile.validation.fullNameMax")),
    phone: z
      .string()
      .trim()
      .refine((v) => v === "" || PHONE_PATTERN.test(v), t("profile.validation.phoneInvalid")),
  });
  type FieldValues = z.infer<typeof schema>;

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isDirty },
  } = useForm<FieldValues>({
    resolver: zodResolver(schema),
    defaultValues: { fullName: "", phone: "" },
  });

  useEffect(() => {
    if (profile) {
      reset({ fullName: profile.fullName, phone: profile.phone ?? "" });
    }
  }, [profile, reset]);

  const onSubmit = (values: FieldValues) => {
    // Gửi chuỗi rỗng (không phải `undefined`) khi người dùng xoá số: B2 là merge-patch — vắng
    // field = giữ nguyên, còn `""` = xoá số đã lưu (`ProfileService.updateProfile`).
    updateProfile.mutate(
      { fullName: values.fullName, phone: values.phone },
      {
        onSuccess: (updated) => {
          queryClient.setQueryData(["auth", "profile"], updated);
          reset({ fullName: updated.fullName, phone: updated.phone ?? "" });
        },
      },
    );
  };

  const onLogout = () => {
    logout.mutate(undefined, {
      onSettled: () => {
        window.location.assign("/auth/login");
      },
    });
  };

  if (isPending) {
    return (
      <p className="flex items-center gap-2 text-body text-text-secondary">
        <Loader2 size={16} className="animate-spin" aria-hidden="true" />
        {t("common:actions.loading")}
      </p>
    );
  }

  if (isError) {
    return (
      <ErrorState
        title={t("profile.loadFailed")}
        onRetry={() => {
          void refetch();
        }}
        retryLabel={t("common:actions.retry")}
      />
    );
  }

  return (
    <div className="flex flex-col gap-5">
      <header>
        <h1 className="text-h2 font-bold text-text-primary lg:text-h1">{t("pages.profile.title")}</h1>
        <p className="pt-1 text-body text-text-secondary">{t("profile.intro")}</p>
      </header>

      <div className="flex flex-col gap-5 xl:flex-row xl:items-start xl:gap-6">
        <form
          onSubmit={(event) => void handleSubmit(onSubmit)(event)}
          className="flex min-w-0 flex-1 flex-col gap-4 rounded-2xl bg-surface p-5 shadow-brand-md"
        >
          <h2 className="flex items-center gap-2 text-h3 font-bold text-text-primary">
            <UserRound size={18} className="text-primary-dark" aria-hidden="true" />
            {t("profile.sectionBasic")}
          </h2>

          <div className="flex flex-col items-center gap-2 pb-1">
            <AvatarUpload
              previewUrl={sessionUser?.avatarUrl ?? null}
              onChange={onPickAvatar}
              onRemoveExisting={onRemoveAvatar}
              hasExisting={Boolean(sessionUser?.avatarUrl)}
              disabled={uploadAvatar.isPending || removeAvatar.isPending}
            />
            <p className="text-center text-caption text-text-tertiary">{t("profile.avatarHelp")}</p>
            <p aria-live="polite" className="min-h-4 text-caption">
              {avatarMessage === "failed" ? (
                <span className="text-danger">{t("profile.avatarFailed")}</span>
              ) : avatarMessage === "saved" ? (
                <span className="text-success-text">{t("profile.avatarSaved")}</span>
              ) : avatarMessage === "removed" ? (
                <span className="text-success-text">{t("profile.avatarRemoved")}</span>
              ) : null}
            </p>
          </div>

          <Input
            label={t("profile.fullName")}
            placeholder={t("profile.fullNamePlaceholder")}
            error={errors.fullName?.message}
            {...register("fullName")}
          />
          <Input
            label={t("profile.phone")}
            placeholder={t("profile.phonePlaceholder")}
            helperText={t("profile.phoneHelp")}
            inputMode="tel"
            error={errors.phone?.message}
            {...register("phone")}
          />

          <div className="flex items-center gap-3">
            <Button type="submit" size="md" disabled={!isDirty} loading={updateProfile.isPending}>
              {t("profile.save")}
            </Button>
            <p aria-live="polite" className="text-caption">
              {updateProfile.isSuccess && !isDirty ? (
                <span className="flex items-center gap-1.5 text-success-text">
                  <Check size={14} aria-hidden="true" />
                  {t("profile.saved")}
                </span>
              ) : updateProfile.isError ? (
                <span className="flex items-center gap-1.5 text-danger">
                  <AlertTriangle size={14} aria-hidden="true" />
                  {t("profile.saveFailed")}
                </span>
              ) : null}
            </p>
          </div>
        </form>

        <div className="flex w-full flex-col gap-5 xl:w-[360px] xl:shrink-0">
          <section className="flex flex-col gap-3 rounded-2xl bg-surface p-5 shadow-brand-md">
            <h2 className="flex items-center gap-2 text-h3 font-bold text-text-primary">
              <Mail size={18} className="text-primary-dark" aria-hidden="true" />
              {t("profile.sectionEmail")}
            </h2>

            <div className="rounded-xl bg-background-alt/60 p-3.5">
              <p className="text-[11px] font-semibold uppercase tracking-wide text-text-tertiary">
                {t("profile.emailCurrent")}
              </p>
              <p className="break-all pt-1 text-body font-bold text-text-primary">{profile.email}</p>
              <p
                className={
                  profile.emailVerified
                    ? "pt-0.5 text-caption text-success-text"
                    : "pt-0.5 text-caption text-warning-text"
                }
              >
                {profile.emailVerified ? t("profile.emailVerified") : t("profile.emailUnverified")}
              </p>
            </div>

            {emailStep === "idle" ? (
              <>
                <Button
                  type="button"
                  variant="tertiary"
                  size="md"
                  onClick={() => {
                    setEmailStep("request");
                  }}
                >
                  {t("profile.emailChangeCta")}
                </Button>
                <p className="text-caption text-text-tertiary">{t("profile.emailChangeHelp")}</p>
              </>
            ) : emailStep === "request" ? (
              <>
                <Input
                  label={t("profile.emailNew")}
                  placeholder={t("profile.emailNewPlaceholder")}
                  type="email"
                  value={newEmail}
                  onChange={(event) => {
                    setNewEmail(event.target.value);
                  }}
                />
                <div className="flex gap-2">
                  <Button
                    type="button"
                    size="md"
                    loading={requestEmailChange.isPending}
                    onClick={() => {
                      requestEmailChange.mutate(newEmail, {
                        onSuccess: () => {
                          setEmailStep("confirm");
                        },
                      });
                    }}
                  >
                    {t("profile.emailChangeSubmit")}
                  </Button>
                  <Button
                    type="button"
                    variant="tertiary"
                    size="md"
                    onClick={() => {
                      setEmailStep("idle");
                    }}
                  >
                    {t("profile.cancel")}
                  </Button>
                </div>
              </>
            ) : (
              <>
                <p className="text-caption text-text-secondary">
                  {t("profile.emailChangeSent", {
                    email: requestEmailChange.data?.maskedNewEmail ?? newEmail,
                  })}
                </p>
                <Input
                  label={t("profile.emailOtp")}
                  inputMode="numeric"
                  value={otp}
                  onChange={(event) => {
                    setOtp(event.target.value);
                  }}
                />
                <div className="flex gap-2">
                  <Button
                    type="button"
                    size="md"
                    loading={confirmEmailChange.isPending}
                    onClick={() => {
                      confirmEmailChange.mutate(otp, {
                        onSuccess: () => {
                          setEmailStep("idle");
                          setOtp("");
                          setNewEmail("");
                          void queryClient.invalidateQueries({ queryKey: ["auth", "profile"] });
                        },
                      });
                    }}
                  >
                    {t("profile.emailOtpSubmit")}
                  </Button>
                  <Button
                    type="button"
                    variant="tertiary"
                    size="md"
                    onClick={() => {
                      setEmailStep("idle");
                    }}
                  >
                    {t("profile.cancel")}
                  </Button>
                </div>
              </>
            )}
          </section>

          <section className="flex flex-col gap-3 rounded-2xl bg-surface p-5 shadow-brand-md">
            <h2 className="flex items-center gap-2 text-h3 font-bold text-text-primary">
              <Link2 size={18} className="text-primary-dark" aria-hidden="true" />
              {t("profile.sectionIdentities")}
            </h2>
            {identities && identities.items.length > 0 ? (
              <ul className="flex flex-col gap-2">
                {identities.items.map((identity) => (
                  <li key={identity.provider} className="flex items-center gap-3 rounded-xl bg-background-alt/60 p-3">
                    <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-surface">
                      {identity.provider === "GOOGLE" ? (
                        <GoogleGlyph className="size-[17px]" />
                      ) : (
                        <UserRound size={17} className="text-primary-dark" aria-hidden="true" />
                      )}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block text-body font-semibold text-text-primary">
                        {identity.provider === "GOOGLE" ? t("profile.identityGoogle") : t("profile.identityLocal")}
                      </span>
                      <span className="block truncate text-caption text-text-secondary">{identity.providerEmail}</span>
                    </span>
                    {identity.provider === "GOOGLE" ? (
                      <button
                        type="button"
                        onClick={() => {
                          unlinkIdentity.mutate(identity.provider);
                        }}
                        className="shrink-0 text-caption font-semibold text-danger hover:underline"
                      >
                        {t("profile.identityUnlink")}
                      </button>
                    ) : null}
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-caption text-text-tertiary">{t("profile.identityEmpty")}</p>
            )}
          </section>
        </div>
      </div>

      <section className="flex items-center justify-between gap-4 rounded-2xl border border-danger/20 bg-danger-bg/35 p-5">
        <div className="min-w-0">
          <h2 className="text-body font-bold text-text-primary">{t("mobile.logout")}</h2>
          <p className="pt-1 text-caption text-text-secondary">{t("profile.logoutHelp")}</p>
        </div>
        <button
          type="button"
          onClick={onLogout}
          disabled={logout.isPending}
          className="flex min-h-11 shrink-0 items-center gap-2 rounded-xl border border-danger/30 bg-surface px-4 py-2.5 text-caption font-semibold text-danger transition-colors hover:bg-danger-bg disabled:cursor-not-allowed disabled:opacity-50"
        >
          <LogOut size={16} aria-hidden="true" />
          {t("mobile.logout")}
        </button>
      </section>
    </div>
  );
}
