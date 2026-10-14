import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import {
  AlertTriangle,
  BadgeCheck,
  Camera,
  ChevronRight,
  Link2,
  Loader2,
  LogOut,
  Mail,
  Trash2,
  UserRound,
} from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";
import { Button, ErrorState, Input, toast } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import { useSessionStore } from "@/entities/user";
import { CatAvatar } from "@/entities/cat";
import { useCatList } from "@/features/cat";
import { Link } from "react-router";
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

const PHONE_PATTERN = /^(0|\+84)\d{8,10}$/;
const MAX_AVATAR_BYTES = 5 * 1024 * 1024;
const AVATAR_TYPES = ["image/jpeg", "image/png", "image/webp"];

function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "?";
  const first = parts[0].charAt(0);
  const last = parts.length > 1 ? parts[parts.length - 1].charAt(0) : "";
  return (first + last).toUpperCase();
}

export function SettingsProfilePage() {
  const { t } = useTranslation(["settings", "common", "cat"]);
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
  const [avatarMessage, setAvatarMessage] = useState<
    "saved" | "removed" | "failed" | "invalidType" | "tooLarge" | null
  >(null);
  const fileRef = useRef<HTMLInputElement>(null);

  const syncSessionAvatar = (avatarUrl: string | null) => {
    if (sessionUser) setSession({ ...sessionUser, avatarUrl });
  };
  const onPickAvatar = (file: File | null) => {
    if (!file) return;
    if (!AVATAR_TYPES.includes(file.type)) {
      setAvatarMessage("invalidType");
      return;
    }
    if (file.size > MAX_AVATAR_BYTES) {
      setAvatarMessage("tooLarge");
      return;
    }
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
    updateProfile.mutate(
      { fullName: values.fullName, phone: values.phone },
      {
        onSuccess: (updated) => {
          queryClient.setQueryData(["auth", "profile"], updated);
          reset({ fullName: updated.fullName, phone: updated.phone ?? "" });
          toast.success(t("profile.saved"));
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

  const avatarUrl = sessionUser?.avatarUrl ?? null;
  const avatarBusy = uploadAvatar.isPending || removeAvatar.isPending;
  const avatarFeedback: Record<NonNullable<typeof avatarMessage>, { text: string; ok: boolean }> = {
    saved: { text: t("profile.avatarSaved"), ok: true },
    removed: { text: t("profile.avatarRemoved"), ok: true },
    failed: { text: t("profile.avatarFailed"), ok: false },
    invalidType: { text: t("cat:avatar.invalidType"), ok: false },
    tooLarge: { text: t("cat:avatar.tooLarge"), ok: false },
  };
  const feedback = avatarMessage ? avatarFeedback[avatarMessage] : null;
  const submitForm = (event?: React.BaseSyntheticEvent) => void handleSubmit(onSubmit)(event);

  return (
    <div className={cn("flex flex-col gap-4 transition-[padding] duration-300", isDirty && "max-lg:pb-24")}>
      <header data-reveal>
        <h1 className="text-h2 font-bold text-text-primary lg:text-h1">{t("pages.profile.title")}</h1>
        <p className="pt-1 text-caption text-text-secondary">{t("profile.intro")}</p>
      </header>

      <section data-reveal className="flex items-center gap-4 rounded-2xl bg-surface p-4 shadow-xs sm:p-5">
        <div className="relative shrink-0">
          <span className="flex size-20 items-center justify-center overflow-hidden rounded-full bg-chip-bg text-h3 font-bold text-primary-dark ring-4 ring-background-alt">
            {avatarUrl ? (
              <img src={avatarUrl} alt="" className="size-full object-cover" />
            ) : (
              <span aria-hidden="true">{initialsOf(profile.fullName)}</span>
            )}
            {avatarBusy ? (
              <span className="absolute inset-0 flex items-center justify-center rounded-full bg-surface/70">
                <Loader2 size={20} className="animate-spin text-primary-dark" aria-hidden="true" />
              </span>
            ) : null}
          </span>
          <button
            type="button"
            disabled={avatarBusy}
            onClick={() => fileRef.current?.click()}
            aria-label={avatarUrl ? t("cat:avatar.change") : t("cat:avatar.add")}
            className="absolute -bottom-0.5 -right-0.5 flex size-8 items-center justify-center rounded-full bg-primary-dark text-white shadow-sm ring-2 ring-surface transition-colors hover:bg-primary disabled:opacity-60"
          >
            <Camera size={15} aria-hidden="true" />
          </button>
          <input
            ref={fileRef}
            type="file"
            accept={AVATAR_TYPES.join(",")}
            className="hidden"
            onChange={(event) => {
              onPickAvatar(event.target.files?.[0] ?? null);
              event.target.value = "";
            }}
          />
        </div>

        <div className="min-w-0 flex-1">
          <p className="truncate text-body font-bold text-text-primary">{profile.fullName}</p>
          <p className="truncate text-caption text-text-secondary">{profile.email}</p>
          <span
            className={cn(
              "mt-1.5 inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-semibold",
              profile.emailVerified ? "bg-success-bg text-success-text" : "bg-warning-bg text-warning-text",
            )}
          >
            {profile.emailVerified ? (
              <BadgeCheck size={12} aria-hidden="true" />
            ) : (
              <AlertTriangle size={12} aria-hidden="true" />
            )}
            {profile.emailVerified ? t("profile.emailVerified") : t("profile.emailUnverified")}
          </span>
          <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1">
            <span className="text-[11px] text-text-tertiary">{t("cat:avatar.hint")}</span>
            {avatarUrl ? (
              <button
                type="button"
                disabled={avatarBusy}
                onClick={onRemoveAvatar}
                className="inline-flex items-center gap-1 text-[11px] font-semibold text-text-secondary hover:text-danger-text disabled:opacity-50"
              >
                <Trash2 size={12} aria-hidden="true" />
                {t("cat:avatar.remove")}
              </button>
            ) : null}
          </div>
          <p aria-live="polite" className="min-h-0 text-[11px]">
            {feedback ? (
              <span className={cn("content-enter mt-1 block", feedback.ok ? "text-success-text" : "text-danger")}>
                {feedback.text}
              </span>
            ) : null}
          </p>
        </div>
      </section>

      <MyCatsShortcut />

      <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:gap-6">
        <form
          data-reveal
          onSubmit={submitForm}
          className="flex min-w-0 flex-1 flex-col rounded-2xl bg-surface shadow-xs"
        >
          <SectionTitle icon={UserRound} title={t("profile.sectionBasic")} />
          <div className="flex flex-col gap-3 px-4 pb-4 sm:px-5">
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
          </div>
          <div className="hidden items-center justify-end gap-3 border-t border-border/60 px-5 py-3 lg:flex">
            <SaveStatus
              dirty={isDirty}
              saveFailed={updateProfile.isError}
              label={t("profile.unsaved")}
              failed={t("profile.saveFailed")}
            />
            <Button
              type="submit"
              size="sm"
              variant={isDirty ? "primary" : "tertiary"}
              disabled={!isDirty}
              loading={updateProfile.isPending}
            >
              {t("profile.save")}
            </Button>
          </div>
        </form>

        <div className="flex w-full flex-col gap-4 xl:w-[360px] xl:shrink-0">
          <section data-reveal className="flex flex-col rounded-2xl bg-surface shadow-xs">
            <SectionTitle icon={Mail} title={t("profile.sectionEmail")} />
            {emailStep === "idle" ? (
              <button
                type="button"
                onClick={() => {
                  setEmailStep("request");
                }}
                className="group mx-2 mb-2 flex items-center gap-3 rounded-xl px-2 py-2.5 text-left transition-colors hover:bg-background-alt sm:mx-3"
              >
                <span className="min-w-0 flex-1">
                  <span className="block text-[11px] font-semibold uppercase tracking-wide text-text-tertiary">
                    {t("profile.emailCurrent")}
                  </span>
                  <span className="block truncate text-body font-semibold text-text-primary">{profile.email}</span>
                  <span className="block text-[11px] text-text-tertiary">{t("profile.emailChangeHelp")}</span>
                </span>
                <span className="flex shrink-0 items-center gap-0.5 text-caption font-semibold text-primary">
                  {t("profile.emailChangeCta")}
                  <ChevronRight
                    size={16}
                    className="transition-transform group-hover:translate-x-0.5"
                    aria-hidden="true"
                  />
                </span>
              </button>
            ) : (
              <div className="content-enter flex flex-col gap-3 px-4 pb-4 sm:px-5">
                {emailStep === "request" ? (
                  <Input
                    label={t("profile.emailNew")}
                    placeholder={t("profile.emailNewPlaceholder")}
                    type="email"
                    value={newEmail}
                    onChange={(event) => {
                      setNewEmail(event.target.value);
                    }}
                  />
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
                      autoComplete="one-time-code"
                      value={otp}
                      onChange={(event) => {
                        setOtp(event.target.value);
                      }}
                    />
                  </>
                )}
                <div className="flex gap-2">
                  {emailStep === "request" ? (
                    <Button
                      type="button"
                      size="sm"
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
                  ) : (
                    <Button
                      type="button"
                      size="sm"
                      loading={confirmEmailChange.isPending}
                      onClick={() => {
                        confirmEmailChange.mutate(otp, {
                          onSuccess: () => {
                            setEmailStep("idle");
                            setOtp("");
                            setNewEmail("");
                            toast.success(t("profile.emailChanged"));
                            void queryClient.invalidateQueries({ queryKey: ["auth", "profile"] });
                          },
                        });
                      }}
                    >
                      {t("profile.emailOtpSubmit")}
                    </Button>
                  )}
                  <Button
                    type="button"
                    variant="tertiary"
                    size="sm"
                    onClick={() => {
                      setEmailStep("idle");
                    }}
                  >
                    {t("profile.cancel")}
                  </Button>
                </div>
              </div>
            )}
          </section>

          <section data-reveal className="flex flex-col rounded-2xl bg-surface shadow-xs">
            <SectionTitle icon={Link2} title={t("profile.sectionIdentities")} />
            {identities && identities.items.length > 0 ? (
              <ul className="flex flex-col divide-y divide-border/60 px-4 pb-2 sm:px-5">
                {identities.items.map((identity) => (
                  <li key={identity.provider} className="flex items-center gap-3 py-2.5">
                    <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-background-alt">
                      {identity.provider === "GOOGLE" ? (
                        <GoogleGlyph className="size-[17px]" />
                      ) : (
                        <UserRound size={17} className="text-primary-dark" aria-hidden="true" />
                      )}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block text-caption font-semibold text-text-primary">
                        {identity.provider === "GOOGLE" ? t("profile.identityGoogle") : t("profile.identityLocal")}
                      </span>
                      <span className="block truncate text-[11px] text-text-secondary">{identity.providerEmail}</span>
                    </span>
                    {identity.provider === "GOOGLE" ? (
                      <button
                        type="button"
                        onClick={() => {
                          unlinkIdentity.mutate(identity.provider);
                        }}
                        className="shrink-0 rounded-md px-2 py-1 text-[11px] font-semibold text-danger hover:bg-danger-bg"
                      >
                        {t("profile.identityUnlink")}
                      </button>
                    ) : null}
                  </li>
                ))}
              </ul>
            ) : (
              <p className="px-4 pb-4 text-caption text-text-tertiary sm:px-5">{t("profile.identityEmpty")}</p>
            )}
          </section>
        </div>
      </div>

      <section
        data-reveal
        className="flex items-center justify-between gap-4 rounded-2xl bg-surface p-4 shadow-xs sm:p-5"
      >
        <div className="min-w-0">
          <h2 className="text-caption font-bold text-text-primary">{t("mobile.logout")}</h2>
          <p className="pt-0.5 text-[11px] text-text-secondary">{t("profile.logoutHelp")}</p>
        </div>
        <button
          type="button"
          onClick={onLogout}
          disabled={logout.isPending}
          className="flex shrink-0 items-center gap-2 rounded-lg border border-danger/30 px-3.5 py-2 text-caption font-semibold text-danger transition-colors hover:bg-danger-bg disabled:cursor-not-allowed disabled:opacity-50"
        >
          <LogOut size={15} aria-hidden="true" />
          {t("mobile.logout")}
        </button>
      </section>

      <div
        aria-hidden={!isDirty}
        inert={!isDirty}
        className={cn(
          "fixed inset-x-0 bottom-0 z-[var(--z-sticky-header)] border-t border-border/60 bg-surface px-4 pb-[calc(12px+env(safe-area-inset-bottom))] pt-3 shadow-top transition-[transform,opacity] duration-300 ease-[cubic-bezier(0.22,1,0.36,1)] lg:hidden",
          isDirty ? "translate-y-0 opacity-100" : "pointer-events-none translate-y-full opacity-0",
        )}
      >
        <div className="mx-auto flex max-w-[480px] items-center gap-3">
          <SaveStatus
            dirty={isDirty}
            saveFailed={updateProfile.isError}
            label={t("profile.unsaved")}
            failed={t("profile.saveFailed")}
          />
          <Button
            type="button"
            variant="tertiary"
            size="sm"
            onClick={() => {
              reset();
            }}
          >
            {t("profile.cancel")}
          </Button>
          <Button
            type="button"
            size="sm"
            loading={updateProfile.isPending}
            onClick={() => {
              submitForm();
            }}
          >
            {t("profile.save")}
          </Button>
        </div>
      </div>
    </div>
  );
}

function SectionTitle({ icon: Icon, title }: { icon: typeof Mail; title: string }) {
  return (
    <h2 className="flex items-center gap-2 px-4 pb-2 pt-4 text-caption font-bold uppercase tracking-wide text-text-secondary sm:px-5">
      <Icon size={15} className="text-primary-dark" aria-hidden="true" />
      {title}
    </h2>
  );
}

function SaveStatus({
  dirty,
  saveFailed,
  label,
  failed,
}: {
  dirty: boolean;
  saveFailed: boolean;
  label: string;
  failed: string;
}) {
  if (saveFailed) {
    return (
      <span aria-live="polite" className="flex min-w-0 flex-1 items-center gap-1.5 text-[12px] text-danger">
        <AlertTriangle size={14} className="shrink-0" aria-hidden="true" />
        <span className="truncate">{failed}</span>
      </span>
    );
  }
  return (
    <span aria-live="polite" className="flex min-w-0 flex-1 items-center gap-1.5 text-[12px] text-text-secondary">
      {dirty ? (
        <>
          <span className="size-1.5 shrink-0 rounded-full bg-warning" aria-hidden="true" />
          <span className="truncate">{label}</span>
        </>
      ) : null}
    </span>
  );
}

function MyCatsShortcut() {
  const { t } = useTranslation("settings");
  const { data } = useCatList("ACTIVE");
  const cats = data?.items ?? [];
  const shown = cats.slice(0, 4);

  return (
    <Link
      data-reveal
      to="/cats"
      className="motion-card flex items-center gap-3 rounded-2xl bg-surface p-4 shadow-xs sm:p-5"
    >
      {shown.length > 0 ? (
        <span className="flex shrink-0 -space-x-2.5">
          {shown.map((cat) => (
            <span key={cat.id} className="rounded-full ring-2 ring-surface">
              <CatAvatar src={cat.avatarUrl} name={cat.name} size="sm" />
            </span>
          ))}
        </span>
      ) : null}
      <span className="flex min-w-0 flex-1 flex-col">
        <span className="text-body font-semibold text-text-primary">{t("profile.myCats.title")}</span>
        <span className="truncate text-small text-text-secondary">
          {cats.length > 0 ? t("profile.myCats.count", { count: cats.length }) : t("profile.myCats.empty")}
        </span>
      </span>
      <ChevronRight size={18} className="shrink-0 text-text-tertiary" aria-hidden="true" />
    </Link>
  );
}
