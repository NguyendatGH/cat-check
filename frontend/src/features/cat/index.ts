// features/cat — CRUD hồ sơ mèo, ảnh đại diện, ghi chú, khảo sát sức khoẻ, dấu hiệu lâm
// sàng (p8 §8.4.4 nhóm D). Mọi import từ bên ngoài PHẢI qua file này (boundaries/entry-point).
//
// GHI CHÚ CẤU TRÚC: theo khuôn mẫu bắt buộc của `features/onboarding` (docs/handovers/A3-fe.md
// mục "Judgment calls") — code nằm ở các file root của feature (không thư mục con) để nhất
// quán với các agent khác đang chạy song song trong cùng wave.
export {
  AvatarUpload,
  ArchivedBadge,
  BreedPicker,
  CatFormFields,
  ClinicalSignEmergencyNotice,
  ClinicalSignPicker,
  ConfirmDialog,
  EmergencyDisclaimerBanner,
  FieldError,
  OptionCard,
  PrimaryBadge,
  SectionHeading,
} from "./components";
export type { EmergencyDisclaimerBannerProps } from "./components";
export {
  catFormSchema,
  clinicalSignFormSchema,
  noteFormSchema,
  surveyFormSchema,
  CAT_NAME_MAX,
  NOTE_BODY_MAX,
  WEIGHT_MAX_KG,
  WEIGHT_MIN_KG,
} from "./schemas";
export type {
  CatFormSchemaValues,
  ClinicalSignFormSchemaValues,
  NoteFormSchemaValues,
  SurveyFormSchemaValues,
} from "./schemas";
export {
  catKeys,
  useArchiveCat,
  useBreeds,
  useCat,
  useCatList,
  useCatNotes,
  useCatSummary,
  useCreateCat,
  useCreateNote,
  useDeleteNote,
  useHealthSurvey,
  usePatchCat,
  usePatchNote,
  useRemoveCatAvatar,
  useReportClinicalSigns,
  useSetPrimaryCat,
  useSoftDeleteCat,
  useSubmitHealthSurvey,
  useUnarchiveCat,
  useUploadCatAvatar,
} from "./hooks";
export { apiFetch, apiUploadAvatar } from "./api";
export { handlers, worker } from "./mocks";
export {
  EMPTY_CAT_FORM_DRAFT,
  MANUAL_CLINICAL_SIGN_SOURCE,
} from "./types";
export type {
  CatAgeMode,
  CatFormDraft,
  CatListFilter,
  CatMutationPayload,
  CatSummaryLastScan,
  CatSummaryResponse,
  ClinicalSignFormValues,
  HealthSurveyFormValues,
  NoteFormValues,
} from "./types";
