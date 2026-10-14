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
  refreshOnboardingSession,
  useActivateCode,
  useCreditBalance,
  useCatSummary,
  usePrimaryCat,
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
