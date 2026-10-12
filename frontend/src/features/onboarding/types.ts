
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

export interface SurveyAnswers {
  litterType: string;
  urinaryHistory: string;
  dietType: string;
  urinationFrequency: string;
  symptoms: string[];
}

export const SURVEY_QUESTIONNAIRE_VERSION = "v1";

export interface SurveyQuestionOption {
  code: string;
  labelKey: string;
}

export interface SurveyQuestionDefinition {
  key: string;
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
  batchId: string;
  packageCode: string;
  initialAmount: number;
  remainingAmount: number;
  activatedAt: string;
  expiresAt: string;
  remainingSeconds: number;
}

export interface CreditBalance {
  availableBalance: number;
  trialScansUsed: number;
  trialScansRemaining: number;
  batches: CreditBatch[];
}

export interface ActivationResult {
  packageCode: string;
  packageName: string;
  creditsGranted: number;
  expiresAt: string;
  balanceAfter: number;
}

export interface PhBand {
  code: string;
  phMin?: number;
  phMax?: number;
  severity: "NORMAL" | "ATTENTION" | "WATCH" | "NEUTRAL";
  label: string;
  colorToken: string;
  sortOrder: number;
}

export type PhBandListResponse = PhBand[];

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
