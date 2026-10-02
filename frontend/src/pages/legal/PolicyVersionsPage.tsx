import { useTranslation } from "react-i18next";
import { Link, useParams } from "react-router";
import { EmptyState, SkeletonLoader } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import { usePolicyCurrent } from "./hooks";
import { LEGAL_ASIDE, LEGAL_ASIDE_CARD, LEGAL_COLUMN, LEGAL_SHELL, LEGAL_SPLIT } from "./shell";
import type { PolicyCode } from "./types";

const SLUG_TO_CODE: Record<string, PolicyCode> = {
  terms: "TERMS",
  privacy: "PRIVACY",
  cookies: "COOKIE",
  "medical-disclaimer": "MEDICAL_DISCLAIMER",
};

/**
 * `/legal/:policyCode/versions` (p9 §9.4.3 #15, REQ-LEGAL-02). Backend hôm nay CHỈ có
 * `GET /policies/{code}` (bản HIỆN HÀNH) — không có endpoint liệt kê lịch sử phiên bản.
 * Trang này hiển thị đúng những gì có (bản hiện hành) và nói rõ lịch sử đầy đủ chưa sẵn
 * sàng, thay vì bịa danh sách phiên bản không có thật (xem `docs/handovers/A2-fe.md`).
 *
 * DESKTOP (>= lg): danh sách phiên bản ở cột chính 560px, còn ghi chú "lịch sử chưa khả
 * dụng" + lối quay lại tài liệu chuyển sang cột phụ 360px — cùng idiom với `settings`.
 */
export function PolicyVersionsPage() {
  const { t } = useTranslation(["legal", "common"]);
  const { policyCode: slug } = useParams<{ policyCode: string }>();
  const code = slug ? SLUG_TO_CODE[slug] : undefined;
  const { data, isLoading } = usePolicyCurrent(code ?? "TERMS", "vi");

  if (!code) {
    return <EmptyState title={t("versions.notFoundTitle")} description={t("versions.notFoundDescription")} />;
  }

  if (isLoading) {
    return (
      <div className="mx-auto flex w-full max-w-2xl flex-col gap-4 px-4 py-8">
        <SkeletonLoader shape="text" className="h-8 w-1/2" />
        <SkeletonLoader shape="card" className="h-24" />
      </div>
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

          {data ? (
            <ul className="flex flex-col gap-2">
              <li className="flex items-center justify-between rounded-lg border border-border bg-surface p-3">
                <div className="flex flex-col">
                  <span className="text-body font-semibold text-text-primary">
                    {t("versions.currentBadge")} — {data.version}
                  </span>
                  <span className="text-caption text-text-secondary">
                    {t("document.versionBanner", {
                      version: data.version,
                      date: new Date(data.effectiveFrom).toLocaleDateString("vi-VN"),
                    })}
                  </span>
                </div>
                <Link
                  to={`/legal/${slug ?? ""}/v/${data.version}`}
                  className="text-caption font-semibold text-primary hover:underline"
                >
                  {t("versions.viewLink")}
                </Link>
              </li>
            </ul>
          ) : (
            <EmptyState title={t("document.notPublishedYet")} />
          )}

          {/* Ghi chú + lối quay lại: ở mobile nằm ngay dưới danh sách, ở desktop chuyển sang cột phụ. */}
          <div className="flex flex-col gap-6 lg:hidden">
            <p className="rounded-lg border border-border bg-background-alt p-3 text-caption text-text-tertiary">
              {t("versions.historyNotAvailable")}
            </p>
            <Link to={`/legal/${slug ?? ""}`} className="text-caption font-semibold text-primary hover:underline">
              {t("versions.backToDocument")}
            </Link>
          </div>
        </div>

        <aside className={LEGAL_ASIDE}>
          <div className={LEGAL_ASIDE_CARD}>
            <p className="text-caption text-text-tertiary">{t("versions.historyNotAvailable")}</p>
            <Link to={`/legal/${slug ?? ""}`} className="text-caption font-semibold text-primary hover:underline">
              {t("versions.backToDocument")}
            </Link>
          </div>
        </aside>
      </div>
    </div>
  );
}
