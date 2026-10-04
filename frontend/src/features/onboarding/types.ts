/**
 * Types cho luồng onboarding 5 bước (p9 §9.4.3 route #17–21, p3 F4–F5).
 * Tên field khớp hợp đồng API p8 (nhóm D/H) và schema p4 (cat, cat_health_survey).
 */

export type OnboardingStep = 1 | 2 | 3 | 4 | 5;

export const ONBOARDING_STEPS: OnboardingStep[] = [1, 2, 3, 4, 5];

export type CatSex = "MALE" | "FEMALE" | "UNKNOWN";

export interface CatProfileDraft {
  name: string;
  breedCode: string;
  sex: CatSex | "";
  neutered: boolean;
  birthDate: string;
  weightKg: string;
  avatarFile: File | null;
}

export const EMPTY_CAT_DRAFT: CatProfileDraft = {
  name: "",
  breedCode: "",
  sex: "",
  neutered: false,
  birthDate: "",
  weightKg: "",
  avatarFile: null,
};

export interface CreatedCat {
  id: string;
  publicCode: string;
  name: string;
  breedCode: string;
  breedName: string;
  sex: CatSex;
  neutered: boolean;
  birthDate: string | null;
  ageMonths: number | null;
  weightKg: number | null;
  avatarUrl: string | null;
}

export interface Breed {
  code: string;
  name: string;
}

export interface BreedListResponse {
  items: Breed[];
}

/** Đáp án khảo sát — khớp JSONB `answers` của `cat_health_survey` (p4 C3). */
export interface SurveyAnswers {
  litterType: string;
  urinaryHistory: string;
  dietType: string;
  urinationFrequency: string;
  symptoms: string[];
}

/** Version bộ câu hỏi đang dùng — gửi kèm D14 và dùng để tra F6. */
export const SURVEY_QUESTIONNAIRE_VERSION = "v1";

/**
 * F6 — `GET /reference/health-survey/{version}` (p8 §8.4.6). Backend trả `labelKey` (khoá
 * i18n) chứ không trả câu chữ, đúng quy ước `monitoring_rule.message_key` (quyết định #15);
 * client resolve bằng namespace `onboarding`.
 */
export interface SurveyQuestionOption {
  code: string;
  labelKey: string;
}

export interface SurveyQuestionDefinition {
  key: string;
  /** `SINGLE` chọn một, `MULTI` chọn nhiều. Giữ `string`: version sau có thể thêm kiểu mới. */
  type: string;
  required: boolean;
  sortOrder: number;
  labelKey: string;
  options: SurveyQuestionOption[];
}

export interface HealthSurveyDefinition {
  version: string;
  questions: SurveyQuestionDefinition[];
}

export const EMPTY_SURVEY_ANSWERS: SurveyAnswers = {
  litterType: "",
  urinaryHistory: "",
  dietType: "",
  urinationFrequency: "",
  symptoms: [],
};

export interface CreditBatch {
  id: string;
  packageCode: string;
  packageName: string;
  initialAmount: number;
  remaining: number;
  activatedAt: string;
  expiresAt: string;
}

export interface CreditBalance {
  totalAvailable: number;
  trialScansRemaining: number;
  batches: CreditBatch[];
}

export interface ActivationResult {
  batch: CreditBatch;
  balance: CreditBalance;
}

/**
 * Khớp ĐÚNG response thật của F1 `GET /reference/ph-bands`.
 *
 * Bug thật đã sửa, hai chỗ cùng lúc:
 *  1. `severity` trước đây khai `"normal" | "mild" | "abnormal"` — server trả CHỮ HOA
 *     `NORMAL | ATTENTION | WATCH | NEUTRAL` (p6 §6.7.1, đã hoà giải R-F05, xem
 *     `entities/ph-bands/model.ts`). Mọi chỗ `find(b => b.severity === "normal")` vì thế
 *     luôn trả `undefined`, dải chuẩn không bao giờ hiện.
 *  2. Dải đầu KHÔNG có `phMin`, dải cuối KHÔNG có `phMax` và server BỎ HẲN key đó khỏi
 *     JSON — khai `number` bắt buộc là nói dối, phải là optional.
 */
export interface PhBand {
  code: string;
  phMin?: number;
  phMax?: number;
  severity: "NORMAL" | "ATTENTION" | "WATCH" | "NEUTRAL";
  label: string;
  colorToken: string;
  sortOrder: number;
}

/**
 * F1 trả BARE ARRAY, không bọc `{items}` — xem `PublicColorChartController.listPhBands`
 * và ghi chú cùng nội dung ở `entities/ph-bands/hooks.ts`. Bản cũ khai `{items: PhBand[]}`
 * nên `data.items` luôn `undefined`, danh sách dải luôn rỗng và card tham chiếu ở
 * onboarding kẹt vĩnh viễn ở trạng thái "Đang tải dải pH tham chiếu…".
 */
export type PhBandListResponse = PhBand[];

/**
 * Khớp ĐÚNG response thật của D12 `GET /cats/{catId}/summary`.
 *
 * Bug thật đã sửa: bản cũ khai `cat: CreatedCat` + `lastScanAt` + `lastClassification` —
 * server KHÔNG trả field nào trong số đó, nên `summary?.cat` luôn `undefined` và
 * `OnboardingSuccessPage` lúc nào cũng rơi vào nhánh `?? createdCat`. Type nói dối khiến
 * TypeScript không bắt được, lại tốn một request thừa mỗi lần mở màn hình.
 */
export interface CatSummary {
  catId: string;
  lastScan: {
    scanId: string;
    capturedAt: string;
    phValue: number | null;
    classification: string;
    confidence: number | null;
  } | null;
  scanCount30d: number;
  inRangeRatio30d: number | null;
  unacknowledgedFlagCount: number;
  nextReminderAt: string | null;
  hasEnoughDataForTrend: boolean;
}
