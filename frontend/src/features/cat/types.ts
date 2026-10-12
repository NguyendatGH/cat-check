import type {
  CatHealthSurveyAnswers,
  CatSex,
  CatStatus,
  ClinicalSign,
  ClinicalSignSource,
  NoteType,
} from "@/entities/cat";


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

export const MANUAL_CLINICAL_SIGN_SOURCE: ClinicalSignSource = "MANUAL";

export interface CatSummaryLastScan {
  scanId: string;
  capturedAt: string;
  phValue: number | null;
  classification: string;
  confidence: number | null;
}

export interface CatSummaryResponse {
  catId: string;
  lastScan: CatSummaryLastScan | null;
  scanCount30d: number;
  inRangeRatio30d: number | null;
  unacknowledgedFlagCount: number;
  nextReminderAt: string | null;
  hasEnoughDataForTrend: boolean;
}
