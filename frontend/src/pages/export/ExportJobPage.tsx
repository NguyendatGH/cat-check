import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router";
import { Button, ErrorState, SkeletonLoader } from "@/shared/ui";
import { ExportJobStatusCard, exportDownloadUrl, useExportJob } from "@/features/export";

const HOUR_MS = 60 * 60 * 1000;

/**
 * `/export/:jobId` — poll trạng thái job xuất PDF tới khi sẵn sàng (p8 §8.4.10 J3/J4).
 *
 * Dựng theo frame `M-10a` / `Web - 10a. Trạng thái & Tải Báo cáo` (design/figma-plugin
 * `30-core.js`): tiêu đề trang, thẻ trạng thái 560px canh giữa, nút "Quay lại". Mọi con số
 * (mã hồ sơ, số trang, số lượt quét, hạn tải) đến từ `ExportJobResponse`.
 *
 * ĐÃ BỎ so với frame: nút "Xem tất cả bản xuất" — không có route danh sách bản xuất.
 */
export function ExportJobPage() {
  const { t } = useTranslation(["export", "common"]);
  const { jobId } = useParams<{ jobId: string }>();
  const navigate = useNavigate();
  const { data: job, isPending, isError, refetch } = useExportJob(jobId);

  const header = <h1 className="text-h2 font-bold text-text-primary lg:text-h1">{t("pages.job.title")}</h1>;

  if (isPending) {
    return (
      <div className="flex flex-col gap-4 lg:mx-auto lg:w-full lg:max-w-[560px]">
        {header}
        <SkeletonLoader shape="card" className="h-56" />
      </div>
    );
  }

  if (isError) {
    return (
      <div className="flex flex-col gap-4 lg:mx-auto lg:w-full lg:max-w-[560px]">
        {header}
        <ErrorState
          title={t("state.loadError")}
          onRetry={() => {
            void refetch();
          }}
          retryLabel={t("actions.retry", { ns: "common" })}
        />
      </div>
    );
  }

  const recreate = () => {
    void navigate("/export");
  };

  // "Còn N ngày H giờ để tải" từ `expiresAt`; không có hạn thì không in.
  let expiry: string | undefined;
  if (job.status === "READY" && job.expiresAt) {
    const remainingHours = Math.max(0, Math.floor((new Date(job.expiresAt).getTime() - Date.now()) / HOUR_MS));
    const days = Math.floor(remainingHours / 24);
    const hours = remainingHours % 24;
    expiry =
      days > 0
        ? t("job.expiresIn", { days, hours: String(hours).padStart(2, "0") })
        : t("job.expiresInHours", { hours: remainingHours });
  }

  return (
    <div className="flex flex-col gap-4 lg:mx-auto lg:w-full lg:max-w-[560px]">
      {header}
      <ExportJobStatusCard
        job={job}
        statusLabel={t(`job.status.${job.status}`)}
        documentCodeLabel={t("job.documentCodeLabel")}
        pageCountLabel={
          job.status === "READY" && job.pageCount !== null && job.scanCount !== null
            ? t("job.pageCountLabel", { pages: job.pageCount, scans: job.scanCount })
            : undefined
        }
        expiryLabel={expiry}
        hint={
          job.status === "FAILED"
            ? t("job.failedHint")
            : job.status === "EXPIRED"
              ? t("job.expiredHint")
              : undefined
        }
        progressLabel={t("job.progressLabel")}
        downloadLabel={t("job.download")}
        downloadHref={jobId ? exportDownloadUrl(jobId) : "#"}
        retryLabel={
          job.status === "FAILED" ? t("job.retry") : job.status === "EXPIRED" ? t("job.recreate") : undefined
        }
        onRetry={job.status === "FAILED" || job.status === "EXPIRED" ? recreate : undefined}
      />
      <Button
        variant="tertiary"
        onClick={() => {
          // Về đúng hồ sơ của bé vừa xuất (`catId` của job), không về danh sách chung.
          void navigate(`/cats/${job.catId}`);
        }}
      >
        {t("job.backToCat")}
      </Button>
    </div>
  );
}
