
export type CatSex = "UNKNOWN" | "FEMALE" | "MALE";

export type CatStatus = "ACTIVE" | "ARCHIVED";

export type NoteType = "GENERAL" | "DIET_CHANGE" | "SYMPTOM" | "VET_VISIT" | "LITTER_CHANGE";

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

export type ClinicalSignSource = "RESULT_SCREEN" | "SURVEY" | "MANUAL";

export type LitterType = "CATCHECK_SMART" | "CLAY" | "TOFU" | "SILICA" | "OTHER";

export type UrinaryHistory = "NONE" | "PAST" | "CURRENT";

export type DietType = "DRY" | "WET" | "MIXED" | "RAW";

export type UrinationFrequency = "LT_1_PER_DAY" | "1_2_PER_DAY" | "GT_2_PER_DAY" | "UNKNOWN";

export interface CatBreed {
  code: string;
  name: string;
  popular: boolean;
}

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
  lastScanAt: string | null;
  lastClassification: string | null;
  unacknowledgedFlagCount: number | null;
}

export interface CatLastScan {
  capturedAt: string;
  classification: string;
  bandCode: string;
  phValue: number | null;
}

export interface PrimaryCatResult {
  catId: string;
  previousPrimaryCatId: string | null;
}

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

export interface CatHealthSurveyAnswers {
  litterType: LitterType | "";
  urinaryHistory: UrinaryHistory | "";
  dietType: DietType | "";
  urinationFrequency: UrinationFrequency | "";
  symptoms: ClinicalSign[];
}

export interface CatHealthSurvey {
  id: string;
  catId: string;
  questionnaireVersion: string;
  answers: CatHealthSurveyAnswers;
  skipped: boolean;
  submittedAt: string | null;
  createdAt: string;
}

export interface TriggeredHealthFlag {
  flagId: string;
  ruleCode: string;
  severity: string;
}

export interface ClinicalSignReportResult {
  reportId: string;
  triggeredFlag: TriggeredHealthFlag | null;
}
