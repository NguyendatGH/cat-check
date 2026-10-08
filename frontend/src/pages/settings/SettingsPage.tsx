import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import {
  BadgeCheck,
  Bell,
  BookOpen,
  CalendarClock,
  ChevronRight,
  Download,
  FileText,
  Headphones,
  Info,
  KeyRound,
  Languages,
  LogOut,
  Mail,
  PawPrint,
  Pencil,
  Plus,
  ShieldCheck,
  SlidersHorizontal,
  Timer,
  UserRound,
  Users,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { useSessionStore } from "@/entities/user";
import { phTokenStyle, usePhBands } from "@/entities/ph-bands";
import type { PhBand } from "@/entities/ph-bands";
import type { Cat, CatLastScan } from "@/entities/cat";
import { useLogout, useProfile } from "@/features/auth";
import { useCatList } from "@/features/cat";
import { NotificationPreferencesForm } from "./NotificationPreferencesForm";
import { useLastScanByCat, useScanReminderSummary, type ScanReminderSummary } from "./settingsSummaries";

/**
 * `/settings` — trang Cài đặt.
 *
 * Hai bố cục tách bạch vì hai bản thiết kế khác cấu trúc:
 *   - `< lg`: thẻ hồ sơ → thẻ bé mèo → các nhóm danh sách → pháp lý → đăng xuất;
 *   - `>= lg`: hero → cột điều hướng trái DÍNH + bốn khối nội dung bên phải.
 *
 * MỌI thứ trên trang này chạy bằng dữ liệu thật: hồ sơ (`GET /users/me`), danh sách bé mèo
 * (`GET /cats`), lần quét gần nhất của từng bé (`GET /scans?catId&limit=1`) + dải pH
 * (`GET /reference/ph-bands`), lịch nhắc quét (`GET /reminders?active=true`), tuỳ chọn thông
 * báo (B11/B12 trong `NotificationPreferencesForm`), đăng xuất, và các lối vào màn hình đã có
 * backend (bảo mật, quyền riêng tư, ngôn ngữ, lịch nhắc, xuất hồ sơ).
 *
 * ĐÃ XOÁ (không phục hồi): thiết bị khay cát IoT, uỷ quyền hồ sơ cho phòng khám, gói đăng ký
 * nhận cát, hội viên/chứng chỉ, hotline, số phiên bản, và khối "Ngưỡng cảnh báo" (ba ô tick
 * đặt cứng — không endpoint nào lưu ngưỡng; dải tham chiếu thuộc `GET /reference/ph-bands`,
 * người dùng không chỉnh được). Nguyên tắc: field nào backend không trả thì BỎ khỏi UI chứ
 * không bịa rồi dán nhãn "dữ liệu mẫu" (p15 REQ-COPY-01, REQ-CLAIM-04).
 *
 * Figma dựng hero mobile bằng một ẢNH CHÂN DUNG chủ nuôi; `GET /users/me` KHÔNG có cột ảnh
 * đại diện nào (`ProfileResponse` chỉ có id/email/fullName/phone/locale/timezone/...), nên
 * khung ảnh ở đây render chữ cái đầu trên nền trang trí thay vì bịa một tấm ảnh.
 *
 * Trang nằm trong `AppLayout`: layout KHÔNG cấp padding ngang ở mobile nhưng có cấp ở `lg`,
 * nên ở đây là `px-4 lg:px-0`.
 */

/**
 * Bốn khối nội dung của bản desktop, ĐÚNG thứ tự render. Cột điều hướng trái dựng TỪ mảng
 * này, nên menu không thể lệch khỏi nội dung: thêm/bỏ một khối là sửa đúng một chỗ.
 */
const SECTIONS = [
  { id: "account", icon: UserRound },
  { id: "cats", icon: PawPrint },
  { id: "notifications", icon: Bell },
  { id: "more", icon: ShieldCheck },
] as const;

type SectionId = (typeof SECTIONS)[number]["id"];

const ANCHOR_PREFIX = "settings-";

function anchorId(id: SectionId): string {
  return `${ANCHOR_PREFIX}${id}`;
}

/**
 * `scroll-mt-20` = 80px: `AppLayout` có header dính `h-16` (64px) ở desktop và `h-14` (56px)
 * ở mobile, nên neo phải chừa chỗ, nếu không tiêu đề khối nhảy vào đúng dưới header.
 */
const SECTION_ANCHOR = "scroll-mt-20";

const SECTION_CARD = "rounded-2xl bg-surface p-5 shadow-brand-md";

/** Nền ô icon tròn-vuông đầu mỗi khối desktop — Figma đổi màu theo nhóm nội dung. */
const SECTION_ICON_TONES = {
  info: "bg-info text-primary-dark",
  brand: "bg-chip-bg text-primary-dark",
  amber: "bg-secondary-light text-secondary-text-on",
  green: "bg-verified-bright text-verified-deep",
} as const;

type SectionIconTone = keyof typeof SECTION_ICON_TONES;

/** Khối "Bảo mật và quyền riêng tư" — mọi dòng là một route thật đã nối backend. */
const SETTINGS_LINKS: { key: string; icon: LucideIcon; to: string }[] = [
  { key: "security", icon: KeyRound, to: "/settings/security" },
  { key: "privacy", icon: ShieldCheck, to: "/account/privacy" },
  { key: "language", icon: Languages, to: "/settings/language" },
  { key: "reminders", icon: Timer, to: "/reminders" },
  { key: "export", icon: Download, to: "/export" },
];

/**
 * Mục đang xem trong cột điều hướng desktop.
 *
 * `rootMargin` cắt 80px mép trên (header dính) và 60% mép dưới: không cắt thì cả bốn khối
 * cùng giao với viewport trên màn hình cao và mục "đang xem" không bao giờ đổi.
 */
function useActiveSection(): SectionId {
  const [active, setActive] = useState<SectionId>("account");

  useEffect(() => {
    const byNode = new Map<Element, SectionId>();
    for (const { id } of SECTIONS) {
      const node = document.getElementById(anchorId(id));
      if (node) byNode.set(node, id);
    }
    if (byNode.size === 0) return;

    const observer = new IntersectionObserver(
      (entries) => {
        let best: { id: SectionId; top: number } | null = null;
        for (const entry of entries) {
          if (!entry.isIntersecting) continue;
          const id = byNode.get(entry.target);
          if (id === undefined) continue;
          const top = entry.boundingClientRect.top;
          if (best === null || top < best.top) best = { id, top };
        }
        if (best !== null) setActive(best.id);
      },
      { rootMargin: "-80px 0px -60% 0px" },
    );
    for (const node of byNode.keys()) {
      observer.observe(node);
    }
    return () => {
      observer.disconnect();
    };
  }, []);

  return active;
}

export function SettingsPage() {
  const { t, i18n } = useTranslation(["settings", "common"]);
  const user = useSessionStore((s) => s.user);
  const { data: profile } = useProfile();
  const { data: cats } = useCatList("ACTIVE");
  const { data: phBands } = usePhBands();
  const logout = useLogout();
  const activeSection = useActiveSection();

  const catItems = cats?.items ?? [];
  const catCount = catItems.length;
  const lastScanByCat = useLastScanByCat(catItems);
  const reminderSummary = useScanReminderSummary();
  const reminderNote = describeReminders(reminderSummary, t, i18n.language);
  const displayName = profile?.fullName ?? user?.displayName ?? "";
  const monogram = (displayName || "?").slice(0, 1).toUpperCase();
  const memberSince = profile?.createdAt
    ? new Date(profile.createdAt).toLocaleDateString(i18n.language, { dateStyle: "long" })
    : "";

  const onLogout = () => {
    logout.mutate(undefined, {
      onSettled: () => {
        window.location.assign("/auth/login");
      },
    });
  };

  return (
    <div className="flex flex-col gap-4 px-4 py-5 lg:gap-5 lg:px-0">
      {/* ======================= DESKTOP (Web-16) ======================= */}
      <div className="hidden flex-col gap-5 lg:flex">
        <section className="flex items-start justify-between gap-6 rounded-2xl bg-surface p-6 shadow-brand-md">
          <div className="min-w-0">
            <h1 className="text-[24px] font-bold text-primary-dark">{t("web.heroTitle")}</h1>
            <p className="max-w-[640px] pt-1.5 text-body leading-relaxed text-text-secondary">{t("web.heroBody")}</p>
          </div>
          <Link
            to="/export"
            className="flex shrink-0 items-center gap-2 rounded-xl bg-deco-backdrop px-4 py-2.5 text-caption font-semibold text-primary-dark hover:bg-chip-bg"
          >
            <Download size={15} aria-hidden="true" />
            {t("web.exportCta")}
          </Link>
        </section>

        {/*
          1024–1279px: sidebar 288px của AppLayout + cột menu 220px chỉ chừa ~430px cho nội dung
          — email bị bẻ giữa chữ, mục menu xuống dòng. Ở khoảng này menu thành một hàng chip
          ngang phía trên; cột menu dính bên trái chỉ hiện từ `xl`.
        */}
        <nav className="flex flex-wrap items-center gap-2 xl:hidden" aria-label={t("web.navLabel")}>
          {SECTIONS.map(({ id, icon: Icon }) => (
            <a
              key={id}
              href={`#${anchorId(id)}`}
              className="flex items-center gap-2 rounded-xl bg-surface px-3.5 py-2 text-caption font-semibold text-text-secondary shadow-brand-md hover:text-text-primary"
            >
              <Icon size={15} className="shrink-0" aria-hidden="true" />
              {t(`web.nav.${id}`)}
            </a>
          ))}
          <button
            type="button"
            onClick={onLogout}
            disabled={logout.isPending}
            className="ml-auto flex items-center gap-2 rounded-xl bg-surface px-3.5 py-2 text-caption font-semibold text-danger shadow-brand-md hover:bg-danger-bg disabled:opacity-50"
          >
            <LogOut size={15} aria-hidden="true" />
            {t("web.logout")}
          </button>
        </nav>

        <div className="flex items-start gap-5">
          {/*
            Cột trái DÍNH. Trước đây nó là một khối tĩnh cao ~400px cạnh một cột phải dài
            ~2000px nên để lại khoảng trắng khổng lồ; `sticky top-20` giữ menu trong tầm mắt
            suốt lúc cuộn và khoảng trắng đó biến mất.
          */}
          <div className="sticky top-20 hidden w-[260px] shrink-0 flex-col gap-3 xl:flex">
            <nav className="rounded-2xl bg-surface p-4 shadow-brand-md" aria-label={t("web.navLabel")}>
              <p className="px-1 pb-2 text-[11px] font-bold uppercase tracking-wide text-text-tertiary">
                {t("web.navHeading")}
              </p>
              <ul className="flex flex-col gap-1">
                {SECTIONS.map(({ id, icon: Icon }) => {
                  const active = activeSection === id;
                  return (
                    <li key={id}>
                      <a
                        href={`#${anchorId(id)}`}
                        aria-current={active ? "true" : undefined}
                        className={cn(
                          "flex w-full items-center gap-2.5 rounded-xl px-3 py-2.5",
                          active
                            ? "bg-primary text-white"
                            : "text-text-secondary hover:bg-background-alt hover:text-text-primary",
                        )}
                      >
                        <Icon size={17} className="shrink-0" aria-hidden="true" />
                        <span className="min-w-0 flex-1 text-left text-caption font-semibold">
                          {t(`web.nav.${id}`)}
                        </span>
                        {id === "cats" ? (
                          <span
                            className={cn(
                              "shrink-0 whitespace-nowrap rounded-md px-1.5 py-0.5 text-[10px] font-bold",
                              active ? "bg-white/20 text-white" : "bg-chip-bg text-primary-dark",
                            )}
                          >
                            {t("web.catCount", { count: catCount })}
                          </span>
                        ) : null}
                        {active ? <ChevronRight size={15} className="shrink-0" aria-hidden="true" /> : null}
                      </a>
                    </li>
                  );
                })}
              </ul>
            </nav>

            <button
              type="button"
              onClick={onLogout}
              disabled={logout.isPending}
              className="flex items-center justify-center gap-2 rounded-2xl bg-surface px-4 py-3 text-caption font-semibold text-danger shadow-brand-md hover:bg-danger-bg disabled:opacity-50"
            >
              <LogOut size={15} aria-hidden="true" />
              {t("web.logout")}
            </button>
          </div>

          <div className="flex min-w-0 flex-1 flex-col gap-5">
            {/* 1. Thông tin tài khoản — GET /users/me */}
            <section id={anchorId("account")} className={cn(SECTION_ANCHOR, SECTION_CARD)}>
              <SectionHeader
                icon={UserRound}
                iconTone="info"
                title={t("web.accountTitle")}
                subtitle={t("web.accountSubtitle")}
                rightSlot={
                  <Link
                    to="/settings/profile"
                    className="flex shrink-0 items-center gap-1.5 rounded-xl bg-background-alt px-3.5 py-2 text-caption font-semibold text-primary hover:bg-chip-bg"
                  >
                    <Pencil size={14} aria-hidden="true" />
                    {t("web.edit")}
                  </Link>
                }
              />

              {/* Lưới 2×2 như Web-16. Ô thứ tư của thiết kế ("Hội viên thú y") không có field
                  nào — thay bằng ngày tạo tài khoản (`ProfileResponse.createdAt`) cùng vị trí. */}
              <dl className="grid grid-cols-2 gap-4 pt-4">
                <InfoCell label={t("web.fieldName")} value={displayName} />
                <InfoCell
                  label={t("web.fieldEmail")}
                  value={profile?.email ?? ""}
                  note={profile?.emailVerified ? t("web.emailVerified") : t("web.emailUnverified")}
                  noteTone={profile?.emailVerified ? "success" : "warning"}
                  noteIcon={BadgeCheck}
                />
                <InfoCell label={t("web.fieldPhone")} value={profile?.phone ?? t("web.phoneEmpty")} />
                <InfoCell label={t("web.fieldMemberSince")} value={memberSince} />
              </dl>
            </section>

            {/* 2. Bé mèo — GET /cats + GET /reference/ph-bands */}
            <section id={anchorId("cats")} className={cn(SECTION_ANCHOR, SECTION_CARD)}>
              <SectionHeader
                icon={PawPrint}
                iconTone="brand"
                title={t("web.catsTitle")}
                subtitle={t("web.catsSubtitle")}
                rightSlot={
                  <Link
                    to="/cats/new"
                    className="flex shrink-0 items-center gap-1.5 rounded-xl bg-primary px-3.5 py-2 text-caption font-bold text-white hover:bg-primary-dark"
                  >
                    <Plus size={14} aria-hidden="true" />
                    {t("web.catsAdd")}
                  </Link>
                }
              />

              {catCount === 0 ? (
                <p className="mt-4 rounded-xl bg-background-alt p-5 text-center text-caption text-text-secondary">
                  {t("web.catsEmpty")}
                </p>
              ) : (
                <ul className="mt-4 grid grid-cols-2 gap-3">
                  {catItems.map((cat) => (
                    <li key={cat.id}>
                      <CatTile cat={cat} bands={phBands} lastScan={lastScanByCat[cat.id]} />
                    </li>
                  ))}
                </ul>
              )}

              <Link
                to="/cats"
                className="mt-3 flex items-center justify-center gap-2 rounded-xl bg-chip-bg py-2.5 text-caption font-bold text-primary-dark hover:bg-deco-backdrop"
              >
                <SlidersHorizontal size={15} aria-hidden="true" />
                {t("web.catsManage")}
              </Link>
            </section>

            {/* 3. Thông báo và cảnh báo — B11/B12 */}
            <section id={anchorId("notifications")} className={cn(SECTION_ANCHOR, SECTION_CARD)}>
              <SectionHeader
                icon={Bell}
                iconTone="amber"
                title={t("web.alertsTitle")}
                subtitle={t("web.alertsSubtitle")}
              />

              {/*
                Hàng "Tần suất nhắc quét định kỳ" của Web-16. Thiết kế đặt một ô chọn tần suất
                chung cho cả tài khoản, nhưng lịch nhắc của CatCheck đặt RIÊNG cho từng bé (I1–I6),
                nên ở đây là bản tóm tắt đọc từ `GET /reminders` + lối sang màn quản lý.
              */}
              <div className="mt-4 flex flex-wrap items-center gap-x-4 gap-y-3 rounded-xl bg-background-alt p-4">
                <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-surface text-primary-dark">
                  <CalendarClock size={17} aria-hidden="true" />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="text-body font-bold text-text-primary">{t("web.remindersTitle")}</p>
                  <p className="pt-0.5 text-caption text-text-secondary">{reminderNote}</p>
                </div>
                <Link
                  to="/reminders"
                  className="flex shrink-0 items-center gap-1.5 rounded-xl bg-surface px-3.5 py-2 text-caption font-semibold text-primary hover:bg-chip-bg"
                >
                  {t("web.remindersManage")}
                  <ChevronRight size={14} aria-hidden="true" />
                </Link>
              </div>

              <NotificationPreferencesForm className="pt-5" />
            </section>

            {/* 4. Bảo mật và quyền riêng tư — lối vào các màn đã nối backend */}
            <section id={anchorId("more")} className={cn(SECTION_ANCHOR, SECTION_CARD)}>
              <SectionHeader
                icon={ShieldCheck}
                iconTone="green"
                title={t("web.moreTitle")}
                subtitle={t("web.moreSubtitle")}
              />

              <ul className="grid gap-3 pt-4 xl:grid-cols-2">
                {SETTINGS_LINKS.map(({ key, icon: Icon, to }) => (
                  <li key={key}>
                    <Link
                      to={to}
                      className="flex h-full items-center gap-3 rounded-xl bg-background-alt p-3.5 hover:bg-chip-bg"
                    >
                      <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-surface text-primary-dark">
                        <Icon size={17} aria-hidden="true" />
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="block text-caption font-bold text-text-primary">
                          {t(`web.links.${key}.title`)}
                        </span>
                        <span className="block pt-0.5 text-[11px] leading-relaxed text-text-tertiary">
                          {t(`web.links.${key}.note`)}
                        </span>
                      </span>
                      <ChevronRight size={16} className="shrink-0 text-text-tertiary" aria-hidden="true" />
                    </Link>
                  </li>
                ))}
              </ul>
            </section>
          </div>
        </div>
      </div>

      {/* ======================= MOBILE (frame 15) ======================= */}
      <div className="flex flex-col gap-4 lg:hidden">
        {/*
          Figma đặt ảnh chân dung chủ nuôi tràn thẻ. `ProfileResponse` không có field ảnh nào
          nên khung dưới đây giữ ĐÚNG hình khối của thiết kế (khung ảnh bo góc + vòng tròn
          trang trí) nhưng hiển thị chữ cái đầu — không bịa ảnh cho tài khoản chưa có ảnh.
        */}
        <section className="overflow-hidden rounded-2xl bg-surface p-4 shadow-brand-md">
          <div className="relative flex h-44 items-center justify-center overflow-hidden rounded-xl bg-background-alt">
            <span
              aria-hidden="true"
              className="absolute -right-8 -top-8 size-28 rounded-full bg-deco-backdrop opacity-60"
            />
            <span className="relative flex size-24 items-center justify-center rounded-full bg-primary text-h1 font-bold text-white">
              {monogram}
            </span>
          </div>
          <p className="truncate pt-3 text-h3 font-bold text-text-primary">{displayName}</p>
          <p className="truncate text-caption text-text-secondary">{profile?.email ?? ""}</p>
          <div className="mt-3 flex items-center gap-2 border-t border-deco-backdrop pt-3">
            <p className="flex min-w-0 flex-1 items-center gap-1.5 truncate text-[13px] font-semibold text-text-secondary">
              <Users size={14} className="shrink-0 text-primary" aria-hidden="true" />
              {t("mobile.catCompanions", { count: catCount })}
            </p>
            <Link
              to="/settings/profile"
              className="flex shrink-0 items-center gap-1.5 rounded-xl bg-chip-bg px-3 py-1.5 text-[13px] font-bold text-primary-dark"
            >
              <Pencil size={14} aria-hidden="true" />
              {t("mobile.editAccount")}
            </Link>
          </div>
        </section>

        {/* Hồ sơ bé mèo — dữ liệu THẬT từ GET /cats + GET /reference/ph-bands */}
        <section className="rounded-2xl bg-surface p-4 shadow-brand-md">
          <div className="flex items-start gap-3">
            <div className="min-w-0 flex-1">
              <h2 className="text-h3 font-bold text-text-primary">{t("mobile.catsTitle")}</h2>
              <p className="pt-0.5 text-caption text-text-secondary">{t("mobile.catsSubtitle")}</p>
            </div>
            <Link
              to="/cats/new"
              className="flex shrink-0 items-center gap-1.5 rounded-xl bg-primary px-3.5 py-2.5 text-caption font-bold text-white"
            >
              <Plus size={15} aria-hidden="true" />
              {t("mobile.catsAdd")}
            </Link>
          </div>

          {catCount === 0 ? (
            <p className="mt-3 rounded-xl bg-background-alt p-4 text-center text-caption text-text-secondary">
              {t("mobile.catsEmpty")}
            </p>
          ) : (
            <ul className="mt-3 flex flex-col gap-3">
              {catItems.map((cat) => (
                <li key={cat.id}>
                  <CatTile cat={cat} bands={phBands} lastScan={lastScanByCat[cat.id]} />
                </li>
              ))}
            </ul>
          )}

          <Link
            to="/cats"
            className="mt-3 flex items-center justify-center gap-2 rounded-xl bg-chip-bg py-2.5 text-caption font-bold text-primary-dark"
          >
            <SlidersHorizontal size={15} aria-hidden="true" />
            {t("mobile.catsManage")}
          </Link>
        </section>

        <MobileGroup icon={Bell} title={t("mobile.groupMonitoring")}>
          <MobileRow icon={Timer} to="/reminders" title={t("mobile.rows.reminders.title")} note={reminderNote} />
          <MobileRow
            icon={Bell}
            to="/settings/notifications"
            title={t("mobile.rows.notifications.title")}
            note={t("mobile.rows.notifications.note")}
          />
        </MobileGroup>

        <MobileGroup icon={FileText} title={t("mobile.groupData")}>
          <MobileRow
            icon={Download}
            to="/export"
            title={t("mobile.rows.export.title")}
            note={t("mobile.rows.export.note")}
            action={t("mobile.exportAction")}
          />
          <MobileRow
            icon={KeyRound}
            to="/settings/security"
            title={t("mobile.rows.security.title")}
            note={t("mobile.rows.security.note")}
          />
          <MobileRow
            icon={ShieldCheck}
            to="/account/privacy"
            title={t("mobile.rows.privacy.title")}
            note={t("mobile.rows.privacy.note")}
          />
          <MobileRow
            icon={Languages}
            to="/settings/language"
            title={t("mobile.rows.language.title")}
            note={t("mobile.rows.language.note")}
          />
        </MobileGroup>

        <MobileGroup icon={Headphones} title={t("mobile.groupSupport")}>
          <MobileRow
            icon={BookOpen}
            to="/community"
            title={t("mobile.rows.guides.title")}
            note={t("mobile.rows.guides.note")}
          />
          <MobileRow
            icon={Mail}
            to="/legal/contact"
            title={t("mobile.rows.contact.title")}
            note={t("mobile.rows.contact.note")}
          />
        </MobileGroup>

        <section className="flex gap-2.5 rounded-2xl bg-chip-bg p-4">
          <Info size={17} className="mt-0.5 shrink-0 text-warning" aria-hidden="true" />
          <div>
            <p className="text-caption font-bold text-text-primary">{t("mobile.disclaimerTitle")}</p>
            <p className="pt-1 text-caption leading-relaxed text-text-secondary">{t("mobile.disclaimerBody")}</p>
          </div>
        </section>

        <nav className="flex flex-wrap justify-center gap-x-3 gap-y-1 text-caption font-bold text-text-primary">
          <Link to="/legal/terms" className="hover:underline">
            {t("mobile.legalTerms")}
          </Link>
          <span aria-hidden="true" className="text-text-tertiary">
            •
          </span>
          <Link to="/legal/privacy" className="hover:underline">
            {t("mobile.legalPrivacy")}
          </Link>
          <span aria-hidden="true" className="text-text-tertiary">
            •
          </span>
          <Link to="/legal/medical-disclaimer" className="hover:underline">
            {t("mobile.legalSafety")}
          </Link>
        </nav>

        <button
          type="button"
          onClick={onLogout}
          disabled={logout.isPending}
          className="flex items-center justify-center gap-2 py-2 text-body font-bold text-danger disabled:opacity-50"
        >
          <LogOut size={17} aria-hidden="true" />
          {t("mobile.logout")}
        </button>
      </div>
    </div>
  );
}

/* ---------------- Thành phần phụ dùng trong trang ---------------- */

/** Đầu khối desktop: ô icon + tiêu đề + phụ đề, kèm chỗ cho một CTA bên phải. */
function SectionHeader({
  icon: Icon,
  iconTone,
  title,
  subtitle,
  rightSlot,
}: {
  icon: LucideIcon;
  iconTone: SectionIconTone;
  title: string;
  subtitle: string;
  rightSlot?: React.ReactNode;
}) {
  return (
    <div className="flex items-start justify-between gap-4">
      <div className="flex items-center gap-3">
        <span
          className={cn("flex size-10 shrink-0 items-center justify-center rounded-xl", SECTION_ICON_TONES[iconTone])}
        >
          <Icon size={19} aria-hidden="true" />
        </span>
        <div className="min-w-0">
          <h2 className="text-[17px] font-bold text-text-primary">{title}</h2>
          <p className="text-caption text-text-secondary">{subtitle}</p>
        </div>
      </div>
      {rightSlot}
    </div>
  );
}

/**
 * Câu mô tả hàng "Lịch nhắc" — số lịch nhắc quét đang bật + mốc nhắc kế tiếp, đều từ
 * `GET /reminders`. Khi chưa tải xong / không đọc được thì dùng câu mô tả chung.
 */
function describeReminders(
  summary: ScanReminderSummary,
  t: (key: string, options?: Record<string, unknown>) => string,
  locale: string,
): string {
  if (summary.status !== "ready") return t("mobile.rows.reminders.note");
  if (summary.activeCount === 0) return t("web.remindersNone");
  if (summary.nextRunAt === null) return t("web.remindersActive", { count: summary.activeCount });
  return t("web.remindersActiveNext", {
    count: summary.activeCount,
    next: new Date(summary.nextRunAt).toLocaleString(locale, { dateStyle: "short", timeStyle: "short" }),
  });
}

/**
 * Ô ảnh một bé mèo (mobile: một cột; desktop: lưới 2 cột trong khối "Bé mèo của bạn").
 * Ảnh lấy từ `cat.avatarUrl` — mèo chưa có ảnh thì hiện khung giữ chỗ, KHÔNG chèn ảnh mẫu.
 * Dòng trạng thái = lần quét gần nhất (`GET /scans?catId&limit=1`): nhãn + màu của dải tra từ
 * `GET /reference/ph-bands` theo `bandCode`, kèm giá trị pH — ứng dụng không tự đặt nhãn.
 */
function CatTile({
  cat,
  bands,
  lastScan,
}: {
  cat: Cat;
  bands: PhBand[] | undefined;
  lastScan: CatLastScan | null | undefined;
}) {
  const { t } = useTranslation("settings");
  const band = lastScan
    ? (bands?.find((item) => item.code === lastScan.bandCode) ??
      bands?.find((item) => item.code === lastScan.classification))
    : undefined;
  const bandStyle = band ? phTokenStyle(band.colorToken) : null;

  return (
    <Link to={`/cats/${cat.id}`} className="block h-full rounded-xl bg-background-alt p-3">
      <div className="relative h-40 overflow-hidden rounded-xl bg-deco-backdrop lg:h-32">
        {cat.avatarUrl ? (
          <img src={cat.avatarUrl} alt={cat.name} className="size-full object-cover" />
        ) : (
          <span className="flex size-full items-center justify-center text-primary opacity-40">
            <PawPrint size={44} aria-hidden="true" />
          </span>
        )}
        <span
          aria-hidden="true"
          className="absolute right-2 top-1/2 flex h-9 w-5 -translate-y-1/2 items-center justify-center rounded-lg bg-surface text-primary-dark"
        >
          <ChevronRight size={15} />
        </span>
      </div>
      <p className="truncate pt-2.5 text-body font-bold text-text-primary">{cat.name}</p>
      <p className="truncate text-caption text-text-secondary">{cat.breedName ?? cat.breedOther ?? cat.publicCode}</p>
      {/*
        Trạng thái nằm DƯỚI ảnh chứ không đè lên ảnh như Figma: nhãn dải do API trả có thể dài
        ("Trong khoảng tham chiếu · pH 6.5"), đè lên ô ảnh hẹp ở lưới desktop thì bị cắt chữ.
        Ở đây nó xuống dòng tự nhiên trong một khối bo góc.
      */}
      {lastScan === null ? (
        <p className="mt-2 w-fit max-w-full rounded-lg bg-surface px-2.5 py-1 text-caption font-semibold text-text-secondary">
          {t("mobile.catNoScan")}
        </p>
      ) : lastScan && band && bandStyle ? (
        <p
          className={`mt-2 w-fit max-w-full rounded-lg px-2.5 py-1 text-caption font-semibold ${bandStyle.bg} ${bandStyle.text}`}
        >
          {band.label}
          {lastScan.phValue != null ? (
            // "· pH 6.5" không bao giờ bị bẻ đôi khi nhãn dải dài phải xuống dòng.
            <>
              {" "}
              <span className="whitespace-nowrap">{t("web.catLastScanPh", { value: lastScan.phValue.toFixed(1) })}</span>
            </>
          ) : null}
        </p>
      ) : null}
    </Link>
  );
}

function InfoCell({
  label,
  value,
  note,
  noteTone = "neutral",
  noteIcon: NoteIcon,
}: {
  label: string;
  value: string;
  note?: string;
  noteTone?: "neutral" | "success" | "warning";
  noteIcon?: LucideIcon;
}) {
  return (
    <div className="rounded-xl bg-background-alt p-3.5">
      <dt className="text-[11px] font-semibold uppercase tracking-wide text-text-tertiary">{label}</dt>
      <dd className="break-words pt-1 text-body font-bold text-text-primary">{value}</dd>
      {note ? (
        <dd
          className={cn(
            "flex items-center gap-1 pt-0.5 text-[11px] leading-relaxed",
            noteTone === "success"
              ? "text-success-text"
              : noteTone === "warning"
                ? "text-warning-text"
                : "text-text-tertiary",
          )}
        >
          {NoteIcon && noteTone !== "neutral" ? <NoteIcon size={12} className="shrink-0" aria-hidden="true" /> : null}
          {note}
        </dd>
      ) : null}
    </div>
  );
}

function MobileGroup({ icon: Icon, title, children }: { icon: LucideIcon; title: string; children: React.ReactNode }) {
  return (
    <section className="rounded-2xl bg-surface p-4 shadow-brand-md">
      <h2 className="flex items-center gap-2 pb-1 text-h3 font-bold text-text-primary">
        <Icon size={18} className="text-primary" aria-hidden="true" />
        {title}
      </h2>
      <ul className="divide-y divide-deco-backdrop">{children}</ul>
    </section>
  );
}

/** Mọi hàng đều dẫn tới một route thật — không còn hàng "chỉ có trong thiết kế". */
function MobileRow({
  icon: Icon,
  to,
  title,
  note,
  action,
}: {
  icon: LucideIcon;
  to: string;
  title: string;
  note: string;
  action?: string;
}) {
  return (
    <li>
      <Link to={to} className="flex min-h-[var(--touch-target-min)] items-center gap-3 py-3">
        <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-deco-backdrop text-primary-dark">
          <Icon size={17} aria-hidden="true" />
        </span>
        <span className="min-w-0 flex-1">
          <span className="block truncate text-body font-bold text-text-primary">{title}</span>
          <span className="line-clamp-2 text-pretty text-caption text-text-secondary">{note}</span>
        </span>
        {action ? (
          <span className="shrink-0 rounded-full bg-chip-bg px-3 py-1 text-caption font-bold text-primary-dark">
            {action}
          </span>
        ) : (
          <ChevronRight size={17} className="shrink-0 text-text-tertiary" aria-hidden="true" />
        )}
      </Link>
    </li>
  );
}
