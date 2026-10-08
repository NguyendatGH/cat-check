import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router";
import { Flag, Repeat2, Trash2 } from "lucide-react";
import { Badge, Button, Card, ErrorState, SkeletonLoader } from "@/shared/ui";
import { DisclaimerBanner } from "@/entities/disclaimer";
import { usePhBands } from "@/entities/ph-bands";
import {
  ScanAdviceCard,
  ScanResultSummary,
  canReassign,
  useClearScanDispute,
  useDeleteScan,
  useDisputeScan,
  useScan,
  useScanAnalysis,
} from "@/features/scan";
import { ScanMetaCard } from "./webPanels";

/**
 * BE lược MỌI field `null` khỏi JSON (`GET /scans/{id}/analysis` của lượt quét seed chỉ có
 * `calibrationMethod`/`chartCode`/…), nên các toạ độ vắng mặt là `undefined` chứ không phải
 * `null` — so sánh `=== null` để lọt và `.toFixed()` làm trắng cả trang. Dùng `Number.isFinite`.
 */
function isFiniteNumber(v: number | null | undefined): v is number {
  return typeof v === "number" && Number.isFinite(v);
}

function formatLabCoordinates(l: number | null, a: number | null, b: number | null): string {
  if (!isFiniteNumber(l) || !isFiniteNumber(a) || !isFiniteNumber(b)) return "—";
  return `${l.toFixed(1)} / ${a.toFixed(1)} / ${b.toFixed(1)}`;
}

/** `/scans/:scanId` (TaskLayout — layout đã cấp `px-4 py-6`) — chi tiết 1 lần quét từ lịch sử:
 * kết quả + phân tích + tranh chấp/xoá (E4/E5/E10/E11/E8). */
export function ScanDetailPage() {
  const { t } = useTranslation(["scan", "common"]);
  const { scanId } = useParams<{ scanId: string }>();
  const navigate = useNavigate();
  const { data: result, isPending, isError, refetch } = useScan(scanId);
  const { data: analysis } = useScanAnalysis(scanId);
  const { data: bands } = usePhBands();
  const dispute = useDisputeScan(scanId ?? "");
  const clearDispute = useClearScanDispute(scanId ?? "");
  const deleteScan = useDeleteScan(scanId ?? "");
  const [noteDraft, setNoteDraft] = useState("");
  const [showDisputeForm, setShowDisputeForm] = useState(false);

  if (isPending) {
    return (
      <div className="flex flex-col gap-3">
        <SkeletonLoader shape="card" className="h-40" />
        <SkeletonLoader shape="card" className="h-28" />
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

  const handleDelete = () => {
    if (!scanId) return;
    if (!window.confirm(t("scanDetail.deleteConfirm"))) return;
    deleteScan.mutate(undefined, {
      onSuccess: () => {
        void navigate("/cats");
      },
    });
  };

  const reassignable = canReassign(result);
  const labCoordinates = analysis ? formatLabCoordinates(analysis.labL, analysis.labA, analysis.labB) : "—";
  const technicalRows: { key: string; label: string; value: string }[] = [];
  if (analysis && labCoordinates !== "—") {
    technicalRows.push({ key: "lab", label: t("scanDetail.labLabel"), value: labCoordinates });
  }
  if (analysis && isFiniteNumber(analysis.deltaEMin)) {
    technicalRows.push({ key: "de", label: t("scanDetail.deltaEMinLabel"), value: analysis.deltaEMin.toFixed(2) });
  }

  return (
    /**
     * Desktop (>= lg) chia hai cột như `/scan/result/:id`: kết quả + lời khuyên ở cột chính,
     * thông tin kỹ thuật + thao tác (tranh chấp/gán lại/xoá) ở cột phụ 340px. Trước đây cả
     * trang là một cột 944px với ba nút to bản xếp chồng ở đáy. Mobile giữ một cột.
     */
    <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:gap-6">
      <div className="flex min-w-0 flex-1 flex-col gap-4">
        {result.disputedAt ? (
          <Badge tone="neutral" className="self-start">
            {t("scanDetail.disputedBadge")}
          </Badge>
        ) : null}

        <ScanResultSummary result={result} bands={bands ?? []} matchLabel={t("result.matchLabel")} />

        {/* Cùng khối "Bạn nên làm gì?" với `/scan/result/:id` để hai màn đọc giống nhau. */}
        <ScanAdviceCard result={result} />

        <DisclaimerBanner variant="short" />
      </div>

      <aside className="flex w-full flex-col gap-4 lg:sticky lg:top-6 lg:w-[340px] lg:shrink-0">
        <ScanMetaCard result={result} />

        {technicalRows.length > 0 ? (
          <Card padding="md" className="flex flex-col gap-2">
            <p className="text-body font-semibold text-text-primary">{t("scanDetail.analysisTitle")}</p>
            <dl className="flex flex-col gap-1.5">
              {technicalRows.map((row) => (
                <div key={row.key} className="flex items-start justify-between gap-3">
                  <dt className="text-caption text-text-secondary">{row.label}</dt>
                  <dd className="text-right text-caption font-semibold text-text-primary">{row.value}</dd>
                </div>
              ))}
            </dl>
          </Card>
        ) : null}

        <div className="flex flex-col gap-2">
          {result.disputedAt ? (
            <Button
              variant="tertiary"
              loading={clearDispute.isPending}
              onClick={() => {
                clearDispute.mutate();
              }}
            >
              {t("dispute.clear")}
            </Button>
          ) : showDisputeForm ? (
            <div className="flex flex-col gap-2 rounded-xl border border-border bg-surface p-3">
              <p className="text-caption font-semibold text-text-primary">{t("dispute.title")}</p>
              <textarea
                value={noteDraft}
                onChange={(e) => {
                  setNoteDraft(e.target.value);
                }}
                placeholder={t("dispute.notePlaceholder")}
                maxLength={500}
                className="min-h-20 rounded-md border border-border bg-surface p-2 text-body text-text-primary"
              />
              {dispute.isError ? (
                <p role="alert" className="text-caption text-danger-text">
                  {t("scanDetail.actionError")}
                </p>
              ) : null}
              <div className="flex gap-2">
                <Button
                  variant="primary"
                  size="sm"
                  className="flex-1"
                  loading={dispute.isPending}
                  onClick={() => {
                    dispute.mutate(
                      { note: noteDraft || undefined },
                      {
                        onSuccess: () => {
                          setShowDisputeForm(false);
                        },
                      },
                    );
                  }}
                >
                  {t("dispute.submit")}
                </Button>
                <Button
                  variant="tertiary"
                  size="sm"
                  className="flex-1"
                  onClick={() => {
                    setShowDisputeForm(false);
                  }}
                >
                  {t("dispute.cancel")}
                </Button>
              </div>
            </div>
          ) : (
            <Button
              variant="tertiary"
              leftIcon={<Flag className="size-4" aria-hidden="true" />}
              onClick={() => {
                setShowDisputeForm(true);
              }}
            >
              {t("result.disputeCta")}
            </Button>
          )}

          {reassignable ? (
            <Button
              variant="tertiary"
              leftIcon={<Repeat2 className="size-4" aria-hidden="true" />}
              onClick={() => {
                void navigate(`/scan/${scanId ?? ""}/reassign-cat`);
              }}
            >
              {t("result.reassignCta")}
            </Button>
          ) : null}

          <Button
            variant="tertiary"
            className="border-transparent text-danger-text hover:bg-danger-bg"
            leftIcon={<Trash2 className="size-4" aria-hidden="true" />}
            loading={deleteScan.isPending}
            onClick={handleDelete}
          >
            {t("scanDetail.deleteCta")}
          </Button>
          {deleteScan.isError || clearDispute.isError ? (
            <p role="alert" className="text-caption text-danger-text">
              {t("scanDetail.actionError")}
            </p>
          ) : null}
        </div>
      </aside>
    </div>
  );
}
