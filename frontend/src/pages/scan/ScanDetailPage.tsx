import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router";
import { Badge, Button, Card, ErrorState, SkeletonLoader } from "@/shared/ui";
import { DisclaimerBanner } from "@/entities/disclaimer";
import { usePhBands } from "@/entities/ph-bands";
import {
  ScanAdviceCard,
  ScanResultSummary,
  useClearScanDispute,
  useDeleteScan,
  useDisputeScan,
  useScan,
  useScanAnalysis,
} from "@/features/scan";

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

/** `/scans/:scanId` (TaskLayout) — chi tiết 1 lần quét từ lịch sử: kết quả + phân tích + tranh
 * chấp/xoá (E4/E5/E10/E11/E8). */
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
      <div className="flex flex-col gap-3 p-4">
        <SkeletonLoader shape="card" className="h-40" />
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

  return (
    <div className="flex flex-col gap-4 p-4">
      {result.disputedAt ? <Badge tone="neutral">{t("scanDetail.disputedBadge")}</Badge> : null}

      <ScanResultSummary result={result} bands={bands ?? []} matchLabel={t("result.matchLabel")} />

      {/* Cùng khối "Bạn nên làm gì?" với `/scan/result/:id` để hai màn đọc giống nhau; phần
          riêng của màn này (phân tích kỹ thuật, tranh chấp, xoá) nằm bên dưới. */}
      <ScanAdviceCard result={result} />

      <DisclaimerBanner variant="short" />

      {analysis ? (
        <Card padding="md" className="flex flex-col gap-2">
          <p className="text-body font-semibold text-text-primary">{t("scanDetail.analysisTitle")}</p>
          <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-caption text-text-secondary">
            <dt>{t("scanDetail.labLabel")}</dt>
            <dd>{formatLabCoordinates(analysis.labL, analysis.labA, analysis.labB)}</dd>
            <dt>{t("scanDetail.deltaEMinLabel")}</dt>
            <dd>{isFiniteNumber(analysis.deltaEMin) ? analysis.deltaEMin.toFixed(2) : "—"}</dd>
            <dt>{t("scanDetail.chartLabel")}</dt>
            <dd>
              {analysis.chartCode
                ? t("scanDetail.chartValue", { code: analysis.chartCode, version: analysis.chartVersion ?? "—" })
                : "—"}
            </dd>
            <dt>{t("scanDetail.engineLabel")}</dt>
            <dd>{analysis.engineVersion ?? "—"}</dd>
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
          <div className="flex flex-col gap-2 rounded-lg border border-border p-3">
            <textarea
              value={noteDraft}
              onChange={(e) => {
                setNoteDraft(e.target.value);
              }}
              placeholder={t("dispute.notePlaceholder")}
              maxLength={500}
              className="min-h-20 rounded-md border border-border bg-surface p-2 text-body text-text-primary"
            />
            <div className="flex gap-2">
              <Button
                variant="primary"
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
            variant="secondary"
            onClick={() => {
              setShowDisputeForm(true);
            }}
          >
            {t("result.disputeCta")}
          </Button>
        )}

        {result.reassignRemaining > 0 ? (
          <Button
            variant="secondary"
            onClick={() => {
              void navigate(`/scan/${scanId ?? ""}/reassign-cat`);
            }}
          >
            {t("result.reassignCta")}
          </Button>
        ) : null}

        <Button variant="tertiary" loading={deleteScan.isPending} onClick={handleDelete}>
          {t("scanDetail.deleteCta")}
        </Button>
      </div>
    </div>
  );
}
