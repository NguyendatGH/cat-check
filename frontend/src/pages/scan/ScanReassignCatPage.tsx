import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router";
import { Button, ErrorState, SkeletonLoader } from "@/shared/ui";
import { CatCard } from "@/entities/cat";
import { SharedTrayOption, useActiveCatsForCapture, useReassignScanCat, useScan } from "@/features/scan";
import { isApiError } from "@/shared/api/errors";

/** `/scan/:scanId/reassign-cat` (E9) — gán lại kết quả cho mèo khác, ≤24h/≤3 lần (mockup `05`
 * áp dụng lại cho trường hợp SAU khi đã có kết quả, khác `SelectCatPage` là bước TRƯỚC khi quét). */
export function ScanReassignCatPage() {
  const { t } = useTranslation(["scan", "common"]);
  const { scanId } = useParams<{ scanId: string }>();
  const navigate = useNavigate();
  const { data: scan, isPending: scanPending } = useScan(scanId);
  const { data: cats, isPending: catsPending, isError, refetch } = useActiveCatsForCapture();
  const reassign = useReassignScanCat(scanId ?? "");

  const [selection, setSelection] = useState<{ id: string; name: string } | "SHARED" | null>(null);

  if (scanPending || catsPending) {
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

  const limitReached = (scan?.reassignRemaining ?? 1) <= 0;

  const handleConfirm = () => {
    if (!scanId || !selection) return;
    reassign.mutate(
      {
        toCatId: selection === "SHARED" ? null : selection.id,
        toAssignment: selection === "SHARED" ? "SHARED_UNKNOWN" : undefined,
      },
      {
        onSuccess: () => {
          void navigate(`/scans/${scanId}`, { replace: true });
        },
      },
    );
  };

  const confirmLabel =
    selection === "SHARED"
      ? t("reassignCat.confirmCtaShared")
      : selection
        ? t("reassignCat.confirmCtaNamed", { name: selection.name })
        : t("selectCat.confirmCtaGeneric");

  return (
    <div className="flex flex-col gap-4 p-4 pb-28">
      <div className="flex flex-col gap-1">
        <h1 className="text-h2 font-bold text-text-primary">{t("reassignCat.heading")}</h1>
        <p className="text-caption text-text-secondary">{t("reassignCat.description")}</p>
      </div>

      {limitReached ? (
        <p role="alert" className="rounded-lg bg-danger-bg p-3 text-caption text-danger-text">
          {t("reassignCat.limitReached")}
        </p>
      ) : null}

      <div className="flex flex-col gap-3">
        {cats
          .filter((cat) => cat.id !== scan?.catId)
          .map((cat) => (
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
        {scan?.assignment !== "SHARED_UNKNOWN" ? (
          <SharedTrayOption
            title={t("selectCat.sharedOptionTitle")}
            description={t("selectCat.sharedOptionDescription")}
            selected={selection === "SHARED"}
            onSelect={() => {
              setSelection("SHARED");
            }}
          />
        ) : null}
      </div>

      {reassign.isError ? (
        <p role="alert" className="text-caption text-danger-text">
          {isApiError(reassign.error) ? reassign.error.message : t("capture.uploadError")}
        </p>
      ) : null}

      <div className="fixed inset-x-0 bottom-0 border-t border-border bg-surface p-4">
        <Button
          variant="primary"
          className="w-full"
          disabled={!selection || limitReached || reassign.isPending}
          onClick={handleConfirm}
        >
          {confirmLabel}
        </Button>
      </div>
    </div>
  );
}
