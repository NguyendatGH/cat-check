import { useSearchParams } from "react-router";
import { useTranslation } from "react-i18next";
import { LogOut, ShieldAlert } from "lucide-react";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/shared/ui";
import { AccessDeniedState, AdminPageHeader, useAdminCan } from "@/features/admin";
import { SecurityIncidentsTab } from "./adminSecurityIncidents";
import { SecuritySessionsTab } from "./adminSecuritySessions";

type SecurityTab = "incidents" | "sessions";

export function AdminSecurityPage() {
  const { t } = useTranslation("admin");
  const can = useAdminCan();
  const [searchParams, setSearchParams] = useSearchParams();

  const available: SecurityTab[] = [];
  if (can("securityIncidentsRead")) available.push("incidents");
  if (can("securitySessionsPurge")) available.push("sessions");

  const header = (
    <AdminPageHeader
      title={t("pages.security.title")}
      description={t("pages.security.description")}
      specRef="L59–L62 — /api/v1/admin/security"
    />
  );

  if (available.length === 0) {
    return (
      <div className="flex flex-col gap-4">
        {header}
        <AccessDeniedState />
      </div>
    );
  }

  const requested = searchParams.get("tab");
  const active: SecurityTab = available.find((tab) => tab === requested) ?? available[0];

  return (
    <div className="flex flex-col gap-4">
      {header}
      <Tabs
        value={active}
        onValueChange={(value) => {
          setSearchParams({ tab: value }, { replace: true });
        }}
      >
        <TabsList aria-label={t("security.tabsLabel")}>
          {available.includes("incidents") ? (
            <TabsTrigger value="incidents" className="inline-flex items-center gap-1.5">
              <ShieldAlert size={14} aria-hidden="true" />
              {t("security.tabIncidents")}
            </TabsTrigger>
          ) : null}
          {available.includes("sessions") ? (
            <TabsTrigger value="sessions" className="inline-flex items-center gap-1.5">
              <LogOut size={14} aria-hidden="true" />
              {t("security.tabSessions")}
            </TabsTrigger>
          ) : null}
        </TabsList>
        {available.includes("incidents") ? (
          <TabsContent value="incidents">
            <SecurityIncidentsTab canWrite={can("securityIncidentsWrite")} />
          </TabsContent>
        ) : null}
        {available.includes("sessions") ? (
          <TabsContent value="sessions">
            <SecuritySessionsTab canReadIncidents={can("securityIncidentsRead")} />
          </TabsContent>
        ) : null}
      </Tabs>
    </div>
  );
}
