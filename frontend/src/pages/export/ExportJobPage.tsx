import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router";
import { Button, ErrorState, SkeletonLoader } from "@/shared/ui";
import { ExportJobStatusCard, exportDownloadUrl, useExportJob } from "@/features/export";

/**
 * `/export/:jobId` — poll trạng thái job xuất PDF tới khi sẵn sàng (p8 §8.4.10 J3/J4).
 *
 * Mockup KHÔNG có frame riêng cho màn này (cả `10` lẫn `Web - 08 & 10`), nên desktop chỉ
 * canh giữa thẻ trạng thái trong khung nội dung 944px của `TaskLayout` — không bịa thêm nội
 * dung mới.
 */
export function ExportJobPage() {
  const { t } = useTranslation(["export", "common"]);
  const { jobId } = useParams<{ jobId: string }>();
  const navigate = useNavigate();
  const { data: job, isPending, isError, refetch } = useExportJob(jobId);

  if (isPending) {
    return (
      <div className="flex flex-col gap-3 lg:mx-auto lg:max-w-[560px]">
        <SkeletonLoader shape="card" className="h-56" />
      </div>
    );
  }

  if (isError) {
    return (
      <ErrorState
        title={t("state.loadError")}
        onRetry={() => {
          void refetch();
        }}
        retryLabel={t("actions.retry", { ns: "common" })}
      />
    );
  }

  return (
    <div className="flex flex-col gap-4 lg:mx-auto lg:max-w-[560px]">
      <ExportJobStatusCard
        job={job}
        statusLabel={t(`job.status.${job.status}`)}
        documentCodeLabel={t("job.documentCodeLabel")}
        pageCountLabel={
          job.status === "READY" && job.pageCount !== null && job.scanCount !== null
            ? t("job.pageCountLabel", { pages: job.pageCount, scans: job.scanCount })
            : undefined
        }
        downloadLabel={t("job.download")}
        downloadHref={jobId ? exportDownloadUrl(jobId) : "#"}
      />
      <Button
        variant="tertiary"
        onClick={() => {
          void navigate("/cats");
        }}
      >
        {t("actions.back", { ns: "common" })}
      </Button>
    </div>
  );
}
