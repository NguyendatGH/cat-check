import { useState, type ReactNode } from "react";
import { format } from "date-fns";
import { useTranslation } from "react-i18next";
import { CalendarPlus, Droplet, FileText } from "lucide-react";
import { Link, useNavigate, useParams } from "react-router";
import { Button, buttonVariants, Card, ErrorState, SkeletonLoader } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import { DisclaimerBanner } from "@/entities/disclaimer";
import { findBandForPh, PhBadge, phTokenStyle, usePhBands, type PhBand } from "@/entities/ph-bands";
import { useCat } from "@/features/history";
import {
  DistributionBar,
  FEATURE_NOT_IN_PLAN,
  PhTrendChart,
  RangeSegmentedControl,
  toTrendPoints,
  useCatTrends,
  useDistributionBuckets,
  type TrendRange,
} from "@/features/trends";
import { WebTrendsScreen } from "./webTrends";
import { isApiError } from "@/shared/api";

/**
 * `/cats/:catId/trends` — xu hướng pH từ D13 `GET /cats/{catId}/trends` (mockup `08`, bỏ
 * "AI Pattern Insights" ngoài phạm vi MVP — xem `features/trends/README.md`).
 *
 * Mobile và desktop dùng CHUNG một query: trước W1-E mobile tự tổng hợp từ `GET /scans` còn
 * desktop gọi D13, nên hai bố cục của cùng một màn hiện số khác nhau.
 */
export function CatTrendsPage() {
  const { t } = useTranslation(["trends", "common"]);
  const { catId } = useParams<{ catId: string }>();
  const navigate = useNavigate();
  const [range, setRange] = useState<TrendRange>("30D");

  const { data: cat } = useCat(catId);
  const { data: bands } = usePhBands();
  const webTrends = useCatTrends(catId, range);
  const { isPending, isError, refetch } = webTrends;
  const summary = webTrends.data?.stats;
  const buckets = useDistributionBuckets(webTrends.data?.points);
  // 403 duy nhất mà D13 trả là FEATURE_NOT_IN_PLAN (GET nên không dính CSRF; chưa đăng nhập
  // thì rơi vào guard trước khi tới đây). Nhận diện theo `errorCode`, nhưng vẫn coi mọi 403
  // là "khoá gói" để không rơi vào màn lỗi trắng nếu body đổi hình dạng.
  const webLocked =
    isApiError(webTrends.error) &&
    webTrends.error.status === 403 &&
    (webTrends.error.code === FEATURE_NOT_IN_PLAN || webTrends.error.code === undefined);

  const allBands = bands ?? [];
  /** Dải tham chiếu = dải `severity === "NORMAL"` do API trả — KHÔNG hard-code mã/ngưỡng. */
  const referenceBand = allBands.find((b) => b.severity === "NORMAL");
  const referenceStyle = phTokenStyle(referenceBand?.colorToken ?? "color-ph-unknown");

  const points = toTrendPoints(webTrends.data?.points);
  const latest = points.length ? points[points.length - 1] : null;
  /**
   * Ưu tiên `classification` BACKEND đã gán cho chính lần quét đó; chỉ suy lại từ con số pH khi
   * mã lạ. `findBandForPh` so `band.phMin === null` nhưng API BỎ HẲN key ở dải mở (`LOW`/`HIGH`)
   * nên `undefined !== null` ⇒ hai dải đó không bao giờ khớp và badge rơi về mã thô (VD pH 7.20
   * hiện ra chữ `SLIGHTLY_HIGH`). Tra theo `code` không dính bẫy này.
   */
  const bandByCode = (code: string | undefined): PhBand | undefined =>
    code === undefined ? undefined : allBands.find((b) => b.code === code);
  const latestBand = bandByCode(latest?.classification) ?? findBandForPh(allBands, latest?.phValue);
  const average = points.length ? points.reduce((sum, p) => sum + p.phValue, 0) / points.length : null;
  const dash = t("web.valueUnavailable");

  /** Biên hữu hạn của một dải (API BỎ key `phMin`/`phMax` ở dải mở, không trả `null`). */
  const finiteBound = (value: number | null | undefined): number | null =>
    typeof value === "number" && Number.isFinite(value) ? value : null;

  const bandRangeLabel = (band: PhBand): string | null => {
    const min = finiteBound(band.phMin);
    const max = finiteBound(band.phMax);
    if (min !== null && max !== null) {
      return t("distribution.rangeLabel", { min: min.toFixed(1), max: max.toFixed(1) });
    }
    if (max !== null) return t("distribution.rangeLabelBelow", { max: max.toFixed(1) });
    if (min !== null) return t("distribution.rangeLabelAbove", { min: min.toFixed(1) });
    return null;
  };

  const referenceLegend = (): string | null => {
    if (!referenceBand) return null;
    const min = finiteBound(referenceBand.phMin);
    const max = finiteBound(referenceBand.phMax);
    if (min === null || max === null) return referenceBand.label;
    return t("chart.referenceLegend", {
      label: referenceBand.label,
      min: min.toFixed(1),
      max: max.toFixed(1),
    });
  };

  let mobileContent: ReactNode;
  if (isPending) {
    mobileContent = (
      <div className="flex flex-col gap-3 p-4">
        <SkeletonLoader shape="card" className="h-56" />
        <SkeletonLoader shape="card" className="h-24" />
      </div>
    );
  } else if (webLocked) {
    // Gói hiện tại không có entitlement `trend` — nói thẳng thay vì màn lỗi trắng.
    mobileContent = (
      <div className="flex flex-col gap-3 p-4">
        <ErrorState title={t("upsell.title", { ns: "common" })} description={t("web.lockedNote")} />
        <Link to="/credits" className={cn(buttonVariants({ variant: "primary" }), "w-full")}>
          {t("web.lockedCta")}
        </Link>
      </div>
    );
  } else if (isError) {
    mobileContent = (
      <ErrorState
        title={t("state.loadError")}
        onRetry={() => {
          void refetch();
        }}
        retryLabel={t("actions.retry", { ns: "common" })}
      />
    );
  } else {
    mobileContent = (
      <div className="flex flex-col gap-4 p-4">
        {/* Mockup `08`: tiêu đề 2 dòng bên trái, pill trạng thái bên phải. Tên bé mèo tách
            xuống dòng phụ — nhồi vào `h1` thì tiêu đề bị ép còn ~45% bề ngang và vỡ 4 dòng. */}
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0 flex-1">
            <h1 className="text-h2 font-bold text-text-primary">{t("pages.catTrends.title")}</h1>
            {cat?.name ? <p className="pt-0.5 text-caption text-text-secondary">{cat.name}</p> : null}
          </div>
          {/* Pill bị kẹp ở ~45% bề ngang (nhãn dải do API trả có thể rất dài) và tự xuống dòng
              BÊN TRONG pill, đúng như mockup — không đẩy tiêu đề vỡ dòng. */}
          {latestBand ? <PhBadge band={latestBand} className="mt-1 max-w-[45%] shrink-0 text-left" /> : null}
        </div>

        <RangeSegmentedControl
          value={range}
          onChange={setRange}
          labels={{ "7D": t("range.7D"), "30D": t("range.30D"), "90D": t("range.90D") }}
        />

        {/* Thẻ biểu đồ — đầy đủ tiêu đề, giá trị trung bình, dòng "lần đo gần nhất",
            trục X/Y, vùng tham chiếu và chú giải (mockup `08`). */}
        <Card padding="md" className="flex flex-col gap-3">
          <div className="flex items-start justify-between gap-3">
            <div className="flex min-w-0 flex-1 items-start gap-2">
              <Droplet className="mt-0.5 size-4 shrink-0 text-primary" aria-hidden="true" />
              <div className="min-w-0">
                <p className="text-body font-bold text-text-primary">{t("chart.title")}</p>
                <p className="pt-0.5 text-caption text-text-secondary">{t("chart.subtitle")}</p>
              </div>
            </div>
            <div className="shrink-0 text-right">
              <p className="text-h3 font-bold text-primary">{average !== null ? average.toFixed(1) : dash}</p>
              <p className="text-small font-semibold text-text-secondary">{t("chart.averageLabel")}</p>
            </div>
          </div>

          {latest ? (
            <div className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-xl bg-background-alt px-3 py-2">
              <span className="text-caption text-text-secondary">
                {t("chart.latestMeta", {
                  date: format(new Date(latest.date), t("chart.latestMetaFormat")),
                })}
              </span>
              <span className="text-caption font-bold text-primary">
                {t("chart.phValue", { value: latest.phValue.toFixed(1) })}
              </span>
              {latestBand ? <PhBadge band={latestBand} /> : null}
            </div>
          ) : null}

          <PhTrendChart
            points={points}
            bands={allBands}
            referenceBand={referenceBand}
            emptyLabel={t("chart.empty")}
            dateFormat={t("chart.dateFormat")}
            tooltipDateFormat={t("chart.tooltipDateFormat")}
            todayLabel={t("chart.today")}
            phLabel={t("web.phUnit")}
          />

          {points.length > 0 ? (
            <div className="flex flex-wrap gap-x-4 gap-y-1.5 border-t border-border pt-3">
              {referenceBand ? (
                <span className="inline-flex items-center gap-1.5 text-small text-text-secondary">
                  <span aria-hidden="true" className={cn("size-2.5 shrink-0 rounded-sm", referenceStyle.solid)} />
                  {referenceLegend()}
                </span>
              ) : null}
              {latest ? (
                <span className="inline-flex items-center gap-1.5 text-small text-text-secondary">
                  <span aria-hidden="true" className="size-2.5 shrink-0 rounded-full bg-primary-dark" />
                  {t("chart.latestLegend", {
                    date: format(new Date(latest.date), t("chart.dateFormat")),
                  })}
                </span>
              ) : null}
            </div>
          ) : null}
        </Card>

        {/* Phân bố phân loại — nhãn/mô tả/ngưỡng đều lấy từ `GET /reference/ph-bands`. */}
        <Card padding="md" className="flex flex-col gap-3">
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <p className="text-body font-bold text-text-primary">{t("distribution.title")}</p>
              <p className="pt-0.5 text-caption text-text-secondary">{t("distribution.description")}</p>
            </div>
            <span className="shrink-0 rounded-lg bg-chip-bg px-2.5 py-1.5 text-small font-bold text-primary-dark">
              {t("distribution.totalChip", { count: summary?.count ?? 0 })}
            </span>
          </div>
          {buckets.length > 0 ? (
            <DistributionBar
              buckets={buckets}
              bands={allBands}
              countLabel={(b) => t("distribution.countLabel", { count: b.count })}
              percentLabel={(b) => t("distribution.percentLabel", { percent: b.percent })}
              rangeLabel={bandRangeLabel}
            />
          ) : (
            <p className="text-caption text-text-secondary">{t("distribution.empty")}</p>
          )}
        </Card>

        <div className="flex flex-col gap-2">
          <Button
            variant="secondary"
            leftIcon={<FileText className="size-4" />}
            onClick={() => {
              void navigate("/export");
            }}
          >
            {t("export.cta")}
          </Button>
          <Link to="/reminders" className={cn(buttonVariants({ variant: "tertiary" }), "w-full bg-surface")}>
            <CalendarPlus className="size-4" aria-hidden="true" />
            {t("actions.scheduleCheck")}
          </Link>
        </div>

        <DisclaimerBanner variant="footerLine" />
      </div>
    );
  }

  return (
    <>
      {/* Desktop (>= lg): bố cục Figma 17:10514, số liệu lấy từ D13 thật. */}
      <div className="hidden lg:block">
        <WebTrendsScreen
          bands={allBands}
          data={webTrends.data}
          isPending={webTrends.isPending}
          locked={webLocked}
          isError={webTrends.isError && !webLocked}
          range={range}
          onRangeChange={setRange}
          onRetry={() => {
            void webTrends.refetch();
          }}
        />
      </div>
      {/* Mobile (< lg): giữ nguyên luồng cũ, kể cả trạng thái loading/lỗi. */}
      <div className="lg:hidden">{mobileContent}</div>
    </>
  );
}
