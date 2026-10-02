import type {
  CatHealthSurveyAnswers,
  CatSex,
  CatStatus,
  ClinicalSign,
  ClinicalSignSource,
  NoteType,
} from "@/entities/cat";

/**
 * Types riêng của feature `cat` — form draft, input mutation. Kiểu domain dùng chung
 * (`Cat`, enum...) nằm ở `entities/cat` (p9 §9.2.4), import lại từ đó, không định nghĩa
 * lại ở đây.
 */

/** Cách nhập tuổi — domain chỉ chấp nhận CHÍNH XÁC một nguồn (`CAT_AGE_CONFLICT`, p8 §8.5.3). */
export type CatAgeMode = "birthDate" | "approx";

export interface CatFormDraft {
  name: string;
  breedCode: string;
  breedOther: string;
  coatColor: string;
  sex: CatSex | "";
  neutered: boolean;
  ageMode: CatAgeMode;
  birthDate: string;
  approxAgeMonths: string;
  weightKg: string;
  notes: string;
  avatarFile: File | null;
}

export const EMPTY_CAT_FORM_DRAFT: CatFormDraft = {
  name: "",
  breedCode: "",
  breedOther: "",
  coatColor: "",
  sex: "",
  neutered: false,
  ageMode: "birthDate",
  birthDate: "",
  approxAgeMonths: "",
  weightKg: "",
  notes: "",
  avatarFile: null,
};

/** D2/D4 — body gửi lên server, đã chuẩn hoá kiểu (number/null) từ `CatFormDraft`. */
export interface CatMutationPayload {
  name: string;
  breedCode: string | null;
  breedOther: string | null;
  coatColor: string | null;
  sex: CatSex;
  neutered: boolean | null;
  birthDate: string | null;
  approxAgeMonths: number | null;
  weightKg: number | null;
  notes: string | null;
  isPrimary?: boolean;
}

export type CatListFilter = CatStatus;

export interface NoteFormValues {
  noteType: NoteType;
  body: string;
  occurredOn: string;
}

export interface ClinicalSignFormValues {
  signs: ClinicalSign[];
  note: string;
}

export type HealthSurveyFormValues = CatHealthSurveyAnswers;

/** Nguồn khai dấu hiệu lâm sàng do UI của tôi kiểm soát trực tiếp (khác `SURVEY`/`RESULT_SCREEN`). */
export const MANUAL_CLINICAL_SIGN_SOURCE: ClinicalSignSource = "MANUAL";

/** Lần quét gần nhất trong D12 — `null` khi mèo chưa từng được quét kết luận được. */
export interface CatSummaryLastScan {
  scanId: string;
  capturedAt: string;
  phValue: number | null;
  classification: string;
  confidence: number | null;
}

/**
 * D12 `GET /cats/{catId}/summary` — hợp đồng THẬT của backend (p8 §8.9).
 *
 * ⚠️ KHÁC với `CatSummary` trong `features/onboarding/types.ts`: kiểu ở đó được viết khi
 * endpoint còn là stub 501 và KHÔNG khớp response thật (nó khai `cat`/`lastScanAt`/
 * `lastClassification`, server không trả các field đó). Không sửa kiểu bên onboarding ở đây
 * vì `OnboardingSuccessPage` ngoài phạm vi — nó dùng `summary?.cat ?? createdCat` nên vẫn
 * chạy, chỉ là luôn rơi vào nhánh fallback.
 */
export interface CatSummaryResponse {
  catId: string;
  lastScan: CatSummaryLastScan | null;
  scanCount30d: number;
  inRangeRatio30d: number | null;
  unacknowledgedFlagCount: number;
  /** Luôn `null` ở MVP — nhắc lịch thuộc M5, chưa có bảng. Đừng dựng UI phụ thuộc field này. */
  nextReminderAt: string | null;
  hasEnoughDataForTrend: boolean;
}
