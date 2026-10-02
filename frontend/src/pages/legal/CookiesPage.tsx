import { useTranslation } from "react-i18next";
import { LegalDocumentPage } from "./LegalDocumentPage";

interface CookieRow {
  name: string;
  purposeKey: string;
  group: "essential" | "measurement";
  duration: string;
  party: string;
}

/**
 * Danh sách cookie THẬT đang dùng — lấy từ hạ tầng identity đã triển khai (p11 §11.1.3),
 * KHÔNG bịa thêm cookie đo lường/quảng cáo nào chưa tồn tại. MVP (ORCHESTRATOR §2) cố ý
 * chưa có phân tích hành vi/quảng cáo — bảng dưới phản ánh đúng thực tế Phase 1.
 */
const COOKIE_ROWS: CookieRow[] = [
  { name: "__Host-CATCHECK_SESSION", purposeKey: "session", group: "essential", duration: "cookies.duration.session", party: "CatCheck" },
  { name: "XSRF-TOKEN", purposeKey: "csrf", group: "essential", duration: "cookies.duration.session", party: "CatCheck" },
];

function CookieTable() {
  const { t } = useTranslation("legal");
  return (
    <div className="flex flex-col gap-3">
      <h3 className="text-h3 font-bold text-text-primary">{t("cookies.tableTitle")}</h3>
      <div className="overflow-x-auto rounded-lg border border-border">
        <table className="w-full min-w-[480px] border-collapse text-caption">
          <thead>
            <tr className="border-b border-border bg-background-alt text-left text-text-secondary">
              <th className="p-3 font-semibold">{t("cookies.columns.name")}</th>
              <th className="p-3 font-semibold">{t("cookies.columns.purpose")}</th>
              <th className="p-3 font-semibold">{t("cookies.columns.group")}</th>
              <th className="p-3 font-semibold">{t("cookies.columns.duration")}</th>
              <th className="p-3 font-semibold">{t("cookies.columns.party")}</th>
            </tr>
          </thead>
          <tbody>
            {COOKIE_ROWS.map((row) => (
              <tr key={row.name} className="border-b border-border last:border-0">
                <td className="p-3 font-mono text-text-primary">{row.name}</td>
                <td className="p-3 text-text-secondary">{t(`cookies.purposes.${row.purposeKey}`)}</td>
                <td className="p-3 text-text-secondary">{t(`cookies.groups.${row.group}`)}</td>
                <td className="p-3 text-text-secondary">{t(row.duration)}</td>
                <td className="p-3 text-text-secondary">{row.party}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="text-caption text-text-tertiary">{t("cookies.noNonEssentialYet")}</p>
    </div>
  );
}

/** `/legal/cookies` (p9 §9.4.3 #11, p15 §15.8.1 G4, §15.9.6). */
export function CookiesPage() {
  return <LegalDocumentPage policyCode="COOKIE" extra={<CookieTable />} />;
}
