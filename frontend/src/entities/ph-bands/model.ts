
export type PhBandSeverity = "NORMAL" | "ATTENTION" | "WATCH" | "NEUTRAL";

export type PhColorToken = "color-ph-normal" | "color-ph-mild" | "color-ph-abnormal" | "color-ph-unknown";

export interface PhBand {
  code: string;
  phMin?: number | null;
  phMax?: number | null;
  minInclusive: boolean;
  maxInclusive: boolean;
  severity: PhBandSeverity;
  label: string;
  description: string;
  colorToken: string;
  iconName: string;
  sortOrder: number;
  triggersAlert: boolean;
}

export interface ActiveColorChart {
  code: string;
  version: number;
  placeholder: boolean;
  productLine: string | null;
}
