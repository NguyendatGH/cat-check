import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import type { Cat } from "@/entities/cat";
import { PhGaugeBar, type PhBand } from "@/entities/ph-bands";
import { useCatNotes, type CatSummaryResponse } from "@/features/cat";
import { useScanHistory } from "@/features/history";
import { cn } from "@/shared/lib/cn";
import iconExportPdf from "@/shared/assets/icons/web-cat/profile-export-pdf.svg";
import iconLastScan from "@/shared/assets/icons/web-cat/profile-last-scan.svg";
import iconAttachment from "@/shared/assets/icons/web-cat/profile-attachment.svg";
import iconDoctor from "@/shared/assets/icons/web-cat/hub-doctor.svg";
import sandScanThumb from "@/shared/assets/images/web-cat/profile-sand-scan-thumb.png";

/**
 * Các panel lâm sàng cột phải của màn `/cats/:catId` ở desktop — Figma
 * `26mOVF2zdu4cI1EPz2Syxw` node `16:4290` "Web - 06 & 07. Hồ sơ Bé Mèo & Sổ khám Y tế",
 * cụ thể `16:4470` (RIGHT COLUMN) và `16:4555` (Medical Timeline).
 *
 * **Dữ liệu THẬT** (từ D12 `GET /cats/{id}/summary`): pH lần quét gần nhất, thời điểm quét,
 * phân loại, độ tin cậy, số lần quét 30 ngày, tỉ lệ trong ngưỡng, số cảnh báo chưa đọc.
 * Dòng thời gian y tế chạy bằng `GET /scans` + `GET /cats/{id}/notes`.
 *
 * ĐÃ BỎ HẲN (W1-E) — `DESIGN_MOCK_PROFILE` và mọi khối phụ thuộc nó, vì backend không có
 * trường nào tương ứng và nguyên tắc là **bỏ khỏi UI, không bịa dữ liệu**:
 *  - thể tích/tần suất bãi tiểu, hydrat hoá, khẩu phần (`NutritionPanel`);
 *  - nhóm máu, tiền sử dị ứng (`CatIdentityTiles`);
 *  - vaccine, tẩy giun; "Quyền giám sát lâm sàng" (chia sẻ hồ sơ real-time) — không có bảng,
 *    không có endpoint, và p10 §6.8 xếp phần chia sẻ đa người ngoài Phase 1;
 *  - ô "Sỏi & cặn bàng quang" và hai nhãn "Nguy cơ Canxi Oxalat/Struvite" — đây là CHẨN ĐOÁN
 *    phân biệt, trái quyết định #6/#8 (chỉ đo pH, không phát hiện máu/khoáng);
 *  - badge "Hồ sơ đã xác thực ISFM Gold Standard" — claim chứng nhận không có bằng chứng
 *    (p15 REQ-CLAIM-02).
 *
 * Thang pH trong `SpectrumCard` nay dùng `PhGaugeBar` của `entities/ph-bands` — ngưỡng và
 * màu đến từ `GET /reference/ph-bands`, không còn gradient 5.0→8.0 hard-code.
 */

/** Cột phải desktop: chỉ số theo dõi, phổ pH, dòng thời gian, dinh dưỡng. */
export function CatClinicalColumn({
  cat,
  summary,
  bands,
  scanCount = null,
  onViewAllHistory,
}: {
  cat: Cat;
  summary?: CatSummaryResponse;
  /** Dải pH từ `GET /reference/ph-bands` — nhãn/màu của timeline tra từ đây. */
  bands: PhBand[];
  /** Tổng số lần quét THẬT (`GET /scans/summary`) cho nhãn "Xem toàn bộ lịch sử (N bản ghi)". */
  scanCount?: number | null;
  onViewAllHistory?: () => void;
}) {
  const { t } = useTranslation("cat");
  return (
    <div className="flex flex-col gap-5">
      <DiagnosticAlertRow summary={summary} />
      <SpectrumCard cat={cat} summary={summary} bands={bands} />
      <MedicalTimeline catId={cat.id} bands={bands} scanCount={scanCount} onViewAll={onViewAllHistory} />
      <p className="text-[10px] leading-relaxed text-text-tertiary">{t("disclaimer.short")}</p>
    </div>
  );
}

function DiagnosticAlertRow({ summary }: { summary?: CatSummaryResponse }) {
  const { t } = useTranslation("cat");
  // CHỈ các trường D12 thật trả: số lần quét 30 ngày, tỉ lệ trong ngưỡng, độ tin cậy lần
  // quét gần nhất. Mèo chưa quét lần nào → 0/— , đúng trạng thái hợp lệ của tài khoản mới.
  const scanCount = summary?.scanCount30d ?? 0;
  const inRangePercent =
    summary?.inRangeRatio30d != null ? `${String(Math.round(summary.inRangeRatio30d * 100))}%` : "—";
  const confidence = summary?.lastScan?.confidence;
  return (
    <div className="grid grid-cols-2 gap-4">
      <div className="rounded-2xl bg-success-bg p-4">
        <p className="text-[10px] font-bold uppercase tracking-[0.5px] text-success-text opacity-80">
          {t("web.profile.inRange30d")}
        </p>
        <p className="pt-1 text-h2 font-bold text-success-text">{inRangePercent}</p>
        <p className="text-caption font-semibold text-success-text">
          {t("web.profile.scanCount30d", { count: scanCount })}
        </p>
        <p className="pt-1 text-[10px] text-success-text opacity-80">{t("web.profile.inRangeNote")}</p>
      </div>

      <div className="rounded-2xl bg-surface p-4 shadow-xs">
        <div className="flex items-start gap-2">
          <img src={iconLastScan} alt="" className="mt-0.5 size-3.5 shrink-0" />
          <p className="text-[10px] font-bold uppercase leading-tight tracking-[0.5px] text-text-tertiary">
            {t("web.profile.confidenceLabel")}
          </p>
        </div>
        <p className="pt-2 text-h3 font-bold text-text-primary">
          {confidence != null ? t("web.profile.confidenceValue", { value: Math.round(confidence * 100) }) : "—"}
        </p>
        <p className="pt-1 text-[10px] text-text-tertiary">{t("web.profile.confidenceNote")}</p>
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

      <div className="mt-4 flex items-center gap-2 rounded-xl bg-info px-3 py-2">
        <img src={iconLastScan} alt="" className="size-3.5 shrink-0" />
        <p className="text-caption font-semibold text-info-text">
          {phValue != null
            ? t("web.profile.phWithName", { name: cat.name, value: phValue })
            : t("web.profile.phUnknown", { name: cat.name })}
        </p>
        <span className="ml-auto text-[10px] text-info-text opacity-80">{lastScanLabel}</span>
      </div>
    </section>
  );
}

/**
 * Dòng thời gian y tế — DỮ LIỆU THẬT, trộn hai nguồn có endpoint:
 *  - `GET /scans` (E2): mỗi lần quét chỉ thị màu cát, nhãn/màu dải tra từ `/reference/ph-bands`;
 *  - `GET /cats/{id}/notes` (D16): ghi chú chủ nuôi, gồm cả loại "Đi khám thú y" và "Đổi loại cát"
 *    — đúng hai loại sự kiện mà Figma vẽ bên cạnh lần quét.
 *
 * Trước đây khối này là ba sự kiện bịa (siêu âm ổ bụng, vaccine, đổi cát) — đã bỏ hẳn:
 * không dựng hồ sơ khám bệnh giả. Hồ sơ chưa có gì thì hiện trạng thái rỗng thật.
 */
function MedicalTimeline({
  catId,
  bands,
  scanCount,
  onViewAll,
}: {
  catId: string;
  bands: PhBand[];
  scanCount: number | null;
  onViewAll?: () => void;
}) {
  const { t, i18n } = useTranslation("cat");
  const { data: scanPages } = useScanHistory(catId, "ALL");
  const { data: notes } = useCatNotes(catId);

  type Entry = {
    key: string;
    at: string;
    title: string;
    subtitle: string | null;
    body: string | null;
    swatch: string | null;
    isScan: boolean;
  };

  const scanEntries: Entry[] = (scanPages?.pages.flatMap((p) => p.items) ?? []).slice(0, 3).map((scan) => {
    const band = bands.find((b) => b.code === scan.bandCode);
    const label = band?.label ?? scan.classification;
    return {
      key: `scan-${scan.scanId}`,
      at: scan.capturedAt,
      title: t("web.profile.timelineScanTitle"),
      subtitle:
        scan.phValue != null
          ? t("web.profile.timelineScanSubtitle", { label, value: scan.phValue.toFixed(1) })
          : t("web.profile.timelineScanSubtitleNoPh", { label }),
      body: null,
      swatch: scan.thumbnailHex,
      isScan: true,
    };
  });

  const noteEntries: Entry[] = (notes?.items ?? []).slice(0, 3).map((note) => ({
    key: `note-${note.id}`,
    at: note.occurredOn ?? note.createdAt,
    title: t(`notes.types.${note.noteType}`),
    subtitle: t("web.profile.timelineNoteFrom"),
    body: note.body,
    swatch: null,
    isScan: false,
  }));

  const entries = [...scanEntries, ...noteEntries]
    .sort((a, b) => new Date(b.at).getTime() - new Date(a.at).getTime())
    .slice(0, 4);

  return (
    <section className="rounded-2xl bg-surface p-6 shadow-xs">
      <div className="flex items-start justify-between gap-4">
        <h3 className="text-h3 font-bold text-text-primary">{t("web.profile.timelineTitle")}</h3>
        <button
          type="button"
          onClick={onViewAll}
          className="shrink-0 text-caption font-semibold text-primary-dark hover:underline"
        >
          {scanCount != null ? t("web.profile.timelineAllCount", { count: scanCount }) : t("web.profile.timelineAll")}
        </button>
      </div>

      {entries.length === 0 ? (
        <p className="pt-4 text-caption text-text-secondary">{t("web.profile.timelineEmpty")}</p>
      ) : (
        <ol className="relative mt-5 flex flex-col gap-6 border-l border-border pl-6">
          {entries.map((ev) => (
            <li key={ev.key} className="relative">
              <span className="absolute -left-[31px] top-1 size-3 rounded-full border-2 border-surface bg-primary" />
              <div className="flex items-start gap-3">
                {ev.isScan ? (
                  ev.swatch ? (
                    <span
                      className="size-14 shrink-0 rounded-xl border border-border"
                      style={{ backgroundColor: ev.swatch }}
                      aria-hidden="true"
                    />
                  ) : (
                    <img src={sandScanThumb} alt="" className="size-14 shrink-0 rounded-xl object-cover" />
                  )
                ) : (
                  <img src={iconAttachment} alt="" className="mt-1 size-4 shrink-0" />
                )}
                <div className="min-w-0">
                  <p className="text-caption font-bold text-text-primary">{ev.title}</p>
                  {ev.subtitle ? <p className="text-caption font-semibold text-primary-dark">{ev.subtitle}</p> : null}
                  <p className="pt-0.5 text-[10px] text-text-tertiary">
                    {new Date(ev.at).toLocaleDateString(i18n.language)}
                  </p>
                  {ev.body ? (
                    <p className="whitespace-pre-wrap pt-1.5 text-caption leading-relaxed text-text-secondary">
                      {ev.body}
                    </p>
                  ) : null}
                </div>
              </div>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}

/**
 * Cột trái desktop. Figma vẽ ba thẻ: "Bác sĩ phụ trách", "Phòng bệnh & ký sinh"
 * (vaccine/tẩy giun) và "Quyền giám sát lâm sàng" (chia sẻ hồ sơ real-time).
 *
 * Chỉ giữ thẻ đầu, ở TRẠNG THÁI RỖNG THẬT: không có endpoint nào gắn mèo với phòng khám
 * (`place` là Phase 2, p4). Hai thẻ còn lại đã bỏ — backend không có bảng vaccine/tẩy giun
 * và không có cơ chế chia sẻ hồ sơ, nên mọi con số ở đó là bịa.
 */
export function CatIdentityExtras() {
  const { t } = useTranslation("cat");
  return (
    <div className="flex flex-col gap-5">
      <section className="rounded-2xl bg-surface p-5 shadow-xs">
        <div className="flex items-start gap-2">
          <img src={iconDoctor} alt="" className="mt-0.5 size-4 shrink-0" />
          <h3 className="text-caption font-bold uppercase tracking-[0.4px] text-text-tertiary">
            {t("web.profile.doctorLabel")}
          </h3>
        </div>
        <p className="pt-2 text-caption font-semibold text-text-primary">{t("web.profile.doctorEmptyTitle")}</p>
        <p className="pt-1 text-[10px] leading-relaxed text-text-tertiary">{t("web.profile.doctorEmptyBody")}</p>
      </section>
    </div>
  );
}

/**
 * Hàng hành động trên cùng của desktop. Badge "Hồ sơ đã xác thực ISFM Gold Standard" của
 * Figma đã bỏ (claim chứng nhận không có bằng chứng — p15 REQ-CLAIM-02). Nút xuất hồ sơ nay
 * dẫn thật sang `/export` thay vì là nút không làm gì.
 */
export function CatProfileTopActions({ className }: { className?: string }) {
  const { t } = useTranslation("cat");
  return (
    <div className={cn("flex items-center justify-end gap-4", className)}>
      <Link
        to="/export"
        className="inline-flex items-center gap-2 rounded-xl bg-primary-dark px-4 py-2 text-caption font-semibold text-white"
      >
        <img src={iconExportPdf} alt="" className="size-3.5" />
        {t("web.profile.exportPdf")}
      </Link>
    </div>
  );
}
