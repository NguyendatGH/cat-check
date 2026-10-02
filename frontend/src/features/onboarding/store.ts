import { create } from "zustand";
import {
  EMPTY_CAT_DRAFT,
  EMPTY_SURVEY_ANSWERS,
  type ActivationResult,
  type CatProfileDraft,
  type CreatedCat,
  type OnboardingStep,
  type SurveyAnswers,
} from "./types";

/**
 * Store zustand cho luồng onboarding — giữ state giữa các bước để user đi lùi/tới
 * không mất dữ liệu. Session-only (không persist): hồ sơ mèo là dữ liệu nhạy cảm,
 * không để lại trong localStorage khi bỏ dở.
 */

interface OnboardingStore {
  step: OnboardingStep;
  catDraft: CatProfileDraft;
  createdCat: CreatedCat | null;
  // Không nullable: khởi tạo và reset đều gán EMPTY_SURVEY_ANSWERS, không nơi nào gán null.
  surveyAnswers: SurveyAnswers;
  surveySkipped: boolean;
  activation: ActivationResult | null;
  setStep: (step: OnboardingStep) => void;
  updateCatDraft: (patch: Partial<CatProfileDraft>) => void;
  setCreatedCat: (cat: CreatedCat) => void;
  setSurveyAnswers: (answers: SurveyAnswers) => void;
  setSurveySkipped: (skipped: boolean) => void;
  setActivation: (result: ActivationResult) => void;
  reset: () => void;
}

const EMPTY_ACTIVATION = null;

export const useOnboardingStore = create<OnboardingStore>((set) => ({
  step: 1,
  catDraft: EMPTY_CAT_DRAFT,
  createdCat: null,
  surveyAnswers: EMPTY_SURVEY_ANSWERS,
  surveySkipped: false,
  activation: EMPTY_ACTIVATION,
  setStep: (step) => {
    set({ step });
  },
  updateCatDraft: (patch) => {
    set((state) => ({ catDraft: { ...state.catDraft, ...patch } }));
  },
  setCreatedCat: (createdCat) => {
    set({ createdCat });
  },
  setSurveyAnswers: (surveyAnswers) => {
    set({ surveyAnswers });
  },
  setSurveySkipped: (surveySkipped) => {
    set({ surveySkipped });
  },
  setActivation: (activation) => {
    set({ activation });
  },
  reset: () => {
    set({
      step: 1,
      catDraft: EMPTY_CAT_DRAFT,
      createdCat: null,
      surveyAnswers: EMPTY_SURVEY_ANSWERS,
      surveySkipped: false,
      activation: EMPTY_ACTIVATION,
    });
  },
}));
