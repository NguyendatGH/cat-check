import { DisclaimerBanner } from "@/entities/disclaimer";
import { LegalDocumentPage } from "./LegalDocumentPage";

export function MedicalDisclaimerPage() {
  return <LegalDocumentPage policyCode="MEDICAL_DISCLAIMER" before={<DisclaimerBanner variant="long" />} />;
}
