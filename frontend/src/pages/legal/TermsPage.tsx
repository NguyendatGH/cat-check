import { LegalDocumentPage } from "./LegalDocumentPage";

/** `/legal/terms` (p9 §9.4.3 #8, p15 §15.8.1 G1). Nội dung từ `policy_version` (doc_code TERMS). */
export function TermsPage() {
  return <LegalDocumentPage policyCode="TERMS" />;
}
