import { useTranslation } from "react-i18next";
import { EmptyState } from "@/shared/ui";

export function ComingSoonPage() {
  const { t } = useTranslation("common");
  return <EmptyState title={t("comingSoon.title")} description={t("comingSoon.description")} />;
}
