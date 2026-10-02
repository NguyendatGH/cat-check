// entities/cat — model dùng chung cho hồ sơ mèo (p9 §9.2.4). Mọi import từ bên ngoài
// PHẢI qua file này (boundaries/entry-point).
export type {
  Cat,
  CatSex,
  CatStatus,
  CatBreed,
  NoteType,
  ClinicalSign,
  ClinicalSignSource,
  LitterType,
  UrinaryHistory,
  DietType,
  UrinationFrequency,
  CatNote,
  CatHealthSurvey,
  CatHealthSurveyAnswers,
  ClinicalSignReportResult,
  TriggeredHealthFlag,
  PrimaryCatResult,
} from "./model";
export { CLINICAL_SIGNS } from "./model";
export { useCatStore } from "./store";
export { CatAvatar, CatCard } from "./components";
export type { CatAvatarProps, CatCardProps } from "./components";
