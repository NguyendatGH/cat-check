import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router";
import { isToday } from "date-fns";
import { Skeleton as AntSkeleton } from "antd";
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
  ShoppingBag,
} from "lucide-react";
import { useCatList } from "@/features/cat";
import { formatScanTimestamp, useScanHistory, useScanSummary } from "@/features/history";
import { useReminders } from "@/features/reminder";
import { useScanCaptureStore } from "@/features/scan";
import { useEntitlement } from "@/features/credit";
import { HealthFlagDisclosure } from "@/features/insight";
import { useCareTips } from "./careTipsApi";
import { HomeGuide } from "./HomeGuide";
import { TREND_RANGE_DAYS, toTrendPoints, useCatTrends } from "@/features/trends";
import type { TrendRange } from "@/features/trends";
import { PhBadge, PhGaugeBar, phTokenStyle, usePhBands } from "@/entities/ph-bands";
import type { PhBand } from "@/entities/ph-bands";
import { useSessionStore } from "@/entities/user";
import { CatAvatar, useCatStore } from "@/entities/cat";
import type { Cat } from "@/entities/cat";
import { displayableScanCount } from "@/entities/scan-result";
import type { ConfidenceBand, ScanListItem } from "@/entities/scan-result";
import type { Reminder } from "@/features/reminder";
import { ErrorState } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import { formatDate } from "@/shared/lib/format/formatDate";
import { formatRelative } from "@/shared/lib/format/formatRelative";
import { useListReveal } from "@/shared/lib/motion/usePageReveal";

const WEB_RANGES: TrendRange[] = ["7D", "30D", "90D"];

interface PhScale {
  min: number;
  max: number;
  normal: PhBand | undefined;
}

function buildScale(bands: PhBand[] | undefined): PhScale | null {
  if (!bands || bands.length === 0) return null;
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

function bandSentence(band: PhBand | undefined, phValue: number | null | undefined): string | null {
  if (!band || band.description === "") return null;
  if (phValue === null || phValue === undefined) return band.description.replace("{0}", "");
  return band.description.replace("{0}", phValue.toFixed(1));
}

function formatRangeBound(value: number | null | undefined, fallback: number): string {
  return (value ?? fallback).toFixed(1);
}

const CONFIDENCE_LABEL_KEY: Record<ConfidenceBand, string> = {
  HIGH: "dashboardWeb.confidenceHIGH",
  MEDIUM: "dashboardWeb.confidenceMEDIUM",
  LOW: "dashboardWeb.confidenceLOW",
};

type ScheduledReminder = Reminder & { nextRunAt: string };

function orderReminders(reminders: Reminder[], now: number): ScheduledReminder[] {
  const scheduled = reminders.filter((r): r is ScheduledReminder => r.nextRunAt !== undefined);
  const at = (r: ScheduledReminder) => Date.parse(r.nextRunAt);
  const upcoming = scheduled.filter((r) => at(r) >= now).sort((a, b) => at(a) - at(b));
  const overdue = scheduled.filter((r) => at(r) < now).sort((a, b) => at(b) - at(a));
  return [...upcoming, ...overdue];
}

function CatSwitcher({
  cats,
  selectedId,
  onSelect,
  label,
}: {
  cats: Cat[];
  selectedId: string | undefined;
  onSelect: (id: string) => void;
  label: string;
}) {
  if (cats.length <= 1) return null;
  return (
    <div className="flex flex-wrap gap-1.5 sm:gap-2" role="group" aria-label={label}>
      {cats.map((cat) => (
        <button
          key={cat.id}
          type="button"
          aria-pressed={cat.id === selectedId}
          onClick={() => {
            onSelect(cat.id);
          }}
          className={cn(
            "flex h-10 items-center gap-1.5 rounded-full border px-3 text-[13px] font-semibold transition-colors sm:h-11 sm:gap-2 sm:px-3.5 sm:text-caption",
            cat.id === selectedId
              ? "border-primary bg-chip-bg text-primary-dark"
              : "border-border bg-surface text-text-secondary hover:bg-background-alt",
          )}
        >
          {cat.isPrimary ? <BadgeCheck size={12} className="shrink-0 sm:size-[14px]" aria-hidden="true" /> : null}
          <span className="max-w-[100px] truncate sm:max-w-[160px]">{cat.name}</span>
        </button>
      ))}
    </div>
  );
}

export function DashboardPage() {
  const { t } = useTranslation(["common", "cat", "reminder"]);
  const user = useSessionStore((s) => s.user);
  const isGuest = !user;
  const catsQuery = useCatList("ACTIVE", !isGuest);
  const cats = useMemo(() => catsQuery.data?.items ?? [], [catsQuery.data]);
  const { data: bands } = usePhBands();
  const historyQuery = useScanHistory(undefined, "ALL", !isGuest);
  const scans = useMemo(() => historyQuery.data?.pages[0]?.items ?? [], [historyQuery.data]);
  const { data: scanSummary } = useScanSummary(undefined, undefined, undefined, !isGuest);
  const totalScanCount = scanSummary ? displayableScanCount(scanSummary) : scans.length;
  const { data: entitlement } = useEntitlement(!isGuest);
  const canUseReminder = !isGuest && (entitlement ? entitlement.hasReminder : false);
  const canUseTrend = !isGuest && (entitlement ? entitlement.hasTrend : false);
  const { data: reminderList } = useReminders({ active: true }, canUseReminder);
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const tick = () => {
      setNow(Date.now());
    };
    const timer = window.setInterval(tick, 60_000);
    window.addEventListener("focus", tick);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener("focus", tick);
    };
  }, []);
  const reminders = useMemo(() => orderReminders(reminderList?.items ?? [], now), [reminderList, now]);

  const catsLoading = !isGuest && catsQuery.isPending;
  const scansLoading = !isGuest && historyQuery.isPending;
  const loadFailed = !isGuest && (catsQuery.isError || historyQuery.isError);

  const latestScan = scans.at(0);
  const recentScans = useMemo(() => scans.slice(0, 5), [scans]);
  const recentHasColor = recentScans.some((scan) => scan.thumbnailHex != null);
  const latestBand = latestScan ? bands?.find((b) => b.code === latestScan.bandCode) : undefined;
  const scale = buildScale(bands);
  const latestByCat = useMemo(() => {
    const map = new Map<string, ScanListItem>();
    for (const scan of scans) {
      if (scan.catId != null && !map.has(scan.catId)) map.set(scan.catId, scan);
    }
    return map;
  }, [scans]);
  const navigate = useNavigate();
  const setStoreSelectedCat = useScanCaptureStore((s) => s.setSelectedCat);
  const setActiveCat = useCatStore((s) => s.setActiveCat);
  const [selectedCatId, setSelectedCatId] = useState<string | null>(null);
  const selectedCat = useMemo(
    () => cats.find((c) => c.id === selectedCatId) ?? cats.find((c) => c.isPrimary) ?? cats.at(0),
    [cats, selectedCatId],
  );
  const selectedCatScan = selectedCat ? latestByCat.get(selectedCat.id) : undefined;
  const selectedCatBand = selectedCatScan ? bands?.find((b) => b.code === selectedCatScan.bandCode) : undefined;
  const selectCat = (id: string) => {
    setSelectedCatId(id);
    setActiveCat(id);
  };
  const startScan = () => {
    if (selectedCat) {
      setStoreSelectedCat({ id: selectedCat.id, name: selectedCat.name });
      void navigate("/scan");
    } else {
      void navigate("/scan/select-cat");
    }
  };
  const overdueReminder = reminders.find((r) => Date.parse(r.nextRunAt) < now);
  const overdueScanReminder = reminders.find((r) => r.type === "SCAN_ROUTINE" && Date.parse(r.nextRunAt) < now);
  const nextScanReminder = reminders.find((r) => r.type === "SCAN_ROUTINE" && Date.parse(r.nextRunAt) >= now);
  const bannerReminder = overdueScanReminder ?? nextScanReminder ?? overdueReminder ?? reminders.at(0);
  const bannerOverdue = bannerReminder !== undefined && Date.parse(bannerReminder.nextRunAt) < now;
  const unacknowledgedFlagCount = cats.reduce((sum, cat) => sum + (cat.unacknowledgedFlagCount ?? 0), 0);
  const { data: careTips } = useCareTips(3);
  const careTip = careTips?.[0];
  const mobileCatsRef = useRef<HTMLDivElement>(null);
  const desktopCatsRef = useRef<HTMLDivElement>(null);
  const recentScansRef = useRef<HTMLTableSectionElement>(null);
  useListReveal(mobileCatsRef, cats.length);
  useListReveal(desktopCatsRef, cats.length);
  useListReveal(recentScansRef, recentScans.length);

  const displayName = user?.displayName ?? t("dashboard.guestName", { ns: "common" });
  const todayLabel = t("dashboard.today", { ns: "common" });
  const yesterdayLabel = t("dashboard.yesterday", { ns: "common" });
  const stamp = (iso: string) => formatScanTimestamp(iso, todayLabel, yesterdayLabel);

  const [range, setRange] = useState<TrendRange>("30D");
  const { data: trendData } = useCatTrends(selectedCat?.id, range, canUseTrend);
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
    <div className="contents">
      {isGuest ? (
        <div
          data-reveal
          className="mx-auto mb-2 flex max-w-[480px] flex-col gap-2.5 px-3 pt-3 sm:gap-3 sm:px-4 sm:pt-4 lg:mb-6 lg:max-w-none lg:px-0 lg:pt-0"
        >
          <div className="flex flex-col gap-2 rounded-2xl border border-border bg-surface p-3.5 sm:p-4 lg:flex-row lg:items-center lg:justify-between lg:p-5">
            <div className="flex flex-col gap-1.5">
              <p className="text-[14px] font-bold text-text-primary sm:text-caption">
                {t("dashboard.guestBanner.title", { ns: "common" })}
              </p>
              <p className="text-[12px] leading-relaxed text-text-secondary">
                {t("dashboard.guestBanner.body", { ns: "common" })}
              </p>
              <div className="flex flex-wrap items-center gap-1.5 pt-1.5 sm:gap-2">
                <span className="inline-flex items-center gap-1 rounded-full border border-primary/20 bg-chip-bg px-2 py-0.5 text-[11px] font-semibold text-primary-dark sm:gap-1.5 sm:px-2.5 sm:py-1 sm:text-overline">
                  <Camera size={11} className="sm:size-[13px]" aria-hidden="true" />
                  {t("welcome.features.scan", { ns: "common" })}
                </span>
                <span className="inline-flex items-center gap-1 rounded-full border border-primary/20 bg-chip-bg px-2 py-0.5 text-[11px] font-semibold text-primary-dark sm:gap-1.5 sm:px-2.5 sm:py-1 sm:text-overline">
                  <FlaskConical size={11} className="sm:size-[13px]" aria-hidden="true" />
                  {t("welcome.features.ph", { ns: "common" })}
                </span>
                <span className="inline-flex items-center gap-1 rounded-full border border-primary/20 bg-chip-bg px-2 py-0.5 text-[11px] font-semibold text-primary-dark sm:gap-1.5 sm:px-2.5 sm:py-1 sm:text-overline">
                  <FileText size={11} className="sm:size-[13px]" aria-hidden="true" />
                  {t("welcome.features.journal", { ns: "common" })}
                </span>
              </div>
            </div>
            <div className="flex shrink-0 flex-wrap gap-1.5 pt-2 sm:gap-2 lg:pt-0">
              <Link
                to="/auth/login"
                className="flex h-10 items-center justify-center rounded-xl bg-primary px-3.5 text-[13px] font-semibold text-white shadow-brand-sm transition-colors hover:bg-primary-dark sm:px-4 sm:text-caption"
              >
                {t("dashboard.guestBanner.login", { ns: "common" })}
              </Link>
              <Link
                to="/auth/register"
                className="flex h-10 items-center justify-center rounded-xl bg-surface px-3.5 text-[13px] font-semibold text-primary-dark shadow-xs transition-colors hover:bg-background-alt sm:px-4 sm:text-caption"
              >
                {t("dashboard.guestBanner.register", { ns: "common" })}
              </Link>
              <Link
                to="/shop"
                className="flex h-10 items-center justify-center gap-1 rounded-xl bg-secondary px-3 text-[13px] font-semibold text-secondary-text-on shadow-xs transition-colors hover:bg-secondary-light sm:gap-1.5 sm:px-4 sm:text-caption"
              >
                <ShoppingBag size={13} className="sm:size-[15px]" aria-hidden="true" />
                {t("dashboard.guestBanner.shop", { ns: "common" })}
              </Link>
            </div>
          </div>
        </div>
      ) : (
        <div data-reveal className="mx-auto mb-2 max-w-[480px] px-3 pt-3 sm:px-4 sm:pt-4 lg:mb-6 lg:max-w-none lg:px-0 lg:pt-0">
          <HomeGuide catName={selectedCat?.name} completed={user.onboarding.completed} userId={user.id} />
        </div>
      )}

      <div className="mx-auto max-w-[480px] px-3 pb-4 sm:px-4 sm:pb-6 lg:hidden">
        {!isGuest ? (
          <header data-reveal className="pt-1">
            <h1 className="text-h2 font-bold tracking-tight text-text-primary sm:text-h1">
              {t("dashboardWeb.greeting", { ns: "common", name: displayName })}
            </h1>
            <div className="pt-2">
              <CatSwitcher
                cats={cats}
                selectedId={selectedCat?.id}
                onSelect={setSelectedCatId}
                label={t("dashboardWeb.catSwitcher", { ns: "common" })}
              />
            </div>
          </header>
        ) : null}

        {loadError}

        {/* 1. Cần xử lý: chỉ hiển thị khi có dữ liệu thật */}
        {!isGuest && overdueReminder ? (
          <div role="status" className="mt-3 flex items-center gap-2.5 rounded-2xl bg-warning-bg px-3 py-3 sm:gap-3 sm:px-3.5">
            <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-secondary text-secondary-text-on sm:size-11">
              <CalendarClock size={18} className="sm:size-[20px]" aria-hidden="true" />
            </span>
            <div className="min-w-0 flex-1">
              <p className="text-[14px] font-semibold leading-5 text-text-primary sm:text-[16px] sm:leading-6">
                {t("dashboard.reminderOverdueTitle", { ns: "common" })}
              </p>
              <p className="text-[12px] leading-4 text-text-secondary">{reminderLine(overdueReminder)}</p>
            </div>
            <Link
              to="/reminders"
              className="flex min-h-10 shrink-0 items-center rounded-md bg-surface px-2.5 text-[12px] font-semibold text-primary-dark shadow-xs sm:min-h-11 sm:px-3 sm:text-[13px]"
            >
              {t("dashboard.reminderManage", { ns: "common" })}
            </Link>
          </div>
        ) : null}

        <HealthFlagDisclosure count={unacknowledgedFlagCount} enabled={!isGuest} className="mt-3" />

        {/* 2. Hành động chính: quét cho mèo đang chọn (hoặc thêm mèo nếu chưa có) */}
        {!isGuest && !catsLoading && cats.length === 0 ? (
          <div
            data-reveal
            className="relative mt-4 overflow-hidden rounded-2xl bg-primary p-4 text-white shadow-brand-md sm:p-5"
          >
            <PawPrint
              aria-hidden="true"
              className="pointer-events-none absolute -right-4 -top-3 size-24 rotate-12 text-white/10 sm:size-28"
            />
            <span className="relative inline-flex items-center gap-1 rounded-full bg-white/15 px-2.5 py-1 text-[12px] font-medium tracking-[0.4px] backdrop-blur-sm sm:gap-1.5 sm:px-3 sm:text-caption">
              <FlaskConical size={12} className="sm:size-[13px]" aria-hidden="true" />
              {t("dashboard.heroBadge", { ns: "common" })}
            </span>
            <h2 className="relative pt-3 text-base font-semibold sm:pt-4 sm:text-h3">{t("dashboardWeb.zeroTitle", { ns: "common" })}</h2>
            <p className="relative max-w-none pt-1 text-[14px] text-on-primary-muted sm:max-w-[280px] sm:text-body">
              {t("dashboardWeb.zeroBody", { ns: "common" })}
            </p>
            <div className="relative pt-3 sm:pt-4">
              <Link
                to="/cats/new"
                className="flex h-11 items-center justify-center gap-2 rounded-xl bg-secondary text-[16px] font-semibold text-secondary-text-on shadow-sm sm:h-[50px] sm:text-[18px]"
              >
                <Plus size={16} className="sm:size-[18px]" aria-hidden="true" />
                {t("dashboardWeb.ctaAddCat", { ns: "common" })}
              </Link>
            </div>
          </div>
        ) : !isGuest ? (
          <div
            data-reveal
            className="relative mt-4 overflow-hidden rounded-2xl bg-primary p-4 text-white shadow-brand-md sm:p-5"
          >
            <PawPrint
              aria-hidden="true"
              className="pointer-events-none absolute -right-4 -top-3 size-24 rotate-12 text-white/10 sm:size-28"
            />
            <span className="relative inline-flex items-center gap-1 rounded-full bg-white/15 px-2.5 py-1 text-[12px] font-medium tracking-[0.4px] backdrop-blur-sm sm:gap-1.5 sm:px-3 sm:text-caption">
              <FlaskConical size={12} className="sm:size-[13px]" aria-hidden="true" />
              {t("dashboard.heroBadge", { ns: "common" })}
            </span>
            <h2 className="relative pt-3 text-base font-semibold sm:pt-4 sm:text-h3">
              {selectedCat
                ? t("dashboard.scanForCat", { ns: "common", name: selectedCat.name })
                : t("dashboard.startScan", { ns: "common" })}
            </h2>
            <p className="relative max-w-none pt-1 text-[14px] text-on-primary-muted sm:max-w-[280px] sm:text-body">
              {t("dashboard.heroBody", { ns: "common" })}
            </p>
            <div className="relative flex flex-col items-stretch gap-2 pt-3 sm:flex-row sm:items-center sm:justify-between sm:pt-4">
              <button
                type="button"
                onClick={startScan}
                className="flex h-11 items-center justify-center gap-2 rounded-xl bg-secondary text-[16px] font-semibold text-secondary-text-on shadow-sm sm:h-[50px] sm:flex-1 sm:text-[18px]"
              >
                <Camera size={16} className="sm:size-[18px]" aria-hidden="true" />
                {t("dashboard.startScan", { ns: "common" })}
              </button>
              <Link
                to="/scan"
                aria-label={t("dashboard.scanHelp", { ns: "common" })}
                className="flex size-11 shrink-0 items-center justify-center rounded-md bg-white/15 backdrop-blur-sm sm:size-[50px]"
              >
                <HelpCircle size={16} className="sm:size-[18px]" aria-hidden="true" />
              </Link>
            </div>
          </div>
        ) : null}

        {!isGuest ? (
          <section data-reveal className="pt-4 sm:pt-5">
            <div className="flex items-baseline justify-between gap-2">
              <h2 className="shrink-0 text-[16px] font-semibold text-text-primary sm:text-[18px]">
                {t("dashboard.latestMonitoring", { ns: "common" })}
              </h2>
              {selectedCatScan?.catName != null && selectedCatScan.catName !== "" ? (
                <p className="min-w-0 truncate text-[11px] tracking-[0.4px] text-text-secondary">
                  {t("dashboard.sessionOf", { ns: "common", name: selectedCatScan.catName })}
                </p>
              ) : null}
            </div>

            {scansLoading ? (
              <div aria-hidden="true" className="mt-1.5 h-[220px] rounded-2xl bg-surface p-3 sm:mt-2 sm:p-4">
                <AntSkeleton active paragraph={{ rows: 5 }} />
              </div>
            ) : selectedCatScan ? (
              <Link
                to={`/scans/${selectedCatScan.scanId}`}
                className="motion-card mt-1.5 block rounded-2xl bg-surface p-3 shadow-brand-md sm:mt-2 sm:p-4"
              >
                <div className="flex items-start justify-between gap-2">
                  {selectedCatBand ? (
                    <PhBadge
                      band={selectedCatBand}
                      className="min-w-0 whitespace-nowrap px-2 py-0.5 text-[11px] leading-3 [&>svg]:size-3 sm:px-2.5 sm:py-1 sm:text-[12px] sm:leading-4 sm:[&>svg]:size-3.5"
                    />
                  ) : null}
                  <div className="shrink-0 text-right">
                    <p className="text-[10px] text-text-secondary sm:text-[11px]">{t("dashboard.analyzed", { ns: "common" })}</p>
                    <p className="whitespace-nowrap text-[11px] font-semibold text-text-primary sm:text-[12px]">
                      {stamp(selectedCatScan.capturedAt)}
                    </p>
                  </div>
                </div>

                <div className="mt-2.5 rounded-xl bg-background-alt/60 p-2.5 sm:mt-3 sm:p-3">
                  <div className="flex items-center justify-between gap-2 sm:gap-3">
                    <span className="flex min-w-0 items-center gap-1 text-[11px] leading-3 text-text-secondary sm:gap-1.5 sm:text-[12px] sm:leading-4">
                      <Droplet size={12} className="shrink-0 text-primary sm:size-[14px]" aria-hidden="true" />
                      <span className="truncate">{t("dashboard.phReadoutLabel", { ns: "common" })}</span>
                    </span>
                    <span
                      className={cn(
                        "shrink-0 whitespace-nowrap rounded-lg bg-surface px-2 py-0.5 text-[13px] font-bold leading-4 sm:px-2.5 sm:py-1 sm:text-[15px] sm:leading-5",
                        phTokenStyle(selectedCatBand?.colorToken ?? "color-ph-unknown").text,
                      )}
                    >
                      {t("dashboardWeb.phBig", {
                        ns: "common",
                        value: selectedCatScan.phValue?.toFixed(1) ?? t("dashboardWeb.emptyCell", { ns: "common" }),
                      })}
                    </span>
                  </div>

                  {bands ? (
                    <div className="pt-2.5 sm:pt-3">
                      <PhGaugeBar bands={bands} value={selectedCatScan.phValue} className="[&>div:last-child]:hidden" />
                      {scale ? (
                        <div className="flex items-center justify-between gap-1 pt-1 text-[9px] text-text-secondary sm:gap-2 sm:pt-1.5 sm:text-[10px]">
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

                {bandSentence(selectedCatBand, selectedCatScan.phValue) !== null ? (
                  <p className="mt-2 flex gap-1.5 text-[13px] leading-4 text-text-secondary sm:mt-3 sm:gap-2 sm:text-[14px] sm:leading-5">
                    <Activity size={13} className="mt-0.5 shrink-0 text-primary sm:size-[15px]" aria-hidden="true" />
                    <span>{bandSentence(selectedCatBand, selectedCatScan.phValue)}</span>
                  </p>
                ) : null}

                <div className="mt-2 flex items-center justify-between gap-1.5 sm:mt-3 sm:gap-2">
                  <span className="flex items-center gap-0.5 text-[12px] font-semibold text-primary-dark sm:text-[13px]">
                    {t("dashboard.viewDetail", { ns: "common" })}
                    <ChevronRight size={12} className="sm:size-[14px]" aria-hidden="true" />
                  </span>
                  {selectedCatScan.confidenceBand != null ? (
                    <span className="text-[10px] text-text-secondary sm:text-[11px]">
                      {t("dashboard.confidence", {
                        ns: "common",
                        value: t(CONFIDENCE_LABEL_KEY[selectedCatScan.confidenceBand], { ns: "common" }),
                      })}
                    </span>
                  ) : null}
                </div>
              </Link>
            ) : (
              <div className="mt-1.5 rounded-2xl bg-surface p-3 text-center shadow-brand-md sm:mt-2 sm:p-4">
                <p className="text-[13px] font-semibold text-text-primary sm:text-caption">
                  {t("dashboard.noScanTitle", { ns: "common" })}
                </p>
                <p className="pt-1 text-[11px] text-text-secondary">{t("dashboard.noScanBody", { ns: "common" })}</p>
              </div>
            )}
          </section>
        ) : null}
        {!isGuest ? (
          <section data-reveal className="pt-5 sm:pt-6">
            <div className="flex items-center justify-between gap-2 sm:gap-3">
              <div className="flex min-w-0 items-center gap-1.5 sm:gap-2">
                <h2 className="shrink-0 text-base font-semibold text-text-primary sm:text-h3">
                  {t("dashboardWeb.otherCats", { ns: "common" })}
                </h2>
                {!catsLoading ? (
                  <span className="truncate rounded-full bg-chip-bg px-1.5 py-0.5 text-[12px] font-medium text-primary-dark sm:px-2 sm:text-caption">
                    {t("dashboard.catsCount", { ns: "common", count: cats.length })}
                  </span>
                ) : null}
              </div>
              <Link to="/cats" className="shrink-0 py-2 text-[12px] font-semibold text-primary-dark sm:text-caption">
                {t("dashboard.viewAll", { ns: "common" })}
              </Link>
            </div>
            <div
              ref={mobileCatsRef}
              className="-mx-3 mt-1 flex snap-x snap-mandatory scroll-px-3 gap-2.5 overflow-x-auto px-3 pb-3 pt-1 [scrollbar-width:none] sm:-mx-4 sm:scroll-px-4 sm:gap-3 sm:px-4"
            >
              {catsLoading
                ? [0, 1].map((i) => (
                    <div key={i} className="h-[140px] w-[180px] shrink-0 rounded-2xl bg-surface p-3 sm:h-[136px] sm:w-[220px]" aria-hidden="true">
                      <AntSkeleton active avatar paragraph={{ rows: 2 }} />
                    </div>
                  ))
                : cats.map((cat) => {
                    const band = catBand(cat);
                    const style = phTokenStyle(band?.colorToken ?? "color-ph-unknown");
                    const scannedAt = catScannedAt(cat);
                    return (
                      <Link
                        key={cat.id}
                        to={`/cats/${cat.id}`}
                        data-reveal-item
                        className="motion-card flex w-[180px] shrink-0 snap-start flex-col gap-2.5 rounded-2xl bg-surface p-3 shadow-brand-md sm:w-[220px] sm:gap-3 sm:p-3.5"
                      >
                        <div className="flex items-center gap-2.5 sm:gap-3">
                          <div className="relative size-12 shrink-0 sm:size-14">
                            <CatAvatar src={cat.avatarUrl} name={cat.name} size="md" className="size-full shadow-xs" />
                            {band ? (
                              <span
                                aria-hidden="true"
                                className={cn(
                                  "absolute bottom-0 right-0 size-3 rounded-full border-2 border-surface sm:size-4",
                                  style.solid,
                                )}
                              />
                            ) : null}
                          </div>
                          <div className="min-w-0 flex-1">
                            <p className="flex items-center gap-0.5 text-[15px] font-semibold leading-5 text-text-primary sm:gap-1 sm:text-[18px] sm:leading-6">
                              <span className="truncate">{cat.name}</span>
                              {cat.isPrimary ? (
                                <BadgeCheck
                                  size={12}
                                  className="shrink-0 text-primary-dark sm:size-[15px]"
                                  aria-label={t("dashboard.primaryCat", { ns: "common" })}
                                />
                              ) : null}
                            </p>
                            <p className="truncate text-[10px] leading-3 tracking-[0.2px] text-text-secondary sm:text-[11px] sm:leading-[14px] sm:tracking-[0.4px]">
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
                        <div
                          className={cn(
                            "flex flex-col gap-0.5 rounded-lg px-2 py-1.5 sm:px-2.5",
                            band ? style.bg : "bg-background-alt/70",
                          )}
                        >
                          <span
                            className={cn(
                              "flex items-center gap-1 text-[10px] font-semibold leading-3 sm:gap-1.5 sm:text-[11px] sm:leading-[14px]",
                              band ? style.text : "text-text-secondary",
                            )}
                          >
                            <span aria-hidden="true" className={cn("size-1.5 shrink-0 rounded-full sm:size-2", style.solid)} />
                            <span className="truncate">
                              {band?.label ?? t("dashboard.catNoScan", { ns: "common" })}
                            </span>
                          </span>
                          {scannedAt ? (
                            <span className="pl-2.5 text-[10px] leading-3 text-text-secondary sm:pl-3.5 sm:text-[11px] sm:leading-[14px]">
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
                  className="flex w-[140px] shrink-0 snap-start flex-col items-center justify-center gap-1.5 rounded-2xl bg-background-alt/70 py-6 sm:w-[130px] sm:gap-2 sm:py-9"
                >
                  <span className="flex size-10 items-center justify-center rounded-full bg-info text-primary-dark shadow-xs sm:size-11">
                    <Plus size={14} className="sm:size-[16px]" aria-hidden="true" />
                  </span>
                  <span className="text-[11px] tracking-[0.2px] text-text-secondary sm:text-[12px] sm:tracking-[0.3px]">
                    {t("dashboard.addCat", { ns: "common" })}
                  </span>
                </Link>
              ) : null}
            </div>
          </section>
        ) : null}

        <div data-reveal>
          {!isGuest && bannerReminder ? (
            <div className="mt-3 flex items-center gap-2.5 rounded-2xl bg-warning-bg px-3 py-3 sm:mt-4 sm:gap-3 sm:px-3.5">
              <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-secondary text-secondary-text-on sm:size-11">
                <CalendarClock size={18} className="sm:size-[20px]" aria-hidden="true" />
              </span>
              <div className="min-w-0 flex-1">
                <p className="text-[14px] font-semibold leading-5 text-text-primary sm:text-body sm:leading-6">
                  {t(bannerOverdue ? "dashboard.reminderOverdueTitle" : "dashboard.reminderTitle", { ns: "common" })}
                </p>
                <p className="text-[12px] leading-4 text-text-secondary sm:text-caption">{reminderLine(bannerReminder)}</p>
              </div>
              <Link
                to="/reminders"
                className="flex min-h-10 shrink-0 items-center rounded-md bg-surface px-2.5 text-[12px] font-semibold text-primary-dark shadow-xs sm:min-h-11 sm:px-3 sm:text-[13px]"
              >
                {t("dashboard.reminderManage", { ns: "common" })}
              </Link>
            </div>
          ) : null}

          {careTip ? (
            <div className="mt-2.5 flex flex-col gap-1 rounded-2xl bg-surface p-3 shadow-brand-md sm:mt-3 sm:p-3.5">
              <p className="flex items-center gap-1 text-[11px] font-semibold text-secondary-text-on">
                <Lightbulb size={11} className="shrink-0 sm:size-3" aria-hidden="true" />
                {t("dashboard.careTipLabel", { ns: "common" })}
              </p>
              <p className="text-[14px] font-semibold leading-4 text-text-primary sm:text-[15px] sm:leading-5">{careTip.title}</p>
              {careTip.summary ? <p className="text-[12px] leading-4 text-text-secondary">{careTip.summary}</p> : null}
            </div>
          ) : null}

          <div className="flex justify-center pt-4 sm:pt-5">
            <p className="flex max-w-[358px] items-center gap-2 rounded-3xl bg-info px-3 py-1.5 text-[11px] leading-4 tracking-[0.4px] text-info-text">
              <ShieldCheck size={14} className="shrink-0 sm:size-[15px]" aria-hidden="true" />
              {t("dashboard.disclaimer", { ns: "common" })}
            </p>
          </div>
        </div>
      </div>

      <div className="hidden lg:block">
        {loadError}

        <section data-reveal className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between xl:gap-6">
          <div className="min-w-0">
            <div className="flex items-center gap-4">
              <span className="rounded bg-secondary/30 px-2.5 py-0.5 text-[11px] font-bold tracking-[0.4px] text-secondary-text-on">
                {t("dashboardWeb.monitorBadge", { ns: "common" })}
              </span>
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
            <h1 className="flex items-center gap-2 pt-2 text-display font-bold tracking-[-0.75px] text-text-primary">
              <span className="min-w-0 break-words">
                {t("dashboardWeb.greeting", { ns: "common", name: displayName })}
              </span>
              <PawPrint size={24} className="shrink-0 text-primary-dark" aria-hidden="true" />
            </h1>
            <p className="max-w-[680px] pt-1 text-body leading-6 text-text-secondary">{introText}</p>
            {!isGuest ? (
              <div className="pt-3">
                <CatSwitcher
                  cats={cats}
                  selectedId={selectedCat?.id}
                  onSelect={selectCat}
                  label={t("dashboardWeb.catSwitcher", { ns: "common" })}
                />
              </div>
            ) : null}
          </div>
          <div className="flex shrink-0 items-center gap-3 xl:pt-6">
            <button
              type="button"
              onClick={startScan}
              className="flex h-16 items-center gap-2 rounded-2xl bg-primary-dark px-5 text-[14px] font-bold text-white shadow-brand-md hover:bg-primary"
            >
              <Camera size={18} aria-hidden="true" />
              <span className="max-w-[88px] text-left leading-5">{t("dashboardWeb.ctaScan", { ns: "common" })}</span>
            </button>
            <Link
              to="/cats/new"
              className="flex h-16 items-center gap-2 rounded-2xl bg-surface px-5 text-[14px] font-bold text-primary-dark shadow-xs hover:bg-background-alt"
            >
              <Plus size={18} aria-hidden="true" />
              <span className="max-w-[72px] text-left leading-5">{t("dashboardWeb.ctaAddCat", { ns: "common" })}</span>
            </Link>
          </div>
        </section>

        <section className="mt-6 grid grid-cols-12 items-start gap-6">
          <div className="col-span-12 flex flex-col gap-6 xl:col-span-8">
            {catsLoading ? (
              <div data-reveal className="grid grid-cols-2 gap-4" aria-hidden="true">
                {[0, 1].map((i) => (
                  <div key={i} className="h-[132px] rounded-2xl bg-surface p-3">
                    <AntSkeleton active avatar paragraph={{ rows: 1 }} />
                  </div>
                ))}
              </div>
            ) : cats.length > 0 ? (
              <div ref={desktopCatsRef} data-reveal className="grid grid-cols-2 gap-4">
                {cats.slice(0, 2).map((cat) => {
                  const band = catBand(cat);
                  const style = phTokenStyle(band?.colorToken ?? "color-ph-unknown");
                  const scan = latestByCat.get(cat.id);
                  const scannedAt = catScannedAt(cat);
                  return (
                    <Link
                      key={cat.id}
                      to={`/cats/${cat.id}`}
                      data-reveal-item
                      className={cn(
                        "motion-card flex flex-col rounded-2xl bg-surface p-4 shadow-xs",
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
              <div data-reveal className="rounded-2xl bg-surface p-6 text-center shadow-xs">
                <p className="text-caption font-bold text-text-primary">
                  {t("dashboardWeb.catsEmptyTitle", { ns: "common" })}
                </p>
                <p className="pt-1 text-[12px] text-text-secondary">
                  {t("dashboardWeb.catsEmptyBody", { ns: "common" })}
                </p>
              </div>
            ) : null}

            <article data-reveal className="rounded-2xl bg-surface p-6 shadow-xs">
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
                  {latestScan.thumbnailHex != null ? (
                    <div
                      className="relative min-h-[150px] w-[170px] shrink-0 self-stretch overflow-hidden rounded-md border border-border"
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
                        <p className="text-[12px] text-text-secondary">
                          {t("dashboardWeb.extracted", { ns: "common" })}
                        </p>
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
                        <p className="text-[14px] font-bold leading-5 text-text-primary">
                          {stamp(latestScan.capturedAt)}
                        </p>
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

            {selectedCat ? (
              <article data-reveal className="rounded-2xl bg-surface p-6 shadow-xs">
                <div className="flex items-start justify-between gap-4">
                  <h2 className="text-[20px] font-bold leading-7 text-text-primary">
                    {t("dashboardWeb.trendTitle", {
                      ns: "common",
                      days: TREND_RANGE_DAYS[range],
                      name: selectedCat.name,
                    })}
                  </h2>
                  <div
                    className="flex shrink-0 gap-1 rounded-sm bg-background-alt p-1 text-[12px] font-semibold"
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

                <div key={range} className="fade-swap mt-4 grid grid-cols-3 gap-3">
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

            {!isGuest ? (
              <article data-reveal className="rounded-2xl bg-surface p-6 shadow-xs">
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
                        {recentHasColor ? (
                          <th className="w-[24%] pb-2.5 font-semibold">
                            {t("dashboardWeb.colColor", { ns: "common" })}
                          </th>
                        ) : null}
                        <th className="w-[12%] pb-2.5 font-semibold">{t("dashboardWeb.colPh", { ns: "common" })}</th>
                        <th className="pb-2.5 font-semibold">{t("dashboardWeb.colAi", { ns: "common" })}</th>
                        <th className="whitespace-nowrap pb-2.5 text-right font-semibold">
                          {t("dashboardWeb.colDetail", { ns: "common" })}
                        </th>
                      </tr>
                    </thead>
                    <tbody ref={recentScansRef}>
                      {recentScans.map((scan) => {
                        const band = bands?.find((b) => b.code === scan.bandCode);
                        const style = phTokenStyle(band?.colorToken ?? "color-ph-unknown");
                        return (
                          <tr key={scan.scanId} data-reveal-item className="border-b border-border/60 last:border-0">
                            <td className="py-2 text-[14px] font-semibold text-text-primary">
                              {stamp(scan.capturedAt)}
                              {scan.catName ? (
                                <span className="block text-[12px] font-normal leading-4 text-text-secondary">
                                  {scan.catName}
                                </span>
                              ) : null}
                            </td>
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
                                  <span
                                    aria-hidden="true"
                                    className={cn("size-1.5 shrink-0 rounded-full", style.solid)}
                                  />
                                  {band.label}
                                </span>
                              ) : null}
                            </td>
                            <td className="py-2 text-right">
                              <Link
                                to={`/scans/${scan.scanId}`}
                                aria-label={t("dashboardWeb.colDetail", { ns: "common" })}
                                className="inline-flex size-11 items-center justify-center rounded-sm text-text-tertiary hover:bg-background-alt hover:text-primary-dark"
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

          <aside
            data-reveal
            className="col-span-12 grid grid-cols-2 items-start gap-6 xl:col-span-4 xl:flex xl:flex-col"
          >
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
                      <span className="flex size-10 shrink-0 items-center justify-center rounded-md bg-surface text-primary-dark shadow-xs">
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
    </div>
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
const CHART_PAD_X = 10;
const CHART_PAD_Y = 10;

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

  const labelCount = Math.min(6, points.length);
  const xLabels = Array.from({ length: labelCount }, (_, i) => {
    const index = labelCount === 1 ? 0 : Math.round((i * (points.length - 1)) / (labelCount - 1));
    return { index, iso: points[index].date };
  }).filter((l, i, all) => {
    const next = all[i + 1] as (typeof all)[number] | undefined;
    return !next || dateLabel(next.iso) !== dateLabel(l.iso);
  });

  return (
    <div className="mt-4 flex flex-col gap-2">
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
