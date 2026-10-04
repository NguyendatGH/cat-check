import { useTranslation } from "react-i18next";
import { Link, useParams } from "react-router";
import { EmptyState, ErrorState, SkeletonLoader } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import { usePolicyVersions } from "./hooks";
import { LEGAL_ASIDE, LEGAL_ASIDE_CARD, LEGAL_COLUMN, LEGAL_SHELL, LEGAL_SPLIT } from "./shell";
import type { PolicyCode } from "./types";

const SLUG_TO_CODE: Record<string, PolicyCode> = {
  terms: "TERMS",
  privacy: "PRIVACY",
  cookies: "COOKIE",
  "medical-disclaimer": "MEDICAL_DISCLAIMER",
};

/**
 * `/legal/:policyCode/versions` (p9 §9.4.3 #15, REQ-LEGAL-02).
 *
 * Nguồn dữ liệu là **F9 `GET /policies/{code}/versions`** — endpoint liệt kê MỌI phiên bản
 * kể cả đã hết hiệu lực. Trước đây trang gọi C16 (`/policies/{code}`, chỉ bản hiện hành) nên
 * lịch sử luôn hiện đúng một dòng; đó là hiển thị SAI, không phải giới hạn backend.
 *
 * Không có trường nào trong response cho biết "ai duyệt"/"lý do sửa" nên trang không vẽ cột
 * đó — chỉ phiên bản, hiệu lực, cờ xin lại đồng ý và tóm tắt thay đổi (`summaryOfChanges`).
 *
 * DESKTOP (>= lg): danh sách phiên bản ở cột chính 560px, ghi chú + lối quay lại tài liệu ở
 * cột phụ 360px — cùng idiom với `settings`.
 */
export function PolicyVersionsPage() {
  const { t, i18n } = useTranslation(["legal", "common"]);
  const { policyCode: slug } = useParams<{ policyCode: string }>();
  const code = slug ? SLUG_TO_CODE[slug] : undefined;
  const { data, isPending, isError, refetch } = usePolicyVersions(code);

  if (!code) {
    return <EmptyState title={t("versions.notFoundTitle")} description={t("versions.notFoundDescription")} />;
  }

  const formatDate = (iso: string) => new Date(iso).toLocaleDateString(i18n.language);

  let body;
  if (isPending) {
    body = (
      <div className="flex flex-col gap-4">
        <SkeletonLoader shape="text" className="h-8 w-1/2" />
        <SkeletonLoader shape="card" className="h-24" />
      </div>
    );
  } else if (isError) {
    body = (
      <ErrorState
        title={t("document.loadError")}
        onRetry={() => {
          void refetch();
        }}
        retryLabel={t("actions.retry", { ns: "common" })}
      />
    );
  } else if (data.length === 0) {
    body = <EmptyState title={t("versions.emptyTitle")} description={t("versions.emptyDescription")} />;
  } else {
    body = (
      <ul className="flex flex-col gap-2">
        {data.map((item) => (
          <li
            key={`${item.version}-${item.locale}`}
            className="flex flex-col gap-2 rounded-lg border border-border bg-surface p-3 sm:flex-row sm:items-start sm:justify-between"
          >
            <div className="flex min-w-0 flex-col gap-1">
              <span className="flex flex-wrap items-center gap-2">
                <span className="text-body font-semibold text-text-primary">
                  {t("versions.versionLabel", { version: item.version })}
                </span>
                {item.current ? (
                  <span className="rounded-full bg-success-bg px-2 py-0.5 text-small font-semibold text-success-text">
                    {t("versions.currentBadge")}
                  </span>
                ) : null}
                {item.requiresReconsent ? (
                  <span className="rounded-full bg-warning-bg px-2 py-0.5 text-small font-semibold text-warning-text">
                    {t("versions.requiresReconsent")}
                  </span>
                ) : null}
              </span>
              <span className="text-caption text-text-secondary">
                {item.effectiveTo
                  ? t("versions.effectiveRange", {
                      from: formatDate(item.effectiveFrom),
                      to: formatDate(item.effectiveTo),
                    })
                  : t("versions.effectiveFrom", { from: formatDate(item.effectiveFrom) })}
              </span>
              {item.summaryOfChanges ? (
                <span className="text-caption text-text-tertiary">
                  {t("document.summaryOfChangesLabel")} {item.summaryOfChanges}
                </span>
              ) : null}
            </div>
            <Link
              to={`/legal/${slug ?? ""}/v/${item.version}`}
              className="shrink-0 text-caption font-semibold text-primary hover:underline"
            >
              {t("versions.viewLink")}
            </Link>
          </li>
        ))}
      </ul>
    );
  }

  return (
    <div className={LEGAL_SHELL}>
      <div className={LEGAL_SPLIT}>
        <div className={cn(LEGAL_COLUMN, "gap-6")}>
          <header className="flex flex-col gap-1">
            <h1 className="text-h1 font-bold text-text-primary">{t("versions.title")}</h1>
            <p className="text-caption text-text-secondary">{t("versions.subtitle")}</p>
          </header>

          {body}

          {/* Ở mobile ghi chú nằm ngay dưới danh sách, ở desktop chuyển sang cột phụ. */}
          <div className="flex flex-col gap-6 lg:hidden">
            <p className="rounded-lg border border-border bg-background-alt p-3 text-caption text-text-tertiary">
              {t("versions.permalinkNote")}
            </p>
            <Link to={`/legal/${slug ?? ""}`} className="text-caption font-semibold text-primary hover:underline">
              {t("versions.backToDocument")}
            </Link>
          </div>
        </div>

        <aside className={LEGAL_ASIDE}>
          <div className={LEGAL_ASIDE_CARD}>
            <p className="text-caption text-text-tertiary">{t("versions.permalinkNote")}</p>
            <Link to={`/legal/${slug ?? ""}`} className="text-caption font-semibold text-primary hover:underline">
              {t("versions.backToDocument")}
            </Link>
          </div>
        </aside>
      </div>
    </div>
  );
}
