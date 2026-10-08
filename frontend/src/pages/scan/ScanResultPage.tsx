import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router";
import { CalendarClock, LineChart, Repeat2, ScanLine } from "lucide-react";
import { Button, ErrorState, SkeletonLoader } from "@/shared/ui";
import { DisclaimerBanner } from "@/entities/disclaimer";
import { usePhBands } from "@/entities/ph-bands";
import { ScanAdviceCard, ScanResultSummary, canReassign, useScan, useScanCaptureStore } from "@/features/scan";
import { useCat } from "@/features/history";
import {
  ExportPdfLink,
  RecentScansStrip,
  ScanImageCard,
  ScanMetaCard,
  WebCatCard,
  WebResultPanel,
  WebScanHeader,
  formatScanTime,
} from "./webPanels";

/**
 * `/scan/result/:scanId` — kết quả ngay sau khi phân tích xong (p8 §8.5.4 E4).
 *
 * Desktop (>= lg) dựng theo `Web - 03 & 04`: cột trái 5/12 (bé mèo, ảnh, thông tin lần quét,
 * hành động), cột phải 7/12 (đánh giá tổng quát, thang pH, "Bạn nên làm gì?"), dải các lần
 * quét gần đây ở đáy. Mobile dựng theo frame `04. Kết quả Quét`.
 */
export function ScanResultPage() {
  const { t } = useTranslation(["scan", "common"]);
  const { scanId } = useParams<{ scanId: string }>();
  const navigate = useNavigate();
  const { data: result, isPending, isError, refetch } = useScan(scanId);
  const { data: bands } = usePhBands();
  const { data: cat } = useCat(result?.catId ?? undefined);
  const startNewAttempt = useScanCaptureStore((s) => s.startNewAttempt);

  if (isPending) {
    return (
      <div className="flex flex-col gap-3 p-4 lg:p-0">
        <SkeletonLoader shape="card" className="h-24" />
        <div className="grid gap-4 lg:grid-cols-12">
          <SkeletonLoader shape="card" className="h-72 lg:col-span-5" />
          <SkeletonLoader shape="card" className="h-72 lg:col-span-7" />
        </div>
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

  const reassignable = canReassign(result);
  const catName = result.catName ?? cat?.name ?? null;
  const catMeta = cat
    ? [
        cat.breedName ?? cat.breedOther,
        typeof cat.weightKg === "number" ? t("selectCat.weight", { value: cat.weightKg }) : null,
      ]
        .filter((part): part is string => Boolean(part))
        .join(" • ")
    : null;

  const goDetail = () => {
    void navigate(scanId ? `/scans/${scanId}` : "/cats");
  };
  const goReassign = () => {
    void navigate(scanId ? `/scan/${scanId}/reassign-cat` : "/scan/select-cat");
  };
  const goNewScan = () => {
    // Giữ nguyên bé/khay đang chọn, chỉ mở một lượt chụp mới (scanRequestId mới).
    startNewAttempt();
    void navigate("/scan");
  };

  return (
    <>
      {/* Desktop (>= lg) — `Web - 03 & 04` */}
      <div className="hidden w-full flex-col gap-4 lg:flex">
        <WebScanHeader
          title={t("web.resultTitle")}
          subtitle={catName ?? t("web.sharedTrayTitle")}
          pills={[
            {
              key: "captured",
              icon: <CalendarClock className="size-4" />,
              label: t("web.capturedAtLabel"),
              value: formatScanTime(result.capturedAt),
            },
          ]}
        />

        <div className="grid grid-cols-12 items-start gap-4">
          <div className="col-span-5 flex flex-col gap-4">
            <WebCatCard
              name={result.assignment === "SHARED_UNKNOWN" ? null : catName}
              meta={catMeta}
              avatarUrl={cat?.avatarUrl}
            />
            <ScanImageCard result={result} />
            <ScanMetaCard result={result} />
          </div>

          <div className="col-span-7">
            <WebResultPanel
              bands={bands ?? []}
              phValue={result.phValue}
              bandCode={result.bandCode}
              result={result}
              actions={
                <>
                  <Button
                    variant="primary"
                    className="flex-1"
                    leftIcon={<LineChart className="size-4" aria-hidden="true" />}
                    onClick={goDetail}
                  >
                    {t("result.viewAnalysisCta")}
                  </Button>
                  <ExportPdfLink />
                  {reassignable ? (
                    <Button
                      variant="tertiary"
                      className="flex-1"
                      leftIcon={<Repeat2 className="size-4" aria-hidden="true" />}
                      onClick={goReassign}
                    >
                      {t("result.reassignCta")}
                    </Button>
                  ) : null}
                  <Button
                    variant="tertiary"
                    className="flex-1"
                    leftIcon={<ScanLine className="size-4" aria-hidden="true" />}
                    onClick={goNewScan}
                  >
                    {t("web.newScanCta")}
                  </Button>
                </>
              }
            >
              <ScanAdviceCard result={result} className="rounded-2xl border-0 shadow-xs" />
              <DisclaimerBanner variant="short" />
              {result.triggeredFlags.length > 0 ? <DisclaimerBanner variant="emergency" hasWarningFlags /> : null}
            </WebResultPanel>
          </div>
        </div>

        {result.catId && catName && scanId ? (
          <RecentScansStrip catId={result.catId} catName={catName} currentScanId={scanId} bands={bands ?? []} />
        ) : null}
      </div>

      {/* Mobile (< lg) — frame `04. Kết quả Quét` */}
      <div className="flex flex-col gap-4 p-4 lg:hidden">
        <ScanResultSummary result={result} bands={bands ?? []} matchLabel={t("result.matchLabel")} />

        <ScanAdviceCard result={result} />

        <DisclaimerBanner variant="short" />
        {result.triggeredFlags.length > 0 ? <DisclaimerBanner variant="emergency" hasWarningFlags /> : null}

        {/* Mockup `04`: CTA chính (xem phân tích) + CTA phụ nền trắng. */}
        <div className="flex flex-col gap-2">
          <Button
            variant="primary"
            size="lg"
            leftIcon={<LineChart className="size-4" aria-hidden="true" />}
            onClick={goDetail}
          >
            {t("result.viewAnalysisCta")}
          </Button>
          {reassignable ? (
            <Button
              variant="tertiary"
              size="lg"
              className="border-transparent bg-surface font-bold"
              leftIcon={<Repeat2 className="size-4" aria-hidden="true" />}
              onClick={goReassign}
            >
              {t("result.reassignCta")}
            </Button>
          ) : null}
          <Button
            variant="tertiary"
            size="lg"
            className="border-transparent bg-surface font-bold"
            leftIcon={<ScanLine className="size-4" aria-hidden="true" />}
            onClick={goNewScan}
          >
            {t("web.newScanCta")}
          </Button>
        </div>
      </div>
    </>
  );
}
