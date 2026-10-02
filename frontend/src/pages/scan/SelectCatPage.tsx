import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { UserPlus } from "lucide-react";
import { Button, EmptyState, ErrorState, SkeletonLoader } from "@/shared/ui";
import { CatCard } from "@/entities/cat";
import { SharedTrayOption, useActiveCatsForCapture, useScanCaptureStore } from "@/features/scan";

/** `/scan/select-cat` (TaskLayout) — bước chọn mèo TRƯỚC khi mở camera (xem `features/scan/store.ts`
 * để biết vì sao thứ tự này khác mockup gốc). */
export function SelectCatPage() {
  const { t } = useTranslation(["scan", "common"]);
  const navigate = useNavigate();
  const { data: cats, isPending, isError, refetch } = useActiveCatsForCapture();
  const setSelectedCat = useScanCaptureStore((s) => s.setSelectedCat);
  const setSharedUnknown = useScanCaptureStore((s) => s.setSharedUnknown);

  const [selection, setSelection] = useState<{ id: string; name: string } | "SHARED" | null>(null);

  const handleConfirm = () => {
    if (selection === "SHARED") {
      setSharedUnknown();
    } else if (selection) {
      setSelectedCat(selection);
    } else {
      return;
    }
    void navigate("/scan");
  };

  if (isPending) {
    return (
      <div className="flex flex-col gap-3 p-4">
        <SkeletonLoader shape="card" className="h-20" />
        <SkeletonLoader shape="card" className="h-20" />
      </div>
    );
  }

  if (isError) {
    return (
      <ErrorState
        title={t("selectCat.loadError")}
        onRetry={() => {
          void refetch();
        }}
        retryLabel={t("actions.retry", { ns: "common" })}
      />
    );
  }

  if (cats.length === 0) {
    return (
      <EmptyState
        icon={<UserPlus className="size-6" aria-hidden="true" />}
        title={t("selectCat.emptyTitle")}
        description={t("selectCat.emptyDescription")}
        action={
          <Button variant="primary" onClick={() => { void navigate("/cats/new"); }}>
            {t("selectCat.goAddCat")}
          </Button>
        }
      />
    );
  }

  const confirmLabel =
    selection === "SHARED"
      ? t("selectCat.confirmCtaShared")
      : selection
        ? t("selectCat.confirmCtaNamed", { name: selection.name })
        : t("selectCat.confirmCtaGeneric");

  return (
    <div className="flex flex-col gap-4 p-4 pb-28">
      <div className="flex flex-col gap-1">
        <h1 className="text-h2 font-bold text-text-primary">{t("selectCat.heading")}</h1>
        <p className="text-caption text-text-secondary">{t("selectCat.description")}</p>
      </div>

      <div className="flex items-start gap-2 rounded-lg border border-border bg-background-alt p-3 text-caption text-text-secondary">
        <span className="font-semibold text-text-primary">{t("selectCat.privacyNoteTitle")}: </span>
        <span>{t("selectCat.privacyNoteBody")}</span>
      </div>

      <div className="flex flex-col gap-3">
        {cats.map((cat) => (
          <CatCard
            key={cat.id}
            cat={cat}
            selected={selection !== "SHARED" && selection?.id === cat.id}
            onSelect={(c) => {
              setSelection({ id: c.id, name: c.name });
            }}
            primaryLabel={t("selectCat.primaryLabel")}
          />
        ))}
        <SharedTrayOption
          title={t("selectCat.sharedOptionTitle")}
          description={t("selectCat.sharedOptionDescription")}
          selected={selection === "SHARED"}
          onSelect={() => {
            setSelection("SHARED");
          }}
        />
      </div>

      <div className="fixed inset-x-0 bottom-0 border-t border-border bg-surface p-4">
        <Button variant="primary" className="w-full" disabled={!selection} onClick={handleConfirm}>
          {confirmLabel}
        </Button>
      </div>
    </div>
  );
}
