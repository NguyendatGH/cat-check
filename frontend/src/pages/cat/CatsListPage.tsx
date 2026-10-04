import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { Plus } from "lucide-react";
import { Button, EmptyState, ErrorState, SkeletonLoader, Tabs, TabsList, TabsTrigger } from "@/shared/ui";
import { CatAvatar, CatCard, type Cat, type CatStatus } from "@/entities/cat";
import { PhGaugeBar, phTokenStyle, usePhBands, type PhBand } from "@/entities/ph-bands";
import { useCatList } from "@/features/cat";
import { cn } from "@/shared/lib/cn";
import iconQuickScan from "@/shared/assets/icons/web-cat/hub-quick-scan.svg";
import iconAddCat from "@/shared/assets/icons/web-cat/hub-add-cat.svg";
import iconSelected from "@/shared/assets/icons/web-cat/hub-selected.svg";
import iconStandby from "@/shared/assets/icons/web-cat/hub-standby.svg";
import iconLastScan from "@/shared/assets/icons/web-cat/hub-last-scan.svg";
import iconScanFor from "@/shared/assets/icons/web-cat/hub-scan-for.svg";
import iconViewRecord from "@/shared/assets/icons/web-cat/hub-view-record.svg";
import iconSwitchTo from "@/shared/assets/icons/web-cat/hub-switch-to.svg";
import iconAddInvite from "@/shared/assets/icons/web-cat/hub-add-invite.svg";
import iconAddNow from "@/shared/assets/icons/web-cat/hub-add-now.svg";
import iconTableTitle from "@/shared/assets/icons/web-cat/hub-table-title.svg";
import iconExportPdf from "@/shared/assets/icons/web-cat/hub-export-pdf.svg";
import iconFilter from "@/shared/assets/icons/web-cat/hub-filter.svg";

/**
 * `/cats` — W1 Multi-Cat Hub.
 *
 * Mobile (mặc định): danh sách hồ sơ + tab lọc như cũ, KHÔNG đổi.
 * Desktop (`lg:` trở lên): dựng theo Figma `26mOVF2zdu4cI1EPz2Syxw` node `16:3703`
 * "Web - 05. Quản lý Đa Mèo & Chuyển đổi Bé Mèo" — banner đầu trang, bento grid 4 cột,
 * bảng so sánh sức khỏe cả đàn.
 */

/**
 * ĐÃ BỎ HẲN (W1-E) — `DESIGN_MOCK_CLINICAL`: BCS/thể trạng, "nguy cơ FLUTD", khay cát liên
 * kết, lịch thú y kế tiếp và bác sĩ phụ trách. `GET /cats` (p8 §8.4.4) chỉ trả hồ sơ +
 * `lastScanAt`/`lastClassification`/`unacknowledgedFlagCount`; không có trường nào cho những
 * chỉ số đó, và "nguy cơ FLUTD" còn là chẩn đoán — trái quyết định #6/#8. Nguyên tắc: thiếu
 * field thì BỎ khỏi UI, không bịa dữ liệu. Bảng so sánh vì vậy còn 4 cột THẬT.
 */

export function CatsListPage() {
  const { t } = useTranslation(["cat", "common"]);
  const navigate = useNavigate();
  const [status, setStatus] = useState<CatStatus>("ACTIVE");
  const { data, isPending, isError, refetch } = useCatList(status);
  // Nhãn/màu dải pH luôn từ API cấu hình — thẻ mèo hiển thị dải của `lastClassification`
  // (trường THẬT của `GET /cats`), không bịa một con số pH.
  const { data: bands } = usePhBands();

  const items = data?.items ?? [];

  return (
    <div className="flex flex-col gap-6">
      {/* ---------- Mobile / tablet: giữ nguyên bản cũ ---------- */}
      <div className="flex flex-col gap-6 lg:hidden">
        <div className="flex items-center justify-between gap-3">
          <h1 className="text-h2 font-bold text-text-primary">{t("pages.list.title")}</h1>
          <Button
            type="button"
            leftIcon={<Plus className="size-4" aria-hidden="true" />}
            onClick={() => {
              void navigate("/cats/new");
            }}
          >
            {t("list.createCta")}
          </Button>
        </div>

        <Tabs
          value={status}
          onValueChange={(v) => {
            setStatus(v as CatStatus);
          }}
        >
          <TabsList aria-label={t("list.ariaList")}>
            <TabsTrigger value="ACTIVE">{t("list.filterActive")}</TabsTrigger>
            <TabsTrigger value="ARCHIVED">{t("list.filterArchived")}</TabsTrigger>
          </TabsList>
        </Tabs>

        {isPending ? (
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            {[0, 1, 2].map((i) => (
              <SkeletonLoader key={i} shape="card" />
            ))}
          </div>
        ) : isError ? (
          <ErrorState
            title={t("list.errorTitle")}
            onRetry={() => {
              void refetch();
            }}
            retryLabel={t("actions.retry", { ns: "common" })}
          />
        ) : items.length === 0 ? (
          <EmptyState
            title={t(status === "ACTIVE" ? "list.emptyActiveTitle" : "list.emptyArchivedTitle")}
            description={t(status === "ACTIVE" ? "list.emptyActiveDescription" : "list.emptyArchivedDescription")}
            action={
              status === "ACTIVE" ? (
                <Button
                  type="button"
                  onClick={() => {
                    void navigate("/cats/new");
                  }}
                >
                  {t("list.createCta")}
                </Button>
              ) : undefined
            }
          />
        ) : (
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            {items.map((cat) => (
              <CatCard
                key={cat.id}
                cat={cat}
                primaryLabel={t("status.primary")}
                archivedLabel={t("status.archived")}
                ageLabel={cat.ageMonths !== null ? t("detail.ageLabel", { months: cat.ageMonths }) : undefined}
                onSelect={(selected) => {
                  void navigate(`/cats/${selected.id}`);
                }}
              />
            ))}
          </div>
        )}
      </div>

      {/* ---------- Desktop: Figma 16:3703 ---------- */}
      <div className="hidden flex-col gap-8 lg:flex">
        <HubBanner
          onScan={() => {
            void navigate("/scan/select-cat");
          }}
          onAdd={() => {
            void navigate("/cats/new");
          }}
        />

        {isError ? (
          <ErrorState
            title={t("list.errorTitle")}
            onRetry={() => {
              void refetch();
            }}
            retryLabel={t("actions.retry", { ns: "common" })}
          />
        ) : (
          <>
            <div className="grid grid-cols-2 gap-5 lg:grid-cols-4">
              {isPending
                ? [0, 1, 2, 3].map((i) => <SkeletonLoader key={i} shape="card" />)
                : items.map((cat) => (
                    <HubCatCard
                      key={cat.id}
                      cat={cat}
                      bands={bands ?? []}
                      onScan={() => {
                        void navigate("/scan/select-cat");
                      }}
                      onOpen={() => {
                        void navigate(`/cats/${cat.id}`);
                      }}
                    />
                  ))}
              <HubAddCard
                onAdd={() => {
                  void navigate("/cats/new");
                }}
              />
            </div>

            {items.length > 0 ? <HubComparisonTable cats={items} bands={bands ?? []} /> : null}

            <HubLitterSeparationGuide />
          </>
        )}
      </div>
    </div>
  );
}

function HubBanner({ onScan, onAdd }: { onScan: () => void; onAdd: () => void }) {
  const { t } = useTranslation("cat");
  return (
    <section className="relative overflow-hidden rounded-3xl bg-primary px-8 py-7 text-white shadow-[0px_10px_24px_-6px_rgba(47,79,178,0.28)]">
      <div className="flex items-start justify-between gap-8">
        <div className="max-w-[640px]">
          <div className="flex flex-wrap items-center gap-3">
            <span className="rounded-full bg-white/15 px-3 py-1 text-overline font-semibold tracking-[0.5px] backdrop-blur-sm">
              {t("web.hub.eyebrow")}
            </span>
          </div>
          <h1 className="pt-3 text-h1 font-bold leading-tight">{t("web.hub.title")}</h1>
          <p className="pt-2 text-caption leading-relaxed text-on-primary-muted">{t("web.hub.intro")}</p>
        </div>

        <div className="flex shrink-0 flex-col gap-2.5">
          <button
            type="button"
            onClick={onScan}
            className="flex items-center justify-center gap-2 rounded-xl bg-secondary px-5 py-3 text-caption font-semibold text-secondary-text-on shadow-sm"
          >
            <img src={iconQuickScan} alt="" className="size-4" />
            {t("web.hub.quickScan")}
          </button>
          <button
            type="button"
            onClick={onAdd}
            className="flex items-center justify-center gap-2 rounded-xl bg-white/15 px-5 py-3 text-caption font-semibold backdrop-blur-sm"
          >
            <img src={iconAddCat} alt="" className="size-4" />
            {t("web.hub.addCat")}
          </button>
        </div>
      </div>
    </section>
  );
}

/** Dải pH của mèo theo `lastClassification` THẬT (`GET /cats`) — `undefined` khi chưa quét. */
function bandOfCat(cat: Cat, bands: PhBand[]): PhBand | undefined {
  return cat.lastClassification ? bands.find((b) => b.code === cat.lastClassification) : undefined;
}

function HubCatCard({
  cat,
  bands,
  onScan,
  onOpen,
}: {
  cat: Cat;
  bands: PhBand[];
  onScan: () => void;
  onOpen: () => void;
}) {
  const { t, i18n } = useTranslation(["cat", "common"]);
  const active = cat.isPrimary;
  const band = bandOfCat(cat, bands);
  const style = phTokenStyle(band?.colorToken ?? "color-ph-unknown");

  return (
    <article
      className={cn(
        "flex flex-col rounded-2xl bg-surface p-4 shadow-[0px_4px_16px_-2px_rgba(47,79,178,0.08)]",
        active ? "ring-2 ring-primary" : "ring-1 ring-border/60",
      )}
    >
      <div className="flex items-start justify-between gap-2">
        <CatAvatar src={cat.avatarUrl} name={cat.name} size="lg" />
        <div className="flex min-w-0 flex-col items-end gap-1 text-right">
          <span
            className={cn(
              "inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-semibold tracking-[0.4px]",
              active ? "bg-success-bg text-success-text" : "bg-chip-bg text-text-secondary",
            )}
          >
            <img src={active ? iconSelected : iconStandby} alt="" className="size-2.5" />
            {active ? t("web.hub.selected") : t("web.hub.standby")}
          </span>
          <p className="w-full truncate text-[10px] tracking-[0.3px] text-text-tertiary">
            {t("detail.publicCode", { code: cat.publicCode })}
          </p>
        </div>
      </div>

      <p className="truncate pt-2 text-body font-bold text-text-primary">{cat.name}</p>
      <p className="truncate text-[11px] text-text-secondary">
        {cat.breedName ?? cat.breedOther ?? t("detail.identityUnknown")}
      </p>

      <div className="mt-3 grid grid-cols-2 gap-2">
        <div className="rounded-xl bg-background-alt px-3 py-2">
          <p className="text-[10px] uppercase tracking-[0.4px] text-text-tertiary">{t("web.hub.weight")}</p>
          <p className="text-body font-bold text-text-primary">
            {cat.weightKg != null ? t("web.hub.weightKg", { value: cat.weightKg }) : "—"}
          </p>
        </div>
        <div className={cn("rounded-xl px-3 py-2", style.bg)}>
          <p className={cn("text-[10px] uppercase tracking-[0.4px] opacity-80", style.text)}>{t("web.hub.phShort")}</p>
          <p className={cn("truncate text-caption font-bold", style.text)}>
            {band?.label ?? t("web.hub.lastScanNone")}
          </p>
        </div>
      </div>

      {/* Thang pH thu nhỏ của Figma — dựng từ chính `bands` (API), không hard-code ngưỡng
          và KHÔNG đặt kim chỉ vì `GET /cats` không trả giá trị pH. */}
      <PhGaugeBar bands={bands} value={null} className="mt-3" />

      <div className="mt-3 flex items-start gap-2 rounded-xl bg-info px-3 py-2">
        <img src={iconLastScan} alt="" className="mt-0.5 size-3.5 shrink-0" />
        <div className="min-w-0">
          <p className="text-[10px] uppercase tracking-[0.4px] text-info-text opacity-80">{t("web.hub.lastScan")}</p>
          <p className="truncate text-[11px] font-semibold text-info-text">
            {cat.lastScanAt
              ? t("web.hub.lastScanAt", { date: new Date(cat.lastScanAt).toLocaleDateString(i18n.language) })
              : t("web.hub.lastScanNone")}
          </p>
        </div>
      </div>

      <div className="mt-4 flex flex-col gap-2">
        <button
          type="button"
          onClick={onScan}
          className="flex items-center justify-center gap-2 rounded-xl bg-primary-dark px-3 py-2.5 text-caption font-semibold text-white"
        >
          <img src={active ? iconScanFor : iconSwitchTo} alt="" className="size-4" />
          {active ? t("web.hub.scanFor", { name: cat.name }) : t("web.hub.switchTo", { name: cat.name })}
        </button>
        <button
          type="button"
          onClick={onOpen}
          className="flex items-center justify-center gap-2 rounded-xl bg-background-alt px-3 py-2.5 text-caption font-semibold text-primary-dark"
        >
          <img src={iconViewRecord} alt="" className="size-4" />
          {t("web.hub.viewRecord")}
        </button>
      </div>
    </article>
  );
}

function HubAddCard({ onAdd }: { onAdd: () => void }) {
  const { t } = useTranslation("cat");
  return (
    <article className="flex flex-col items-center justify-center gap-3 rounded-2xl border-2 border-dashed border-border bg-background-alt/60 p-6 text-center">
      <img src={iconAddInvite} alt="" className="size-10" />
      <p className="text-body font-bold text-text-primary">{t("web.hub.addTitle")}</p>
      <p className="text-[11px] leading-relaxed text-text-secondary">{t("web.hub.addBody")}</p>
      <button
        type="button"
        onClick={onAdd}
        className="mt-1 flex items-center justify-center gap-2 rounded-xl bg-primary px-4 py-2.5 text-caption font-semibold text-white"
      >
        <img src={iconAddNow} alt="" className="size-3.5" />
        {t("web.hub.addNow")}
      </button>
      <p className="text-[10px] tracking-[0.3px] text-text-tertiary">{t("web.hub.addPerk")}</p>
    </article>
  );
}

function HubComparisonTable({ cats, bands }: { cats: Cat[]; bands: PhBand[] }) {
  const { t } = useTranslation(["cat", "common"]);
  // Chỉ còn các cột có field THẬT trong `GET /cats` + `GET /reference/ph-bands`.
  const cols = [t("web.hub.colCat"), t("web.hub.colAge"), t("web.hub.colBody"), t("web.hub.colPh")];

  return (
    <section className="rounded-2xl bg-surface p-6 shadow-[0px_4px_16px_-2px_rgba(47,79,178,0.06)]">
      <div className="flex items-start justify-between gap-6">
        <div className="flex items-start gap-3">
          <img src={iconTableTitle} alt="" className="mt-1 size-5 shrink-0" />
          <div>
            <h2 className="text-h3 font-bold text-text-primary">{t("web.hub.tableTitle")}</h2>
            <p className="pt-1 text-caption text-text-secondary">{t("web.hub.tableIntro")}</p>
          </div>
        </div>
        <div className="flex shrink-0 gap-2">
          <button
            type="button"
            className="flex items-center gap-2 rounded-xl bg-primary-dark px-4 py-2 text-caption font-semibold text-white"
          >
            <img src={iconExportPdf} alt="" className="size-3.5" />
            {t("web.hub.exportPdf")}
          </button>
          <button
            type="button"
            className="flex items-center gap-2 rounded-xl bg-background-alt px-4 py-2 text-caption font-semibold text-primary-dark"
          >
            <img src={iconFilter} alt="" className="size-3.5" />
            {t("web.hub.filterMetric")}
          </button>
        </div>
      </div>

      <div className="mt-5 overflow-x-auto">
        <table className="w-full min-w-[900px] border-collapse text-left">
          <thead>
            <tr className="border-b border-border">
              {cols.map((c) => (
                <th key={c} className="px-3 py-2.5 text-[10px] font-bold uppercase tracking-[0.5px] text-text-tertiary">
                  {c}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {cats.map((cat) => {
              const band = bandOfCat(cat, bands);
              const style = phTokenStyle(band?.colorToken ?? "color-ph-unknown");
              return (
                <tr key={cat.id} className="border-b border-border/60 last:border-0">
                  <td className="px-3 py-3">
                    <div className="flex items-center gap-2.5">
                      <CatAvatar src={cat.avatarUrl} name={cat.name} size="sm" />
                      <div className="min-w-0">
                        <p className="truncate text-caption font-bold text-text-primary">{cat.name}</p>
                        <p className="text-[10px] text-text-tertiary">
                          {cat.isPrimary ? t("status.primary") : t("web.hub.standby")}
                        </p>
                      </div>
                    </div>
                  </td>
                  <td className="px-3 py-3">
                    <p className="text-caption text-text-primary">{cat.breedName ?? cat.breedOther ?? "—"}</p>
                    <p className="text-[10px] text-text-tertiary">
                      {cat.ageMonths !== null ? t("detail.ageLabel", { months: cat.ageMonths }) : "—"}
                    </p>
                  </td>
                  <td className="px-3 py-3">
                    <p className="text-caption font-semibold text-text-primary">
                      {cat.weightKg != null ? t("web.hub.weightKg", { value: cat.weightKg }) : "—"}
                    </p>
                  </td>
                  <td className="px-3 py-3">
                    <span className={cn("rounded-full px-2.5 py-1 text-[11px] font-bold", style.bg, style.text)}>
                      {band?.label ?? t("web.hub.lastScanNone")}
                    </span>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </section>
  );
}

/**
 * Khối cuối trang của Figma `16:3703` — "Chế độ Phân Tách Khay Cát Thông Minh & Nhận Diện
 * Bãi Thải": 3 bước hướng dẫn cho nhà nuôi nhiều mèo.
 *
 * ⚠️ COPY LỆCH BẢN THIẾT KẾ CÓ CHỦ ĐÍCH. Mockup mô tả phần cứng không tồn tại
 * ("AI Vision Multi-Box Hub", "Khay CleanBox IoT cân tải", camera nhận diện khuôn mặt mèo,
 * "Khớp 99.4%", "chuẩn xác 100%", "Tỉ lệ gán đúng bãi thải tự động 98.2%") và một cam kết
 * độ chính xác tuyệt đối — cả hai đều không có trong sản phẩm và vi phạm REQ-COPY-01.
 * Nội dung dưới đây viết lại thành hướng dẫn bố trí khay THẬT, nối với luồng có thật
 * "Chưa rõ / Dùng chung khay" khi quét. Không ảnh minh hoạ phần cứng.
 */
function HubLitterSeparationGuide() {
  const { t } = useTranslation("cat");
  const steps = [
    { title: t("web.hub.guideStep1Title"), body: t("web.hub.guideStep1Body") },
    { title: t("web.hub.guideStep2Title"), body: t("web.hub.guideStep2Body") },
    { title: t("web.hub.guideStep3Title"), body: t("web.hub.guideStep3Body") },
  ];

  return (
    <section className="rounded-2xl bg-surface p-6 shadow-[0px_4px_16px_-2px_rgba(47,79,178,0.06)]">
      <div className="flex flex-wrap items-center gap-3">
        <span className="rounded-full bg-secondary px-3 py-1 text-[11px] font-bold tracking-[0.4px] text-secondary-text-on">
          {t("web.hub.guideEyebrow")}
        </span>
        <span className="text-caption font-semibold text-text-secondary">{t("web.hub.guideKicker")}</span>
      </div>

      <h2 className="pt-3 text-h3 font-bold text-text-primary">{t("web.hub.guideTitle")}</h2>
      <p className="max-w-[760px] pt-2 text-caption leading-relaxed text-text-secondary">{t("web.hub.guideIntro")}</p>

      <ol className="mt-5 grid grid-cols-1 gap-4 md:grid-cols-3">
        {steps.map((step, index) => (
          <li key={step.title} className="rounded-xl bg-background-alt p-4">
            <span className="inline-flex size-7 items-center justify-center rounded-lg bg-primary text-caption font-bold text-white">
              {index + 1}
            </span>
            <p className="pt-2 text-caption font-bold text-text-primary">{step.title}</p>
            <p className="pt-1 text-[11px] leading-relaxed text-text-secondary">{step.body}</p>
          </li>
        ))}
      </ol>

      <div className="mt-4 flex items-start gap-2 rounded-xl bg-info px-4 py-3">
        <img src={iconLastScan} alt="" className="mt-0.5 size-3.5 shrink-0" />
        <p className="text-caption leading-relaxed text-info-text">{t("web.hub.guideNote")}</p>
      </div>
    </section>
  );
}
