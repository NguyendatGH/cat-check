import { LegalDocumentPage } from "./LegalDocumentPage";

/** `/legal/privacy` (p9 §9.4.3 #9, p15 §15.8.1 G2). Nội dung từ `policy_version` (doc_code PRIVACY). */
export function PrivacyPage() {
  return <LegalDocumentPage policyCode="PRIVACY" />;
}
