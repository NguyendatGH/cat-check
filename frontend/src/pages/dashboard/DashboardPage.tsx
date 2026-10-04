import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import {
  Activity,
  BadgeCheck,
  CalendarClock,
  CalendarDays,
  Camera,
  ChevronRight,
  Droplet,
  Eye,
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
import type { ScanListItem } from "@/entities/scan-result";
import type { Reminder } from "@/features/reminder";
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
 * Chrome (sidebar 288px + header 64px) nằm ở `AppLayout`; `main` đã cấp sẵn padding 24px
 * nên cây desktop dưới đây KHÔNG tự đặt padding ngang.
 *
 * NGUYÊN TẮC DỮ LIỆU: trang này KHÔNG bịa mèo/ảnh/lần quét/chỉ số để ảnh chụp giống Figma.
 * Mọi con số đều đến từ API thật (`/cats`, `/scans`, `/reminders`, `/reference/ph-bands`);
 * không có dữ liệu thì khối tự render empty state. Sau W1-E trang KHÔNG còn hằng
 * `DESIGN_MOCK_*` nào.
 *
 * COPY: quyết định #8 (`context/spec/00-decisions.md`) — "Chỉ số đo: Chỉ pH. Không phát hiện
 * máu." Vì vậy các chuỗi của Figma hứa hẹn phát hiện máu/khoáng chất ("Không phát hiện dấu
 * hiệu … hồng cầu", "Hệ khoáng chất • Không có máu") KHÔNG được chép vào; phần nhận xét lấy
 * thẳng `band.description` do API trả về.
 */

/**
 * Mẹo chăm sóc nay lấy từ **F7 `GET /api/v1/care-tips`** (`useCareTips`) — trước đây là hằng
 * `DESIGN_MOCK_CARE_TIP` (ảnh Figma) cộng hai chuỗi i18n viết cứng. Response chỉ có
 * `title`/`summary`, KHÔNG có ảnh minh hoạ, nên thẻ bỏ hẳn ảnh thay vì gắn ảnh trang trí giả.
 *
 * Hai thẻ "Cơ sở đồng hành" (`DESIGN_MOCK_CLINIC`) và "Kho cát gia đình"
 * (`DESIGN_MOCK_LITTER_STOCK`) đã bỏ: chúng thuộc miền Bản đồ (Phase 2) và Cửa hàng
 * (Phase 3) — `featureFlags.map`/`featureFlags.shop` đang TẮT nên `/map` và `/shop` chỉ ra
 * `ComingSoonPage`; để lại phòng khám và tồn kho bịa trên Trang chủ là mâu thuẫn thẳng với
 * chính hai cờ đó.
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

export function DashboardPage() {
  const { t } = useTranslation(["common", "cat", "scan", "history"]);
  const user = useSessionStore((s) => s.user);
  // Trang chủ mở cho cả khách chưa đăng nhập (route nằm ngoài RequireAuth). Khách KHÔNG gọi
  // `/cats`, `/scans`, `/reminders` — các endpoint này cần phiên, gọi sẽ 403; để `enabled=false`
  // thay vì bắn request rồi nuốt lỗi. `/reference/ph-bands` là endpoint công khai nên vẫn gọi được.
  const isGuest = !user;
  const { data: catsPage } = useCatList("ACTIVE", !isGuest);
  const cats = useMemo(() => catsPage?.items ?? [], [catsPage]);
  const { data: bands } = usePhBands();
  const { data: historyPages } = useScanHistory(undefined, "ALL", !isGuest);
  const scans = useMemo(() => historyPages?.pages[0]?.items ?? [], [historyPages]);
  // "Xem toàn bộ N bản ghi" phải là TỔNG THẬT, không phải số đã tải: `useScanHistory` chỉ trả
  // trang đầu nên `scans.length` dừng ở page size và con số nói dối ngay khi user có nhiều
  // scan hơn một trang. E3 `GET /scans/summary` có sẵn `count`; truyền `!isGuest` để khách
  // không gọi vào một endpoint chắc chắn 403.
  const { data: scanSummary } = useScanSummary(undefined, undefined, undefined, !isGuest);
  const totalScanCount = scanSummary?.count ?? scans.length;
  const { data: reminderList } = useReminders({ active: true }, !isGuest);
  const reminders = useMemo(() => reminderList?.items ?? [], [reminderList]);

  const latestScan = scans.at(0);
  const latestBand = latestScan ? bands?.find((b) => b.code === latestScan.bandCode) : undefined;
  const scale = buildScale(bands);
  const primaryCat = cats.find((c) => c.isPrimary) ?? cats.at(0);
  const nextReminder = reminders.find((r) => r.nextRunAt !== undefined);
  /**
   * Tổng số dấu hiệu chưa xác nhận của cả đàn — `GET /cats` đã trả
   * `unacknowledgedFlagCount` cho từng bé, không cần endpoint đếm riêng. Con số này là NÚT
   * mở danh sách thật (G1 `GET /health-flags`), không phải badge chết.
   */
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
  // D13 `GET /cats/{id}/trends` — CÙNG nguồn với màn `/cats/:catId/trends`. Trước W1-E chỗ
  // này tự tổng hợp từ `GET /scans` nên Trang chủ và màn Xu hướng hiện số khác nhau.
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

  const catMeta = (cat: Cat): string =>
    [
      cat.breedName,
      cat.ageMonths != null
        ? t("dashboard.catMeta", {
            ns: "common",
            age: Math.floor(cat.ageMonths / 12),
            sex: t(`form.sex.${cat.sex === "MALE" ? "male" : cat.sex === "FEMALE" ? "female" : "unknown"}`, {
              ns: "cat",
            }),
          })
        : null,
      cat.weightKg != null ? t("dashboardWeb.weightKg", { ns: "common", value: cat.weightKg }) : null,
    ]
      .filter((part): part is string => typeof part === "string" && part.length > 0)
      .join(" • ");

  const reminderLine = (reminder: Reminder | undefined): string => {
    if (!reminder?.nextRunAt) return t("dashboard.reminderEmpty", { ns: "common" });
    const when = formatRelative(reminder.nextRunAt);
    const cat = reminder.catId != null ? cats.find((c) => c.id === reminder.catId) : undefined;
    return cat
      ? t("dashboard.reminderNext", { ns: "common", when, name: cat.name })
      : t("dashboard.reminderNextNoCat", { ns: "common", when });
  };

  return (
    <>
      {/* Dải báo chế độ khách — hiện ở cả mobile lẫn desktop khi chưa đăng nhập. */}
      {isGuest ? (
        <div className="mx-auto mb-2 flex max-w-[480px] flex-col gap-2 px-4 pt-4 lg:max-w-none lg:flex-row lg:items-center lg:justify-between lg:px-0 lg:pt-0">
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
              className="flex h-9 items-center justify-center rounded-xl bg-primary px-4 text-caption font-semibold text-white"
            >
              {t("dashboard.guestBanner.login", { ns: "common" })}
            </Link>
            <Link
              to="/auth/register"
              className="flex h-9 items-center justify-center rounded-xl bg-surface px-4 text-caption font-semibold text-primary-dark shadow-xs"
            >
              {t("dashboard.guestBanner.register", { ns: "common" })}
            </Link>
          </div>
        </div>
      ) : null}

      {/* ============================ MOBILE (Figma 1:2017) ============================ */}
      <div className="mx-auto max-w-[480px] px-4 pb-6 lg:hidden">
        {/* Section 1 — Hero quét cát. Figma mobile KHÔNG có dòng chào ở trên hero. */}
        <div className="relative mt-4 overflow-hidden rounded-xl bg-primary p-5 text-white shadow-[0px_10px_24px_-6px_rgba(47,79,178,0.28)]">
          <span className="inline-flex items-center gap-1.5 rounded-full bg-white/15 px-3 py-1 text-[11px] tracking-[0.4px] backdrop-blur-sm">
            <FlaskConical size={13} aria-hidden="true" />
            {t("dashboard.heroBadge", { ns: "common" })}
          </span>
          <h3 className="pt-4 text-[20px] font-semibold">{t("dashboard.heroTitle", { ns: "common" })}</h3>
          <p className="max-w-[270px] pt-1 text-caption text-on-primary-muted">
            {t("dashboard.heroBody", { ns: "common" })}
          </p>
          <div className="flex items-center justify-between gap-2 pt-4">
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

        {/* Section 2 — Mèo của bạn */}
        <div className="pt-6">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <h3 className="text-[18px] font-semibold text-text-primary">
                {t("dashboard.yourCats", { ns: "common" })}
              </h3>
              <span className="rounded-full bg-chip-bg px-2 py-0.5 text-[11px] tracking-[0.4px] text-primary-dark">
                {t("dashboard.catsCount", { ns: "common", count: cats.length })}
              </span>
            </div>
            <Link to="/cats" className="text-caption font-medium text-primary-dark">
              {t("dashboard.viewAll", { ns: "common" })}
            </Link>
          </div>
          <div className="mt-2 flex gap-3 overflow-x-auto pb-1">
            {cats.map((cat) => {
              const band = catBand(cat);
              const style = phTokenStyle(band?.colorToken ?? "color-ph-unknown");
              const scannedAt = catScannedAt(cat);
              return (
                <Link
                  key={cat.id}
                  to={`/cats/${cat.id}`}
                  className="flex w-[210px] shrink-0 flex-col gap-3 rounded-xl bg-gradient-to-br from-white to-background-alt p-3.5 shadow-[0px_4px_16px_-2px_rgba(47,79,178,0.08)]"
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
                      <p className="flex items-center gap-1 text-[18px] font-semibold text-text-primary">
                        <span className="truncate">{cat.name}</span>
                        {cat.isPrimary ? (
                          <BadgeCheck
                            size={15}
                            className="shrink-0 text-primary-dark"
                            aria-label={t("dashboard.primaryCat", { ns: "common" })}
                          />
                        ) : null}
                      </p>
                      <p className="text-[11px] tracking-[0.4px] text-text-secondary">
                        {t("dashboard.catMeta", {
                          ns: "common",
                          age: cat.ageMonths != null ? Math.floor(cat.ageMonths / 12) : "?",
                          sex: t(
                            `form.sex.${cat.sex === "MALE" ? "male" : cat.sex === "FEMALE" ? "female" : "unknown"}`,
                            { ns: "cat" },
                          ),
                        })}
                      </p>
                    </div>
                  </div>
                  {/* Dải trạng thái sức khoẻ + giờ quét gần nhất (Figma 1:2017). */}
                  <div
                    className={cn(
                      "flex items-start gap-2 rounded-lg px-2.5 py-1.5 text-[11px] leading-[14px]",
                      band ? style.bg : "bg-background-alt/70",
                    )}
                  >
                    <span aria-hidden="true" className={cn("mt-1 size-2 shrink-0 rounded-full", style.solid)} />
                    <span className={cn("min-w-0 flex-1 font-semibold", band ? style.text : "text-text-secondary")}>
                      {band?.label ?? t("dashboard.catNoScan", { ns: "common" })}
                    </span>
                    {scannedAt ? (
                      <span className="w-[62px] shrink-0 text-right text-text-secondary">{stamp(scannedAt)}</span>
                    ) : null}
                  </div>
                </Link>
              );
            })}
            <Link
              to="/cats/new"
              className="flex w-[130px] shrink-0 flex-col items-center justify-center gap-2 rounded-xl bg-background-alt/70 py-9"
            >
              <span className="flex size-11 items-center justify-center rounded-full bg-info shadow-xs">
                <Plus size={16} aria-hidden="true" />
              </span>
              <span className="text-[12px] tracking-[0.3px] text-text-secondary">
                {t("dashboard.addCat", { ns: "common" })}
              </span>
            </Link>
          </div>
        </div>

        {/* Section 3 — Lần theo dõi gần nhất */}
        <div className="pt-6">
          <div className="flex items-center justify-between">
            <h3 className="text-[18px] font-semibold text-text-primary">
              {t("dashboard.latestMonitoring", { ns: "common" })}
            </h3>
            {latestScan?.catName != null && latestScan.catName !== "" ? (
              <p className="text-[11px] tracking-[0.4px] text-text-secondary">
                {t("dashboard.sessionOf", { ns: "common", name: latestScan.catName })}
              </p>
            ) : null}
          </div>

          {latestScan ? (
            <Link
              to={`/scans/${latestScan.scanId}`}
              className="mt-2 block rounded-xl bg-surface p-4 shadow-[0px_4px_16px_-2px_rgba(47,79,178,0.06)]"
            >
              <div className="flex items-start justify-between gap-3">
                {latestBand ? <PhBadge band={latestBand} className="min-w-0" /> : null}
                <div className="shrink-0 text-right">
                  <p className="text-[11px] text-text-secondary">{t("dashboard.analyzed", { ns: "common" })}</p>
                  <p className="whitespace-nowrap text-[12px] font-semibold text-text-primary">
                    {stamp(latestScan.capturedAt)}
                  </p>
                </div>
              </div>

              <div className="mt-3 rounded-lg bg-background-alt/60 p-3">
                {/* Chỉ số pH + nhãn dải (Figma: "pH 6.8 (Trung tính / Tối ưu)"). */}
                <div className="flex items-start justify-between gap-3">
                  <span className="flex w-[38%] shrink-0 items-start gap-1.5 text-[12px] leading-4 text-text-secondary">
                    <Droplet size={14} className="mt-0.5 shrink-0 text-primary" aria-hidden="true" />
                    {t("dashboard.phReadoutLabel", { ns: "common" })}
                  </span>
                  <span
                    className={cn(
                      "min-w-0 flex-1 rounded-lg bg-surface px-2 py-1 text-caption font-bold leading-4",
                      phTokenStyle(latestBand?.colorToken ?? "color-ph-unknown").text,
                    )}
                  >
                    {t("dashboard.phReadoutValue", {
                      ns: "common",
                      value: latestScan.phValue?.toFixed(1) ?? "—",
                      label: latestBand?.label ?? "—",
                    })}
                  </span>
                </div>

                {bands ? (
                  <div className="pt-3">
                    {/* ponytail: Figma chỉ có MỘT hàng nhãn thang (đã kèm cận dưới/trên trong chữ), còn
                        `PhGaugeBar` luôn tự in thêm hàng số min/max -> ẩn hàng cuối của nó bằng
                        variant con. Trần: phụ thuộc DOM của component dùng chung. Nâng cấp: thêm
                        prop `showBounds` cho `entities/ph-bands` khi được phép sửa file đó. */}
                    <PhGaugeBar bands={bands} value={latestScan.phValue} className="[&>div:last-child]:hidden" />
                    {scale ? (
                      <div className="flex items-center justify-between gap-2 pt-1.5 text-[10px] text-text-secondary">
                        <span>{t("dashboard.scaleAcid", { ns: "common", value: scale.min.toFixed(1) })}</span>
                        <span className="font-semibold text-success-text">
                          {t("dashboard.scaleIdeal", {
                            ns: "common",
                            min: formatRangeBound(scale.normal?.phMin, scale.min),
                            max: formatRangeBound(scale.normal?.phMax, scale.max),
                          })}
                        </span>
                        <span>{t("dashboard.scaleAlkaline", { ns: "common", value: scale.max.toFixed(1) })}</span>
                      </div>
                    ) : null}
                  </div>
                ) : null}
              </div>

              {/* Câu nhận xét — LẤY TỪ API (`band.description`), không viết lại ở FE. */}
              {bandSentence(latestBand, latestScan.phValue) !== null ? (
                <p className="mt-3 flex gap-2 text-caption leading-5 text-text-secondary">
                  <Activity size={15} className="mt-0.5 shrink-0 text-primary" aria-hidden="true" />
                  <span>{bandSentence(latestBand, latestScan.phValue)}</span>
                </p>
              ) : null}

              <div className="mt-3 flex items-center justify-between gap-2">
                <span className="flex items-center gap-0.5 text-caption font-semibold text-primary-dark">
                  {t("dashboard.viewDetail", { ns: "common" })}
                  <ChevronRight size={14} aria-hidden="true" />
                </span>
                {latestScan.confidence !== null ? (
                  <span className="text-[11px] text-text-secondary">
                    {t("dashboard.confidence", {
                      ns: "common",
                      value: (latestScan.confidence * 100).toFixed(1),
                    })}
                  </span>
                ) : null}
              </div>
            </Link>
          ) : (
            <div className="mt-2 rounded-xl bg-surface p-4 text-center shadow-[0px_4px_16px_-2px_rgba(47,79,178,0.06)]">
              <p className="text-caption font-semibold text-text-primary">
                {t("dashboard.noScanTitle", { ns: "common" })}
              </p>
              <p className="pt-1 text-[11px] text-text-secondary">{t("dashboard.noScanBody", { ns: "common" })}</p>
            </div>
          )}
        </div>

        {/* Section 4 — Lịch kiểm tra tiếp theo (nguồn: `GET /reminders?active=true`) */}
        <div className="mt-4 flex items-center gap-3 rounded-xl bg-warning-bg px-3.5 py-3">
          <span className="flex size-11 shrink-0 items-center justify-center rounded-full bg-secondary text-secondary-text-on">
            <CalendarClock size={20} aria-hidden="true" />
          </span>
          <div className="min-w-0 flex-1">
            <p className="text-[16px] font-semibold text-text-primary">
              {t("dashboard.reminderTitle", { ns: "common" })}
            </p>
            <p className="text-[12px] leading-4 text-text-secondary">{reminderLine(nextReminder)}</p>
          </div>
          <Link
            to="/reminders"
            className="shrink-0 rounded-lg bg-surface px-3 py-2 text-caption font-semibold text-primary-dark shadow-xs"
          >
            {t("dashboard.reminderManage", { ns: "common" })}
          </Link>
        </div>

        {/* Dấu hiệu theo dõi chưa xác nhận (G1/G3) */}
        <HealthFlagDisclosure count={unacknowledgedFlagCount} enabled={!isGuest} className="mt-3" />

        {/* Section 5 — Thói quen chăm sóc trong ngày (F7 `GET /care-tips`) */}
        {careTip ? (
          <div className="mt-3 flex flex-col gap-1 rounded-xl bg-surface p-3 shadow-[0px_4px_16px_-2px_rgba(47,79,178,0.06)]">
            <p className="flex items-center gap-1 text-[11px] font-semibold text-secondary-text-on">
              <Lightbulb size={12} className="shrink-0" aria-hidden="true" />
              {t("dashboard.careTipLabel", { ns: "common" })}
            </p>
            <p className="text-[15px] font-semibold text-text-primary">{careTip.title}</p>
            {careTip.summary ? <p className="text-[11px] text-text-secondary">{careTip.summary}</p> : null}
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
        {/* Section: Welcome & Primary Callouts (16:7089) — chiếm trọn bề ngang */}
        <section className="flex items-start justify-between gap-6">
          <div className="min-w-0">
            <div className="flex items-center gap-4">
              <span className="rounded bg-secondary/30 px-2.5 py-0.5 text-[11px] font-bold tracking-[0.4px] text-secondary-text-on">
                {t("dashboardWeb.monitorBadge", { ns: "common" })}
              </span>
              <span className="flex items-center gap-2 text-[11px] text-text-secondary">
                <span className="size-1.5 rounded-full bg-success" />
                {t("dashboardWeb.syncedAt", {
                  ns: "common",
                  time: new Date().toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" }),
                })}
              </span>
            </div>
            <h1 className="flex items-center gap-2 pt-2 text-[30px] font-bold leading-10 tracking-[-0.75px] text-text-primary">
              {t("dashboardWeb.greeting", { ns: "common", name: displayName })}
              <PawPrint size={24} className="shrink-0 text-primary-dark" aria-hidden="true" />
            </h1>
            <p className="max-w-[650px] pt-1 text-caption leading-6 text-text-secondary">
              {t("dashboardWeb.intro", { ns: "common" })}
            </p>
          </div>
          <div className="flex shrink-0 items-center gap-3 pt-6">
            <Link
              to="/scan/select-cat"
              className="flex h-16 items-center gap-2 rounded-2xl bg-primary-dark px-5 text-caption font-bold text-white shadow-[0px_4px_6px_-1px_rgba(13,54,154,0.2)]"
            >
              <Camera size={18} aria-hidden="true" />
              <span className="max-w-[88px] text-left leading-5">{t("dashboardWeb.ctaScan", { ns: "common" })}</span>
            </Link>
            <Link
              to="/cats/new"
              className="flex h-16 items-center gap-2 rounded-2xl bg-surface px-5 text-caption font-bold text-primary-dark shadow-xs"
            >
              <Plus size={18} aria-hidden="true" />
              <span className="max-w-[72px] text-left leading-5">{t("dashboardWeb.ctaAddCat", { ns: "common" })}</span>
            </Link>
          </div>
        </section>

        {/* Lưới 12 cột: 8/12 nội dung chính + 4/12 cột phải (bắt đầu NGAY từ dải mèo). */}
        <section className="mt-6 grid grid-cols-12 items-start gap-6">
          <div className="col-span-8 flex flex-col gap-6">
            {/* Dải chuyển mèo (16:7113) */}
            {cats.length > 0 ? (
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
                        "rounded-2xl bg-surface p-4 shadow-xs",
                        cat.isPrimary && "outline outline-2 outline-primary-dark",
                      )}
                    >
                      <div className="flex items-start gap-3">
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
                          <p className="flex items-center gap-1 text-caption font-bold text-text-primary">
                            <span className="truncate">{cat.name}</span>
                            {cat.isPrimary ? (
                              <BadgeCheck
                                size={14}
                                className="shrink-0 text-primary-dark"
                                aria-label={t("dashboard.primaryCat", { ns: "common" })}
                              />
                            ) : null}
                          </p>
                          <p className="text-[11px] leading-[14px] text-text-secondary">{catMeta(cat)}</p>
                        </div>
                        <span
                          className={cn(
                            "max-w-[38%] shrink-0 rounded-lg px-2 py-1 text-center text-[10px] font-semibold leading-[13px]",
                            band ? `${style.bg} ${style.text}` : "bg-background-alt text-text-secondary",
                          )}
                        >
                          {band?.label ?? t("dashboard.catNoScan", { ns: "common" })}
                        </span>
                      </div>
                      {scannedAt ? (
                        <div className="mt-3 flex items-center justify-between gap-2 border-t border-border pt-3 text-[11px]">
                          <span className="flex min-w-0 items-center gap-1.5 font-semibold text-text-primary">
                            <span
                              aria-hidden="true"
                              className={cn("size-2 shrink-0 rounded-full", scan?.thumbnailHex == null && style.solid)}
                              style={scan?.thumbnailHex != null ? { backgroundColor: scan.thumbnailHex } : undefined}
                            />
                            <span className="truncate">
                              {scan?.phValue != null
                                ? t("dashboardWeb.phValue", { ns: "common", value: scan.phValue.toFixed(1) })
                                : (band?.label ?? "")}
                            </span>
                          </span>
                          <span className="shrink-0 text-text-secondary">
                            {t("dashboardWeb.scannedAt", { ns: "common", time: stamp(scannedAt) })}
                          </span>
                        </div>
                      ) : null}
                    </Link>
                  );
                })}
              </div>
            ) : (
              <div className="rounded-2xl bg-surface p-6 text-center shadow-xs">
                <p className="text-caption font-bold text-text-primary">
                  {t("dashboardWeb.catsEmptyTitle", { ns: "common" })}
                </p>
                <p className="pt-1 text-[11px] text-text-secondary">
                  {t("dashboardWeb.catsEmptyBody", { ns: "common" })}
                </p>
              </div>
            )}

            {/* Widget: Chỉ thị màu hạt cát & Nồng độ pH */}
            <article className="rounded-2xl bg-surface p-6 shadow-xs">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="text-[11px] font-bold tracking-[0.6px] text-primary">
                    {t("dashboardWeb.colorimetryLabel", { ns: "common" })}
                  </p>
                  <h2 className="pt-1 text-[20px] font-bold text-text-primary">
                    {t("dashboardWeb.colorimetryTitle", { ns: "common" })}
                  </h2>
                </div>
                {latestScan?.confidence != null ? (
                  <span className="flex shrink-0 items-center gap-1.5 rounded-full bg-background-alt px-3 py-1.5 text-[11px] font-semibold text-text-secondary">
                    <BadgeCheck size={14} className="shrink-0 text-success" aria-hidden="true" />
                    {t("dashboardWeb.confidence", {
                      ns: "common",
                      value: (latestScan.confidence * 100).toFixed(1),
                    })}
                  </span>
                ) : null}
              </div>

              {latestScan ? (
                <div className="mt-4 flex gap-5 rounded-xl bg-background-alt/60 p-4">
                  {/* `GET /scans` (E2) KHÔNG trả URL ảnh — chỉ `thumbnailHex`, tức màu đã
                      đo được của vùng hạt. Hiển thị đúng ô màu đó thay cho tấm ảnh hạt cát
                      lấy từ Figma (ảnh của một lần quét không có thật). */}
                  <div
                    className="relative min-h-[150px] w-[170px] shrink-0 self-stretch overflow-hidden rounded-xl border border-border bg-background-alt"
                    style={latestScan.thumbnailHex ? { backgroundColor: latestScan.thumbnailHex } : undefined}
                  >
                    <span className="absolute bottom-1.5 left-1.5 rounded bg-surface/90 px-1.5 py-0.5 text-[10px] font-semibold text-text-primary">
                      {t("dashboardWeb.measuredSwatch", { ns: "common" })}
                    </span>
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-start justify-between gap-4">
                      <div>
                        <p className="text-[11px] text-text-secondary">
                          {t("dashboardWeb.extracted", { ns: "common" })}
                        </p>
                        <p className="flex items-center gap-2 text-[32px] font-bold leading-9 text-primary-dark">
                          {t("dashboardWeb.phBig", { ns: "common", value: latestScan.phValue?.toFixed(1) ?? "—" })}
                          {latestBand ? (
                            <span
                              className={cn(
                                "max-w-[110px] rounded-lg px-2 py-1 text-center text-[11px] font-semibold leading-[14px]",
                                phTokenStyle(latestBand.colorToken).bg,
                                phTokenStyle(latestBand.colorToken).text,
                              )}
                            >
                              {latestBand.label}
                            </span>
                          ) : null}
                        </p>
                      </div>
                      <div className="text-right">
                        <p className="text-[11px] text-text-secondary">
                          {t("dashboardWeb.capturedLabel", { ns: "common" })}
                        </p>
                        <p className="text-caption font-bold text-text-primary">{stamp(latestScan.capturedAt)}</p>
                      </div>
                    </div>

                    {bands ? (
                      <div className="pt-3">
                        <PhGaugeBar bands={bands} value={latestScan.phValue} className="[&>div:last-child]:hidden" />
                        {scale ? (
                          <div className="flex justify-between pt-1.5 text-[10px] text-text-secondary">
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
                      <p className="pt-3 text-[13px] leading-5 text-text-secondary">
                        {bandSentence(latestBand, latestScan.phValue)}
                      </p>
                    ) : null}
                  </div>
                </div>
              ) : (
                <div className="mt-4 rounded-xl bg-background-alt/60 p-8 text-center">
                  <p className="text-caption font-bold text-text-primary">
                    {t("dashboardWeb.scanEmptyTitle", { ns: "common" })}
                  </p>
                  <p className="pt-1 text-[11px] text-text-secondary">
                    {t("dashboardWeb.scanEmptyBody", { ns: "common" })}
                  </p>
                </div>
              )}
            </article>

            {/* Widget: Xu hướng pH */}
            <article className="rounded-2xl bg-surface p-6 shadow-xs">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="text-[11px] font-bold tracking-[0.6px] text-primary">
                    {t("dashboardWeb.trendLabel", { ns: "common" })}
                  </p>
                  <h2 className="pt-1 text-[20px] font-bold text-text-primary">
                    {t("dashboardWeb.trendTitle", {
                      ns: "common",
                      days: TREND_RANGE_DAYS[range],
                      name: primaryCat?.name ?? displayName,
                    })}
                  </h2>
                </div>
                <div className="flex shrink-0 gap-1 rounded-lg bg-background-alt p-1 text-[11px] font-semibold">
                  {WEB_RANGES.map((r) => (
                    <button
                      key={r}
                      type="button"
                      onClick={() => {
                        setRange(r);
                      }}
                      className={cn(
                        "rounded px-2.5 py-1",
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
                <p className="mt-6 rounded-xl bg-background-alt/60 px-4 py-10 text-center text-[12px] text-text-secondary">
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
                    <p className="text-[11px] text-text-secondary">{s.label}</p>
                    <p className="pt-0.5 text-body font-bold text-text-primary">{s.value}</p>
                  </div>
                ))}
              </div>
            </article>

            {/* Widget: Nhật ký 5 lần quét gần nhất */}
            <article className="rounded-2xl bg-surface p-6 shadow-xs">
              <div className="flex items-center justify-between">
                <h2 className="text-[20px] font-bold text-text-primary">
                  {t("dashboardWeb.logTitle", { ns: "common" })}
                </h2>
                <Link to="/history" className="text-[11px] font-semibold text-primary-dark hover:underline">
                  {t("dashboardWeb.logViewAll", { ns: "common", count: totalScanCount })} →
                </Link>
              </div>
              {scans.length > 0 ? (
                <table className="mt-4 w-full text-left">
                  <thead>
                    <tr className="border-b border-border text-[10px] tracking-[0.4px] text-text-secondary">
                      <th className="w-[26%] pb-2 font-semibold">{t("dashboardWeb.colTime", { ns: "common" })}</th>
                      <th className="w-[30%] pb-2 font-semibold">{t("dashboardWeb.colColor", { ns: "common" })}</th>
                      <th className="w-[9%] pb-2 font-semibold">{t("dashboardWeb.colPh", { ns: "common" })}</th>
                      <th className="pb-2 font-semibold">{t("dashboardWeb.colAi", { ns: "common" })}</th>
                      <th className="whitespace-nowrap pb-2 text-right font-semibold">
                        {t("dashboardWeb.colDetail", { ns: "common" })}
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {scans.slice(0, 5).map((scan) => {
                      const band = bands?.find((b) => b.code === scan.bandCode);
                      return (
                        <tr key={scan.scanId} className="border-b border-border/60 last:border-0">
                          <td className="py-3 text-[11px] font-semibold text-text-primary">{stamp(scan.capturedAt)}</td>
                          {/* MÀU PHẢN ỨNG CÁT — ô màu THẬT từ `thumbnailHex` của E2. Hệ thống chưa
                              có endpoint trả TÊN màu (`color_chart_point.display_name_vi` không lộ
                              ra ở `GET /scans`), nên hiển thị mã màu thay vì bịa tên. */}
                          <td className="py-3 text-[11px] text-text-secondary">
                            <span className="flex items-center gap-2">
                              <span
                                aria-hidden="true"
                                className={cn(
                                  "size-2.5 shrink-0 rounded-full border border-border",
                                  scan.thumbnailHex == null && "bg-background-alt",
                                )}
                                style={scan.thumbnailHex != null ? { backgroundColor: scan.thumbnailHex } : undefined}
                              />
                              {scan.thumbnailHex ?? t("dashboardWeb.emptyCell", { ns: "common" })}
                            </span>
                          </td>
                          <td className="py-3 text-caption font-bold text-primary-dark">
                            {scan.phValue?.toFixed(1) ?? t("dashboardWeb.emptyCell", { ns: "common" })}
                          </td>
                          <td className="py-3">
                            {band ? (
                              <PhBadge band={band} className="gap-1 whitespace-nowrap px-2 py-0.5 [&>svg]:size-3" />
                            ) : null}
                          </td>
                          <td className="py-3 text-right">
                            <Link
                              to={`/scans/${scan.scanId}`}
                              aria-label={t("dashboardWeb.colDetail", { ns: "common" })}
                            >
                              <Eye size={15} className="ml-auto text-text-tertiary" aria-hidden="true" />
                            </Link>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              ) : (
                <p className="mt-4 rounded-xl bg-background-alt/60 px-4 py-8 text-center text-[12px] text-text-secondary">
                  {t("dashboardWeb.logEmpty", { ns: "common" })}
                </p>
              )}
            </article>
          </div>

          {/* Cột phải (4/12) */}
          <aside className="col-span-4 flex flex-col gap-6">
            {/* Thêm bé mèo mới vào khay cát */}
            <Link
              to="/cats/new"
              className="flex flex-col items-center gap-2 rounded-2xl bg-background-alt/70 px-5 py-7 text-center"
            >
              <span className="flex size-12 items-center justify-center rounded-full bg-surface shadow-xs">
                <PawPrint size={22} className="text-primary-dark" aria-hidden="true" />
              </span>
              <span className="text-caption font-bold text-primary-dark">
                {t("dashboardWeb.addCatTitle", { ns: "common" })}
              </span>
              <span className="text-[11px] text-text-secondary">{t("dashboardWeb.addCatNote", { ns: "common" })}</span>
            </Link>

            {/* LỊCH CHĂM SÓC (nguồn: `GET /reminders?active=true`) */}
            <article className="rounded-2xl bg-surface p-5 shadow-xs">
              <div className="flex items-center justify-between gap-2">
                <p className="text-[11px] font-bold tracking-[0.5px] text-text-secondary">
                  {t("dashboardWeb.scheduleLabel", { ns: "common" })}
                </p>
                <CalendarDays size={16} className="shrink-0 text-primary-dark" aria-hidden="true" />
              </div>
              <h3 className="pt-1.5 text-[17px] font-bold leading-6 text-text-primary">
                {t("dashboardWeb.scheduleTitle", { ns: "common" })}
              </h3>
              {reminders.length > 0 ? (
                <ul className="flex flex-col gap-2 pt-3">
                  {reminders.slice(0, 2).map((reminder) => (
                    <li key={reminder.id} className="flex items-start gap-3 rounded-xl bg-background-alt/70 p-2.5">
                      <span className="flex size-11 shrink-0 flex-col items-center justify-center rounded-lg bg-secondary text-secondary-text-on">
                        {reminder.nextRunAt !== undefined ? (
                          <>
                            <span className="text-[9px] font-bold uppercase leading-none">
                              {formatDate(reminder.nextRunAt, "EEE")}
                            </span>
                            <span className="pt-0.5 text-caption font-bold leading-none">
                              {formatDate(reminder.nextRunAt, "dd")}
                            </span>
                          </>
                        ) : (
                          <CalendarClock size={18} aria-hidden="true" />
                        )}
                      </span>
                      <div className="min-w-0 flex-1">
                        <p className="text-[13px] font-bold leading-5 text-text-primary">
                          {reminder.intervalDays !== undefined
                            ? t("dashboardWeb.scheduleEvery", { ns: "common", days: reminder.intervalDays })
                            : t("dashboardWeb.scheduleRepeat", { ns: "common" })}
                        </p>
                        <p className="text-[11px] leading-4 text-text-secondary">
                          {reminder.nextRunAt === undefined
                            ? t("dashboardWeb.scheduleEmpty", { ns: "common" })
                            : reminder.preferredTimeStart !== undefined
                              ? t("dashboardWeb.scheduleWhen", {
                                  ns: "common",
                                  when: formatRelative(reminder.nextRunAt),
                                  time: reminder.preferredTimeStart.slice(0, 5),
                                })
                              : t("dashboardWeb.scheduleWhenPlain", {
                                  ns: "common",
                                  when: formatRelative(reminder.nextRunAt),
                                })}
                        </p>
                      </div>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="mt-3 rounded-xl bg-background-alt/70 p-3 text-[12px] text-text-secondary">
                  {t("dashboardWeb.scheduleEmpty", { ns: "common" })}
                </p>
              )}
              <Link
                to="/reminders"
                className="mt-3 block rounded-xl bg-chip-bg/70 py-2.5 text-center text-[12px] font-semibold text-primary-dark"
              >
                {t("dashboardWeb.scheduleCta", { ns: "common" })}
              </Link>
            </article>

            {/* Mẹo chăm sóc — F7 `GET /care-tips`. Thẻ "LỜI KHUYÊN BÁC SĨ FELINE" của Figma
                đã bỏ: nội dung đó là lời khuyên y tế viết cứng trong i18n (kèm ngưỡng pH
                hard-code) và link của nó trỏ sang `/community` vốn đang tắt theo cờ. */}
            {careTips && careTips.length > 0 ? (
              <article className="rounded-2xl border-l-4 border-primary-dark bg-info/60 p-5">
                <p className="flex items-center gap-2 text-[11px] font-bold tracking-[0.5px] text-primary-dark">
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

            <p className="rounded-xl bg-chip-bg/60 px-4 py-3 text-center text-[11px] leading-4 text-info-text">
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

/**
 * Biểu đồ xu hướng pH — SVG thuần (recharts bị eslint giới hạn trong `features/trends`
 * và `features/export`). Thang đứng lấy từ `bands` qua props, KHÔNG hard-code ngưỡng.
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
  const w = 600;
  const h = 170;
  const span = domainMax - domainMin || 1;
  const y = (v: number) => h - ((Math.min(domainMax, Math.max(domainMin, v)) - domainMin) / span) * h;
  const step = points.length > 1 ? w / (points.length - 1) : w;
  // Catmull-Rom -> Bézier bậc 3: Figma vẽ đường trơn, `polyline` cho ra đường gấp khúc.
  const coords = points.map((p, i) => ({ x: i * step, y: y(p.value) }));
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
  const mid = (domainMin + domainMax) / 2;

  // Tối đa 6 nhãn trục hoành, luôn giữ điểm đầu và điểm cuối.
  const labelCount = Math.min(6, points.length);
  const xLabels = Array.from({ length: labelCount }, (_, i) => {
    const index = labelCount === 1 ? 0 : Math.round((i * (points.length - 1)) / (labelCount - 1));
    return { index, iso: points[index].date };
  });

  return (
    <div className="mt-5 flex gap-3">
      <div className="flex h-[170px] w-16 shrink-0 flex-col justify-between text-right text-[10px] leading-none text-text-tertiary">
        <span>{axisLabel(domainMax)}</span>
        <span>{axisLabel(mid)}</span>
        <span>{axisLabel(domainMin)}</span>
      </div>
      <div className="min-w-0 flex-1">
        <div className="relative">
          <svg
            viewBox={`0 0 ${String(w)} ${String(h)}`}
            preserveAspectRatio="none"
            className="h-[170px] w-full"
            role="img"
            aria-label={safeLabel}
          >
            {[domainMax, mid, domainMin].map((v) => (
              <line
                key={v}
                x1="0"
                x2={w}
                y1={y(v)}
                y2={y(v)}
                className="stroke-border"
                strokeWidth="1"
                strokeDasharray="4 4"
              />
            ))}
            <rect
              x="0"
              y={y(safeMax)}
              width={w}
              height={Math.max(0, y(safeMin) - y(safeMax))}
              rx="10"
              className="fill-success-bg"
            />
            <path d={path} className="fill-none stroke-primary-dark" strokeWidth="2.5" strokeLinejoin="round" />
            {points.map((p, i) => (
              <circle
                key={`${p.date}-${String(i)}`}
                cx={i * step}
                cy={y(p.value)}
                r="4"
                className={cn("stroke-primary-dark", i === points.length - 1 ? "fill-primary-dark" : "fill-surface")}
                strokeWidth="2.5"
              />
            ))}
          </svg>
          <span
            className="pointer-events-none absolute left-2 text-[11px] font-semibold text-success-text"
            style={{ top: `${String((y(safeMax) / h) * 100)}%` }}
          >
            {safeLabel}
          </span>
        </div>
        <div className="flex justify-between pt-1.5 text-[10px] text-text-tertiary">
          {xLabels.map((l, i) => (
            <span
              key={`${l.iso}-${String(l.index)}`}
              className={i === xLabels.length - 1 ? "font-semibold text-primary-dark" : undefined}
            >
              {i === xLabels.length - 1 ? todayLabel : dateLabel(l.iso)}
            </span>
          ))}
        </div>
      </div>
    </div>
  );
}
