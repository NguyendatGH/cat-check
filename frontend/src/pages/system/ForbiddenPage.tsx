import { useTranslation } from "react-i18next";
import { EmptyState } from "@/shared/ui";

export function ForbiddenPage() {
  const { t } = useTranslation("common");
  return <EmptyState title={t("forbidden.title")} description={t("forbidden.description")} />;
}
