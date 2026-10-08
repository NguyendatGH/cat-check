// entities/cat — model dùng chung cho hồ sơ mèo (p9 §9.2.4). Mọi import từ bên ngoài
// PHẢI qua file này (boundaries/entry-point).
export type {
  Cat,
  CatLastScan,
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
export { resolveCatLastScan } from "./lastScan";
export { useCatStore } from "./store";
export { CatAvatar, CatCard } from "./components";
export { splitCatAge, useFormatCatAge } from "./age";
export type { CatAvatarProps, CatCardProps } from "./components";
