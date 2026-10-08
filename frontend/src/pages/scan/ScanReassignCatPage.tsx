import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router";
import { AlertTriangle, Check, Repeat2 } from "lucide-react";
import { Button, ErrorState, SkeletonLoader } from "@/shared/ui";
import { isApiError } from "@/shared/api/errors";
import { usePhBands } from "@/entities/ph-bands";
import {
  MultiCatTip,
  SelectCatOption,
  SharedTrayOption,
  isReassignWindowOpen,
  useActiveCatsForCapture,
  useLatestScanByCat,
  useReassignScanCat,
  useScan,
} from "@/features/scan";

/** Mã lỗi E9 → câu chữ (409 cửa sổ đóng / hết lượt có câu riêng, còn lại dùng câu chung). */
function reassignErrorKey(error: unknown): string {
  if (isApiError(error)) {
    if (error.code === "SCAN_REASSIGN_WINDOW_CLOSED") return "reassignCat.windowClosed";
    if (error.code === "SCAN_REASSIGN_LIMIT_REACHED") return "reassignCat.limitReached";
  }
  return "scanDetail.actionError";
}

/**
 * `/scan/:scanId/reassign-cat` (E9) — gán lại kết quả cho mèo khác, ≤24h/≤3 lần. Dùng lại
 * đúng thẻ chọn mèo của mockup `05` (`SelectCatOption`) như bước chọn mèo trước khi quét.
 *
 * Trang nằm trong `AppLayout`: mobile tự chừa lề `px-4`; nút xác nhận nằm TRONG luồng trang
 * (trước đây là thanh `fixed bottom-0` — bị thanh tab dưới che trên mobile và trải ngang cả
 * sidebar trên desktop).
 */
export function ScanReassignCatPage() {
  const { t } = useTranslation(["scan", "common"]);
  const { scanId } = useParams<{ scanId: string }>();
  const navigate = useNavigate();
  const { data: scan, isPending: scanPending, isError: scanError, refetch: refetchScan } = useScan(scanId);
  const { data: cats, isPending: catsPending, isError, refetch } = useActiveCatsForCapture();
  const { data: bands } = usePhBands();
  const reassign = useReassignScanCat(scanId ?? "");
  const latestByCat = useLatestScanByCat((cats ?? []).map((cat) => cat.id));

  const [selection, setSelection] = useState<{ id: string; name: string } | "SHARED" | null>(null);

  if (scanPending || catsPending) {
    return (
      <div className="flex flex-col gap-3 px-4 pt-5 lg:p-0">
        <SkeletonLoader shape="card" className="h-20" />
        <SkeletonLoader shape="card" className="h-20" />
      </div>
    );
  }

  if (isError || scanError) {
    return (
      <ErrorState
        title={t("selectCat.loadError")}
        onRetry={() => {
          void refetch();
          void refetchScan();
        }}
        retryLabel={t("actions.retry", { ns: "common" })}
      />
    );
  }

  const windowClosed = !isReassignWindowOpen(scan);
  const limitReached = scan.reassignRemaining <= 0;
  const blocked = windowClosed || limitReached;
  const otherCats = cats.filter((cat) => cat.id !== scan.catId);
  const canPickShared = scan.assignment !== "SHARED_UNKNOWN";

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

  const backToResult = () => {
    void navigate(scanId ? `/scan/result/${scanId}` : "/history");
  };

  return (
    <div className="flex flex-col gap-4 px-4 pb-6 pt-5 lg:p-0">
      <div className="flex flex-col gap-1">
        <h1 className="text-h2 font-bold text-text-primary">{t("reassignCat.heading")}</h1>
        <p className="text-caption text-text-secondary">{t("reassignCat.description")}</p>
      </div>

      <p className="inline-flex items-center gap-2 self-start rounded-full bg-chip-bg px-3 py-1.5 text-caption font-semibold text-primary-dark">
        <Repeat2 className="size-4 shrink-0" aria-hidden="true" />
        {scan.assignment === "SHARED_UNKNOWN" || !scan.catName
          ? t("reassignCat.currentShared")
          : t("reassignCat.currentCat", { name: scan.catName })}
      </p>

      {blocked ? (
        <p role="alert" className="flex items-start gap-2 rounded-xl bg-warning-bg p-3 text-caption text-warning-text">
          <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          {windowClosed ? t("reassignCat.windowClosed") : t("reassignCat.limitReached")}
        </p>
      ) : null}

      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:gap-6">
        <div className="flex min-w-0 flex-1 flex-col gap-3">
          {otherCats.map((cat) => (
            <SelectCatOption
              key={cat.id}
              cat={cat}
              bands={bands ?? []}
              latestScan={latestByCat[cat.id]}
              selected={selection !== "SHARED" && selection?.id === cat.id}
              onSelect={() => {
                if (!blocked) setSelection({ id: cat.id, name: cat.name });
              }}
              className={blocked ? "pointer-events-none opacity-60" : undefined}
            />
          ))}
          {canPickShared ? (
            <SharedTrayOption
              title={t("selectCat.sharedOptionTitle")}
              description={t("selectCat.sharedOptionDescription")}
              selected={selection === "SHARED"}
              onSelect={() => {
                if (!blocked) setSelection("SHARED");
              }}
              className={blocked ? "pointer-events-none opacity-60" : undefined}
            />
          ) : null}
          {otherCats.length === 0 && !canPickShared ? (
            <p className="rounded-2xl bg-surface p-4 text-caption text-text-secondary">{t("reassignCat.noOtherCat")}</p>
          ) : null}
        </div>

        <aside className="flex w-full flex-col gap-4 lg:sticky lg:top-6 lg:w-[340px] lg:shrink-0">
          <MultiCatTip className="hidden lg:flex" />

          {reassign.isError ? (
            <p role="alert" className="rounded-xl bg-danger-bg p-3 text-caption text-danger-text">
              {t(reassignErrorKey(reassign.error))}
            </p>
          ) : null}

          <div className="flex flex-col gap-1">
            <Button
              variant="primary"
              size="lg"
              className="w-full"
              disabled={!selection || blocked}
              loading={reassign.isPending}
              leftIcon={<Check className="size-4" aria-hidden="true" />}
              onClick={handleConfirm}
            >
              {confirmLabel}
            </Button>
            <Button variant="tertiary" className="w-full border-transparent" onClick={backToResult}>
              {t("reassignCat.backToResult")}
            </Button>
          </div>
        </aside>
      </div>
    </div>
  );
}
