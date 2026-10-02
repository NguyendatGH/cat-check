// entities/scan-result — model dữ liệu kết quả quét (p9 §9.2.4). Mọi import từ bên ngoài
// PHẢI qua file này (boundaries/entry-point). KHÔNG có store/component ở entity này: state
// server-side đi qua react-query (features/scan|history|trends hooks.ts), hiển thị (màu/nhãn/
// icon pH) đi qua entities/ph-bands trực tiếp từ feature — 1 entity không được import entity
// khác (eslint boundaries, ngoại lệ duy nhất là entities/user) nên component ghép cả hai phải
// sống ở feature, không phải ở đây.
export type {
  ScanClassification,
  ConfidenceBand,
  ScanAssignment,
  CaptureSource,
  CalibrationMethod,
  QualityFlagSeverity,
  QualityFlag,
  TriggeredFlag,
  ScanResult,
  ScanListItem,
  ScanListPage,
  ScanSummary,
  ScanAnalysisDetail,
  ScanConfig,
} from "./model";
