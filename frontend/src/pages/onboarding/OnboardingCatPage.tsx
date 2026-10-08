import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { toast } from "sonner";
import {
  ArrowRight,
  CalendarDays,
  CirclePlus,
  HelpCircle,
  IdCard,
  Mars,
  Scale,
  ShieldCheck,
  Venus,
} from "lucide-react";
import { Button, Input } from "@/shared/ui";
import {
  AvatarUpload,
  BreedPicker,
  Checkbox,
  FieldError,
  FieldLabel,
  OnboardingShell,
  OptionCard,
  PhBandBar,
  catProfileSchema,
  useBreeds,
  useCreateCat,
  useOnboardingStore,
  usePhBands,
  useUpdateCat,
  useUploadAvatar,
  type CatProfileFormValues,
  type CatSex,
  type CreatedCat,
  type PhBand,
} from "@/features/onboarding";
import { LogoPawIcon } from "@/shared/assets/icons/AppIcons";
import { isApiError } from "@/shared/api";

/**
 * Bước 1/5 — Hồ sơ bé mèo (M1 01c-3, W1 Web-01c-3; p2 US-E2-01).
 * Tạo `cat` qua POST /cats (p8 D1); nếu đã tạo (đi lùi từ bước 2) thì PATCH (D2).
 * Ảnh đại diện giữ trong store, upload qua PUT /cats/{id}/avatar (D10) lúc submit.
 *
 * Desktop (`lg:`, W1 Web-01c-3 — khung 1280px, 2 cột ~7/5): cột trái giữ NGUYÊN form
 * mobile, cột phải là "Thẻ căn cước y tế thú cưng" của thiết kế — ở đây dựng thành
 * preview CẬP NHẬT TRỰC TIẾP theo giá trị đang nhập (dữ liệu thật của chính form, không
 * mock) + dải pH tham chiếu lấy từ `GET /reference/ph-bands`.
 *
 * KHÔNG dựng 3 khối còn lại của mockup web vì không có nguồn dữ liệu thật và/hoặc vi phạm
 * ranh giới "không hứa hẹn y tế": "Chỉ số nguy cơ sỏi bàng quang", "Thuật toán chẩn đoán
 * CATCHECK Biomarker v2.4", card trích lời một bác sĩ có tên, và khối khuyến mãi tặng mẫu
 * cát 200g (fulfillment vật lý, chưa có entity — xem W1 §4 "Ghi chú / mâu thuẫn").
 */

/** Tuổi (tháng) từ ngày sinh dạng `YYYY-MM-DD`; `null` khi chưa nhập/không hợp lệ. */
function monthsSinceBirth(birthDate: string): number | null {
  if (!birthDate) return null;
  const born = new Date(birthDate);
  if (Number.isNaN(born.getTime())) return null;
  const now = new Date();
  let months = (now.getFullYear() - born.getFullYear()) * 12 + (now.getMonth() - born.getMonth());
  if (now.getDate() < born.getDate()) months -= 1;
  return months < 0 ? null : months;
}

/** Giới tính sinh học — icon khớp M1 01c-3 (♂ / ♀), lựa chọn "Chưa rõ" giữ nguyên của app. */
const SEX_OPTIONS = [
  { value: "MALE", icon: <Mars size={20} aria-hidden="true" /> },
  { value: "FEMALE", icon: <Venus size={20} aria-hidden="true" /> },
  { value: "UNKNOWN", icon: <HelpCircle size={20} aria-hidden="true" /> },
] as const;

/** Các bước còn lại — GIỮ ĐÚNG đánh số 1..5 của `OnboardingStepper`, không đánh số lại. */
const REMAINING_STEPS = [
  { step: 2, key: "healthSurvey" },
  { step: 3, key: "disclaimer" },
  { step: 4, key: "activate" },
  { step: 5, key: "success" },
] as const;

interface CatIdCardPreviewProps {
  name: string;
  breedName: string | undefined;
  identityLine: string;
  metaLine: string;
  avatarUrl: string | null;
  publicCode: string | undefined;
  bands: PhBand[];
}

/** Cột phải desktop — thẻ hồ sơ dựng từ chính giá trị người dùng đang nhập. */
function CatIdCardPreview({
  name,
  breedName,
  identityLine,
  metaLine,
  avatarUrl,
  publicCode,
  bands,
}: CatIdCardPreviewProps) {
  const { t } = useTranslation("onboarding");
  const normalBand = bands.find((b) => b.severity === "NORMAL");
  // Nhãn 2 đầu thang lấy từ dải CÓ biên — INCONCLUSIVE ("chưa đủ dữ liệu") không phải đầu thang.
  const ranged = bands.filter((b) => typeof b.phMin === "number" || typeof b.phMax === "number");
  const firstBand = ranged.at(0);
  const lastBand = ranged.at(-1);

  return (
    <div className="flex flex-col gap-5 rounded-3xl bg-gradient-to-br from-primary-dark to-primary p-6 shadow-brand-xl">
      <div className="flex items-center justify-between gap-3">
        <span className="flex items-center gap-2 text-overline font-bold uppercase tracking-[1px] text-on-primary-muted">
          <ShieldCheck className="size-4" aria-hidden="true" />
          {t("web.cat.card.title")}
        </span>
        {publicCode ? (
          <span className="rounded-md bg-white/15 px-2 py-0.5 text-small font-semibold tracking-[0.5px] text-white">
            {publicCode}
          </span>
        ) : null}
      </div>

      <div className="flex items-center gap-4">
        {avatarUrl ? (
          <img src={avatarUrl} alt="" className="size-20 shrink-0 rounded-2xl object-cover" />
        ) : (
          <span className="flex size-20 shrink-0 items-center justify-center rounded-2xl bg-white/15">
            <LogoPawIcon size={32} className="text-white" />
          </span>
        )}
        <div className="flex min-w-0 flex-col gap-1">
          <p className="truncate text-h3 font-bold text-white">{name.trim() || t("web.cat.card.namePlaceholder")}</p>
          <p className="truncate text-caption text-on-primary-subtle">
            {breedName ?? t("web.cat.card.breedPlaceholder")}
          </p>
          <p className="truncate text-caption text-on-primary-subtle">{identityLine}</p>
          <p className="truncate text-caption text-on-primary-subtle">{metaLine}</p>
        </div>
      </div>

      <div className="flex flex-col gap-2 rounded-2xl bg-white/10 p-4">
        <div className="flex items-baseline justify-between gap-3">
          <p className="text-caption font-semibold text-on-primary-subtle">{t("web.cat.card.phLabel")}</p>
          {normalBand ? (
            <p className="text-caption font-bold text-verified-bright">
              {normalBand.phMin}–{normalBand.phMax}
            </p>
          ) : null}
        </div>
        {bands.length > 0 ? (
          <>
            <PhBandBar bands={bands} />
            <div className="flex items-center justify-between gap-2 text-small text-on-primary-muted">
              <span className="truncate">{firstBand?.label}</span>
              {normalBand ? (
                <span className="truncate font-semibold text-verified-bright">{normalBand.label}</span>
              ) : null}
              <span className="truncate">{lastBand?.label}</span>
            </div>
          </>
        ) : (
          <p className="text-small text-on-primary-muted">{t("web.cat.card.phLoading")}</p>
        )}
      </div>

      <p className="text-small text-on-primary-muted">{t("web.cat.card.live")}</p>
    </div>
  );
}

/** Cột phải desktop — nhắc 4 bước onboarding còn lại (nhãn lấy từ `stepper.steps.*`). */
function RemainingStepsCard() {
  const { t } = useTranslation("onboarding");
  return (
    <div className="flex flex-col gap-3 rounded-2xl border border-border bg-surface p-5">
      <div>
        <p className="text-body font-semibold text-text-primary">{t("web.cat.nextSteps.title")}</p>
        <p className="text-caption text-text-secondary">{t("web.cat.nextSteps.subtitle")}</p>
      </div>
      <ol className="flex flex-col gap-2.5">
        {REMAINING_STEPS.map(({ step, key }) => (
          <li key={key} className="flex items-center gap-3">
            <span className="flex size-7 shrink-0 items-center justify-center rounded-full bg-info text-caption font-bold text-primary-dark">
              {step}
            </span>
            <span className="text-caption text-text-secondary">{t(`stepper.steps.${key}`)}</span>
          </li>
        ))}
      </ol>
    </div>
  );
}
export function OnboardingCatPage() {
  const { t } = useTranslation(["onboarding", "common"]);
  const navigate = useNavigate();
  const { catDraft, createdCat, updateCatDraft, setCreatedCat, setStep } = useOnboardingStore();
  const { data: breeds, isLoading: breedsLoading } = useBreeds();
  const createCat = useCreateCat();
  const updateCat = useUpdateCat(createdCat?.id ?? "");
  const uploadAvatar = useUploadAvatar();

  const [avatarPreview, setAvatarPreview] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    watch,
    reset,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<CatProfileFormValues>({
    resolver: zodResolver(catProfileSchema),
    defaultValues: {
      name: catDraft.name,
      breedCode: catDraft.breedCode,
      sex: catDraft.sex,
      birthDate: catDraft.birthDate,
      weightKg: catDraft.weightKg,
    },
  });

  const watchedSex = watch("sex");
  const avatarFile = catDraft.avatarFile;
  const neutered = catDraft.neutered;

  // --- Preview thẻ hồ sơ (cột phải desktop) — dựng từ chính giá trị form, không mock. ---
  const { data: phBands } = usePhBands();
  const watchedName = watch("name");
  const watchedBreedCode = watch("breedCode");
  const watchedBirthDate = watch("birthDate");
  const watchedWeightKg = watch("weightKg");
  const selectedBreed = breeds?.items.find((b) => b.code === watchedBreedCode);
  const previewAgeMonths = monthsSinceBirth(watchedBirthDate);
  const previewAgeText =
    previewAgeMonths == null
      ? t("web.cat.card.unknown")
      : previewAgeMonths >= 12
        ? t("success.summary.ageYears", {
            years: Math.floor(previewAgeMonths / 12),
            months: previewAgeMonths % 12,
          })
        : t("success.summary.ageMonthsOnly", { months: previewAgeMonths });
  const previewIdentityLine = [
    watchedSex ? t(`cat.sex.${watchedSex.toLowerCase()}`) : t("web.cat.card.unknown"),
    neutered ? t("success.summary.neutered") : t("success.summary.notNeutered"),
  ].join(" · ");
  const previewMetaLine = [
    previewAgeText,
    watchedWeightKg ? t("web.cat.card.weight", { weight: watchedWeightKg }) : t("web.cat.card.unknown"),
  ].join(" · ");

  useEffect(() => {
    if (avatarFile) {
      const url = URL.createObjectURL(avatarFile);
      setAvatarPreview(url);
      return () => {
        URL.revokeObjectURL(url);
      };
    }
    setAvatarPreview(null);
  }, [avatarFile]);

  const isSaving = isSubmitting || createCat.isPending || updateCat.isPending || uploadAvatar.isPending;

  const persistDraft = (values: CatProfileFormValues) => {
    updateCatDraft({
      name: values.name,
      breedCode: values.breedCode,
      // zodResolver đã validate non-empty (min(1)) trước khi handleSubmit gọi tới đây —
      // ép kiểu CatSex là an toàn ở đây (3 OptionCard chỉ phát "MALE"/"FEMALE"/"UNKNOWN").
      sex: values.sex as CatSex,
      birthDate: values.birthDate,
      weightKg: values.weightKg,
    });
  };

  const submitCat = async (values: CatProfileFormValues): Promise<boolean> => {
    setSubmitError(null);
    persistDraft(values);
    let cat: CreatedCat;
    try {
      const payload = {
        name: values.name,
        breedCode: values.breedCode,
        sex: values.sex as CatSex,
        neutered,
        birthDate: values.birthDate,
        weightKg: values.weightKg,
      };
      cat = createdCat
        ? await updateCat.mutateAsync({ ...payload, avatarFile: null })
        : await createCat.mutateAsync({ ...payload, avatarFile: null });
    } catch (error) {
      if (isApiError(error) && error.code === "CAT_PROFILE_LIMIT_REACHED") {
        setSubmitError(t("cat.limitError.description", { max: 1 }));
      } else {
        setSubmitError(t("errors.generic"));
      }
      return false;
    }

    // Persist the id immediately after POST/PATCH succeeds. Avatar upload is a separate request;
    // if it fails, retrying must PATCH this cat instead of creating another row in `cat`.
    setCreatedCat(cat);

    if (avatarFile) {
      try {
        const { avatarUrl } = await uploadAvatar.mutateAsync({ catId: cat.id, file: avatarFile });
        cat = { ...cat, avatarUrl };
      } catch {
        setSubmitError(t("errors.generic"));
        return false;
      }
    }

    setCreatedCat(cat);
    return true;
  };

  const onSubmit = async (values: CatProfileFormValues) => {
    const ok = await submitCat(values);
    if (!ok) return;
    setStep(2);
    void navigate("/onboarding/health-survey");
  };

  const onAddAnother = async (values: CatProfileFormValues) => {
    setSubmitError(null);
    try {
      await createCat.mutateAsync({
        name: values.name,
        breedCode: values.breedCode,
        sex: values.sex as CatSex,
        neutered,
        birthDate: values.birthDate,
        weightKg: values.weightKg,
        avatarFile: null,
      });
      toast.success(t("cat.addedToast"));
      reset({ name: "", breedCode: "", sex: "", birthDate: "", weightKg: "" });
      updateCatDraft({ name: "", breedCode: "", sex: "", birthDate: "", weightKg: "" });
    } catch (error) {
      if (isApiError(error) && error.code === "CAT_PROFILE_LIMIT_REACHED") {
        setSubmitError(t("cat.limitError.description", { max: 1 }));
      } else {
        setSubmitError(t("errors.generic"));
      }
    }
  };

  return (
    <OnboardingShell
      step={1}
      title={t("cat.title")}
      subtitle={t("cat.subtitle")}
      panel
      onBack={undefined}
      onStepClick={(step) => {
        if (step === 1) {
          void navigate("/onboarding/cat");
        }
      }}
      aside={
        <>
          <CatIdCardPreview
            name={watchedName}
            breedName={selectedBreed?.name}
            identityLine={previewIdentityLine}
            metaLine={previewMetaLine}
            avatarUrl={avatarPreview ?? createdCat?.avatarUrl ?? null}
            publicCode={createdCat?.publicCode}
            bands={phBands ?? []}
          />
          <RemainingStepsCard />
          <p className="text-small text-text-tertiary">{t("success.footer")}</p>
        </>
      }
      footer={
        <div className="flex flex-col gap-3">
          {submitError ? (
            <div role="alert" className="rounded-lg border border-danger bg-danger-bg p-3">
              <p className="text-body font-semibold text-danger-text">{t("cat.limitError.title")}</p>
              <p className="text-small text-danger-text">{submitError}</p>
            </div>
          ) : null}
          <Button
            type="button"
            size="lg"
            loading={isSaving}
            className="w-full"
            disabled={isSaving}
            onClick={() => {
              void handleSubmit(onSubmit)();
            }}
          >
            {isSaving ? t("cat.saving") : t("cat.submit")}
            {isSaving ? null : <ArrowRight className="size-5" aria-hidden="true" />}
          </Button>
          <Button
            type="button"
            variant="tertiary"
            size="md"
            className="w-full"
            disabled={isSaving}
            leftIcon={<CirclePlus className="size-5" aria-hidden="true" />}
            onClick={() => {
              void handleSubmit(onAddAnother)();
            }}
          >
            {t("cat.addAnother")}
          </Button>
        </div>
      }
    >
      <div className="flex flex-col gap-6">
        <AvatarUpload
          file={avatarFile}
          previewUrl={avatarPreview}
          onChange={(file) => {
            updateCatDraft({ avatarFile: file });
          }}
        />

        {/* Desktop: tên + giống đứng cạnh nhau (W1 Web-01c-3). */}
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2 lg:items-start">
          <div className="flex flex-col gap-1.5">
            <FieldLabel htmlFor="catName" required>
              {t("cat.name.label")}
            </FieldLabel>
            <Input
              id="catName"
              placeholder={t("cat.name.placeholder")}
              autoComplete="off"
              leftIcon={<IdCard className="size-5" aria-hidden="true" />}
              {...register("name")}
            />
            <FieldError message={errors.name?.message} />
          </div>

          <BreedPicker
            breeds={breeds?.items}
            loading={breedsLoading}
            value={watchedBreedCode}
            onChange={(code) => {
              setValue("breedCode", code, { shouldValidate: true });
            }}
            error={errors.breedCode?.message}
          />
        </div>

        <fieldset className="flex flex-col gap-3">
          <legend className="sr-only">{t("cat.sex.label")}</legend>
          <FieldLabel required>{t("cat.sex.label")}</FieldLabel>
          {/* 3 thẻ cùng một hàng, icon trên — 2 cột (M1 01c-3) để thẻ "Chưa rõ" mồ côi ở hàng
              2, còn icon trái ở 3 cột thì "Chưa xác định" bị bẻ dòng. */}
          <div className="grid grid-cols-3 gap-3">
            {SEX_OPTIONS.map(({ value, icon }) => (
              <OptionCard
                key={value}
                type="radio"
                name="sex"
                value={value}
                checked={watchedSex === value}
                onChange={(next) => {
                  setValue("sex", next, { shouldValidate: true });
                }}
                title={t(`cat.sex.${value.toLowerCase()}`)}
                description={t(`cat.sex.${value.toLowerCase()}Desc`)}
                icon={icon}
                layout="stack"
              />
            ))}
          </div>
          <FieldError message={errors.sex?.message} />
        </fieldset>

        <Checkbox
          checked={neutered}
          onChange={(checked) => {
            updateCatDraft({ neutered: checked });
          }}
          label={t("cat.neutered.label")}
        />

        {/* `md:` chứ không `sm:` (sm = 375px): ở 390px hai cột làm ô ngày sinh native bị cắt
            mất "yyyy". */}
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <div className="flex flex-col gap-1.5">
            <FieldLabel htmlFor="birthDate">{t("cat.birthDate.label")}</FieldLabel>
            {/* M1 01c-3: icon lịch bên trái, cùng khung với ô cân nặng. */}
            <Input
              id="birthDate"
              type="date"
              max={new Date().toISOString().slice(0, 10)}
              leftIcon={<CalendarDays className="size-5" aria-hidden="true" />}
              helperText={t("cat.birthDate.hint")}
              error={errors.birthDate ? t(errors.birthDate.message ?? "") : undefined}
              {...register("birthDate")}
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <FieldLabel htmlFor="weightKg">{t("cat.weight.label")}</FieldLabel>
            {/* M1 01c-3: icon cân bên trái, đơn vị "kg" nằm trong khung ở mép phải. */}
            <Input
              id="weightKg"
              type="number"
              inputMode="decimal"
              step="0.1"
              min="0.5"
              max="15"
              placeholder={t("cat.weight.placeholder")}
              leftIcon={<Scale className="size-5" aria-hidden="true" />}
              rightSlot={
                <span className="pr-2 text-caption text-text-tertiary" aria-hidden="true">
                  {t("cat.weight.unit")}
                </span>
              }
              error={errors.weightKg ? t(errors.weightKg.message ?? "") : undefined}
              {...register("weightKg")}
            />
          </div>
        </div>
      </div>
    </OnboardingShell>
  );
}
