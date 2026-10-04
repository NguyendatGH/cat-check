// features/onboarding — luồng thiết lập ban đầu 5 bước (p9 §9.4.3 route #17–21, p3 F4–F5).
// Mọi import từ bên ngoài PHẢI qua file này (boundaries/entry-point).
//
// GHI CHÚ CẤU TRÚC: toàn bộ code nằm ở các file root của feature (không có thư mục con)
// vì eslint.config.js thiếu policy `feature → feature` nên import giữa các file cùng
// feature bị rule `boundaries/dependencies` chặn. W3 bổ sung policy trong config thì tách
// lại thư mục components/ + mocks/ (xem docs/handovers/A7.md).
export {
  OnboardingShell,
  OnboardingStepper,
  OptionCard,
  PhBandBar,
  QuestionField,
  AvatarUpload,
  BreedPicker,
  ActivationCodeInput,
  toCanonicalCode,
  DisclaimerScroll,
  Checkbox,
  FieldError,
  FieldLabel,
} from "./components";
export { useOnboardingStore } from "./store";
export { catProfileSchema, surveySchema, isValidActivationCode, normalizeActivationCode } from "./schemas";
export type { CatProfileFormValues, SurveyFormValues } from "./schemas";
export {
  useBreeds,
  useHealthSurveyDefinition,
  usePhBands,
  useCreateCat,
  useUpdateCat,
  useUploadAvatar,
  useSubmitSurvey,
  useActivateCode,
  useCreditBalance,
  useCatSummary,
} from "./hooks";
export { apiFetch, apiUploadAvatar } from "./api";
export { handlers, worker } from "./mocks";
export type {
  HealthSurveyDefinition,
  SurveyQuestionDefinition,
  SurveyQuestionOption,
  OnboardingStep,
  CatSex,
  CatProfileDraft,
  CreatedCat,
  Breed,
  SurveyAnswers,
  ActivationResult,
  CreditBatch,
  CreditBalance,
  PhBand,
  CatSummary,
} from "./types";
export { ONBOARDING_STEPS } from "./types";
export { EMPTY_CAT_DRAFT, EMPTY_SURVEY_ANSWERS, SURVEY_QUESTIONNAIRE_VERSION } from "./types";
