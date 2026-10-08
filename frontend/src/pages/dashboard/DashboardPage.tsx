import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { isToday } from "date-fns";
import {
  Activity,
  BadgeCheck,
  CalendarClock,
  CalendarDays,
  Camera,
  ChevronRight,
  Droplet,
  Eye,
  FileText,
  FlaskConical,
  HelpCircle,
  Lightbulb,
  PawPrint,
  Plus,
  ShieldCheck,
} from "lucide-react";
import { useCatList } from "@/features/cat";
import { formatScanTimestamp, useScanHistory, useScanSummary } from "@/features/history";
import { useReminders } from "@/features/reminder";
import { HealthFlagDisclosure } from "@/features/insight";
import { useCareTips } from "./careTipsApi";
import { TREND_RANGE_DAYS, toTrendPoints, useCatTrends } from "@/features/trends";
import type { TrendRange } from "@/features/trends";
import { PhBadge, PhGaugeBar, phTokenStyle, usePhBands } from "@/entities/ph-bands";
import type { PhBand } from "@/entities/ph-bands";
import { useSessionStore } from "@/entities/user";
import { CatAvatar } from "@/entities/cat";
import type { Cat } from "@/entities/cat";
import { displayableScanCount } from "@/entities/scan-result";
import type { ConfidenceBand, ScanListItem } from "@/entities/scan-result";
import type { Reminder } from "@/features/reminder";
import { ErrorState } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import { formatDate } from "@/shared/lib/format/formatDate";
import { formatRelative } from "@/shared/lib/format/formatRelative";

/**
 * Trang chủ (Home Dashboard).
 *
 * Figma có HAI frame cho màn này và cả hai đều được dựng ở đây:
 * - mobile 390px (node 1:2017) — cây `lg:hidden`.
 * - web 1280px (node 16:7086) — cây `hidden lg:block`: hàng chào mừng chiếm trọn bề ngang,
 *   bên dưới là lưới 12 cột (8/12 nội dung chính + 4/12 cột phải).
 * Chrome (sidebar 288px + header 64px) nằm ở `AppLayout`; `main` đã cấp sẵn padding nên cây
 * desktop dưới đây KHÔNG tự đặt padding ngang.
 *
 * NGUYÊN TẮC DỮ LIỆU: trang này KHÔNG bịa mèo/ảnh/lần quét/chỉ số để ảnh chụp giống Figma.
 * Mọi con số đều đến từ API thật (`/cats`, `/scans`, `/scans/summary`, `/reminders`,
 * `/reference/ph-bands`, `/cats/{id}/trends`, `/care-tips`); không có dữ liệu thì khối tự
 * render empty state, đang tải thì skeleton.
 *
 * COPY: quyết định #8 — "Chỉ số đo: Chỉ pH. Không phát hiện máu." Các câu của Figma hứa hẹn
 * phát hiện máu/khoáng chất/"dấu ấn sinh học", hay khẳng định "dữ liệu sáng nay hoàn toàn ổn
 * định" KHÔNG được chép vào: nhận xét lấy thẳng `band.description` do API trả về, câu chào dựng
 * từ tên mèo + lần quét gần nhất thật.
 *
 * Hai thẻ "Cơ sở đồng hành" và "Kho cát gia đình" của Figma bỏ: phòng khám "đang mở cửa", điểm
 * đánh giá, khoảng cách và tồn kho cát tại nhà đều không có field nào trong API.
 */

const WEB_RANGES: TrendRange[] = ["7D", "30D", "90D"];

/** Thang hiển thị suy ra TỪ DỮ LIỆU `bands` — không hard-code ngưỡng (p6 §6.7.3). */
interface PhScale {
  min: number;
  max: number;
  normal: PhBand | undefined;
}

function buildScale(bands: PhBand[] | undefined): PhScale | null {
  if (!bands || bands.length === 0) return null;
  // `number | null | undefined`: server bỏ hẳn key `phMin`/`phMax` ở dải mở (LOW/HIGH) chứ
  // không gửi null — xem ghi chú ở `PhBand.phMin`.
  const isNumber = (v: number | null | undefined): v is number => typeof v === "number" && Number.isFinite(v);
  const lower = bands.map((b) => b.phMin).filter(isNumber);
  const upper = bands.map((b) => b.phMax).filter(isNumber);
  if (lower.length === 0 || upper.length === 0) return null;
  return {
    min: Math.min(...lower),
    max: Math.max(...upper),
    normal: bands.find((b) => b.severity === "NORMAL"),
  };
}

/** `description` của dải mang placeholder `{0}` cho giá trị pH — thay tại chỗ hiển thị. */
function bandSentence(band: PhBand | undefined, phValue: number | null | undefined): string | null {
  if (!band || band.description === "") return null;
  if (phValue === null || phValue === undefined) return band.description.replace("{0}", "");
  return band.description.replace("{0}", phValue.toFixed(1));
}

function formatRangeBound(value: number | null | undefined, fallback: number): string {
  return (value ?? fallback).toFixed(1);
}

/**
 * `GET /scans` đã trả sẵn `confidenceBand` (HIGH/MEDIUM/LOW) — chỗ hiển thị chỉ đổi nhãn, KHÔNG
 * tự tính ngưỡng từ `confidence` và không in "% độ tin cậy thuật toán".
 */
const CONFIDENCE_LABEL_KEY: Record<ConfidenceBand, string> = {
  HIGH: "dashboardWeb.confidenceHIGH",
  MEDIUM: "dashboardWeb.confidenceMEDIUM",
  LOW: "dashboardWeb.confidenceLOW",
};

/** Lịch đang bật có `nextRunAt` (lịch tắt thì server bỏ hẳn khoá). */
type ScheduledReminder = Reminder & { nextRunAt: string };

/**
 * Thứ tự hiển thị lịch nhắc: lịch SẮP TỚI trước (gần nhất lên đầu), lịch đã quá hạn xuống
 * cuối. Trước đây lấy phần tử đầu của API nên lịch "Nhắc hạn gói" đã quá hạn 11 giờ được in
 * thành "Quét định kỳ mỗi 1 ngày · khoảng 11 giờ trước".
 */
function orderReminders(reminders: Reminder[], now: number): ScheduledReminder[] {
  const scheduled = reminders.filter((r): r is ScheduledReminder => r.nextRunAt !== undefined);
  const at = (r: ScheduledReminder) => Date.parse(r.nextRunAt);
  const upcoming = scheduled.filter((r) => at(r) >= now).sort((a, b) => at(a) - at(b));
  const overdue = scheduled.filter((r) => at(r) < now).sort((a, b) => at(b) - at(a));
  return [...upcoming, ...overdue];
}

export function DashboardPage() {
  const { t } = useTranslation(["common", "cat", "reminder"]);
  const user = useSessionStore((s) => s.user);
  // Trang chủ mở cho cả khách chưa đăng nhập (route nằm ngoài RequireAuth). Khách KHÔNG gọi
  // `/cats`, `/scans`, `/reminders` — các endpoint này cần phiên, gọi sẽ 403. `/reference/ph-bands`
  // và `/care-tips` là endpoint công khai nên vẫn gọi được.
  const isGuest = !user;
  const catsQuery = useCatList("ACTIVE", !isGuest);
  const cats = useMemo(() => catsQuery.data?.items ?? [], [catsQuery.data]);
  const { data: bands } = usePhBands();
  const historyQuery = useScanHistory(undefined, "ALL", !isGuest);
  const scans = useMemo(() => historyQuery.data?.pages[0]?.items ?? [], [historyQuery.data]);
  // "Xem toàn bộ N lượt quét" phải là TỔNG THẬT (E3 `GET /scans/summary`), không phải số đã tải.
  // `displayableScanCount` bỏ bản ghi `INCONCLUSIVE` mà `GET /scans` không trả — một nguồn đếm
  // chung với màn Lịch sử.
  const { data: scanSummary } = useScanSummary(undefined, undefined, undefined, !isGuest);
  const totalScanCount = scanSummary ? displayableScanCount(scanSummary) : scans.length;
  const { data: reminderList } = useReminders({ active: true }, !isGuest);
  const [now] = useState(() => Date.now());
  const reminders = useMemo(() => orderReminders(reminderList?.items ?? [], now), [reminderList, now]);

  const catsLoading = !isGuest && catsQuery.isPending;
  const scansLoading = !isGuest && historyQuery.isPending;
  const loadFailed = !isGuest && (catsQuery.isError || historyQuery.isError);

  const latestScan = scans.at(0);
  const recentScans = useMemo(() => scans.slice(0, 5), [scans]);
  // `!= null`: server BỎ HẲN khoá `thumbnailHex` khi lần quét không có màu (không gửi null),
  // nên so `!== null` cho `undefined` lọt qua và cột màu hiện header trên một cột trống.
  const recentHasColor = recentScans.some((scan) => scan.thumbnailHex != null);
  const latestBand = latestScan ? bands?.find((b) => b.code === latestScan.bandCode) : undefined;
  const scale = buildScale(bands);
  const primaryCat = cats.find((c) => c.isPrimary) ?? cats.at(0);
  const nextReminder =
    reminders.find((r) => r.type === "SCAN_ROUTINE" && Date.parse(r.nextRunAt) >= now) ?? reminders.at(0);
  /** Tổng dấu hiệu chưa xác nhận của cả đàn (`GET /cats` → `unacknowledgedFlagCount`). */
  const unacknowledgedFlagCount = cats.reduce((sum, cat) => sum + (cat.unacknowledgedFlagCount ?? 0), 0);
  // F7 — nội dung chăm sóc đã công bố. Công khai nên khách chưa đăng nhập vẫn đọc được.
  const { data: careTips } = useCareTips(3);
  const careTip = careTips?.[0];

  /** Lần quét gần nhất CỦA TỪNG BÉ — suy ra từ chính `/scans`, không gọi thêm endpoint. */
  const latestByCat = useMemo(() => {
    const map = new Map<string, ScanListItem>();
    for (const scan of scans) {
      if (scan.catId !== null && !map.has(scan.catId)) map.set(scan.catId, scan);
    }
    return map;
  }, [scans]);

  const displayName = user?.displayName ?? t("dashboard.guestName", { ns: "common" });
  const todayLabel = t("dashboard.today", { ns: "common" });
  const yesterdayLabel = t("dashboard.yesterday", { ns: "common" });
  const stamp = (iso: string) => formatScanTimestamp(iso, todayLabel, yesterdayLabel);

  const [range, setRange] = useState<TrendRange>("30D");
  // D13 `GET /cats/{id}/trends` — CÙNG nguồn với màn `/cats/:catId/trends`.
  const { data: trendData } = useCatTrends(primaryCat?.id, range);
  const trendPoints = useMemo(
    () => toTrendPoints(trendData?.points).map((p) => ({ date: p.date, value: p.phValue })),
    [trendData],
  );
  const trendStats = useMemo(() => {
    if (trendPoints.length === 0) return null;
    const values = trendPoints.map((p) => p.value);
    const avg = values.reduce((a, b) => a + b, 0) / values.length;
    const variance = values.reduce((a, b) => a + (b - avg) ** 2, 0) / values.length;
    return {
      avg: avg.toFixed(2),
      deviation: Math.sqrt(variance).toFixed(2),
      frequency: (values.length / TREND_RANGE_DAYS[range]).toFixed(1),
    };
  }, [trendPoints, range]);

  const catBand = (cat: Cat): PhBand | undefined => {
    const scan = latestByCat.get(cat.id);
    const code = scan?.bandCode ?? cat.lastClassification;
    return code != null ? bands?.find((b) => b.code === code) : undefined;
  };
  const catScannedAt = (cat: Cat): string | null => latestByCat.get(cat.id)?.capturedAt ?? cat.lastScanAt ?? null;

  const sexLabel = (cat: Cat) =>
    t(`form.sex.${cat.sex === "MALE" ? "male" : cat.sex === "FEMALE" ? "female" : "unknown"}`, { ns: "cat" });

  const catMeta = (cat: Cat): string =>
    [
      cat.breedName,
      cat.ageMonths != null
        ? t("dashboard.catMeta", { ns: "common", age: Math.floor(cat.ageMonths / 12), sex: sexLabel(cat) })
        : sexLabel(cat),
      cat.weightKg != null ? t("dashboardWeb.weightKg", { ns: "common", value: cat.weightKg }) : null,
    ]
      .filter((part): part is string => typeof part === "string" && part.length > 0)
      .join(" • ");

  /** Câu dưới lời chào — dựng từ tên mèo + lần quét gần nhất THẬT, không khẳng định tĩnh. */
  const introText = (() => {
    const names = new Intl.ListFormat("vi", { style: "long", type: "conjunction" }).format(cats.map((c) => c.name));
    const wish =
      cats.length > 0
        ? t("dashboardWeb.introWish", { ns: "common", names })
        : t("dashboardWeb.introWishNoCat", { ns: "common" });
    if (isGuest || scansLoading) return wish;
    if (!latestScan) return `${wish} ${t("dashboardWeb.introNoScan", { ns: "common" })}`;
    if (latestScan.phValue == null || !latestBand) return wish;
    const params = {
      ns: "common",
      when: stamp(latestScan.capturedAt),
      value: latestScan.phValue.toFixed(1),
      label: latestBand.label,
    };
    const latest = latestScan.catName
      ? t("dashboardWeb.introLatest", { ...params, name: latestScan.catName })
      : t("dashboardWeb.introLatestNoCat", params);
    return `${wish} ${latest}`;
  })();

  const reminderTitle = (reminder: ScheduledReminder): string => {
    if (reminder.type !== "SCAN_ROUTINE") return t(`type.${reminder.type}`, { ns: "reminder" });
    const title =
      reminder.intervalDays !== undefined
        ? t("dashboardWeb.scheduleEvery", { ns: "common", days: reminder.intervalDays })
        : t("dashboardWeb.scheduleRepeat", { ns: "common" });
    const cat = reminder.catId != null ? cats.find((c) => c.id === reminder.catId) : undefined;
    return cat ? t("dashboardWeb.scheduleScanFor", { ns: "common", title, name: cat.name }) : title;
  };

  const reminderWhen = (reminder: ScheduledReminder): string => {
    if (Date.parse(reminder.nextRunAt) < now) {
      return t("dashboardWeb.scheduleOverdue", { ns: "common", time: formatDate(reminder.nextRunAt, "HH:mm dd/MM") });
    }
    const when = formatRelative(reminder.nextRunAt);
    return reminder.preferredTimeStart !== undefined
      ? t("dashboardWeb.scheduleWhen", { ns: "common", when, time: reminder.preferredTimeStart.slice(0, 5) })
      : t("dashboardWeb.scheduleWhenPlain", { ns: "common", when });
  };

  const reminderLine = (reminder: ScheduledReminder | undefined): string => {
    if (!reminder) return t("dashboard.reminderEmpty", { ns: "common" });
    if (Date.parse(reminder.nextRunAt) < now) {
      return t("dashboard.reminderOverdue", { ns: "common", time: formatDate(reminder.nextRunAt, "HH:mm dd/MM") });
    }
    const when = formatRelative(reminder.nextRunAt);
    const cat = reminder.catId != null ? cats.find((c) => c.id === reminder.catId) : undefined;
    return cat
      ? t("dashboard.reminderNext", { ns: "common", when, name: cat.name })
      : t("dashboard.reminderNextNoCat", { ns: "common", when });
  };

  const retry = () => {
    void catsQuery.refetch();
    void historyQuery.refetch();
  };

  const loadError = loadFailed ? (
    <ErrorState
      title={t("dashboardWeb.loadError", { ns: "common" })}
      description={t("dashboardWeb.loadErrorBody", { ns: "common" })}
      onRetry={retry}
      className="mt-4 rounded-2xl bg-surface py-8 shadow-xs lg:mt-0 lg:mb-6"
    />
  ) : null;

  return (
    <>
      {/* Dải báo chế độ khách — hiện ở cả mobile lẫn desktop khi chưa đăng nhập. */}
      {isGuest ? (
        <div className="mx-auto mb-2 flex max-w-[480px] flex-col gap-2 px-4 pt-4 lg:mb-6 lg:max-w-none lg:flex-row lg:items-center lg:justify-between lg:px-0 lg:pt-0">
          <div>
            <p className="text-caption font-semibold text-text-primary">
              {t("dashboard.guestBanner.title", { ns: "common" })}
            </p>
            <p className="text-[11px] leading-relaxed text-text-secondary">
              {t("dashboard.guestBanner.body", { ns: "common" })}
            </p>
          </div>
          <div className="flex shrink-0 gap-2">
            <Link
              to="/auth/login"
              className="flex h-11 items-center justify-center rounded-xl bg-primary px-4 text-caption font-semibold text-white"
            >
              {t("dashboard.guestBanner.login", { ns: "common" })}
            </Link>
            <Link
              to="/auth/register"
              className="flex h-11 items-center justify-center rounded-xl bg-surface px-4 text-caption font-semibold text-primary-dark shadow-xs"
            >
              {t("dashboard.guestBanner.register", { ns: "common" })}
            </Link>
          </div>
        </div>
      ) : null}

      {/* ============================ MOBILE (Figma 1:2017) ============================ */}
      <div className="mx-auto max-w-[480px] px-4 pb-6 lg:hidden">
        {loadError}

        {/* Section 1 — Hero quét cát. Figma mobile KHÔNG có dòng chào ở trên hero. */}
        <div className="relative mt-4 overflow-hidden rounded-2xl bg-primary p-5 text-white shadow-[0px_10px_24px_-6px_rgba(47,79,178,0.28)]">
          <PawPrint
            aria-hidden="true"
            className="pointer-events-none absolute -right-4 -top-3 size-28 rotate-12 text-white/10"
          />
          <span className="relative inline-flex items-center gap-1.5 rounded-full bg-white/15 px-3 py-1 text-[11px] tracking-[0.4px] backdrop-blur-sm">
            <FlaskConical size={13} aria-hidden="true" />
            {t("dashboard.heroBadge", { ns: "common" })}
          </span>
          <h2 className="relative pt-4 text-[20px] font-semibold leading-7">
            {t("dashboard.heroTitle", { ns: "common" })}
          </h2>
          <p className="relative max-w-[280px] pt-1 text-[14px] leading-5 text-on-primary-muted">
            {t("dashboard.heroBody", { ns: "common" })}
          </p>
          <div className="relative flex items-center justify-between gap-2 pt-4">
            <Link
              to="/scan/select-cat"
              className="flex h-[50px] flex-1 items-center justify-center gap-2 rounded-xl bg-secondary text-[18px] font-semibold text-secondary-text-on shadow-sm"
            >
              <Camera size={18} aria-hidden="true" />
              {t("dashboard.startScan", { ns: "common" })}
            </Link>
            <Link
              to="/scan"
              aria-label={t("dashboard.scanHelp", { ns: "common" })}
              className="flex size-[50px] shrink-0 items-center justify-center rounded-xl bg-white/15 backdrop-blur-sm"
            >
              <HelpCircle size={18} aria-hidden="true" />
            </Link>
          </div>
        </div>

        {/* Section 2 — Mèo của bạn (carousel cuộn ngang có chủ đích: thẻ sau ló ra ở mép phải) */}
        {!isGuest ? (
          <section className="pt-6">
            <div className="flex items-center justify-between gap-3">
              <div className="flex min-w-0 items-center gap-2">
                <h2 className="shrink-0 text-[18px] font-semibold text-text-primary">
                  {t("dashboard.yourCats", { ns: "common" })}
                </h2>
                {!catsLoading ? (
                  <span className="truncate rounded-full bg-chip-bg px-2 py-0.5 text-[11px] tracking-[0.4px] text-primary-dark">
                    {t("dashboard.catsCount", { ns: "common", count: cats.length })}
                  </span>
                ) : null}
              </div>
              <Link to="/cats" className="shrink-0 py-2 text-caption font-semibold text-primary-dark">
                {t("dashboard.viewAll", { ns: "common" })}
              </Link>
            </div>
            <div className="-mx-4 mt-1 flex snap-x snap-mandatory scroll-px-4 gap-3 overflow-x-auto px-4 pb-3 pt-1 [scrollbar-width:none]">
              {catsLoading
                ? [0, 1].map((i) => (
                    <div
                      key={i}
                      aria-hidden="true"
                      className="h-[136px] w-[220px] shrink-0 animate-pulse rounded-2xl bg-chip-bg/60"
                    />
                  ))
                : cats.map((cat) => {
                    const band = catBand(cat);
                    const style = phTokenStyle(band?.colorToken ?? "color-ph-unknown");
                    const scannedAt = catScannedAt(cat);
                    return (
                      <Link
                        key={cat.id}
                        to={`/cats/${cat.id}`}
                        className="flex w-[220px] shrink-0 snap-start flex-col gap-3 rounded-2xl bg-gradient-to-br from-white to-background-alt p-3.5 shadow-[0px_4px_16px_-2px_rgba(47,79,178,0.08)]"
                      >
                        <div className="flex items-center gap-3">
                          <div className="relative size-14 shrink-0">
                            <CatAvatar src={cat.avatarUrl} name={cat.name} size="md" className="size-14 shadow-xs" />
                            {band ? (
                              <span
                                aria-hidden="true"
                                className={cn(
                                  "absolute bottom-0 right-0 size-4 rounded-full border-2 border-surface",
                                  style.solid,
                                )}
                              />
                            ) : null}
                          </div>
                          <div className="min-w-0 flex-1">
                            <p className="flex items-center gap-1 text-[18px] font-semibold leading-6 text-text-primary">
                              <span className="truncate">{cat.name}</span>
                              {cat.isPrimary ? (
                                <BadgeCheck
                                  size={15}
                                  className="shrink-0 text-primary-dark"
                                  aria-label={t("dashboard.primaryCat", { ns: "common" })}
                                />
                              ) : null}
                            </p>
                            <p className="truncate text-[11px] tracking-[0.4px] text-text-secondary">
                              {cat.ageMonths != null
                                ? t("dashboard.catMeta", {
                                    ns: "common",
                                    age: Math.floor(cat.ageMonths / 12),
                                    sex: sexLabel(cat),
                                  })
                                : sexLabel(cat)}
                            </p>
                          </div>
                        </div>
                        {/* Dải phân loại lần quét gần nhất + giờ quét: hai dòng, không ép
                            nhãn dài của API ("Trong khoảng tham chiếu") vỡ 3 dòng cạnh giờ. */}
                        <div
                          className={cn(
                            "flex flex-col gap-0.5 rounded-lg px-2.5 py-1.5",
                            band ? style.bg : "bg-background-alt/70",
                          )}
                        >
                          <span
                            className={cn(
                              "flex items-center gap-1.5 text-[11px] font-semibold leading-[14px]",
                              band ? style.text : "text-text-secondary",
                            )}
                          >
                            <span aria-hidden="true" className={cn("size-2 shrink-0 rounded-full", style.solid)} />
                            <span className="truncate">
                              {band?.label ?? t("dashboard.catNoScan", { ns: "common" })}
                            </span>
                          </span>
                          {scannedAt ? (
                            <span className="pl-3.5 text-[11px] leading-[14px] text-text-secondary">
                              {t("dashboard.catScannedAt", { ns: "common", time: stamp(scannedAt) })}
                            </span>
                          ) : null}
                        </div>
                      </Link>
                    );
                  })}
              {!catsLoading ? (
                <Link
                  to="/cats/new"
                  className="flex w-[130px] shrink-0 snap-start flex-col items-center justify-center gap-2 rounded-2xl bg-background-alt/70 py-9"
                >
                  <span className="flex size-11 items-center justify-center rounded-full bg-info text-primary-dark shadow-xs">
                    <Plus size={16} aria-hidden="true" />
                  </span>
                  <span className="text-[12px] tracking-[0.3px] text-text-secondary">
                    {t("dashboard.addCat", { ns: "common" })}
                  </span>
                </Link>
              ) : null}
            </div>
          </section>
        ) : null}

        {/* Section 3 — Lần theo dõi gần nhất */}
        {!isGuest ? (
          <section className="pt-5">
            <div className="flex items-baseline justify-between gap-3">
              <h2 className="shrink-0 text-[18px] font-semibold text-text-primary">
                {t("dashboard.latestMonitoring", { ns: "common" })}
              </h2>
              {latestScan?.catName != null && latestScan.catName !== "" ? (
                <p className="min-w-0 truncate text-[11px] tracking-[0.4px] text-text-secondary">
                  {t("dashboard.sessionOf", { ns: "common", name: latestScan.catName })}
                </p>
              ) : null}
            </div>

            {scansLoading ? (
              <div aria-hidden="true" className="mt-2 h-[220px] animate-pulse rounded-2xl bg-chip-bg/60" />
            ) : latestScan ? (
              <Link
                to={`/scans/${latestScan.scanId}`}
                className="mt-2 block rounded-2xl bg-surface p-4 shadow-[0px_4px_16px_-2px_rgba(47,79,178,0.06)]"
              >
                <div className="flex items-start justify-between gap-3">
                  {latestBand ? (
                    <PhBadge
                      band={latestBand}
                      className="min-w-0 whitespace-nowrap px-2.5 py-1 text-[12px] leading-4 [&>svg]:size-3.5"
                    />
                  ) : null}
                  <div className="shrink-0 text-right">
                    <p className="text-[11px] text-text-secondary">{t("dashboard.analyzed", { ns: "common" })}</p>
                    <p className="whitespace-nowrap text-[12px] font-semibold text-text-primary">
                      {stamp(latestScan.capturedAt)}
                    </p>
                  </div>
                </div>

                <div className="mt-3 rounded-xl bg-background-alt/60 p-3">
                  {/* Chỉ số pH (Figma: "Chỉ số pH nước tiểu" · "pH 6.8 (…)"): nhãn dải đã nằm
                      ở badge phía trên nên ô giá trị chỉ còn con số — không lặp nhãn cho vỡ dòng. */}
                  <div className="flex items-center justify-between gap-3">
                    <span className="flex min-w-0 items-center gap-1.5 text-[12px] leading-4 text-text-secondary">
                      <Droplet size={14} className="shrink-0 text-primary" aria-hidden="true" />
                      <span className="truncate">{t("dashboard.phReadoutLabel", { ns: "common" })}</span>
                    </span>
                    <span
                      className={cn(
                        "shrink-0 whitespace-nowrap rounded-lg bg-surface px-2.5 py-1 text-[15px] font-bold leading-5",
                        phTokenStyle(latestBand?.colorToken ?? "color-ph-unknown").text,
                      )}
                    >
                      {t("dashboardWeb.phBig", {
                        ns: "common",
                        value: latestScan.phValue?.toFixed(1) ?? t("dashboardWeb.emptyCell", { ns: "common" }),
                      })}
                    </span>
                  </div>

                  {bands ? (
                    <div className="pt-3">
                      {/* ponytail: Figma chỉ có MỘT hàng nhãn thang (đã kèm cận dưới/trên trong
                          chữ), còn `PhGaugeBar` luôn tự in thêm hàng số min/max -> ẩn hàng cuối
                          bằng variant con. Nâng cấp: thêm prop `showBounds` cho `entities/ph-bands`. */}
                      <PhGaugeBar bands={bands} value={latestScan.phValue} className="[&>div:last-child]:hidden" />
                      {scale ? (
                        <div className="flex items-center justify-between gap-2 pt-1.5 text-[10px] text-text-secondary">
                          <span className="whitespace-nowrap">
                            {t("dashboard.scaleAcid", { ns: "common", value: scale.min.toFixed(1) })}
                          </span>
                          <span className="whitespace-nowrap font-semibold text-success-text">
                            {t("dashboard.scaleIdeal", {
                              ns: "common",
                              min: formatRangeBound(scale.normal?.phMin, scale.min),
                              max: formatRangeBound(scale.normal?.phMax, scale.max),
                            })}
                          </span>
                          <span className="whitespace-nowrap">
                            {t("dashboard.scaleAlkaline", { ns: "common", value: scale.max.toFixed(1) })}
                          </span>
                        </div>
                      ) : null}
                    </div>
                  ) : null}
                </div>

                {/* Câu nhận xét — LẤY TỪ API (`band.description`), không viết lại ở FE. */}
                {bandSentence(latestBand, latestScan.phValue) !== null ? (
                  <p className="mt-3 flex gap-2 text-[14px] leading-5 text-text-secondary">
                    <Activity size={15} className="mt-0.5 shrink-0 text-primary" aria-hidden="true" />
                    <span>{bandSentence(latestBand, latestScan.phValue)}</span>
                  </p>
                ) : null}

                <div className="mt-3 flex items-center justify-between gap-2">
                  <span className="flex items-center gap-0.5 text-[13px] font-semibold text-primary-dark">
                    {t("dashboard.viewDetail", { ns: "common" })}
                    <ChevronRight size={14} aria-hidden="true" />
                  </span>
                  {latestScan.confidenceBand !== null ? (
                    <span className="text-[11px] text-text-secondary">
                      {t("dashboard.confidence", {
                        ns: "common",
                        value: t(CONFIDENCE_LABEL_KEY[latestScan.confidenceBand], { ns: "common" }),
                      })}
                    </span>
                  ) : null}
                </div>
              </Link>
            ) : (
              <div className="mt-2 rounded-2xl bg-surface p-4 text-center shadow-[0px_4px_16px_-2px_rgba(47,79,178,0.06)]">
                <p className="text-caption font-semibold text-text-primary">
                  {t("dashboard.noScanTitle", { ns: "common" })}
                </p>
                <p className="pt-1 text-[11px] text-text-secondary">{t("dashboard.noScanBody", { ns: "common" })}</p>
              </div>
            )}
          </section>
        ) : null}

        {/* Section 4 — Lịch kiểm tra tiếp theo (nguồn: `GET /reminders?active=true`) */}
        {!isGuest ? (
          <div className="mt-4 flex items-center gap-3 rounded-2xl bg-warning-bg px-3.5 py-3">
            <span className="flex size-11 shrink-0 items-center justify-center rounded-full bg-secondary text-secondary-text-on">
              <CalendarClock size={20} aria-hidden="true" />
            </span>
            <div className="min-w-0 flex-1">
              <p className="text-[16px] font-semibold leading-6 text-text-primary">
                {t("dashboard.reminderTitle", { ns: "common" })}
              </p>
              <p className="text-[12px] leading-4 text-text-secondary">{reminderLine(nextReminder)}</p>
            </div>
            <Link
              to="/reminders"
              className="flex min-h-11 shrink-0 items-center rounded-lg bg-surface px-3 text-[13px] font-semibold text-primary-dark shadow-xs"
            >
              {t("dashboard.reminderManage", { ns: "common" })}
            </Link>
          </div>
        ) : null}

        {/* Dấu hiệu theo dõi chưa xác nhận (G1/G3) */}
        <HealthFlagDisclosure count={unacknowledgedFlagCount} enabled={!isGuest} className="mt-3" />

        {/* Section 5 — Thói quen chăm sóc trong ngày (F7 `GET /care-tips`) */}
        {careTip ? (
          <div className="mt-3 flex flex-col gap-1 rounded-2xl bg-surface p-3.5 shadow-[0px_4px_16px_-2px_rgba(47,79,178,0.06)]">
            <p className="flex items-center gap-1 text-[11px] font-semibold text-secondary-text-on">
              <Lightbulb size={12} className="shrink-0" aria-hidden="true" />
              {t("dashboard.careTipLabel", { ns: "common" })}
            </p>
            <p className="text-[15px] font-semibold leading-5 text-text-primary">{careTip.title}</p>
            {careTip.summary ? (
              <p className="text-[12px] leading-4 text-text-secondary">{careTip.summary}</p>
            ) : null}
          </div>
        ) : null}

        {/* Footer — miễn trừ y tế */}
        <div className="flex justify-center pt-5">
          <p className="flex max-w-[358px] items-center gap-2 rounded-3xl bg-info px-3 py-1.5 text-[11px] leading-4 tracking-[0.4px] text-info-text">
            <ShieldCheck size={15} className="shrink-0" aria-hidden="true" />
            {t("dashboard.disclaimer", { ns: "common" })}
          </p>
        </div>
      </div>

      {/* ============================ DESKTOP (Figma 16:7086) ============================ */}
      <div className="hidden lg:block">
        {loadError}

        {/* Section: Welcome & Primary Callouts (16:7089) — chiếm trọn bề ngang */}
        <section className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between xl:gap-6">
          <div className="min-w-0">
            <div className="flex items-center gap-4">
              <span className="rounded bg-secondary/30 px-2.5 py-0.5 text-[11px] font-bold tracking-[0.4px] text-secondary-text-on">
                {t("dashboardWeb.monitorBadge", { ns: "common" })}
              </span>
              {/* Mốc đồng bộ = lúc `GET /scans` trả về lần cuối, không phải giờ hiện tại. */}
              {historyQuery.dataUpdatedAt > 0 ? (
                <span className="flex items-center gap-2 text-[11px] text-text-secondary">
                  <span className="size-1.5 rounded-full bg-success" />
                  {t("dashboardWeb.syncedAt", {
                    ns: "common",
                    time: formatDate(historyQuery.dataUpdatedAt, "HH:mm"),
                  })}
                </span>
              ) : null}
            </div>
            <h1 className="flex items-center gap-2 pt-2 text-[30px] font-bold leading-10 tracking-[-0.75px] text-text-primary">
              <span className="min-w-0 break-words">{t("dashboardWeb.greeting", { ns: "common", name: displayName })}</span>
              <PawPrint size={24} className="shrink-0 text-primary-dark" aria-hidden="true" />
            </h1>
            <p className="max-w-[680px] pt-1 text-[15px] leading-6 text-text-secondary">{introText}</p>
          </div>
          <div className="flex shrink-0 items-center gap-3 xl:pt-6">
            <Link
              to="/scan/select-cat"
              className="flex h-16 items-center gap-2 rounded-2xl bg-primary-dark px-5 text-[14px] font-bold text-white shadow-[0px_4px_6px_-1px_rgba(13,54,154,0.2)] hover:bg-primary"
            >
              <Camera size={18} aria-hidden="true" />
              <span className="max-w-[88px] text-left leading-5">{t("dashboardWeb.ctaScan", { ns: "common" })}</span>
            </Link>
            <Link
              to="/cats/new"
              className="flex h-16 items-center gap-2 rounded-2xl bg-surface px-5 text-[14px] font-bold text-primary-dark shadow-xs hover:bg-background-alt"
            >
              <Plus size={18} aria-hidden="true" />
              <span className="max-w-[72px] text-left leading-5">{t("dashboardWeb.ctaAddCat", { ns: "common" })}</span>
            </Link>
          </div>
        </section>

        {/* Lưới 12 cột: 8/12 nội dung chính + 4/12 cột phải (bắt đầu NGAY từ dải mèo). */}
        <section className="mt-6 grid grid-cols-12 items-start gap-6">
          {/* `< xl` (1024–1279px) cột 8/12 quá hẹp cho thẻ mèo và bảng nhật ký: xếp một cột,
              cột phải xuống dưới thành lưới 2 cột. */}
          <div className="col-span-12 flex flex-col gap-6 xl:col-span-8">
            {/* Dải chuyển mèo (16:7113) */}
            {catsLoading ? (
              <div className="grid grid-cols-2 gap-4" aria-hidden="true">
                <div className="h-[132px] animate-pulse rounded-2xl bg-chip-bg/60" />
                <div className="h-[132px] animate-pulse rounded-2xl bg-chip-bg/60" />
              </div>
            ) : cats.length > 0 ? (
              <div className="grid grid-cols-2 gap-4">
                {cats.slice(0, 2).map((cat) => {
                  const band = catBand(cat);
                  const style = phTokenStyle(band?.colorToken ?? "color-ph-unknown");
                  const scan = latestByCat.get(cat.id);
                  const scannedAt = catScannedAt(cat);
                  return (
                    <Link
                      key={cat.id}
                      to={`/cats/${cat.id}`}
                      className={cn(
                        "flex flex-col rounded-2xl bg-surface p-4 shadow-xs hover:shadow-brand-md",
                        cat.isPrimary && "outline outline-2 outline-primary-dark",
                      )}
                    >
                      <div className="mb-3 flex items-start gap-3">
                        <span className="relative size-12 shrink-0">
                          <CatAvatar src={cat.avatarUrl} name={cat.name} size="md" className="size-12" />
                          {band ? (
                            <span
                              aria-hidden="true"
                              className={cn(
                                "absolute bottom-0 right-0 size-3.5 rounded-full border-2 border-surface",
                                style.solid,
                              )}
                            />
                          ) : null}
                        </span>
                        <div className="min-w-0 flex-1">
                          <p className="flex items-center gap-1 text-[17px] font-bold leading-6 text-text-primary">
                            <span className="truncate">{cat.name}</span>
                            {cat.isPrimary ? (
                              <BadgeCheck
                                size={15}
                                className="shrink-0 text-primary-dark"
                                aria-label={t("dashboard.primaryCat", { ns: "common" })}
                              />
                            ) : null}
                          </p>
                          <p className="text-[12px] leading-4 text-text-secondary">{catMeta(cat)}</p>
                        </div>
                        {/* Vị trí chip trạng thái của Figma: con số pH thật của lần quét gần
                            nhất (ngắn, một dòng), màu theo dải. Nhãn dải dài nằm ở hàng dưới. */}
                        {scan?.phValue != null ? (
                          <span
                            className={cn(
                              "shrink-0 whitespace-nowrap rounded-full px-2.5 py-1 text-[12px] font-bold leading-4",
                              style.bg,
                              style.text,
                            )}
                          >
                            {t("dashboardWeb.phValue", { ns: "common", value: scan.phValue.toFixed(1) })}
                          </span>
                        ) : null}
                      </div>
                      <div className="mt-auto flex flex-wrap items-center justify-between gap-x-3 gap-y-1 border-t border-border pt-3">
                        <span
                          className={cn(
                            "flex min-w-0 items-center gap-1.5 text-[12px] font-semibold leading-4",
                            band ? style.text : "text-text-secondary",
                          )}
                        >
                          <span
                            aria-hidden="true"
                            className={cn("size-2 shrink-0 rounded-full", scan?.thumbnailHex == null && style.solid)}
                            style={scan?.thumbnailHex != null ? { backgroundColor: scan.thumbnailHex } : undefined}
                          />
                          <span className="truncate">{band?.label ?? t("dashboard.catNoScan", { ns: "common" })}</span>
                        </span>
                        {scannedAt ? (
                          <span className="shrink-0 whitespace-nowrap text-[12px] leading-4 text-text-secondary">
                            {t("dashboardWeb.scannedAt", { ns: "common", time: stamp(scannedAt) })}
                          </span>
                        ) : null}
                      </div>
                    </Link>
                  );
                })}
              </div>
            ) : !isGuest ? (
              <div className="rounded-2xl bg-surface p-6 text-center shadow-xs">
                <p className="text-caption font-bold text-text-primary">
                  {t("dashboardWeb.catsEmptyTitle", { ns: "common" })}
                </p>
                <p className="pt-1 text-[12px] text-text-secondary">
                  {t("dashboardWeb.catsEmptyBody", { ns: "common" })}
                </p>
              </div>
            ) : null}

            {/* Widget: Chỉ thị màu hạt cát & Nồng độ pH (lần quét gần nhất) */}
            <article className="rounded-2xl bg-surface p-6 shadow-xs">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="text-overline tracking-[0.6px] text-primary">
                    {t("dashboardWeb.colorimetryLabel", { ns: "common" })}
                  </p>
                  <h2 className="pt-1 text-[20px] font-bold leading-7 text-text-primary">
                    {t("dashboardWeb.colorimetryTitle", { ns: "common" })}
                  </h2>
                </div>
                {latestScan?.confidenceBand != null ? (
                  <span className="flex shrink-0 items-center gap-1.5 whitespace-nowrap rounded-full bg-background-alt px-3 py-1.5 text-[12px] font-semibold text-text-secondary">
                    <BadgeCheck size={14} className="shrink-0 text-success" aria-hidden="true" />
                    {t("dashboardWeb.confidenceLabel", { ns: "common" })}:{" "}
                    {t(CONFIDENCE_LABEL_KEY[latestScan.confidenceBand], { ns: "common" })}
                  </span>
                ) : null}
              </div>

              {scansLoading ? (
                <div aria-hidden="true" className="mt-4 h-[150px] animate-pulse rounded-xl bg-chip-bg/60" />
              ) : latestScan ? (
                <div className="mt-4 flex gap-5 rounded-xl bg-background-alt/60 p-4">
                  {/* `GET /scans` KHÔNG trả URL ảnh — chỉ `thumbnailHex` (màu đo được của vùng
                      hạt). Có màu thì vẽ đúng ô màu đó thay cho ảnh hạt cát của Figma; lần quét
                      nhập tay không có màu thì không vẽ ô trống. */}
                  {latestScan.thumbnailHex != null ? (
                    <div
                      className="relative min-h-[150px] w-[170px] shrink-0 self-stretch overflow-hidden rounded-xl border border-border"
                      style={{ backgroundColor: latestScan.thumbnailHex }}
                    >
                      <span className="absolute bottom-1.5 left-1.5 rounded bg-surface/90 px-1.5 py-0.5 text-[11px] font-semibold text-text-primary">
                        {t("dashboardWeb.measuredSwatch", { ns: "common" })}
                      </span>
                    </div>
                  ) : null}
                  <div className="min-w-0 flex-1">
                    <div className="flex items-start justify-between gap-4">
                      <div className="min-w-0">
                        <p className="text-[12px] text-text-secondary">{t("dashboardWeb.extracted", { ns: "common" })}</p>
                        <p className="flex flex-wrap items-center gap-x-3 gap-y-1 pt-0.5">
                          <span className="text-[32px] font-bold leading-9 text-primary-dark">
                            {t("dashboardWeb.phBig", {
                              ns: "common",
                              value: latestScan.phValue?.toFixed(1) ?? t("dashboardWeb.emptyCell", { ns: "common" }),
                            })}
                          </span>
                          {latestBand ? (
                            <PhBadge
                              band={latestBand}
                              className="whitespace-nowrap px-2.5 py-1 text-[12px] leading-4 [&>svg]:size-3.5"
                            />
                          ) : null}
                        </p>
                      </div>
                      <div className="shrink-0 text-right">
                        <p className="text-[12px] text-text-secondary">
                          {t("dashboardWeb.capturedLabel", { ns: "common" })}
                        </p>
                        <p className="text-[14px] font-bold leading-5 text-text-primary">{stamp(latestScan.capturedAt)}</p>
                        {latestScan.catName ? (
                          <p className="text-[12px] leading-4 text-text-secondary">
                            {t("dashboard.sessionOf", { ns: "common", name: latestScan.catName })}
                          </p>
                        ) : null}
                      </div>
                    </div>

                    {bands ? (
                      <div className="pt-4">
                        <PhGaugeBar bands={bands} value={latestScan.phValue} className="[&>div:last-child]:hidden" />
                        {scale ? (
                          <div className="flex justify-between gap-2 pt-1.5 text-[11px] text-text-secondary">
                            <span>{t("dashboardWeb.scaleAcid", { ns: "common", value: scale.min.toFixed(1) })}</span>
                            <span className="font-semibold text-success-text">
                              {t("dashboardWeb.scaleSafe", {
                                ns: "common",
                                min: formatRangeBound(scale.normal?.phMin, scale.min),
                                max: formatRangeBound(scale.normal?.phMax, scale.max),
                              })}
                            </span>
                            <span>
                              {t("dashboardWeb.scaleAlkaline", { ns: "common", value: scale.max.toFixed(1) })}
                            </span>
                          </div>
                        ) : null}
                      </div>
                    ) : null}

                    {/* Nhận xét lấy từ API — KHÔNG chép câu "…hồng cầu" của Figma (QĐ #8). */}
                    {bandSentence(latestBand, latestScan.phValue) !== null ? (
                      <p className="pt-3 text-[14px] leading-5 text-text-secondary">
                        {bandSentence(latestBand, latestScan.phValue)}
                      </p>
                    ) : null}
                  </div>
                </div>
              ) : (
                <div className="mt-4 rounded-xl bg-background-alt/60 p-8 text-center">
                  <p className="text-caption font-bold text-text-primary">
                    {isGuest
                      ? t("dashboard.guestBanner.title", { ns: "common" })
                      : t("dashboardWeb.scanEmptyTitle", { ns: "common" })}
                  </p>
                  <p className="pt-1 text-[12px] text-text-secondary">
                    {isGuest
                      ? t("dashboard.guestBanner.body", { ns: "common" })
                      : t("dashboardWeb.scanEmptyBody", { ns: "common" })}
                  </p>
                </div>
              )}
            </article>

            {/* Widget: Xu hướng pH (mèo chính) */}
            {primaryCat ? (
              <article className="rounded-2xl bg-surface p-6 shadow-xs">
                <div className="flex items-start justify-between gap-4">
                  <h2 className="text-[20px] font-bold leading-7 text-text-primary">
                    {t("dashboardWeb.trendTitle", {
                      ns: "common",
                      days: TREND_RANGE_DAYS[range],
                      name: primaryCat.name,
                    })}
                  </h2>
                  <div
                    className="flex shrink-0 gap-1 rounded-lg bg-background-alt p-1 text-[12px] font-semibold"
                    role="group"
                  >
                    {WEB_RANGES.map((r) => (
                      <button
                        key={r}
                        type="button"
                        aria-pressed={r === range}
                        onClick={() => {
                          setRange(r);
                        }}
                        className={cn(
                          "rounded-md px-3 py-1.5",
                          r === range ? "bg-surface text-primary-dark shadow-xs" : "text-text-secondary",
                        )}
                      >
                        {t(`dashboardWeb.range${String(TREND_RANGE_DAYS[r])}`, { ns: "common" })}
                      </button>
                    ))}
                  </div>
                </div>

                {scale && trendPoints.length > 1 ? (
                  <TrendChart
                    points={trendPoints}
                    domainMin={scale.min}
                    domainMax={scale.max}
                    safeMin={scale.normal?.phMin ?? scale.min}
                    safeMax={scale.normal?.phMax ?? scale.max}
                    safeLabel={t("dashboardWeb.safeBand", {
                      ns: "common",
                      min: formatRangeBound(scale.normal?.phMin, scale.min),
                      max: formatRangeBound(scale.normal?.phMax, scale.max),
                    })}
                    axisLabel={(v) => t("dashboardWeb.phAxis", { ns: "common", value: v.toFixed(1) })}
                    dateLabel={(iso) => formatDate(iso, "dd MMM")}
                    todayLabel={t("dashboardWeb.today", { ns: "common" })}
                  />
                ) : (
                  <p className="mt-6 rounded-xl bg-background-alt/60 px-4 py-10 text-center text-[13px] text-text-secondary">
                    {t("dashboardWeb.trendEmpty", { ns: "common" })}
                  </p>
                )}

                <div className="mt-4 grid grid-cols-3 gap-3">
                  {[
                    {
                      label: t("dashboardWeb.statAvg", { ns: "common" }),
                      value: trendStats
                        ? t("dashboardWeb.statAvgValue", { ns: "common", value: trendStats.avg })
                        : t("dashboardWeb.emptyCell", { ns: "common" }),
                    },
                    {
                      label: t("dashboardWeb.statDeviation", { ns: "common" }),
                      value: trendStats
                        ? t("dashboardWeb.statDeviationValue", { ns: "common", value: trendStats.deviation })
                        : t("dashboardWeb.emptyCell", { ns: "common" }),
                    },
                    {
                      label: t("dashboardWeb.statFrequency", { ns: "common" }),
                      value: trendStats
                        ? t("dashboardWeb.statFrequencyValue", { ns: "common", value: trendStats.frequency })
                        : t("dashboardWeb.emptyCell", { ns: "common" }),
                    },
                  ].map((s) => (
                    <div key={s.label} className="rounded-xl bg-background-alt/60 px-4 py-3 text-center">
                      <p className="text-[12px] text-text-secondary">{s.label}</p>
                      <p className="pt-0.5 text-[18px] font-bold leading-7 text-text-primary">{s.value}</p>
                    </div>
                  ))}
                </div>
              </article>
            ) : null}

            {/* Widget: Nhật ký 5 lần quét gần nhất */}
            {!isGuest ? (
              <article className="rounded-2xl bg-surface p-6 shadow-xs">
                <div className="flex items-center justify-between gap-4">
                  <h2 className="text-[20px] font-bold leading-7 text-text-primary">
                    {t("dashboardWeb.logTitle", { ns: "common" })}
                  </h2>
                  {scans.length > 0 ? (
                    <Link
                      to="/history"
                      className="shrink-0 py-2 text-[12px] font-semibold text-primary-dark hover:underline"
                    >
                      {t("dashboardWeb.logViewAll", { ns: "common", count: totalScanCount })} →
                    </Link>
                  ) : null}
                </div>
                {scansLoading ? (
                  <div aria-hidden="true" className="mt-4 h-[240px] animate-pulse rounded-xl bg-chip-bg/60" />
                ) : scans.length > 0 ? (
                  <table className="mt-3 w-full text-left">
                    <thead>
                      <tr className="border-b border-border text-overline tracking-[0.4px] text-text-secondary">
                        <th className="w-[28%] pb-2.5 font-semibold">{t("dashboardWeb.colTime", { ns: "common" })}</th>
                        {/* Cột màu CHỈ tồn tại khi ít nhất một lần quét thật sự đo được màu. */}
                        {recentHasColor ? (
                          <th className="w-[24%] pb-2.5 font-semibold">{t("dashboardWeb.colColor", { ns: "common" })}</th>
                        ) : null}
                        <th className="w-[12%] pb-2.5 font-semibold">{t("dashboardWeb.colPh", { ns: "common" })}</th>
                        <th className="pb-2.5 font-semibold">{t("dashboardWeb.colAi", { ns: "common" })}</th>
                        <th className="whitespace-nowrap pb-2.5 text-right font-semibold">
                          {t("dashboardWeb.colDetail", { ns: "common" })}
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {recentScans.map((scan) => {
                        const band = bands?.find((b) => b.code === scan.bandCode);
                        const style = phTokenStyle(band?.colorToken ?? "color-ph-unknown");
                        return (
                          <tr key={scan.scanId} className="border-b border-border/60 last:border-0">
                            <td className="py-2 text-[14px] font-semibold text-text-primary">
                              {stamp(scan.capturedAt)}
                              {scan.catName ? (
                                <span className="block text-[12px] font-normal leading-4 text-text-secondary">
                                  {scan.catName}
                                </span>
                              ) : null}
                            </td>
                            {/* Ô màu THẬT từ `thumbnailHex` (E2). Chưa có endpoint trả TÊN màu nên
                                hiển thị mã màu thay vì bịa tên. */}
                            {recentHasColor ? (
                              <td className="py-2 text-[14px] text-text-secondary">
                                {scan.thumbnailHex != null ? (
                                  <span className="flex items-center gap-2">
                                    <span
                                      aria-hidden="true"
                                      className="size-3 shrink-0 rounded-full border border-border"
                                      style={{ backgroundColor: scan.thumbnailHex }}
                                    />
                                    {scan.thumbnailHex}
                                  </span>
                                ) : null}
                              </td>
                            ) : null}
                            <td className="py-2 text-[18px] font-bold text-primary-dark">
                              {scan.phValue?.toFixed(1) ?? t("dashboardWeb.emptyCell", { ns: "common" })}
                            </td>
                            <td className="py-2">
                              {band ? (
                                <span
                                  className={cn(
                                    "inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-2.5 py-0.5 text-[12px] font-semibold leading-5",
                                    style.bg,
                                    style.text,
                                  )}
                                >
                                  <span aria-hidden="true" className={cn("size-1.5 shrink-0 rounded-full", style.solid)} />
                                  {band.label}
                                </span>
                              ) : null}
                            </td>
                            <td className="py-2 text-right">
                              <Link
                                to={`/scans/${scan.scanId}`}
                                aria-label={t("dashboardWeb.colDetail", { ns: "common" })}
                                className="inline-flex size-11 items-center justify-center rounded-lg text-text-tertiary hover:bg-background-alt hover:text-primary-dark"
                              >
                                <Eye size={18} aria-hidden="true" />
                              </Link>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                ) : (
                  <p className="mt-4 rounded-xl bg-background-alt/60 px-4 py-8 text-center text-[13px] text-text-secondary">
                    {t("dashboardWeb.logEmpty", { ns: "common" })}
                  </p>
                )}
              </article>
            ) : null}
          </div>

          {/* Cột phải (4/12) */}
          <aside className="col-span-12 grid grid-cols-2 items-start gap-6 xl:col-span-4 xl:flex xl:flex-col">
            {/* Lối tắt — ba lối tắt đều trỏ tới route có thật, cùng chiều cao 56px. */}
            <article className="rounded-2xl bg-surface p-5 shadow-xs">
              <p className="text-overline tracking-[0.5px] text-text-secondary">
                {t("dashboardWeb.shortcutsLabel", { ns: "common" })}
              </p>
              <h3 className="pt-1.5 text-[18px] font-bold leading-6 text-text-primary">
                {t("dashboardWeb.shortcutsTitle", { ns: "common" })}
              </h3>
              <ul className="flex flex-col gap-2 pt-3">
                {[
                  {
                    to: "/cats/new",
                    icon: PawPrint,
                    title: t("dashboardWeb.shortcutAddCat", { ns: "common" }),
                    note: t("dashboardWeb.shortcutAddCatNote", { ns: "common" }),
                  },
                  {
                    to: "/history",
                    icon: Activity,
                    title: t("dashboardWeb.shortcutHistory", { ns: "common" }),
                    note: t("dashboardWeb.shortcutHistoryNote", { ns: "common" }),
                  },
                  {
                    to: "/export",
                    icon: FileText,
                    title: t("dashboardWeb.shortcutExport", { ns: "common" }),
                    note: t("dashboardWeb.shortcutExportNote", { ns: "common" }),
                  },
                ].map((item) => (
                  <li key={item.to}>
                    <Link
                      to={item.to}
                      className="flex min-h-14 items-center gap-3 rounded-xl bg-background-alt/70 p-2.5 hover:bg-chip-bg"
                    >
                      <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-surface text-primary-dark shadow-xs">
                        <item.icon size={18} aria-hidden="true" />
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-[14px] font-bold leading-5 text-text-primary">
                          {item.title}
                        </span>
                        <span className="block truncate text-[12px] leading-4 text-text-secondary">{item.note}</span>
                      </span>
                      <ChevronRight size={16} className="shrink-0 text-text-tertiary" aria-hidden="true" />
                    </Link>
                  </li>
                ))}
              </ul>
            </article>

            {/* LỊCH CHĂM SÓC (nguồn: `GET /reminders?active=true`) */}
            {!isGuest ? (
              <article className="rounded-2xl bg-surface p-5 shadow-xs">
                <div className="flex items-center justify-between gap-2">
                  <p className="text-overline tracking-[0.5px] text-text-secondary">
                    {t("dashboardWeb.scheduleLabel", { ns: "common" })}
                  </p>
                  <CalendarDays size={18} className="shrink-0 text-primary-dark" aria-hidden="true" />
                </div>
                <h3 className="pt-1.5 text-[18px] font-bold leading-6 text-text-primary">
                  {t("dashboardWeb.scheduleTitle", { ns: "common" })}
                </h3>
                {reminders.length > 0 ? (
                  <ul className="flex flex-col gap-2 pt-3">
                    {reminders.slice(0, 2).map((reminder) => {
                      const overdue = Date.parse(reminder.nextRunAt) < now;
                      return (
                        <li key={reminder.id}>
                          <Link
                            to={`/reminders/${reminder.id}`}
                            className="flex items-center gap-3 rounded-xl bg-background-alt/70 p-2.5 hover:bg-chip-bg"
                          >
                            <span
                              className={cn(
                                "flex size-11 shrink-0 flex-col items-center justify-center rounded-lg",
                                overdue ? "bg-danger-bg text-danger-text" : "bg-secondary text-secondary-text-on",
                              )}
                            >
                              <span className="text-[9px] font-bold uppercase leading-none">
                                {formatDate(reminder.nextRunAt, "EEE")}
                              </span>
                              <span className="pt-0.5 text-[15px] font-bold leading-none">
                                {formatDate(reminder.nextRunAt, "dd")}
                              </span>
                            </span>
                            <span className="min-w-0 flex-1">
                              <span className="block text-[14px] font-bold leading-5 text-text-primary">
                                {reminderTitle(reminder)}
                              </span>
                              <span
                                className={cn(
                                  "block text-[12px] leading-4",
                                  overdue ? "text-danger-text" : "text-text-secondary",
                                )}
                              >
                                {reminderWhen(reminder)}
                              </span>
                            </span>
                          </Link>
                        </li>
                      );
                    })}
                  </ul>
                ) : (
                  <p className="mt-3 rounded-xl bg-background-alt/70 p-3 text-[13px] text-text-secondary">
                    {t("dashboardWeb.scheduleEmpty", { ns: "common" })}
                  </p>
                )}
                <Link
                  to="/reminders"
                  className="mt-3 flex min-h-11 items-center justify-center rounded-xl bg-chip-bg/70 text-[13px] font-semibold text-primary-dark hover:bg-chip-bg"
                >
                  {t("dashboardWeb.scheduleCta", { ns: "common" })}
                </Link>
              </article>
            ) : null}

            {/* Mẹo chăm sóc — F7 `GET /care-tips`. Thẻ "LỜI KHUYÊN BÁC SĨ FELINE" của Figma bỏ:
                lời khuyên y tế viết cứng kèm ngưỡng pH hard-code. */}
            {careTips && careTips.length > 0 ? (
              <article className="rounded-2xl border-l-4 border-primary-dark bg-info/60 p-5">
                <p className="flex items-center gap-2 text-overline tracking-[0.5px] text-primary-dark">
                  <Lightbulb size={14} className="shrink-0" aria-hidden="true" />
                  {t("dashboard.careTipLabel", { ns: "common" })}
                </p>
                <ul className="flex flex-col gap-3 pt-2">
                  {careTips.map((tip) => (
                    <li key={tip.id} className="flex flex-col gap-0.5">
                      <span className="text-[15px] font-bold leading-6 text-text-primary">{tip.title}</span>
                      {tip.summary ? (
                        <span className="text-[13px] leading-5 text-text-secondary">{tip.summary}</span>
                      ) : null}
                    </li>
                  ))}
                </ul>
              </article>
            ) : null}

            <p className="col-span-2 flex items-start gap-2 rounded-xl bg-chip-bg/60 px-4 py-3 text-[12px] leading-4 text-info-text">
              <ShieldCheck size={15} className="mt-px shrink-0" aria-hidden="true" />
              {t("dashboard.disclaimer", { ns: "common" })}
            </p>
          </aside>
        </section>
      </div>
    </>
  );
}

interface TrendChartProps {
  points: { date: string; value: number }[];
  domainMin: number;
  domainMax: number;
  safeMin: number;
  safeMax: number;
  safeLabel: string;
  axisLabel: (value: number) => string;
  dateLabel: (iso: string) => string;
  todayLabel: string;
}

const CHART_W = 600;
const CHART_H = 170;
/** Lề trong vùng vẽ (đơn vị viewBox = px vì cao 170 vẽ ra đúng 170px) để chấm ở mép không bị cắt nửa. */
const CHART_PAD_X = 10;
const CHART_PAD_Y = 10;

/**
 * Biểu đồ xu hướng pH — SVG thuần (recharts bị eslint giới hạn trong `features/trends` và
 * `features/export`). Thang đứng lấy từ `bands` qua props, KHÔNG hard-code ngưỡng; nếu điểm đo
 * vượt ra ngoài thang thì thang nới theo dữ liệu thay vì kẹp điểm vào mép.
 */
function TrendChart({
  points,
  domainMin,
  domainMax,
  safeMin,
  safeMax,
  safeLabel,
  axisLabel,
  dateLabel,
  todayLabel,
}: TrendChartProps) {
  const values = points.map((p) => p.value);
  const lo = Math.min(domainMin, ...values);
  const hi = Math.max(domainMax, ...values);
  const span = hi - lo || 1;
  const plotH = CHART_H - CHART_PAD_Y * 2;
  const y = (v: number) => CHART_PAD_Y + plotH - ((v - lo) / span) * plotH;
  const step = points.length > 1 ? (CHART_W - CHART_PAD_X * 2) / (points.length - 1) : 0;
  const x = (i: number) => CHART_PAD_X + i * step;
  // Catmull-Rom -> Bézier bậc 3: Figma vẽ đường trơn, `polyline` cho ra đường gấp khúc.
  const coords = points.map((p, i) => ({ x: x(i), y: y(p.value) }));
  const at = (i: number) => coords[Math.min(coords.length - 1, Math.max(0, i))];
  const line = coords
    .slice(1)
    .map((_, i) => {
      const [p0, p1, p2, p3] = [at(i - 1), at(i), at(i + 1), at(i + 2)];
      const c1 = { x: p1.x + (p2.x - p0.x) / 6, y: p1.y + (p2.y - p0.y) / 6 };
      const c2 = { x: p2.x - (p3.x - p1.x) / 6, y: p2.y - (p3.y - p1.y) / 6 };
      return `C ${String(c1.x)} ${String(c1.y)} ${String(c2.x)} ${String(c2.y)} ${String(p2.x)} ${String(p2.y)}`;
    })
    .join(" ");
  const path = `M ${String(coords[0].x)} ${String(coords[0].y)} ${line}`;
  const ticks = [hi, (lo + hi) / 2, lo];

  // Tối đa 6 nhãn trục hoành, luôn giữ điểm đầu và điểm cuối. "Hôm nay" chỉ khi điểm cuối
  // thật sự là hôm nay — trước đây điểm cuối luôn bị gắn "Hôm nay" kể cả khi là hôm qua.
  const labelCount = Math.min(6, points.length);
  const xLabels = Array.from({ length: labelCount }, (_, i) => {
    const index = labelCount === 1 ? 0 : Math.round((i * (points.length - 1)) / (labelCount - 1));
    return { index, iso: points[index].date };
  }).filter((l, i, all) => {
    // Nhiều lần quét cùng ngày cho nhãn trùng nhau: chỉ giữ nhãn cuối của mỗi ngày.
    const next = all[i + 1] as (typeof all)[number] | undefined;
    return !next || dateLabel(next.iso) !== dateLabel(l.iso);
  });

  return (
    <div className="mt-4 flex flex-col gap-2">
      {/* Chú giải vùng tham chiếu đặt riêng phía trên, không đè lên đường dữ liệu. */}
      <p className="flex items-center gap-2 text-[12px] font-semibold text-success-text">
        <span aria-hidden="true" className="size-3 shrink-0 rounded-sm bg-success-bg ring-1 ring-success/40" />
        {safeLabel}
      </p>
      <div className="flex gap-3">
        <div className="relative h-[170px] w-14 shrink-0 text-right text-[12px] leading-none text-text-tertiary">
          {ticks.map((v) => (
            <span key={v} className="absolute right-0 -translate-y-1/2 whitespace-nowrap" style={{ top: y(v) }}>
              {axisLabel(v)}
            </span>
          ))}
        </div>
        <div className="min-w-0 flex-1">
          <svg
            viewBox={`0 0 ${String(CHART_W)} ${String(CHART_H)}`}
            preserveAspectRatio="none"
            className="h-[170px] w-full overflow-visible"
            role="img"
            aria-label={safeLabel}
          >
            {ticks.map((v) => (
              <line
                key={v}
                x1="0"
                x2={CHART_W}
                y1={y(v)}
                y2={y(v)}
                className="stroke-border"
                strokeWidth="1"
                strokeDasharray="4 4"
                vectorEffect="non-scaling-stroke"
              />
            ))}
            <rect
              x="0"
              y={y(safeMax)}
              width={CHART_W}
              height={Math.max(0, y(safeMin) - y(safeMax))}
              rx="10"
              className="fill-success-bg"
            />
            <path
              d={path}
              className="fill-none stroke-primary-dark"
              strokeWidth="2.5"
              strokeLinejoin="round"
              vectorEffect="non-scaling-stroke"
            />
            {points.map((p, i) => (
              <circle
                key={`${p.date}-${String(i)}`}
                cx={x(i)}
                cy={y(p.value)}
                r="4"
                className={cn("stroke-primary-dark", i === points.length - 1 ? "fill-primary-dark" : "fill-surface")}
                strokeWidth="2.5"
              />
            ))}
          </svg>
          <div className="flex justify-between pt-1.5 text-[12px] text-text-tertiary">
            {xLabels.map((l, i) => {
              const last = i === xLabels.length - 1;
              return (
                <span
                  key={`${l.iso}-${String(l.index)}`}
                  className={last ? "font-semibold text-primary-dark" : undefined}
                >
                  {last && isToday(new Date(l.iso)) ? todayLabel : dateLabel(l.iso)}
                </span>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
