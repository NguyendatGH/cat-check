/**
 * Model dùng chung cho hồ sơ mèo — tên field khớp `CatResponse`
 * (`backend/src/main/java/com/catcheck/cat/api/dto/CatResponse.java`) và domain
 * `com.catcheck.cat.domain.Cat` (bảng `cat`, p4 §4.2.2 nhóm C1). Dùng bởi `features/cat`
 * và bất kỳ feature nào khác cần hiển thị mèo (`scan`, `history`, `trends` — p9 §9.2.4).
 */

/** p4 §4.4.4 — lưu `VARCHAR + CHECK`, không dùng ordinal. */
export type CatSex = "UNKNOWN" | "FEMALE" | "MALE";

/** p4 §4.4.4 — `ARCHIVED` = "bé đã mất / đã cho đi", KHÁC xoá mềm (p4 §4.8.2). */
export type CatStatus = "ACTIVE" | "ARCHIVED";

/** p4 §4.4.4 — loại ghi chú của chủ nuôi (bảng `cat_note`). */
export type NoteType = "GENERAL" | "DIET_CHANGE" | "SYMPTOM" | "VET_VISIT" | "LITTER_CHANGE";

/**
 * Sáu dấu hiệu lâm sàng chủ nuôi tự khai (p4 §4.4.4, p6 §6.9.6) — tập hằng số cố định,
 * KHÔNG phải danh mục cấu hình. Không suy ra chẩn đoán/tên bệnh từ tập này (quyết định #6, #8).
 */
export type ClinicalSign =
  "STRAINING" | "NO_URINE" | "CRYING" | "BLOOD_VISIBLE" | "LETHARGY_ANOREXIA" | "EXCESSIVE_LICKING";

export const CLINICAL_SIGNS: ClinicalSign[] = [
  "STRAINING",
  "NO_URINE",
  "CRYING",
  "BLOOD_VISIBLE",
  "LETHARGY_ANOREXIA",
  "EXCESSIVE_LICKING",
];

/** Nơi phát sinh khai báo dấu hiệu lâm sàng (p4 §4.4.4). */
export type ClinicalSignSource = "RESULT_SCREEN" | "SURVEY" | "MANUAL";

/** Câu 1 khảo sát sức khoẻ — loại cát (C29, p4 §4.4.4). */
export type LitterType = "CATCHECK_SMART" | "CLAY" | "TOFU" | "SILICA" | "OTHER";

/** Câu 2 khảo sát sức khoẻ — tiền sử tiết niệu (C29, p4 §4.4.4). */
export type UrinaryHistory = "NONE" | "PAST" | "CURRENT";

/** Câu 3 khảo sát sức khoẻ — chế độ ăn (C29, p4 §4.4.4). */
export type DietType = "DRY" | "WET" | "MIXED" | "RAW";

/**
 * Câu 4 khảo sát sức khoẻ — tần suất đi vệ sinh (C29, p4 §4.4.4). Giá trị dây khác tên hằng
 * số Java (`UrinationFrequency.wireValue()`) vì `1_2_PER_DAY` không phải định danh hợp lệ.
 */
export type UrinationFrequency = "LT_1_PER_DAY" | "1_2_PER_DAY" | "GT_2_PER_DAY" | "UNKNOWN";

/** Một giống mèo trong danh mục `cat_breed` (p4 C2) — trả về từ `GET /reference/cat-breeds`. */
export interface CatBreed {
  code: string;
  name: string;
  popular: boolean;
}

/**
 * Hồ sơ mèo — khớp `CatResponse` (D3/D4/D6/D7/D8/D10/D11). `ageMonths` là tuổi tính sẵn
 * server-side từ MỘT nguồn (`birthDate` HOẶC `approxAgeMonths`) — FE không tự tính lại.
 */
export interface Cat {
  id: string;
  publicCode: string;
  name: string;
  birthDate: string | null;
  approxAgeMonths: number | null;
  ageMonths: number | null;
  breedCode: string | null;
  breedName: string | null;
  breedOther: string | null;
  coatColor: string | null;
  sex: CatSex;
  neutered: boolean | null;
  weightKg: number | null;
  weightUpdatedAt: string | null;
  avatarUrl: string | null;
  status: CatStatus;
  isPrimary: boolean;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
  /**
   * Trường tóm tắt tuỳ chọn — D1 (`GET /cats`) trả kèm theo p8 §8.4.4, nguồn từ module scan
   * (A6). Có thể vắng/`null` cho tới khi scan wiring xong; UI phải xử lý được cả hai.
   */
  lastScanAt?: string | null;
  lastClassification?: string | null;
  unacknowledgedFlagCount?: number;
}

/** D8 — kết quả đặt mèo chính: `{catId, previousPrimaryCatId}` (p8 "Nhóm D còn lại"). */
export interface PrimaryCatResult {
  catId: string;
  previousPrimaryCatId: string | null;
}

/** Một ghi chú của chủ nuôi (`cat_note`, D16/D17/D18/D19). */
export interface CatNote {
  id: string;
  catId: string;
  noteType: NoteType;
  body: string;
  occurredOn: string | null;
  scanId: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Đáp án khảo sát sức khoẻ — khớp JSONB `answers` của `cat_health_survey` (p4 C3). */
export interface CatHealthSurveyAnswers {
  litterType: LitterType | "";
  urinaryHistory: UrinaryHistory | "";
  dietType: DietType | "";
  urinationFrequency: UrinationFrequency | "";
  symptoms: ClinicalSign[];
}

/** Bản khảo sát đã nộp (D14/D15). */
export interface CatHealthSurvey {
  id: string;
  catId: string;
  questionnaireVersion: string;
  answers: CatHealthSurveyAnswers;
  skipped: boolean;
  submittedAt: string | null;
  createdAt: string;
}

/** Cờ được kích hoạt khi khai dấu hiệu lâm sàng trùng luật cảnh báo khẩn (D20). */
export interface TriggeredHealthFlag {
  flagId: string;
  ruleCode: string;
  severity: string;
}

/** Kết quả D20 — `POST /cats/{id}/clinical-signs`. */
export interface ClinicalSignReportResult {
  reportId: string;
  triggeredFlag: TriggeredHealthFlag | null;
}
