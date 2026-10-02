import { DisclaimerBanner } from "@/entities/disclaimer";
import { LegalDocumentPage } from "./LegalDocumentPage";

/**
 * `/legal/medical-disclaimer` (p9 §9.4.3 #10, p15 §15.8.1 G3, §15.7.2 P8). Bản `D-LONG`
 * (p15 §15.7.3, i18n `legal.disclaimer.long.*`) là nội dung thực chất luôn hiển thị được
 * ngay cả khi `policy_version` (doc_code MEDICAL_DISCLAIMER) chưa được luật sư/DPO seed —
 * CMS render thêm bên dưới khi đã có.
 */
export function MedicalDisclaimerPage() {
  return <LegalDocumentPage policyCode="MEDICAL_DISCLAIMER" before={<DisclaimerBanner variant="long" />} />;
}
