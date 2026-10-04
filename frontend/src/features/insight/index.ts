// features/insight — dấu hiệu theo dõi xu hướng sức khoẻ (`health_flag`, p8 §8.4.7 nhóm G).
// Mọi import từ bên ngoài PHẢI qua file này (boundaries/entry-point).
//
// GHI CHÚ CẤU TRÚC: code nằm ở các file root của feature (không thư mục con) theo khuôn mẫu
// bắt buộc của `features/onboarding` / `features/privacy`.
export { HealthFlagDisclosure, HealthFlagList, HealthFlagRow } from "./components";
export type { HealthFlagDisclosureProps, HealthFlagListProps, HealthFlagRowProps } from "./components";
export { insightKeys, useAcknowledgeHealthFlag, useHealthFlag, useHealthFlags } from "./hooks";
export { apiFetch as insightApiFetch } from "./api";
export type { HealthFlagFilter, HealthFlagListResponse, HealthFlagSeverity, HealthFlagView } from "./types";
