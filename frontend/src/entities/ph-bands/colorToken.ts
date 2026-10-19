
export interface PhTokenStyle {
  bg: string;
  text: string;
  solid: string;
}

const PH_TOKEN_STYLES: Record<string, PhTokenStyle> = {
  "color-ph-normal": {
    bg: "bg-[var(--color-ph-normal-bg)]",
    text: "text-[var(--color-ph-normal-text)]",
    solid: "bg-[var(--color-ph-normal)]",
  },
  "color-ph-mild": {
    bg: "bg-[var(--color-ph-mild-bg)]",
    text: "text-[var(--color-ph-mild-text)]",
    solid: "bg-[var(--color-ph-mild)]",
  },
  "color-ph-abnormal": {
    bg: "bg-[var(--color-ph-abnormal-bg)]",
    text: "text-[var(--color-ph-abnormal-text)]",
    solid: "bg-[var(--color-ph-abnormal)]",
  },
  "color-ph-unknown": {
    bg: "bg-[var(--color-ph-unknown-bg)]",
    text: "text-[var(--color-ph-unknown-text,var(--color-text-secondary))]",
    solid: "bg-[var(--color-ph-unknown)]",
  },
};

const FALLBACK_STYLE = PH_TOKEN_STYLES["color-ph-unknown"];

export function phTokenStyle(colorToken: string): PhTokenStyle {
  return PH_TOKEN_STYLES[colorToken] ?? FALLBACK_STYLE;
}
