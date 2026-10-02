import { useEffect } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { Button, EmptyState } from "@/shared/ui";
import {
  AnalyzingState,
  CaptureTrigger,
  InconclusiveNotice,
  useScanCaptureStore,
  useSubmitScan,
} from "@/features/scan";
import { isApiError } from "@/shared/api/errors";
import { usePhBands } from "@/entities/ph-bands";
import { WebCapturePanel, WebResultPanel, WebScanHeader } from "./webPanels";

/**
 * `/scan` — chụp/chọn ảnh + gửi phân tích một bước (E1, TD-02). Yêu cầu đã có mèo/khay chung được
 * chọn từ `/scan/select-cat` (`useScanCaptureStore`) — nếu chưa, điều hướng ngược lại bước đó.
 */
export function ScanPage() {
  const { t } = useTranslation(["scan", "common"]);
  const navigate = useNavigate();
  const { scanRequestId, assignment, selectedCatId, file, previewUrl, setFile, startNewRequest } =
    useScanCaptureStore();
  const submitScan = useSubmitScan();
  const { data: bands } = usePhBands();
  const resultScanId = submitScan.isSuccess ? submitScan.data.scanId : null;

  useEffect(() => {
    if (resultScanId) {
      void navigate(`/scan/result/${resultScanId}`, { replace: true });
    }
  }, [resultScanId, navigate]);

  if (!assignment) {
    return (
      <EmptyState
        title={t("capture.missingCatTitle")}
        description={t("capture.missingCatDescription")}
        action={
          <Button variant="primary" onClick={() => { void navigate("/scan/select-cat"); }}>
            {t("capture.goSelectCat")}
          </Button>
        }
      />
    );
  }

  if (submitScan.isPending) {
    return <AnalyzingState text={t("analyzing.text")} />;
  }

  if (submitScan.isSuccess && submitScan.data.scanId === null) {
    return (
      <InconclusiveNotice
        title={t("result.inconclusive.title")}
        description={t("result.inconclusive.description")}
        retryHint={submitScan.data.retryHintKey ? t(submitScan.data.retryHintKey.replace(/^scan\./, "")) : undefined}
        retryLabel={t("result.inconclusive.retryCta")}
        onRetry={() => {
          startNewRequest();
          void navigate("/scan/select-cat");
        }}
      />
    );
  }

  if (resultScanId) {
    return <AnalyzingState text={t("analyzing.text")} />;
  }

  const handleSubmit = () => {
    if (!file) return;
    submitScan.mutate({
      metadata: {
        scanRequestId,
        catId: assignment === "SHARED_UNKNOWN" ? null : selectedCatId,
        assignment,
        capturedAt: new Date().toISOString(),
        captureSource: "CAMERA",
      },
      file,
    });
  };

  const submitError =
    submitScan.isError && isApiError(submitScan.error) && submitScan.error.status === 429
      ? t("capture.busyError")
      : submitScan.isError
        ? t("capture.uploadError")
        : null;

  return (
    <>
      {/* Desktop (>= lg): lưới 2 cột theo Figma 16:6368 — chụp/cân chỉnh bên trái, kết quả bên phải. */}
      <div className="hidden w-full flex-col gap-4 py-6 lg:flex">
        <WebScanHeader />
        <div className="grid grid-cols-12 gap-4">
          <div className="col-span-5">
            <WebCapturePanel
              previewUrl={previewUrl}
              onPickFile={setFile}
              onSubmit={handleSubmit}
              submitDisabled={!file}
            />
          </div>
          <div className="col-span-7">
            <WebResultPanel bands={bands ?? []} />
          </div>
        </div>
      </div>

      {/* Mobile (< lg): giữ nguyên luồng chụp cũ. */}
      <div className="flex flex-col gap-4 p-4 lg:hidden">
      <CaptureTrigger
        previewUrl={previewUrl}
        onFileSelected={setFile}
        guideText={t("capture.guide")}
        retakeLabel={t("capture.retake")}
        cameraLabel={t("capture.camera")}
        chooseFromGalleryLabel={t("capture.gallery")}
        disabled={submitScan.isPending}
      />
      {submitError ? (
        <p role="alert" className="text-caption text-danger-text">
          {submitError}
        </p>
      ) : null}
      <p className="text-caption text-text-tertiary">{t("capture.creditNote")}</p>
      <Button variant="primary" disabled={!file} onClick={handleSubmit}>
        {t("capture.submit")}
      </Button>
      </div>
    </>
  );
}
