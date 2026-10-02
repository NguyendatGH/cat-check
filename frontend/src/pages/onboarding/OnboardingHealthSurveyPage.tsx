import { useState, type ReactNode } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import {
  CheckCircle2,
  AlertTriangle,
  HelpCircle,
  Cookie,
  Soup,
  Scale,
  Fish,
  ShieldCheck,
  FlaskConical,
  Camera,
  Droplets,
  PawPrint,
} from "lucide-react";
import { Button, Dialog, DialogContent, DialogDescription, DialogTitle } from "@/shared/ui";
import {
  OnboardingShell,
  OptionCard,
  QuestionField,
  surveySchema,
  useOnboardingStore,
  usePhBands,
  useSubmitSurvey,
  type CreatedCat,
  type PhBand,
  type SurveyFormValues,
} from "@/features/onboarding";
import litterSilica from "@/shared/assets/images/onboarding-litter-silica.jpg";
import litterBentonite from "@/shared/assets/images/onboarding-litter-bentonite.jpg";
import litterOrganic from "@/shared/assets/images/onboarding-litter-organic.jpg";
import { cn } from "@/shared/lib/cn";

const QUESTION_NAMES = [
  "litterType",
  "urinaryHistory",
  "dietType",
  "urinationFrequency",
] as const;

type OptionMap = Record<string, string>;

/** Ảnh thật từng loại cát (M1 01c-4) — trích từ SVG thiết kế gốc, không dùng cho SILICA vì
 * Figma không mock lựa chọn này riêng (trùng hình với CATCHECK_SMART trong bản thiết kế). */
const LITTER_TYPE_IMAGES: Partial<Record<string, string>> = {
  CATCHECK_SMART: litterSilica,
  BENTONITE: litterBentonite,
  TOFU_WOOD: litterOrganic,
};

const ICON_PROPS = { size: 20, "aria-hidden": true } as const;

/** Icon từng lựa chọn — khớp Figma M1 01c-4. `urinationFrequency`/`symptoms` KHÔNG có icon
 * vì Figma không mock 2 câu hỏi này (chỉ 4 câu: litter/tiền sử/ăn uống/lịch quét — lịch quét
 * thuộc M5 reminder, đã loại theo ORCHESTRATOR §2, thay bằng `urinationFrequency` có sẵn
 * trong `SurveyAnswers`); không tự bịa icon khi không có nguồn thiết kế. */
const OPTION_ICONS: Partial<Record<string, Partial<Record<string, ReactNode>>>> = {
  // SILICA là lựa chọn duy nhất không có ảnh thật trong Figma — dùng icon để 4 thẻ cùng
  // chiều cao và cùng có khối dẫn bên trái.
  litterType: { SILICA: <Droplets {...ICON_PROPS} /> },
  urinaryHistory: {
    NONE: <CheckCircle2 {...ICON_PROPS} />,
    CYSTITIS_OR_STONES: <AlertTriangle {...ICON_PROPS} />,
    UNKNOWN: <HelpCircle {...ICON_PROPS} />,
  },
  dietType: {
    DRY: <Cookie {...ICON_PROPS} />,
    WET: <Soup {...ICON_PROPS} />,
    MIXED: <Scale {...ICON_PROPS} />,
    RAW: <Fish {...ICON_PROPS} />,
  },
};

/** Bố cục lưới từng câu — M1 01c-4: câu 1 & 2 xếp dọc trên mobile, câu 3 hai cột. */
const QUESTION_GRID: Record<(typeof QUESTION_NAMES)[number], string> = {
  litterType: "grid-cols-1 lg:grid-cols-2",
  urinaryHistory: "grid-cols-1 lg:grid-cols-2",
  dietType: "grid-cols-2",
  urinationFrequency: "grid-cols-1 sm:grid-cols-2",
};

/** Câu hỏi dùng thẻ xếp dọc (icon trên, chữ dưới) — M1 01c-4 câu "Chế độ ăn". */
const STACKED_QUESTIONS = new Set<string>(["dietType"]);

/** Câu hỏi có mô tả phụ dưới tên lựa chọn (M1 01c-4 / W1 Web-01c-4). */
const QUESTIONS_WITH_DESCRIPTION = new Set<string>(["litterType", "dietType"]);

/** Câu hỏi có chú thích căn phải ở nhãn (M1 01c-4: "Chọn 1", "Ảnh hưởng độ pH"). */
const QUESTIONS_WITH_META = new Set<string>(["litterType", "dietType"]);

/** Lựa chọn tiền sử có chú thích phụ căn phải (M1 01c-4). */
const URINARY_NOTE_VALUES = new Set<string>(["CYSTITIS_OR_STONES", "UNKNOWN"]);

/** Lựa chọn được gắn nhãn "Khuyến dùng" trong thiết kế (M1 01c-4 câu 1). */
const RECOMMENDED_LITTER = "CATCHECK_SMART";

const GUIDE_STEPS = ["step1", "step2", "step3"] as const;

/** Thanh dải pH: màu theo `severity` (token `--color-ph-*` chỉ khai ở `:root`). */
const PH_SEVERITY_BAR: Record<PhBand["severity"], string> = {
  NORMAL: "bg-[var(--color-ph-normal)]",
  ATTENTION: "bg-[var(--color-ph-mild)]",
  WATCH: "bg-[var(--color-ph-abnormal)]",
  NEUTRAL: "bg-[var(--color-ph-unknown)]",
};

interface SurveyAsideProps {
  cat: CreatedCat | null;
  bands: PhBand[];
}

/**
 * Cột phải desktop (W1 Web-01c-4): thẻ nhận diện bé mèo (dữ liệu THẬT từ `createdCat`),
 * dải pH tham chiếu (`GET /reference/ph-bands`) và 3 bước hướng dẫn quét lần đầu.
 *
 * KHÔNG dựng card "Phân tích Sơ bộ của AI / Mức rủi ro FLUTD ban đầu" của mockup: backend
 * không trả chỉ số nào như vậy và nó là phát ngôn y tế (W1 §5 đã đánh dấu mâu thuẫn với
 * quyết định #6). Khối bảo mật "HIPAA" cũng bị bỏ — W1 §5 ghi rõ HIPAA không áp dụng ở VN.
 */
function SurveyAside({ cat, bands }: SurveyAsideProps) {
  const { t } = useTranslation("onboarding");
  const normalBand = bands.find((b) => b.severity === "NORMAL");

  const ageText =
    cat?.ageMonths == null
      ? null
      : cat.ageMonths >= 12
        ? t("success.summary.ageYears", {
            years: Math.floor(cat.ageMonths / 12),
            months: cat.ageMonths % 12,
          })
        : t("success.summary.ageMonthsOnly", { months: cat.ageMonths });

  return (
    <>
      {cat ? (
        <div className="flex items-center gap-4 rounded-2xl border border-border bg-surface p-5">
          {cat.avatarUrl ? (
            <img src={cat.avatarUrl} alt="" className="size-14 shrink-0 rounded-full object-cover" />
          ) : (
            <span className="flex size-14 shrink-0 items-center justify-center rounded-full bg-chip-bg text-h3 font-bold text-primary">
              {cat.name.charAt(0).toUpperCase()}
            </span>
          )}
          <div className="flex min-w-0 flex-col">
            <p className="flex items-center gap-2">
              <span className="truncate text-body font-bold text-text-primary">{cat.name}</span>
              {ageText ? (
                <span className="shrink-0 rounded-full bg-chip-bg px-2 py-0.5 text-small font-semibold text-primary">
                  {ageText}
                </span>
              ) : null}
            </p>
            <p className="truncate text-caption text-text-secondary">
              {cat.breedName}
              {" · "}
              {t(`success.summary.sex.${cat.sex.toLowerCase()}`)}
              {" · "}
              {cat.neutered ? t("success.summary.neutered") : t("success.summary.notNeutered")}
            </p>
          </div>
          <ShieldCheck
            className="ml-auto size-5 shrink-0 text-[var(--color-ph-normal-text)]"
            aria-label={t("web.survey.catCard.created")}
          />
        </div>
      ) : null}

      <div className="flex flex-col gap-3 rounded-2xl border border-border bg-surface p-5">
        <p className="flex items-center gap-2 text-body font-semibold text-text-primary">
          <FlaskConical className="size-5 text-primary" aria-hidden="true" />
          {t("web.survey.phCard.title")}
        </p>
        {normalBand ? (
          <>
            <p className="text-h2 font-bold text-[var(--color-ph-normal-text)]">
              {normalBand.phMin}–{normalBand.phMax}
            </p>
            <div className="flex h-2 overflow-hidden rounded-full">
              {bands.map((band) => (
                <span
                  key={band.code}
                  className={cn("h-full flex-1", PH_SEVERITY_BAR[band.severity])}
                  aria-hidden="true"
                />
              ))}
            </div>
            <p className="text-caption text-text-secondary">
              {t("web.survey.phCard.body", { catName: cat?.name ?? "" })}
            </p>
          </>
        ) : (
          <p className="text-caption text-text-tertiary">{t("web.survey.phCard.loading")}</p>
        )}
      </div>

      <div className="flex flex-col gap-4 rounded-2xl border border-border bg-surface p-5">
        <p className="flex items-center gap-2 text-body font-semibold text-text-primary">
          <Camera className="size-5 text-primary" aria-hidden="true" />
          {t("web.survey.guide.title")}
        </p>
        <ol className="flex flex-col gap-3.5">
          {GUIDE_STEPS.map((key, index) => (
            <li key={key} className="flex gap-3">
              <span className="flex size-7 shrink-0 items-center justify-center rounded-full bg-info text-caption font-bold text-primary-dark">
                {index + 1}
              </span>
              <span className="flex flex-col gap-0.5">
                <span className="text-caption font-semibold text-text-primary">
                  {t(`web.survey.guide.${key}.title`)}
                </span>
                <span className="text-caption text-text-secondary">
                  {t(`web.survey.guide.${key}.body`)}
                </span>
              </span>
            </li>
          ))}
        </ol>
      </div>

      <p className="text-small text-text-tertiary">{t("success.footer")}</p>
    </>
  );
}

/**
 * Bước 2/5 — Khảo sát sức khoẻ ban đầu (M1 01c-4, W1 Web-01c-4; p2 US-E2-02).
 * 5 câu hỏi — khớp JSONB `answers` của `cat_health_survey` (p4 C3). Bỏ qua được
 * (ghi `skipped = true`), hoàn tất sau trong Cài đặt.
 *
 * Desktop (`lg:`): 2 cột ~7/5 — bộ câu hỏi bên trái, panel bối cảnh bên phải
 * ({@link SurveyAside}). Mobile giữ nguyên 1 cột.
 */
export function OnboardingHealthSurveyPage() {
  const { t } = useTranslation(["onboarding", "common"]);
  const navigate = useNavigate();

  const questionOptions = (key: string): OptionMap => t(key, { returnObjects: true }) as OptionMap;
  const { createdCat, surveyAnswers, setSurveyAnswers, setSurveySkipped, setStep } = useOnboardingStore();
  const submitSurvey = useSubmitSurvey(createdCat?.id ?? "");
  const { data: phBands } = usePhBands();
  const [skipOpen, setSkipOpen] = useState(false);

  const {
    handleSubmit,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<SurveyFormValues>({
    resolver: zodResolver(surveySchema),
    defaultValues: {
      litterType: surveyAnswers.litterType,
      urinaryHistory: surveyAnswers.urinaryHistory,
      dietType: surveyAnswers.dietType,
      urinationFrequency: surveyAnswers.urinationFrequency,
      symptoms: surveyAnswers.symptoms,
    },
  });

  const watchedSymptoms = watch("symptoms");
  const isSaving = isSubmitting || submitSurvey.isPending;

  const handleSymptomChange = (value: string, checked: boolean) => {
    let next: string[];
    if (value === "NONE") {
      next = checked ? ["NONE"] : [];
    } else {
      next = checked
        ? [...watchedSymptoms.filter((s) => s !== "NONE"), value]
        : watchedSymptoms.filter((s) => s !== value);
    }
    setValue("symptoms", next, { shouldValidate: true });
  };

  const onSubmit = async (values: SurveyFormValues) => {
    if (!createdCat) return;
    setSurveyAnswers(values);
    try {
      await submitSurvey.mutateAsync({ answers: values, skipped: false });
      setStep(3);
      void navigate("/onboarding/disclaimer");
    } catch {
      // lỗi mạng — giữ nguyên màn, user thử lại
    }
  };

  const onSkip = async () => {
    if (!createdCat) return;
    setSkipOpen(false);
    setSurveySkipped(true);
    try {
      await submitSurvey.mutateAsync({ answers: surveyAnswers, skipped: true });
    } catch {
      // vẫn cho tiếp tục dù API lỗi — skipped là tùy chọn
    }
    setStep(3);
    void navigate("/onboarding/disclaimer");
  };

  const catName = createdCat?.name ?? "";

  return (
    <OnboardingShell
      step={2}
      title={t("survey.title")}
      subtitle={t("survey.subtitle", { catName })}
      panel
      onBack={() => { void navigate("/onboarding/cat"); }}
      onStepClick={(step) => {
        if (step === 1) { void navigate("/onboarding/cat"); }
        if (step === 2) { void navigate("/onboarding/health-survey"); }
      }}
      aside={<SurveyAside cat={createdCat} bands={phBands ?? []} />}
      footer={
        <div className="flex flex-col gap-3">
          <Button
            type="button"
            size="lg"
            loading={isSaving}
            className="w-full"
            disabled={isSaving || !createdCat}
            onClick={() => { void handleSubmit(onSubmit)(); }}
          >
            {isSaving ? t("survey.saving") : t("survey.submit")}
          </Button>
          <Button
            type="button"
            variant="tertiary"
            size="md"
            className="w-full"
            disabled={isSaving}
            onClick={() => { setSkipOpen(true); }}
          >
            {t("survey.skip")}
          </Button>
        </div>
      }
    >
      <div className="flex flex-col gap-6">
        {/* M1 01c-4: chip vàng nhạt + icon chân mèo, không phải chip xanh. */}
        <p className="inline-flex w-fit items-center gap-2 rounded-full bg-secondary-light px-3 py-1 text-caption font-semibold text-secondary-text-on">
          <PawPrint className="size-4" aria-hidden="true" />
          {t("survey.forCat", { catName })}
        </p>

        {QUESTION_NAMES.map((name, index) => (
          <QuestionField
            key={name}
            number={index + 1}
            label={t(`survey.questions.${name}.label`)}
            meta={QUESTIONS_WITH_META.has(name) ? t(`survey.questions.${name}.meta`) : undefined}
            error={errors[name]?.message}
          >
            <div className={cn("grid gap-3", QUESTION_GRID[name])}>
              {Object.entries(questionOptions(`survey.questions.${name}.options`)).map(
                ([value, label]) => (
                  <OptionCard
                    key={value}
                    type="radio"
                    name={name}
                    value={value}
                    checked={watch(name) === value}
                    onChange={(v) => { setValue(name, v, { shouldValidate: true }); }}
                    title={label}
                    description={
                      QUESTIONS_WITH_DESCRIPTION.has(name)
                        ? t(`survey.questions.${name}.descriptions.${value}`)
                        : undefined
                    }
                    meta={
                      name === "urinaryHistory" && URINARY_NOTE_VALUES.has(value)
                        ? t(`survey.questions.urinaryHistory.notes.${value}`)
                        : undefined
                    }
                    badge={
                      name === "litterType" && value === RECOMMENDED_LITTER
                        ? t("survey.questions.litterType.recommended")
                        : undefined
                    }
                    layout={STACKED_QUESTIONS.has(name) ? "stack" : "row"}
                    imageUrl={name === "litterType" ? LITTER_TYPE_IMAGES[value] : undefined}
                    icon={OPTION_ICONS[name]?.[value]}
                  />
                ),
              )}
            </div>
          </QuestionField>
        ))}

        <QuestionField
          label={t("survey.questions.symptoms.label")}
          hint={t("survey.questions.symptoms.hint")}
          error={errors.symptoms?.message}
        >
          <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
            {Object.entries(questionOptions("survey.questions.symptoms.options")).map(
              ([value, label]) => (
                <OptionCard
                  key={value}
                  type="checkbox"
                  name="symptoms"
                  value={value}
                  checked={watchedSymptoms.includes(value)}
                  onChange={() => { handleSymptomChange(value, !watchedSymptoms.includes(value)); }}
                  title={label}
                />
              ),
            )}
          </div>
        </QuestionField>
      </div>

      <Dialog open={skipOpen} onOpenChange={setSkipOpen}>
        <DialogContent>
          <DialogTitle>{t("survey.skipConfirm.title")}</DialogTitle>
          <DialogDescription>{t("survey.skipConfirm.description")}</DialogDescription>
          <div className="mt-4 flex flex-col gap-3">
            <Button type="button" variant="tertiary" onClick={() => { void onSkip(); }}>
              {t("survey.skipConfirm.confirm")}
            </Button>
            <Button type="button" onClick={() => { setSkipOpen(false); }}>
              {t("survey.skipConfirm.cancel")}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </OnboardingShell>
  );
}
