import { useTranslation } from "react-i18next";
import { EmptyState } from "@/shared/ui";

export function UpsellPage() {
  const { t } = useTranslation("common");
  return <EmptyState title={t("upsell.title")} description={t("upsell.description")} />;
}
