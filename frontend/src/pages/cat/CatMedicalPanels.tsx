import { useTranslation } from "react-i18next";
import type { Cat } from "@/entities/cat";
import type { PhBand } from "@/entities/ph-bands";
import { useCatNotes, type CatSummaryResponse } from "@/features/cat";
import { useScanHistory } from "@/features/history";
import { cn } from "@/shared/lib/cn";
import iconVerified from "@/shared/assets/icons/web-cat/profile-verified-isfm.svg";
import iconExportPdf from "@/shared/assets/icons/web-cat/profile-export-pdf.svg";
import iconVaccine from "@/shared/assets/icons/web-cat/profile-vaccine.svg";
import iconClockNext from "@/shared/assets/icons/web-cat/profile-clock-next.svg";
import iconDeworm from "@/shared/assets/icons/web-cat/profile-deworm.svg";
import iconAccessShield from "@/shared/assets/icons/web-cat/profile-access-shield.svg";
import iconAllergy from "@/shared/assets/icons/web-cat/profile-allergy.svg";
import iconCrystals from "@/shared/assets/icons/web-cat/profile-crystals.svg";
import iconVoiding from "@/shared/assets/icons/web-cat/profile-voiding.svg";
import iconLastScan from "@/shared/assets/icons/web-cat/profile-last-scan.svg";
import iconAttachment from "@/shared/assets/icons/web-cat/profile-attachment.svg";
import iconEditMenu from "@/shared/assets/icons/web-cat/profile-edit-menu.svg";
import iconHydration from "@/shared/assets/icons/web-cat/profile-hydration.svg";
import iconDiet from "@/shared/assets/icons/web-cat/profile-diet.svg";
import iconDoctor from "@/shared/assets/icons/web-cat/hub-doctor.svg";
import sandScanThumb from "@/shared/assets/images/web-cat/profile-sand-scan-thumb.png";

/**
 * Các panel lâm sàng cột phải của màn `/cats/:catId` ở desktop — Figma
 * `26mOVF2zdu4cI1EPz2Syxw` node `16:4290` "Web - 06 & 07. Hồ sơ Bé Mèo & Sổ khám Y tế",
 * cụ thể `16:4470` (RIGHT COLUMN) và `16:4555` (Medical Timeline).
 *
 * **Dữ liệu THẬT** (từ D12 `GET /cats/{id}/summary`, nay đã chạy): pH lần quét gần nhất,
 * thời điểm quét, phân loại, độ tin cậy, số lần quét 30 ngày, tỉ lệ trong ngưỡng, số cảnh báo
 * chưa đọc. Truyền xuống qua prop `summary` — `null` khi đang tải / lỗi / mèo chưa quét lần nào.
 *
 * **Dữ liệu THẬT bổ sung**: dòng thời gian y tế nay chạy bằng `GET /scans` + `GET /cats/{id}/notes`
 * (xem {@link MedicalTimeline}) — không còn sự kiện khám bệnh bịa.
 *
 * ⚠️ **Phần còn lại vẫn là DỮ LIỆU MẪU THEO THIẾT KẾ** ({@link DESIGN_MOCK_PROFILE}): thể tích
 * bãi tiểu, khẩu phần, hydrat hoá, vaccine, tẩy giun, nhóm máu, dị nguyên. Backend KHÔNG có
 * field nào cho những thứ này (không có bảng, không có endpoint) nên không thể nối thật —
 * giữ nguyên mock và đánh dấu rõ, không nguỵ trang thành dữ liệu thật.
 */

const DESIGN_MOCK_PROFILE = {
  voiding: "45",
  voidingUnit: "ml/lần",
  voidingFreq: "Tần suất: 3.2 lần/ngày (Bình thường)",
  phValue: 6.8,
  bloodType: "Loại A (Type A)",
  bloodNote: "Kháng thể chuẩn",
  allergy: "Không phát hiện",
  allergyNote: "Đã test 18 dị nguyên",
  vaccineNext: "18/11/2025",
  vaccineRemain: "Còn 385 ngày",
  dewormLast: "Đã uống: 2 tuần trước (14/10)",
  dewormNext: "14/11",
  hydration: "160 ml / ngày",
  hydrationNote: "Đạt 38ml/kg thể trọng (Tối ưu)",
  hydrationGoal: "100% mục tiêu",
  diet: "Royal Canin Feline Urinary S/O kết hợp Pâté Monge hồi phục đường tiết niệu vào bữa tối (50g hạt + 85g ướt).",
};

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
      <SpectrumCard cat={cat} summary={summary} />
      <MedicalTimeline catId={cat.id} bands={bands} scanCount={scanCount} onViewAll={onViewAllHistory} />
      <NutritionPanel />
      <p className="text-[10px] leading-relaxed text-text-tertiary">{t("disclaimer.short")}</p>
    </div>
  );
}

function DiagnosticAlertRow({ summary }: { summary?: CatSummaryResponse }) {
  const { t } = useTranslation("cat");
  // THẬT: số lần quét 30 ngày + tỉ lệ trong ngưỡng. Mèo chưa quét lần nào → hiển thị 0/—,
  // đúng trạng thái hợp lệ của tài khoản mới chứ không phải lỗi.
  const scanCount = summary?.scanCount30d ?? 0;
  const inRangePercent =
    summary?.inRangeRatio30d != null ? `${String(Math.round(summary.inRangeRatio30d * 100))}%` : "—";
  return (
    <div className="grid grid-cols-3 gap-4">
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
          <img src={iconCrystals} alt="" className="mt-0.5 size-3.5 shrink-0" />
          <p className="text-[10px] font-bold uppercase leading-tight tracking-[0.5px] text-text-tertiary">
            {t("web.profile.crystals")}
          </p>
        </div>
        <p className="pt-2 text-caption font-semibold text-text-primary">{t("web.profile.crystalsValue")}</p>
        <p className="pt-1 text-[10px] text-text-tertiary">{t("web.profile.crystalsNote")}</p>
      </div>

      <div className="rounded-2xl bg-surface p-4 shadow-xs">
        <div className="flex items-start gap-2">
          <img src={iconVoiding} alt="" className="mt-0.5 size-3.5 shrink-0" />
          <p className="text-[10px] font-bold uppercase leading-tight tracking-[0.5px] text-text-tertiary">
            {t("web.profile.voiding")}
          </p>
        </div>
        <p className="pt-2 text-h3 font-bold text-text-primary">
          {DESIGN_MOCK_PROFILE.voiding} <span className="text-caption font-normal">{DESIGN_MOCK_PROFILE.voidingUnit}</span>
        </p>
        <p className="text-[10px] text-text-tertiary">{DESIGN_MOCK_PROFILE.voidingFreq}</p>
        <p className="pt-1 text-[10px] text-text-tertiary">{t("web.profile.voidingNote")}</p>
      </div>
    </div>
  );
}

function SpectrumCard({ cat, summary }: { cat: Cat; summary?: CatSummaryResponse }) {
  const { t, i18n } = useTranslation("cat");
  // THẬT: pH + thời điểm của lần quét gần nhất. Chưa có lần quét kết luận được thì rơi về
  // giá trị mẫu để khung thiết kế không vỡ, và nhãn bên dưới nói rõ là chưa có dữ liệu.
  const lastScan = summary?.lastScan ?? null;
  const phValue = lastScan?.phValue ?? null;
  const gaugeValue = phValue ?? DESIGN_MOCK_PROFILE.phValue;
  // Vị trí kim chỉ trên dải pH 5.0 → 8.0, kẹp trong [0,100] phòng giá trị ngoài dải.
  const pct = Math.min(100, Math.max(0, ((gaugeValue - 5) / 3) * 100));
  const lastScanLabel = lastScan
    ? t("web.profile.lastScanAt", {
        date: new Date(lastScan.capturedAt).toLocaleDateString(i18n.language),
      })
    : t("web.profile.lastScanNone");
  return (
    <section className="rounded-2xl bg-surface p-6 shadow-xs">
      <h3 className="text-h3 font-bold text-text-primary">{t("web.profile.spectrumTitle")}</h3>
      <p className="pt-1 text-caption text-text-secondary">{t("web.profile.spectrumSub")}</p>

      <div className="relative mt-6">
        <div className="h-4 w-full rounded-full bg-gradient-to-r from-ph-abnormal via-ph-normal to-ph-mild" />
        <div className="absolute -top-1 -translate-x-1/2" style={{ left: `${String(pct)}%` }}>
          <div className="size-6 rounded-full border-4 border-surface bg-primary-dark shadow-sm" />
        </div>
      </div>

      <div className="mt-3 flex justify-between text-[10px] text-text-tertiary">
        <span>{t("web.profile.spectrumAcid")}</span>
        <span className="font-semibold text-ph-normal-text">{t("web.profile.spectrumIdeal")}</span>
        <span>{t("web.profile.spectrumAlkaline")}</span>
      </div>
      <div className="mt-1 flex justify-between text-[10px] text-text-tertiary">
        <span>{t("web.profile.spectrumRiskOxalate")}</span>
        <span>{t("web.profile.spectrumRiskStruvite")}</span>
      </div>

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
        scan.phValue !== null
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
          {scanCount != null
            ? t("web.profile.timelineAllCount", { count: scanCount })
            : t("web.profile.timelineAll")}
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
                  {ev.subtitle ? (
                    <p className="text-caption font-semibold text-primary-dark">{ev.subtitle}</p>
                  ) : null}
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

function NutritionPanel() {
  const { t } = useTranslation("cat");
  return (
    <section className="rounded-2xl bg-surface p-6 shadow-xs">
      <div className="flex items-start justify-between gap-4">
        <h3 className="text-h3 font-bold text-text-primary">{t("web.profile.nutritionTitle")}</h3>
        <button
          type="button"
          className="flex shrink-0 items-center gap-1.5 text-caption font-semibold text-primary-dark hover:underline"
        >
          <img src={iconEditMenu} alt="" className="size-3.5" />
          {t("web.profile.nutritionUpdate")}
        </button>
      </div>

      <div className="mt-4 grid grid-cols-2 gap-4">
        <div className="rounded-xl bg-info p-4">
          <div className="flex items-center gap-2">
            <img src={iconHydration} alt="" className="size-4" />
            <p className="text-[10px] font-bold uppercase tracking-[0.5px] text-info-text">
              {t("web.profile.hydrationLabel")}
            </p>
          </div>
          <p className="pt-2 text-h3 font-bold text-info-text">{DESIGN_MOCK_PROFILE.hydration}</p>
          <p className="text-[10px] text-info-text opacity-80">{DESIGN_MOCK_PROFILE.hydrationNote}</p>
          <div className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-surface/60">
            <div className="h-full w-full rounded-full bg-primary" />
          </div>
          <p className="pt-1 text-[10px] font-semibold text-info-text">{DESIGN_MOCK_PROFILE.hydrationGoal}</p>
        </div>

        <div className="rounded-xl bg-background-alt p-4">
          <div className="flex items-center gap-2">
            <img src={iconDiet} alt="" className="size-4" />
            <p className="text-[10px] font-bold uppercase tracking-[0.5px] text-text-tertiary">
              {t("web.profile.dietLabel")}
            </p>
          </div>
          <p className="pt-2 text-caption leading-relaxed text-text-secondary">{DESIGN_MOCK_PROFILE.diet}</p>
        </div>
      </div>
    </section>
  );
}

/**
 * Hai ô "Nhóm máu" / "Tiền sử dị ứng" — ở Figma chúng nằm TRONG lưới nhận diện của thẻ hồ sơ
 * (`16:4300`), không phải khối rời cuối cột. Tách ra để `CatProfileHero` nhận qua `extraTiles`.
 * Nội dung vẫn là {@link DESIGN_MOCK_PROFILE} (backend không có hai trường này).
 */
export function CatIdentityTiles() {
  const { t } = useTranslation("cat");
  return (
    <>
      <div className="rounded-xl bg-background-alt p-3">
        <p className="text-small text-text-tertiary">{t("web.profile.bloodType")}</p>
        <p className="pt-0.5 text-caption font-bold text-text-primary">{DESIGN_MOCK_PROFILE.bloodType}</p>
        <p className="text-small text-text-tertiary">{DESIGN_MOCK_PROFILE.bloodNote}</p>
      </div>
      <div className="rounded-xl bg-background-alt p-3">
        <p className="flex items-center gap-1.5 text-small text-text-tertiary">
          <img src={iconAllergy} alt="" className="size-3" />
          {t("web.profile.allergy")}
        </p>
        <p className="pt-0.5 text-caption font-bold text-text-primary">{DESIGN_MOCK_PROFILE.allergy}</p>
        <p className="text-small text-text-tertiary">{DESIGN_MOCK_PROFILE.allergyNote}</p>
      </div>
    </>
  );
}

/** Cột trái desktop: bác sĩ phụ trách, thẻ tiêm phòng/tẩy giun, quyền giám sát lâm sàng. */
export function CatIdentityExtras() {
  const { t } = useTranslation("cat");
  return (
    <div className="flex flex-col gap-5">
      {/* Figma có thẻ "BÁC SĨ PHỤ TRÁCH" với tên + phòng khám + nút gọi. KHÔNG có endpoint
          nào gắn mèo với phòng khám (`place` là Phase 2, p4) nên dựng đúng khung và hiển thị
          TRẠNG THÁI RỖNG THẬT thay vì bịa tên một bác sĩ. */}
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

      <section className="rounded-2xl bg-surface p-5 shadow-xs">
        <h3 className="text-caption font-bold uppercase tracking-[0.4px] text-text-tertiary">
          {t("web.profile.vaccineTitle")}
        </h3>

        <div className="mt-3 flex items-start gap-3 rounded-xl bg-success-bg p-3">
          <img src={iconVaccine} alt="" className="mt-0.5 size-4 shrink-0" />
          <div className="min-w-0">
            <p className="text-caption font-semibold text-success-text">{t("web.profile.vaccineName")}</p>
            <p className="text-[10px] text-success-text opacity-80">{t("web.profile.vaccineNote")}</p>
            <p className="pt-1 inline-flex items-center gap-1 text-[10px] font-semibold text-success-text">
              <img src={iconClockNext} alt="" className="size-3" />
              {t("web.profile.vaccineNext", { date: DESIGN_MOCK_PROFILE.vaccineNext })} · {DESIGN_MOCK_PROFILE.vaccineRemain}
            </p>
          </div>
        </div>

        <div className="mt-3 flex items-start gap-3 rounded-xl bg-background-alt p-3">
          <img src={iconDeworm} alt="" className="mt-0.5 size-4 shrink-0" />
          <div className="min-w-0">
            <p className="text-caption font-semibold text-text-primary">{t("web.profile.dewormName")}</p>
            <p className="text-[10px] text-text-tertiary">{t("web.profile.dewormNote")}</p>
            <p className="pt-1 text-[10px] text-text-secondary">
              {DESIGN_MOCK_PROFILE.dewormLast} · {DESIGN_MOCK_PROFILE.dewormNext}
            </p>
          </div>
        </div>
      </section>

      <section className="rounded-2xl bg-surface p-5 shadow-xs">
        <div className="flex items-start gap-2">
          <img src={iconAccessShield} alt="" className="mt-0.5 size-4 shrink-0" />
          <h3 className="text-caption font-bold uppercase leading-tight tracking-[0.4px] text-text-tertiary">
            {t("web.profile.accessTitle")}
          </h3>
        </div>
        <p className="pt-2 text-caption leading-relaxed text-text-secondary">{t("web.profile.accessBody")}</p>
        <button
          type="button"
          className="mt-3 w-full rounded-xl bg-background-alt px-4 py-2 text-caption font-semibold text-primary-dark"
        >
          {t("web.profile.accessManage")}
        </button>
      </section>

    </div>
  );
}

/** Hàng hành động trên cùng của desktop: badge xác thực + xuất bệnh án. */
export function CatProfileTopActions({ className }: { className?: string }) {
  const { t } = useTranslation("cat");
  return (
    <div className={cn("flex items-center justify-between gap-4", className)}>
      <span className="inline-flex items-center gap-2 rounded-full bg-success-bg px-3 py-1.5 text-[11px] font-semibold text-success-text">
        <img src={iconVerified} alt="" className="size-3.5" />
        {t("web.profile.verified")}
      </span>
      <button
        type="button"
        className="inline-flex items-center gap-2 rounded-xl bg-primary-dark px-4 py-2 text-caption font-semibold text-white"
      >
        <img src={iconExportPdf} alt="" className="size-3.5" />
        {t("web.profile.exportPdf")}
      </button>
    </div>
  );
}
