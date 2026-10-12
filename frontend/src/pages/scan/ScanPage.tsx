import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import type { TFunction } from "i18next";
import { Navigate, useNavigate } from "react-router";
import { ArrowLeft, History, Ticket } from "lucide-react";
import { Button } from "@/shared/ui";
import {
  AnalyzingState,
  CaptureTrigger,
  InconclusiveNotice,
  useRecentCatScans,
  useScanCaptureStore,
  useSubmitScan,
} from "@/features/scan";
import { useCreditBalance, useEntitlement } from "@/features/credit";
import { isApiError } from "@/shared/api/errors";
import { usePhBands } from "@/entities/ph-bands";
import type { CaptureSource } from "@/entities/scan-result";
import { useCat } from "@/features/history";
import { useBreakpoint } from "@/shared/lib/hooks/useBreakpoint";
import { SelectCatPage } from "./SelectCatPage";
import {
  OutOfCreditPanel,
  WebCapturePanel,
  WebCatCard,
  WebResultPanel,
  WebScanHeader,
  formatScanTime,
  type CaptureErrorMessage,
  type HeaderPill,
} from "./webPanels";

function captureErrorOf(error: unknown, t: TFunction<["scan", "common"]>): CaptureErrorMessage {
  if (!isApiError(error)) return { tone: "danger", text: t("capture.uploadError") };
  switch (error.code) {
    case "IMAGE_TOO_SMALL":
      return { tone: "danger", text: t("capture.errors.imageTooSmall") };
    case "IMAGE_TOO_LARGE":
      return { tone: "danger", text: t("capture.errors.imageTooLarge") };
    case "IMAGE_FORMAT_UNSUPPORTED":
      return { tone: "danger", text: t("capture.errors.imageUnsupported") };
    case "IMAGE_DECODE_FAILED":
      return { tone: "danger", text: t("capture.errors.imageDecodeFailed") };
    case "VISION_ENGINE_UNAVAILABLE":
      return { tone: "warning", text: t("capture.errors.engineUnavailable") };
    case "CAT_NOT_OWNED":
    case "CAT_ARCHIVED":
      return { tone: "danger", text: t("capture.errors.catUnavailable") };
    default:
      break;
  }
  if (error.status === 413) return { tone: "danger", text: t("capture.errors.imageTooLarge") };
  if (error.status === 415) return { tone: "danger", text: t("capture.errors.imageUnsupported") };
  if (error.status === 429 || error.status === 409) return { tone: "warning", text: t("capture.busyError") };
  if (error.status === 503) return { tone: "warning", text: t("capture.errors.engineUnavailable") };
  return { tone: "danger", text: t("capture.uploadError") };
}

export function ScanPage() {
  const { t } = useTranslation(["scan", "common"]);
  const navigate = useNavigate();
  const { scanRequestId, assignment, selectedCatId, file, previewUrl, setFile, setSelectedCat, startNewAttempt } =
    useScanCaptureStore();
  const submitScan = useSubmitScan();
  const { data: bands } = usePhBands();
  const balanceQuery = useCreditBalance();
  const entitlementQuery = useEntitlement();
  const [captureSource, setCaptureSource] = useState<CaptureSource>("CAMERA");
  const catId = assignment === "ASSIGNED" ? (selectedCatId ?? undefined) : undefined;
  const { data: selectedCat } = useCat(catId);
  const { data: latestScans } = useRecentCatScans(catId, 1);
  const isDesktop = useBreakpoint("lg");
  const resultScanId = submitScan.isSuccess ? (submitScan.data.scanId ?? null) : null;

  useEffect(() => {
    if (resultScanId) {
      void navigate(`/scan/result/${resultScanId}`, { replace: true });
    }
  }, [resultScanId, navigate]);

  if (!assignment) {
    return isDesktop ? <SelectCatPage /> : <Navigate to="/scan/select-cat" replace />;
  }

  const balance = balanceQuery.data;
  const outOfCredit =
    (balance !== undefined && balance.availableBalance <= 0 && balance.trialScansRemaining <= 0) ||
    (submitScan.isError && isApiError(submitScan.error) && submitScan.error.status === 402);
  const hasPackage = Boolean(entitlementQuery.data?.currentPackage);
  const inconclusive = submitScan.isSuccess && resultScanId === null ? submitScan.data : null;
  const analyzing = submitScan.isPending || resultScanId !== null;
  const submitError = submitScan.isError && !outOfCredit ? captureErrorOf(submitScan.error, t) : null;
  const inconclusiveBand = bands?.find((band) => band.code === "INCONCLUSIVE");

  const handleSubmit = () => {
    if (!file || outOfCredit) return;
    submitScan.mutate({
      metadata: {
        scanRequestId,
        catId: assignment === "SHARED_UNKNOWN" ? null : selectedCatId,
        assignment,
        capturedAt: new Date().toISOString(),
        captureSource,
      },
      file,
    });
  };

  const handlePick = (picked: File | null, source?: CaptureSource) => {
    submitScan.reset();
    setFile(picked);
    if (source) setCaptureSource(source);
  };

  const retryAfterInconclusive = () => {
    submitScan.reset();
    startNewAttempt();
  };

  const inconclusiveNotice = inconclusive ? (
    <InconclusiveNotice
      title={t("result.inconclusive.title")}
      description={t("result.inconclusive.description")}
      band={inconclusiveBand}
      flags={inconclusive.qualityFlags}
      retryHint={
        inconclusive.retryHintKey
          ? t(inconclusive.retryHintKey.replace(/^scan\./, ""), { defaultValue: t("result.inconclusive.hint") })
          : t("result.inconclusive.hint")
      }
      retryLabel={t("result.inconclusive.retryCta")}
      onRetry={retryAfterInconclusive}
      footnote={
        balance !== undefined ? t("result.inconclusive.balanceKept", { count: balance.availableBalance }) : undefined
      }
    />
  ) : null;

  const outOfCreditPanel = balance ? (
    <OutOfCreditPanel
      availableBalance={balance.availableBalance}
      trialScansRemaining={balance.trialScansRemaining}
      hasPackage={hasPackage}
    />
  ) : null;

  const latest = latestScans?.[0];
  const pills: HeaderPill[] = [];
  if (latest) {
    pills.push({
      key: "last",
      icon: <History className="size-4" />,
      label: t("web.lastScanLabel"),
      value:
        typeof latest.phValue === "number"
          ? t("web.lastScanValue", { date: formatScanTime(latest.capturedAt), value: latest.phValue.toFixed(1) })
          : formatScanTime(latest.capturedAt),
    });
  }
  if (balance) {
    pills.push({
      key: "balance",
      icon: <Ticket className="size-4" />,
      label: t("web.balanceLabel"),
      value: t("web.balanceValue", { count: balance.availableBalance }),
    });
  }

  const catMeta = selectedCat
    ? [
        selectedCat.breedName ?? selectedCat.breedOther,
        typeof selectedCat.weightKg === "number" ? t("selectCat.weight", { value: selectedCat.weightKg }) : null,
      ]
        .filter((part): part is string => Boolean(part))
        .join(" • ")
    : null;

  if (isDesktop) {
    let rightPanel;
    if (analyzing) {
      rightPanel = (
        <div className="rounded-2xl bg-surface shadow-xs">
          <AnalyzingState text={t("analyzing.text")} note={t("analyzing.note")} previewUrl={previewUrl} />
        </div>
      );
    } else if (inconclusiveNotice) {
      rightPanel = <div className="rounded-2xl bg-surface shadow-xs">{inconclusiveNotice}</div>;
    } else if (outOfCredit && outOfCreditPanel) {
      rightPanel = outOfCreditPanel;
    } else {
      rightPanel = <WebResultPanel bands={bands ?? []} />;
    }

    return (
      <div className="flex w-full flex-col gap-4">
        <WebScanHeader title={t("web.pageTitle")} subtitle={t("web.pageSubtitle")} pills={pills} />
        <div className="grid grid-cols-12 items-start gap-4">
          <div className="col-span-5">
            <WebCapturePanel
              catCard={
                <WebCatCard
                  name={assignment === "SHARED_UNKNOWN" ? null : (selectedCat?.name ?? null)}
                  meta={catMeta}
                  avatarUrl={selectedCat?.avatarUrl}
                  action={
                    <button
                      type="button"
                      disabled={analyzing}
                      onClick={() => {
                        submitScan.reset();
                        setSelectedCat(null);
                      }}
                      className="inline-flex min-h-9 shrink-0 items-center rounded-lg px-3 text-caption font-semibold text-primary-dark hover:bg-chip-bg disabled:opacity-50"
                    >
                      {t("selectCat.changeCta")}
                    </button>
                  }
                />
              }
              previewUrl={previewUrl}
              onPickFile={handlePick}
              onSubmit={handleSubmit}
              submitDisabled={!file || analyzing || inconclusive !== null}
              busy={analyzing}
              locked={outOfCredit}
              error={submitError}
            />
          </div>
          <div className="col-span-7">{rightPanel}</div>
        </div>
      </div>
    );
  }

  if (analyzing) {
    return <AnalyzingState text={t("analyzing.text")} note={t("analyzing.note")} previewUrl={previewUrl} />;
  }

  const mobileTopBar = (
    <div className="flex items-center gap-2">
      <button
        type="button"
        onClick={() => {
          void navigate(-1);
        }}
        aria-label={t("actions.back", { ns: "common" })}
        className="flex size-11 shrink-0 items-center justify-center rounded-full text-text-primary hover:bg-background-alt"
      >
        <ArrowLeft className="size-5" aria-hidden="true" />
      </button>
      <h1 className="truncate text-h3 font-bold text-text-primary">{t("web.pageTitle")}</h1>
    </div>
  );

  if (inconclusiveNotice) {
    return (
      <div className="flex flex-col gap-2 p-4">
        {mobileTopBar}
        {inconclusiveNotice}
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4 p-4">
      {mobileTopBar}
      <CaptureTrigger
        previewUrl={previewUrl}
        onFileSelected={handlePick}
        guideText={t("capture.guide")}
        retakeLabel={t("capture.retake")}
        cameraLabel={t("capture.camera")}
        chooseFromGalleryLabel={t("capture.gallery")}
        disabled={submitScan.isPending}
      />
      {outOfCredit && outOfCreditPanel ? (
        outOfCreditPanel
      ) : (
        <>
          {submitError ? (
            <p
              role="alert"
              className={
                submitError.tone === "danger"
                  ? "rounded-xl bg-danger-bg p-3 text-caption text-danger-text"
                  : "rounded-xl bg-warning-bg p-3 text-caption text-warning-text"
              }
            >
              {submitError.text}
            </p>
          ) : null}
          <p className="text-caption text-text-tertiary">{t("capture.creditNote")}</p>
          <Button variant="primary" size="lg" disabled={!file} onClick={handleSubmit}>
            {t("capture.submit")}
          </Button>
        </>
      )}
    </div>
  );
}
