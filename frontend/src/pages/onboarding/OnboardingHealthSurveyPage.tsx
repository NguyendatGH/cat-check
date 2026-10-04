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
import { Button, Dialog, DialogContent, DialogDescription, DialogTitle, ErrorState, SkeletonLoader } from "@/shared/ui";
import {
  OnboardingShell,
  OptionCard,
  PhBandBar,
  QuestionField,
  surveySchema,
  useHealthSurveyDefinition,
  useOnboardingStore,
  usePhBands,
  useSubmitSurvey,
  EMPTY_SURVEY_ANSWERS,
  type CreatedCat,
  type PhBand,
  type SurveyFormValues,
  type SurveyQuestionDefinition,
} from "@/features/onboarding";
import litterSilica from "@/shared/assets/images/onboarding-litter-silica.jpg";
import litterBentonite from "@/shared/assets/images/onboarding-litter-bentonite.jpg";
import litterOrganic from "@/shared/assets/images/onboarding-litter-organic.jpg";
import { cn } from "@/shared/lib/cn";

/**
 * Tên trường hợp lệ của form = chính shape của `surveySchema` (nguồn kiểu duy nhất). Danh
 * sách/thứ tự/lựa chọn CÂU HỎI đến từ F6 `GET /reference/health-survey/{version}`; chỗ này
 * chỉ là cầu nối kiểu để `react-hook-form` biết trường nào là chuỗi, trường nào là mảng.
 */
type SurveyFieldName = keyof SurveyFormValues;
type MultiFieldName = { [K in SurveyFieldName]: SurveyFormValues[K] extends string[] ? K : never }[SurveyFieldName];
type SingleFieldName = Exclude<SurveyFieldName, MultiFieldName>;

const SURVEY_FIELD_NAMES = Object.keys(surveySchema.shape) as SurveyFieldName[];

function isSurveyField(key: string): key is SurveyFieldName {
  return (SURVEY_FIELD_NAMES as string[]).includes(key);
}

function isMultiField(key: SurveyFieldName): key is MultiFieldName {
  return Array.isArray(EMPTY_SURVEY_ANSWERS[key]);
}

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
 * trong `SurveyAnswers`); không tự bịa icon khi không có nguồn thiết kế — 2 câu đó nhận ô
 * radio/checkbox mặc định của `OptionCard` như bản web W1 Web-01c-4. */
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

/**
 * Bố cục lưới từng câu — M1 01c-4: câu 1 & 2 xếp dọc trên mobile, câu 3 hai cột. Đây là dữ
 * liệu THIẾT KẾ theo khoá câu hỏi, không phải định nghĩa câu hỏi; khoá lạ (backend thêm câu
 * mới) rơi về `QUESTION_GRID_DEFAULT`.
 */
const QUESTION_GRID: Record<string, string> = {
  litterType: "grid-cols-1 lg:grid-cols-2",
  // 1 cột ở mọi breakpoint (M1 01c-4): mỗi lựa chọn có chú thích phụ căn phải, 2 cột làm
  // chú thích vỡ 3 dòng trong thẻ cột trái bản web.
  urinaryHistory: "grid-cols-1",
  dietType: "grid-cols-2",
  urinationFrequency: "grid-cols-1 sm:grid-cols-2",
  symptoms: "grid-cols-1 md:grid-cols-2",
};

const QUESTION_GRID_DEFAULT = "grid-cols-1 sm:grid-cols-2";

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

interface SurveyAsideProps {
  cat: CreatedCat | null;
  catName: string;
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
function SurveyAside({ cat, catName, bands }: SurveyAsideProps) {
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
            <PhBandBar bands={bands} />
            <p className="text-caption text-text-secondary">{t("web.survey.phCard.body", { catName })}</p>
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
                <span className="text-caption text-text-secondary">{t(`web.survey.guide.${key}.body`)}</span>
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

  const { createdCat, surveyAnswers, setSurveyAnswers, setSurveySkipped, setStep } = useOnboardingStore();
  const submitSurvey = useSubmitSurvey(createdCat?.id ?? "");
  const { data: phBands } = usePhBands();
  // F6 — bộ câu hỏi THẬT. Thứ tự, kiểu (SINGLE/MULTI) và danh sách lựa chọn đều của server.
  const definitionQuery = useHealthSurveyDefinition();
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

  const isSaving = isSubmitting || submitSurvey.isPending;

  /**
   * Câu MULTI: `NONE` loại trừ mọi lựa chọn khác (và ngược lại) — quy tắc nghiệp vụ của
   * `cat_health_survey.answers`, không phải của một câu hỏi cụ thể nào.
   */
  const toggleMultiValue = (name: MultiFieldName, current: string[], value: string, checked: boolean) => {
    let next: string[];
    if (value === "NONE") {
      next = checked ? ["NONE"] : [];
    } else {
      next = checked ? [...current.filter((v) => v !== "NONE"), value] : current.filter((v) => v !== value);
    }
    setValue(name, next, { shouldValidate: true });
  };

  /** Lựa chọn nào có mô tả/chú thích riêng trong i18n thì dùng, không có thì bỏ qua. */
  const optionalText = (key: string): string | undefined => t(key, { defaultValue: "" }) || undefined;

  /**
   * Chỉ render câu hỏi mà schema client biết: backend thêm câu mới (version sau) thì trường
   * đó chưa có chỗ trong `SurveyFormValues`, hiển thị nó ra sẽ tạo ô nhập không bao giờ gửi
   * đi được. Bỏ qua lặng lẽ an toàn hơn là dựng ô giả.
   */
  const questions: SurveyQuestionDefinition[] = [...(definitionQuery.data?.questions ?? [])]
    .filter((q) => isSurveyField(q.key))
    .sort((a, b) => a.sortOrder - b.sortOrder);

  const renderQuestion = (question: SurveyQuestionDefinition, index: number) => {
    if (!isSurveyField(question.key)) return null;
    const name = question.key;
    const grid = cn("grid gap-3", QUESTION_GRID[name] ?? QUESTION_GRID_DEFAULT);
    const label = t(question.labelKey);
    const multi = question.type === "MULTI";

    if (multi && isMultiField(name)) {
      const selected = watch(name);
      return (
        <QuestionField
          key={name}
          number={index + 1}
          label={label}
          hint={optionalText(`survey.questions.${name}.hint`)}
          error={errors[name]?.message}
        >
          <div className={grid}>
            {question.options.map((option) => (
              <OptionCard
                key={option.code}
                type="checkbox"
                name={name}
                value={option.code}
                checked={selected.includes(option.code)}
                onChange={() => {
                  toggleMultiValue(name, selected, option.code, !selected.includes(option.code));
                }}
                title={t(option.labelKey)}
              />
            ))}
          </div>
        </QuestionField>
      );
    }

    const singleName = name as SingleFieldName;
    return (
      <QuestionField
        key={name}
        number={index + 1}
        label={label}
        meta={QUESTIONS_WITH_META.has(name) ? optionalText(`survey.questions.${name}.meta`) : undefined}
        error={errors[singleName]?.message}
      >
        <div className={grid}>
          {question.options.map((option) => (
            <OptionCard
              key={option.code}
              type="radio"
              name={singleName}
              value={option.code}
              checked={watch(singleName) === option.code}
              onChange={(v) => {
                setValue(singleName, v, { shouldValidate: true });
              }}
              title={t(option.labelKey)}
              description={
                QUESTIONS_WITH_DESCRIPTION.has(name)
                  ? optionalText(`survey.questions.${name}.descriptions.${option.code}`)
                  : undefined
              }
              meta={
                name === "urinaryHistory" && URINARY_NOTE_VALUES.has(option.code)
                  ? optionalText(`survey.questions.urinaryHistory.notes.${option.code}`)
                  : undefined
              }
              badge={
                name === "litterType" && option.code === RECOMMENDED_LITTER
                  ? optionalText("survey.questions.litterType.recommended")
                  : undefined
              }
              layout={STACKED_QUESTIONS.has(name) ? "stack" : "row"}
              imageUrl={name === "litterType" ? LITTER_TYPE_IMAGES[option.code] : undefined}
              icon={OPTION_ICONS[name]?.[option.code]}
            />
          ))}
        </div>
      </QuestionField>
    );
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

  // Chưa có hồ sơ (vào thẳng URL) thì câu "…phù hợp cho {catName}." bị cụt thành "cho ."
  const catName = createdCat?.name ?? t("survey.yourCat");

  return (
    <OnboardingShell
      step={2}
      title={t("survey.title")}
      subtitle={t("survey.subtitle", { catName })}
      panel
      onBack={() => {
        void navigate("/onboarding/cat");
      }}
      onStepClick={(step) => {
        if (step === 1) {
          void navigate("/onboarding/cat");
        }
        if (step === 2) {
          void navigate("/onboarding/health-survey");
        }
      }}
      aside={<SurveyAside cat={createdCat} catName={catName} bands={phBands ?? []} />}
      footer={
        <div className="flex flex-col gap-3">
          <Button
            type="button"
            size="lg"
            loading={isSaving}
            className="w-full"
            disabled={isSaving || !createdCat}
            onClick={() => {
              void handleSubmit(onSubmit)();
            }}
          >
            {isSaving ? t("survey.saving") : t("survey.submit")}
          </Button>
          <Button
            type="button"
            variant="tertiary"
            size="md"
            className="w-full"
            disabled={isSaving}
            onClick={() => {
              setSkipOpen(true);
            }}
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

        {definitionQuery.isPending ? (
          <div className="flex flex-col gap-4">
            <SkeletonLoader shape="card" className="h-40" />
            <SkeletonLoader shape="card" className="h-40" />
          </div>
        ) : definitionQuery.isError ? (
          <ErrorState
            title={t("survey.definitionError")}
            onRetry={() => {
              void definitionQuery.refetch();
            }}
            retryLabel={t("actions.retry", { ns: "common" })}
          />
        ) : (
          questions.map(renderQuestion)
        )}
      </div>

      <Dialog open={skipOpen} onOpenChange={setSkipOpen}>
        <DialogContent>
          <DialogTitle>{t("survey.skipConfirm.title")}</DialogTitle>
          <DialogDescription>{t("survey.skipConfirm.description")}</DialogDescription>
          <div className="mt-4 flex flex-col gap-3">
            <Button
              type="button"
              variant="tertiary"
              onClick={() => {
                void onSkip();
              }}
            >
              {t("survey.skipConfirm.confirm")}
            </Button>
            <Button
              type="button"
              onClick={() => {
                setSkipOpen(false);
              }}
            >
              {t("survey.skipConfirm.cancel")}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </OnboardingShell>
  );
}
