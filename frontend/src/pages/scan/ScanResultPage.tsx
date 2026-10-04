import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router";
import { LineChart, Repeat2 } from "lucide-react";
import { Button, ErrorState, SkeletonLoader } from "@/shared/ui";
import { DisclaimerBanner } from "@/entities/disclaimer";
import { usePhBands } from "@/entities/ph-bands";
import { ScanAdviceCard, ScanResultSummary, useScan } from "@/features/scan";
import { WebResultPanel, WebScanHeader } from "./webPanels";

/**
 * `/scan/result/:scanId` — kết quả ngay sau khi phân tích xong (p8 §8.5.4 E4, mockup `04`).
 *
 * Từ `lg` trở lên dùng bố cục desktop theo Figma `16:6333` (cột phải "Clinical Results" +
 * dải xu hướng đáy trang); dưới `lg` giữ nguyên bố cục mobile cũ.
 */
export function ScanResultPage() {
  const { t } = useTranslation(["scan", "common"]);
  const { scanId } = useParams<{ scanId: string }>();
  const navigate = useNavigate();
  const { data: result, isPending, isError, refetch } = useScan(scanId);
  const { data: bands } = usePhBands();

  if (isPending) {
    return (
      <div className="flex flex-col gap-3 p-4">
        <SkeletonLoader shape="card" className="h-40" />
        <SkeletonLoader shape="text" className="h-6 w-1/2" />
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

  return (
    <>
      {/* Desktop (>= lg): bố cục Figma 16:6333 */}
      <div className="hidden w-full flex-col gap-4 py-6 lg:flex">
        <WebScanHeader />
        <WebResultPanel bands={bands ?? []} phValue={result.phValue} bandCode={result.bandCode} />
        <DisclaimerBanner variant="short" />
        {result.triggeredFlags.length > 0 ? <DisclaimerBanner variant="emergency" hasWarningFlags /> : null}
      </div>

      {/* Mobile (< lg): giữ nguyên bố cục cũ */}
      <div className="flex flex-col gap-4 p-4 lg:hidden">
        <ScanResultSummary result={result} bands={bands ?? []} matchLabel={t("result.matchLabel")} />

        <ScanAdviceCard result={result} />

        <DisclaimerBanner variant="short" />
        {result.triggeredFlags.length > 0 ? <DisclaimerBanner variant="emergency" hasWarningFlags /> : null}

        {/* Mockup `04`: đúng 2 CTA — chính (xem phân tích) + phụ nền trắng. "Về trang chủ" bỏ
            đi vì thanh tab dưới đã có sẵn lối về Trang chủ. */}
        <div className="flex flex-col gap-2">
          <Button
            variant="primary"
            size="lg"
            leftIcon={<LineChart className="size-4" aria-hidden="true" />}
            onClick={() => {
              void navigate(scanId ? `/scans/${scanId}` : "/cats");
            }}
          >
            {t("result.viewAnalysisCta")}
          </Button>
          {result.reassignRemaining > 0 ? (
            <Button
              variant="tertiary"
              size="lg"
              className="border-transparent bg-surface font-bold"
              leftIcon={<Repeat2 className="size-4" aria-hidden="true" />}
              onClick={() => {
                void navigate(scanId ? `/scan/${scanId}/reassign-cat` : "/scan/select-cat");
              }}
            >
              {t("result.reassignCta")}
            </Button>
          ) : null}
        </div>
      </div>
    </>
  );
}
