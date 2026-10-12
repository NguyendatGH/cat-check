export interface DisclaimerAcceptance {
  policyCode: string;
  version: string;
  acceptedAt: string | null;
}

export type DisclaimerVariant = "short" | "medium" | "long" | "emergency" | "footerLine";
