import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { BellRing, Check, ChevronRight, Droplet, Pencil, Plus, Trash2 } from "lucide-react";
import type { Cat } from "@/entities/cat";
import { PhGaugeBar, phTokenStyle, type PhBand } from "@/entities/ph-bands";
import { useCatNotes, type CatSummaryResponse } from "@/features/cat";
import { useScanHistory } from "@/features/history";
import { cn } from "@/shared/lib/cn";
import iconExportPdf from "@/shared/assets/icons/web-cat/profile-export-pdf.svg";
import iconLastScan from "@/shared/assets/icons/web-cat/profile-last-scan.svg";
import iconAttachment from "@/shared/assets/icons/web-cat/profile-attachment.svg";
import iconDoctor from "@/shared/assets/icons/web-cat/hub-doctor.svg";
import { useNextReminder, useReminderStatText } from "./CatOverviewPanels";

/**
 * Các panel DESKTOP của màn `/cats/:catId` — Figma `26mOVF2zdu4cI1EPz2Syxw` node `16:4290`
 * "Web - 06 & 07. Hồ sơ Bé Mèo & Sổ khám Y tế": thanh chọn hồ sơ đầu trang, cột phải
 * (`16:4470`) và dòng thời gian (`16:4555`).
 *
 * **Dữ liệu THẬT**:
 *  - thanh chọn hồ sơ: `GET /cats?status=ACTIVE`;
 *  - ô chỉ số: D12 `GET /cats/{id}/summary` (tỉ lệ trong ngưỡng 30 ngày, độ tin cậy lần quét gần
 *    nhất) + I1 `GET /reminders?catId=…` (lần nhắc kế tiếp);
 *  - thang pH: `GET /reference/ph-bands` + pH lần quét gần nhất;
 *  - dòng thời gian: E2 `GET /scans` + D16 `GET /cats/{id}/notes`.
 *
 * ĐÃ BỎ HẲN vì backend không có trường nào tương ứng (nguyên tắc: bỏ khỏi UI, không bịa):
 * thể tích/tần suất bãi tiểu, hydrat hoá, khẩu phần; nhóm máu, dị ứng; vaccine, tẩy giun;
 * "Quyền giám sát lâm sàng"; "Bác sĩ phụ trách"; ô "Sỏi & cặn bàng quang" và nhãn "Nguy cơ
 * Canxi Oxalat/Struvite" (chẩn đoán — trái quyết định #6/#8); badge "Hồ sơ đã xác thực ISFM
 * Gold Standard" (claim chứng nhận không có bằng chứng — p15 REQ-CLAIM-02).
 *
 * ĐÃ BỎ (W6): ảnh stock khay cát gắn cho MỌI lần quét trong dòng thời gian. `GET /scans` trả
 * `imageAvailable:false` và không trả URL ảnh — ô ảnh nay là màu đo được (`thumbnailHex`)
 * hoặc biểu tượng tô theo màu dải pH của chính lần quét đó.
 */

/* ================================================================== Thanh chọn hồ sơ */

/**
 * "HỒ SƠ THÚ CƯNG" + một tab cho mỗi bé + "Thêm hồ sơ bé mèo mới" + "Xuất hồ sơ PDF" (đầu
 * frame `16:4290`). Tab là liên kết thật sang hồ sơ từng bé.
 */
export function CatProfileBar({
  cats,
  currentCat,
  onExport,
  className,
}: {
  cats: Cat[];
  currentCat: Cat;
  onExport: () => void;
  className?: string;
}) {
  const { t } = useTranslation("cat");
  // Bé đang xem có thể đã lưu trữ (không nằm trong danh sách ACTIVE) — vẫn phải có tab của bé.
  const tabs = cats.some((c) => c.id === currentCat.id) ? cats : [...cats, currentCat];

  return (
    <section className={cn("flex flex-wrap items-center gap-3 rounded-2xl bg-surface px-5 py-4 shadow-xs", className)}>
      <h2 className="text-overline font-bold uppercase tracking-[0.5px] text-text-secondary">
        {t("web.profile.barLabel")}
      </h2>
      <nav aria-label={t("web.profile.barAria")} className="flex min-w-0 flex-wrap gap-1 rounded-xl bg-background-alt p-1">
        {tabs.map((cat) => {
          const current = cat.id === currentCat.id;
          const breed = cat.breedName ?? cat.breedOther;
          return (
            <Link
              key={cat.id}
              to={`/cats/${cat.id}`}
              aria-current={current ? "page" : undefined}
              className={cn(
                "inline-flex min-h-9 items-center gap-2 rounded-lg px-3 text-caption transition-colors",
                current ? "bg-surface shadow-xs" : "hover:bg-surface/70",
              )}
            >
              <span
                aria-hidden="true"
                className={cn("size-2 shrink-0 rounded-full", current ? "bg-success" : "bg-border-strong")}
              />
              <span className={cn("font-semibold", current ? "text-primary-dark" : "text-text-secondary")}>
                {cat.name}
              </span>
              {breed ? <span className="text-text-tertiary">({breed})</span> : null}
              {current ? <Check className="size-3.5 shrink-0 text-primary-dark" aria-hidden="true" /> : null}
            </Link>
          );
        })}
      </nav>
      <Link
        to="/cats/new"
        className="inline-flex min-h-9 items-center gap-1.5 rounded-lg bg-chip-bg px-3 text-caption font-semibold text-primary-dark hover:bg-background-alt"
      >
        <Plus className="size-3.5" aria-hidden="true" />
        {t("web.profile.barAddCat")}
      </Link>
      <button
        type="button"
        onClick={onExport}
        className="ml-auto inline-flex min-h-9 items-center gap-2 rounded-xl bg-primary-dark px-4 text-caption font-semibold text-white"
      >
        <img src={iconExportPdf} alt="" className="size-3.5" />
        {t("web.profile.exportPdf")}
      </button>
    </section>
  );
}

/* ================================================================== Cột phải */

export interface CatClinicalColumnProps {
  cat: Cat;
  summary?: CatSummaryResponse;
  /** Dải pH từ `GET /reference/ph-bands` — nhãn/màu của timeline tra từ đây. */
  bands: PhBand[];
  /** Tổng số lần quét THẬT (`GET /scans/summary`) cho nhãn "Xem toàn bộ lịch sử (N bản ghi)". */
  scanCount?: number | null;
  onViewAllHistory: () => void;
  onOpenScan: (scanId: string) => void;
  onAddNote: () => void;
  onEditNote: (noteId: string) => void;
  onDeleteNote: (noteId: string) => void;
  /** Các thẻ hành động (khảo sát, báo dấu hiệu) do trang cha dựng — đặt cuối cột. */
  footer?: ReactNode;
}

/** Cột phải desktop: chỉ số theo dõi, thang pH, dòng thời gian, thẻ hành động. */
export function CatClinicalColumn({
  cat,
  summary,
  bands,
  scanCount = null,
  onViewAllHistory,
  onOpenScan,
  onAddNote,
  onEditNote,
  onDeleteNote,
  footer,
}: CatClinicalColumnProps) {
  const { t } = useTranslation("cat");
  return (
    <div className="flex flex-col gap-5">
      <StatRow catId={cat.id} summary={summary} />
      <SpectrumCard cat={cat} summary={summary} bands={bands} />
      <MedicalTimeline
        catId={cat.id}
        bands={bands}
        scanCount={scanCount}
        onViewAll={onViewAllHistory}
        onOpenScan={onOpenScan}
        onAddNote={onAddNote}
        onEditNote={onEditNote}
        onDeleteNote={onDeleteNote}
      />
      {footer}
      <p className="text-small leading-relaxed text-text-tertiary">{t("disclaimer.short")}</p>
    </div>
  );
}

function StatRow({ catId, summary }: { catId: string; summary?: CatSummaryResponse }) {
  const { t } = useTranslation("cat");
  // CHỈ các trường D12 thật trả: số lần quét 30 ngày, tỉ lệ trong ngưỡng, độ tin cậy lần
  // quét gần nhất. Mèo chưa quét lần nào → 0/— , đúng trạng thái hợp lệ của tài khoản mới.
  const scanCount = summary?.scanCount30d ?? 0;
  const inRangePercent =
    summary?.inRangeRatio30d != null ? `${String(Math.round(summary.inRangeRatio30d * 100))}%` : "—";
  const confidence = summary?.lastScan?.confidence;
  const { nextRunAt, isPending: reminderPending } = useNextReminder(catId);
  const reminder = useReminderStatText(nextRunAt);

  return (
    <div className="grid grid-cols-3 gap-4">
      <div className="flex flex-col rounded-2xl bg-success-bg p-4">
        <p className="text-[10px] font-bold uppercase leading-tight tracking-[0.5px] text-success-text opacity-80">
          {t("web.profile.inRange30d")}
        </p>
        <p className="pt-2 text-h2 font-bold text-success-text">{inRangePercent}</p>
        <p className="text-caption font-semibold text-success-text">
          {t("web.profile.scanCount30d", { count: scanCount })}
        </p>
        <p className="mt-auto pt-2 text-[10px] leading-snug text-success-text opacity-80">
          {t("web.profile.inRangeNote")}
        </p>
      </div>

      <div className="flex flex-col rounded-2xl bg-surface p-4 shadow-xs">
        <div className="flex items-start gap-2">
          <img src={iconLastScan} alt="" className="mt-0.5 size-3.5 shrink-0" />
          <p className="text-[10px] font-bold uppercase leading-tight tracking-[0.5px] text-text-tertiary">
            {t("web.profile.confidenceLabel")}
          </p>
        </div>
        <p className="pt-2 text-h2 font-bold text-text-primary">
          {confidence != null ? t("web.profile.confidenceValue", { value: Math.round(confidence * 100) }) : "—"}
        </p>
        <p className="mt-auto pt-2 text-[10px] leading-snug text-text-tertiary">{t("web.profile.confidenceNote")}</p>
      </div>

      <div className="flex flex-col rounded-2xl bg-surface p-4 shadow-xs">
        <div className="flex items-start gap-2">
          <BellRing className="mt-0.5 size-3.5 shrink-0 text-primary" aria-hidden="true" />
          <p className="text-[10px] font-bold uppercase leading-tight tracking-[0.5px] text-text-tertiary">
            {t("web.profile.reminderLabel")}
          </p>
        </div>
        <p className="pt-2 text-h2 font-bold text-text-primary">{reminderPending ? "—" : reminder.value}</p>
        {reminderPending ? null : <p className="text-caption text-text-secondary">{reminder.foot}</p>}
        {reminderPending ? null : (
          <Link
            to={nextRunAt ? "/reminders" : "/reminders/new"}
            className="mt-auto pt-2 text-small font-semibold text-primary-dark hover:underline"
          >
            {nextRunAt ? t("overview.statReminderManage") : t("overview.statReminderCta")}
          </Link>
        )}
      </div>
    </div>
  );
}

function SpectrumCard({ cat, summary, bands }: { cat: Cat; summary?: CatSummaryResponse; bands: PhBand[] }) {
  const { t, i18n } = useTranslation("cat");
  // THẬT: pH + thời điểm của lần quét gần nhất. Chưa có lần quét kết luận được thì KHÔNG đặt
  // kim chỉ (không có giá trị mẫu nào) và nhãn bên dưới nói rõ là chưa có dữ liệu.
  const lastScan = summary?.lastScan ?? null;
  const phValue = lastScan?.phValue ?? null;
  const lastScanLabel = lastScan
    ? t("web.profile.lastScanAt", {
        date: new Date(lastScan.capturedAt).toLocaleDateString(i18n.language),
      })
    : t("web.profile.lastScanNone");
  return (
    <section className="rounded-2xl bg-surface p-6 shadow-xs">
      <h3 className="text-h3 font-bold text-text-primary">{t("web.profile.spectrumTitle")}</h3>
      <p className="pt-1 text-caption text-text-secondary">{t("web.profile.spectrumSub")}</p>

      {/* Dải màu + nhãn ngưỡng lấy TỪ API (`GET /reference/ph-bands`) — không hard-code
          ngưỡng pH và không vẽ nhãn "nguy cơ sỏi" (chẩn đoán, trái quyết định #6/#8). */}
      <PhGaugeBar bands={bands} value={phValue} className="mt-6" />

      <div className="mt-4 flex flex-wrap items-center gap-x-2 gap-y-1 rounded-xl bg-info px-3 py-2">
        <img src={iconLastScan} alt="" className="size-3.5 shrink-0" />
        <p className="text-caption font-semibold text-info-text">
          {phValue != null
            ? t("web.profile.phWithName", { name: cat.name, value: phValue })
            : t("web.profile.phUnknown", { name: cat.name })}
        </p>
        <span className="ml-auto text-small text-info-text opacity-80">{lastScanLabel}</span>
      </div>
    </section>
  );
}

/* ================================================================== Dòng thời gian */

const TIMELINE_LIMIT = 5;

type TimelineEntry =
  | {
      kind: "scan";
      key: string;
      at: string;
      scanId: string;
      subtitle: string;
      confidence: number | null;
      swatch: string | null;
      band: PhBand | undefined;
    }
  | { kind: "note"; key: string; at: string; noteId: string; title: string; body: string };

/**
 * Dòng thời gian — DỮ LIỆU THẬT, trộn hai nguồn có endpoint:
 *  - `GET /scans` (E2): mỗi lần quét chỉ thị màu cát, nhãn/màu dải tra từ `/reference/ph-bands`;
 *  - `GET /cats/{id}/notes` (D16): ghi chú chủ nuôi (gồm "Đi khám thú y", "Đổi loại cát" — đúng
 *    hai loại sự kiện Figma vẽ bên cạnh lần quét). Ghi chú sửa/xoá được ngay tại đây (D17/D18),
 *    thay cho tab "Ghi chú" của bản mobile.
 */
function MedicalTimeline({
  catId,
  bands,
  scanCount,
  onViewAll,
  onOpenScan,
  onAddNote,
  onEditNote,
  onDeleteNote,
}: {
  catId: string;
  bands: PhBand[];
  scanCount: number | null;
  onViewAll: () => void;
  onOpenScan: (scanId: string) => void;
  onAddNote: () => void;
  onEditNote: (noteId: string) => void;
  onDeleteNote: (noteId: string) => void;
}) {
  const { t, i18n } = useTranslation("cat");
  const { data: scanPages, isPending: scansPending } = useScanHistory(catId, "ALL");
  const { data: notes, isPending: notesPending } = useCatNotes(catId);

  const scanEntries: TimelineEntry[] = (scanPages?.pages.flatMap((p) => p.items) ?? [])
    .slice(0, TIMELINE_LIMIT)
    .map((scan) => {
      const band = bands.find((b) => b.code === scan.bandCode);
      const label = band?.label ?? scan.classification;
      return {
        kind: "scan",
        key: `scan-${scan.scanId}`,
        at: scan.capturedAt,
        scanId: scan.scanId,
        subtitle:
          scan.phValue != null
            ? t("web.profile.timelineScanSubtitle", { label, value: scan.phValue.toFixed(1) })
            : t("web.profile.timelineScanSubtitleNoPh", { label }),
        confidence: scan.confidence,
        swatch: scan.thumbnailHex,
        band,
      };
    });

  const noteEntries: TimelineEntry[] = (notes?.items ?? []).slice(0, TIMELINE_LIMIT).map((note) => ({
    kind: "note",
    key: `note-${note.id}`,
    at: note.occurredOn ?? note.createdAt,
    noteId: note.id,
    title: t(`notes.types.${note.noteType}`),
    body: note.body,
  }));

  const entries = [...scanEntries, ...noteEntries]
    .sort((a, b) => new Date(b.at).getTime() - new Date(a.at).getTime())
    .slice(0, TIMELINE_LIMIT);

  return (
    <section className="rounded-2xl bg-surface p-6 shadow-xs">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <h3 className="text-h3 font-bold text-text-primary">{t("web.profile.timelineTitle")}</h3>
        <div className="flex shrink-0 items-center gap-2">
          <button
            type="button"
            onClick={onAddNote}
            className="inline-flex min-h-9 items-center gap-1.5 rounded-lg bg-chip-bg px-3 text-caption font-semibold text-primary-dark hover:bg-background-alt"
          >
            <Plus className="size-3.5" aria-hidden="true" />
            {t("web.profile.timelineAddNote")}
          </button>
          <button
            type="button"
            onClick={onViewAll}
            className="inline-flex min-h-9 items-center gap-1 rounded-lg px-2 text-caption font-semibold text-primary-dark hover:underline"
          >
            {scanCount != null ? t("web.profile.timelineAllCount", { count: scanCount }) : t("web.profile.timelineAll")}
            <ChevronRight className="size-4" aria-hidden="true" />
          </button>
        </div>
      </div>

      {scansPending || notesPending ? (
        <div className="mt-5 flex flex-col gap-3">
          {[0, 1, 2].map((i) => (
            <div key={i} className="h-16 animate-pulse rounded-xl bg-background-alt" />
          ))}
        </div>
      ) : entries.length === 0 ? (
        <p className="pt-4 text-caption text-text-secondary">{t("web.profile.timelineEmpty")}</p>
      ) : (
        <ol className="relative mt-5 flex flex-col gap-3 border-l-2 border-primary/15 pl-6">
          {entries.map((ev) => (
            <li key={ev.key} className="relative">
              <span
                aria-hidden="true"
                className={cn(
                  "absolute -left-[33px] top-5 size-3.5 rounded-full border-[3px] border-surface",
                  ev.kind === "scan" ? phTokenStyle(ev.band?.colorToken ?? "color-ph-unknown").solid : "bg-primary",
                )}
              />
              {ev.kind === "scan" ? (
                <button
                  type="button"
                  onClick={() => {
                    onOpenScan(ev.scanId);
                  }}
                  aria-label={t("web.profile.timelineOpenScan")}
                  className="flex w-full items-center gap-3 rounded-xl bg-background-alt/60 p-3 text-left transition-colors hover:bg-background-alt"
                >
                  <ScanThumb swatch={ev.swatch} band={ev.band} />
                  <span className="min-w-0 flex-1">
                    <span className="block text-caption font-bold text-text-primary">
                      {t("web.profile.timelineScanTitle")}
                    </span>
                    <span
                      className={cn(
                        "block text-caption font-semibold",
                        phTokenStyle(ev.band?.colorToken ?? "color-ph-unknown").text,
                      )}
                    >
                      {ev.subtitle}
                    </span>
                    <span className="block pt-0.5 text-small text-text-tertiary">
                      {[
                        new Date(ev.at).toLocaleDateString(i18n.language),
                        ev.confidence != null
                          ? t("web.profile.timelineScanMeta", { value: Math.round(ev.confidence * 100) })
                          : null,
                      ]
                        .filter(Boolean)
                        .join(" • ")}
                    </span>
                  </span>
                  <ChevronRight className="size-4 shrink-0 text-text-tertiary" aria-hidden="true" />
                </button>
              ) : (
                <div className="flex items-start gap-3 rounded-xl bg-background-alt/60 p-3">
                  <span className="flex size-12 shrink-0 items-center justify-center rounded-xl bg-surface">
                    <img src={iconAttachment} alt="" className="size-4" />
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="text-caption font-bold text-text-primary">{ev.title}</p>
                    <p className="text-small text-text-tertiary">
                      {[t("web.profile.timelineNoteFrom"), new Date(ev.at).toLocaleDateString(i18n.language)].join(
                        " • ",
                      )}
                    </p>
                    <p className="whitespace-pre-wrap break-words pt-1 text-caption leading-relaxed text-text-secondary">
                      {ev.body}
                    </p>
                  </div>
                  <div className="flex shrink-0 gap-1">
                    <button
                      type="button"
                      onClick={() => {
                        onEditNote(ev.noteId);
                      }}
                      aria-label={t("notes.editAction")}
                      title={t("notes.editAction")}
                      className="flex size-8 items-center justify-center rounded-lg text-text-secondary hover:bg-surface"
                    >
                      <Pencil className="size-3.5" aria-hidden="true" />
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        onDeleteNote(ev.noteId);
                      }}
                      aria-label={t("notes.deleteAction")}
                      title={t("notes.deleteAction")}
                      className="flex size-8 items-center justify-center rounded-lg text-danger-text hover:bg-surface"
                    >
                      <Trash2 className="size-3.5" aria-hidden="true" />
                    </button>
                  </div>
                </div>
              )}
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}

/**
 * Ô 48px đầu mỗi lần quét: màu hạt đo được (`thumbnailHex`) nếu API có; không có thì biểu
 * tượng giọt tô theo màu dải pH của CHÍNH lần quét — không bao giờ là ảnh minh hoạ.
 */
function ScanThumb({ swatch, band }: { swatch: string | null; band: PhBand | undefined }) {
  if (swatch) {
    return (
      <span
        aria-hidden="true"
        className="size-12 shrink-0 rounded-xl border border-border"
        style={{ backgroundColor: swatch }}
      />
    );
  }
  const style = phTokenStyle(band?.colorToken ?? "color-ph-unknown");
  return (
    <span aria-hidden="true" className={cn("flex size-12 shrink-0 items-center justify-center rounded-xl", style.bg)}>
      <Droplet className={cn("size-5", style.text)} />
    </span>
  );
}

/* ================================================================== Cột trái */

/**
 * Thẻ "Cơ sở thú y" — Figma vẽ "Bác sĩ phụ trách"/"Phòng bệnh & ký sinh"/"Quyền giám sát lâm
 * sàng", cả ba không có bảng/endpoint nào đứng sau nên đã bỏ. Thay bằng lối vào `/map` — tính
 * năng CÓ THẬT, cùng mục đích (tìm chỗ khám cho bé) và không hứa gì.
 */
export function CatIdentityExtras() {
  const { t } = useTranslation("cat");
  return (
    <section className="rounded-2xl bg-surface p-5 shadow-xs">
      <div className="flex items-start gap-2">
        <img src={iconDoctor} alt="" className="mt-0.5 size-4 shrink-0" />
        <h3 className="text-caption font-bold uppercase tracking-[0.4px] text-text-tertiary">
          {t("web.profile.vetFinderLabel")}
        </h3>
      </div>
      <p className="pt-2 text-caption font-semibold text-text-primary">{t("web.profile.vetFinderTitle")}</p>
      <p className="pt-1 text-small leading-relaxed text-text-tertiary">{t("web.profile.vetFinderBody")}</p>
      <Link
        to="/map"
        className="mt-3 inline-flex items-center justify-center rounded-xl bg-background-alt px-3 py-2 text-caption font-semibold text-primary-dark hover:bg-chip-bg"
      >
        {t("web.profile.vetFinderCta")}
      </Link>
    </section>
  );
}
