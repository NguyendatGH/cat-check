import { useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router";
import {
  BadgeCheck,
  BatteryFull,
  Bell,
  BookOpen,
  Building2,
  Camera,
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
  Phone,
  Plus,
  Radio,
  ScanLine,
  ShieldCheck,
  ShoppingBag,
  SlidersHorizontal,
  Timer,
  Truck,
  UserRound,
  Users,
  Wifi,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { Switch } from "@/shared/ui";
import { useSessionStore } from "@/entities/user";
import { PhBadge, usePhBands } from "@/entities/ph-bands";
import type { PhBand } from "@/entities/ph-bands";
import type { Cat } from "@/entities/cat";
import { useLogout, useProfile } from "@/features/auth";
import { useCatList } from "@/features/cat";
import { NotificationPreferencesForm } from "./NotificationPreferencesForm";
import {
  SETTINGS_ALERT_THRESHOLDS,
  SETTINGS_APP_VERSION,
  SETTINGS_DEVICE,
  SETTINGS_HERO,
  SETTINGS_STANDARD_CARD,
  SETTINGS_SUBSCRIPTION,
  SETTINGS_SUPPORT_HOTLINE,
  SETTINGS_VET_MEMBERSHIP,
  SETTINGS_VET_SHARING,
} from "./mockData";

/**
 * `/settings` — trang Cài đặt.
 *
 * Hai bản thiết kế KHÁC cấu trúc nên trang này dựng hai bố cục tách bạch:
 *   - mobile `15. Hồ sơ & cài đặt` (438×2753): thẻ hồ sơ → thẻ bé mèo → các nhóm danh sách →
 *     pháp lý → đăng xuất.
 *   - desktop `Web - 16. Cài đặt & Tùy chọn Ứng dụng` (1296×2141): hero → cột điều hướng
 *     trái + các khối nội dung bên phải.
 *
 * Dữ liệu THẬT: hồ sơ (`GET /users/me`), danh sách bé mèo (`GET /cats` — ảnh, tên, dải pH lần
 * quét gần nhất), dải pH (`GET /reference/ph-bands`), tuỳ chọn thông báo (B11/B12 qua
 * `NotificationPreferencesForm`), đăng xuất. Các khối phần cứng IoT / uỷ quyền phòng khám /
 * subscription KHÔNG có backend — lấy từ `mockData.ts` và gắn nhãn dữ liệu mẫu.
 *
 * Figma dựng hero mobile bằng một ẢNH CHÂN DUNG chủ nuôi; `GET /users/me` KHÔNG có cột ảnh
 * đại diện nào (`ProfileResponse` chỉ có id/email/fullName/phone/locale/timezone/...), nên
 * khung ảnh ở đây render chữ cái đầu trên nền trang trí thay vì bịa một tấm ảnh.
 *
 * Trang nằm trong `AppLayout`: layout KHÔNG cấp padding ngang ở mobile nhưng có cấp ở `lg`,
 * nên ở đây là `px-4 lg:px-0` (hộp nội dung desktop 944px).
 */

interface NavItem {
  key: string;
  icon: LucideIcon;
  to?: string;
  badge?: string;
  badgeTone?: "brand" | "success" | "warning";
  dot?: boolean;
}

const NAV_ITEMS: NavItem[] = [
  { key: "profile", icon: UserRound, to: "/settings/profile" },
  { key: "cats", icon: PawPrint, to: "/cats", badgeTone: "brand" },
  { key: "device", icon: Radio, dot: true },
  { key: "reminders", icon: Timer, to: "/reminders" },
  { key: "vetSharing", icon: Building2 },
  { key: "security", icon: ShieldCheck, to: "/settings/security" },
  { key: "subscription", icon: ShoppingBag, to: "/shop", badge: "VIP", badgeTone: "warning" },
];

const BADGE_TONES: Record<NonNullable<NavItem["badgeTone"]>, string> = {
  brand: "bg-chip-bg text-primary-dark",
  success: "bg-success-bg text-success-text",
  warning: "bg-warning-bg text-warning-text",
};

/** Icon đi kèm ba chip trạng thái phần cứng của Web-16 (cùng thứ tự `SETTINGS_DEVICE.chips`). */
const DEVICE_CHIP_ICONS: LucideIcon[] = [Wifi, BatteryFull, Camera];

/** Nền ô icon tròn-vuông đầu mỗi khối desktop — Figma đổi màu theo nhóm nội dung. */
const SECTION_ICON_TONES = {
  info: "bg-info text-primary-dark",
  brand: "bg-chip-bg text-primary-dark",
  amber: "bg-secondary-light text-secondary-text-on",
  amberStrong: "bg-secondary text-secondary-text-on",
  green: "bg-verified-bright text-verified-deep",
} as const;

type SectionIconTone = keyof typeof SECTION_ICON_TONES;

export function SettingsPage() {
  const { t } = useTranslation(["settings", "common"]);
  const navigate = useNavigate();
  const user = useSessionStore((s) => s.user);
  const { data: profile } = useProfile();
  const { data: cats } = useCatList("ACTIVE");
  const { data: phBands } = usePhBands();
  const logout = useLogout();

  const catItems = cats?.items ?? [];
  const catCount = catItems.length;
  const displayName = profile?.fullName ?? user?.displayName ?? "";
  const monogram = (displayName || "?").slice(0, 1).toUpperCase();

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
            <p className="flex flex-wrap items-center gap-3">
              <span className="rounded-full bg-info px-3 py-1 text-[11px] font-bold tracking-wide text-primary-dark">
                {SETTINGS_HERO.systemBadge}
              </span>
              <span className="inline-flex items-center gap-1.5 text-[12px] font-bold text-primary-dark">
                <span className="size-2 rounded-full bg-success-strong" />
                {SETTINGS_HERO.syncLabel}
              </span>
            </p>
            <h1 className="pt-2 text-[24px] font-bold text-primary-dark">{t("web.heroTitle")}</h1>
            <p className="max-w-[640px] pt-1.5 text-body leading-relaxed text-text-secondary">
              {t("web.heroBody")}
            </p>
          </div>
          <div className="flex shrink-0 gap-3">
            <Link
              to="/export"
              className="flex items-center gap-2 rounded-xl bg-deco-backdrop px-4 py-2.5 text-caption font-semibold text-primary-dark hover:bg-chip-bg"
            >
              <Download size={15} aria-hidden="true" />
              {t("web.exportCta")}
            </Link>
            <span className="flex items-center gap-2 rounded-xl bg-secondary px-4 py-2.5 text-caption font-semibold text-secondary-text-on">
              <BadgeCheck size={15} aria-hidden="true" />
              {t("web.isfmCta")}
            </span>
          </div>
        </section>

        <div className="flex items-start gap-5">
          <div className="flex w-[260px] shrink-0 flex-col gap-4">
            <nav className="rounded-2xl bg-surface p-4 shadow-brand-md" aria-label={t("web.navLabel")}>
              <p className="px-1 pb-2 text-[11px] font-bold uppercase tracking-wide text-text-tertiary">
                {t("web.navHeading")}
              </p>
              <ul className="flex flex-col gap-1">
                {NAV_ITEMS.map((item, index) => {
                  const Icon = item.icon;
                  const badge = item.key === "cats" ? t("web.catCount", { count: catCount }) : item.badge;
                  const active = index === 0;
                  const inner = (
                    <>
                      <Icon size={17} className="shrink-0" aria-hidden="true" />
                      <span className="min-w-0 flex-1 text-left text-caption font-semibold">
                        {t(`web.nav.${item.key}`)}
                      </span>
                      {badge ? (
                        <span
                          className={cn(
                            "shrink-0 rounded-md px-1.5 py-0.5 text-[10px] font-bold",
                            active ? "bg-white/20 text-white" : BADGE_TONES[item.badgeTone ?? "brand"],
                          )}
                        >
                          {badge}
                        </span>
                      ) : null}
                      {item.dot ? <span className="size-2 shrink-0 rounded-full bg-success" /> : null}
                      {item.to ? <ChevronRight size={15} className="shrink-0 opacity-60" aria-hidden="true" /> : null}
                    </>
                  );
                  const classes = cn(
                    "flex w-full items-center gap-2.5 rounded-xl px-3 py-2.5",
                    active
                      ? "bg-primary text-white"
                      : "text-text-secondary hover:bg-background-alt hover:text-text-primary",
                  );
                  return (
                    <li key={item.key}>
                      {item.to ? (
                        <Link to={item.to} className={classes}>
                          {inner}
                        </Link>
                      ) : (
                        <span className={cn(classes, "cursor-default")} title={t("web.designOnly")}>
                          {inner}
                        </span>
                      )}
                    </li>
                  );
                })}
              </ul>
            </nav>

            <section className="rounded-2xl bg-background-alt p-4">
              <h2 className="flex items-center gap-2 text-body font-bold text-primary-dark">
                <ShieldCheck size={16} aria-hidden="true" />
                {t("web.standardTitle")}
              </h2>
              <p className="pt-2 text-caption leading-relaxed text-text-secondary">
                {SETTINGS_STANDARD_CARD.body}
              </p>
              <p className="flex items-center justify-between gap-2 pt-3 text-[11px]">
                <span className="text-text-tertiary">{t("web.certificateLabel")}</span>
                <span className="font-bold text-primary">{SETTINGS_STANDARD_CARD.certificateCode}</span>
              </p>
            </section>
          </div>

          <div className="flex min-w-0 flex-1 flex-col gap-5">
            {/* Thông tin tài khoản — dữ liệu THẬT từ GET /users/me */}
            <section className="rounded-2xl bg-surface p-5 shadow-brand-md">
              <div className="flex items-start justify-between gap-4">
                <div className="flex items-center gap-3">
                  <span className={cn("flex size-10 shrink-0 items-center justify-center rounded-xl", SECTION_ICON_TONES.info)}>
                    <UserRound size={19} aria-hidden="true" />
                  </span>
                  <div>
                    <h2 className="text-[17px] font-bold text-text-primary">{t("web.accountTitle")}</h2>
                    <p className="text-caption text-text-secondary">{t("web.accountSubtitle")}</p>
                  </div>
                </div>
                <Link
                  to="/settings/profile"
                  className="flex shrink-0 items-center gap-1.5 rounded-xl bg-background-alt px-3.5 py-2 text-caption font-semibold text-primary hover:bg-chip-bg"
                >
                  <Pencil size={14} aria-hidden="true" />
                  {t("web.edit")}
                </Link>
              </div>

              <dl className="grid grid-cols-2 gap-4 pt-4">
                <InfoCell label={t("web.fieldName")} value={displayName} note={t("web.primaryGuardian")} />
                <InfoCell
                  label={t("web.fieldEmail")}
                  value={profile?.email ?? ""}
                  note={profile?.emailVerified ? t("web.emailVerified") : t("web.emailUnverified")}
                  noteTone={profile?.emailVerified ? "success" : "warning"}
                  noteIcon={BadgeCheck}
                />
                <InfoCell
                  label={t("web.fieldPhone")}
                  value={profile?.phone ?? t("web.phoneEmpty")}
                  note={t("web.phoneNote")}
                />
                <InfoCell
                  label={t("web.fieldVetMember")}
                  value={SETTINGS_VET_MEMBERSHIP.id}
                  valueTone="muted"
                  chip={SETTINGS_VET_MEMBERSHIP.badge}
                  chipIcon={BadgeCheck}
                  mock
                />
              </dl>
            </section>

            {/* Thiết bị IoT — không có backend */}
            <MockSection
              icon={Radio}
              iconTone="brand"
              title={t("web.deviceTitle")}
              subtitle={t("web.deviceSubtitle")}
              rightSlot={
                <span className="inline-flex shrink-0 items-center gap-1.5 whitespace-nowrap rounded-full bg-verified-bright px-3 py-1 text-[12px] font-bold text-verified-deep">
                  <span className="size-2 rounded-full bg-verified-deep" />
                  {SETTINGS_DEVICE.statusLabel}
                </span>
              }
            >
              <div className="rounded-xl bg-background-alt p-4">
                <div className="flex items-start gap-3">
                  <span className="flex h-14 w-10 shrink-0 items-center justify-center rounded-xl bg-surface text-primary-dark">
                    <ScanLine size={19} aria-hidden="true" />
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="text-body font-bold text-text-primary">{SETTINGS_DEVICE.name}</p>
                    <p className="text-caption text-text-secondary">
                      {t("web.deviceMeta", { location: SETTINGS_DEVICE.location, mac: SETTINGS_DEVICE.mac })}
                    </p>
                  </div>
                  <ul className="flex w-[290px] shrink-0 flex-wrap content-start gap-2">
                    {SETTINGS_DEVICE.chips.map((chip, index) => {
                      const ChipIcon = DEVICE_CHIP_ICONS[index] ?? Info;
                      return (
                        <li
                          key={chip}
                          className="flex items-center gap-1.5 rounded-lg bg-surface px-2.5 py-1 text-[11px] font-medium text-text-secondary"
                        >
                          <ChipIcon size={13} className="shrink-0 text-primary" aria-hidden="true" />
                          {chip}
                        </li>
                      );
                    })}
                  </ul>
                </div>
                <div className="mt-4 flex items-center gap-4 rounded-xl bg-surface p-3.5">
                  <div className="min-w-0 flex-1">
                    <p className="text-body font-bold text-text-primary">{SETTINGS_DEVICE.autoCaptureTitle}</p>
                    <p className="pt-0.5 text-caption leading-relaxed text-text-secondary">
                      {SETTINGS_DEVICE.autoCaptureBody}
                    </p>
                  </div>
                  <Switch checked disabled aria-label={SETTINGS_DEVICE.autoCaptureTitle} />
                </div>
              </div>
            </MockSection>

            {/* Thông báo — dữ liệu THẬT (B11/B12) */}
            <section className="rounded-2xl bg-surface p-5 shadow-brand-md">
              <div className="flex items-center gap-3">
                <span className={cn("flex size-10 shrink-0 items-center justify-center rounded-xl", SECTION_ICON_TONES.amber)}>
                  <Bell size={19} aria-hidden="true" />
                </span>
                <div>
                  <h2 className="text-[17px] font-bold text-text-primary">{t("web.alertsTitle")}</h2>
                  <p className="text-caption text-text-secondary">{t("web.alertsSubtitle")}</p>
                </div>
              </div>

              <NotificationPreferencesForm className="pt-4" />

              <div className="mt-5 rounded-xl bg-background-alt p-4">
                <p className="pb-2.5 text-[11px] font-bold uppercase tracking-wide text-text-tertiary">
                  {t("web.thresholdsLegend")}
                </p>
                <ul className="flex flex-col gap-2">
                  {SETTINGS_ALERT_THRESHOLDS.map((item) => (
                    <li
                      key={item.key}
                      className="flex items-start gap-3 rounded-lg bg-surface p-3"
                    >
                      <span
                        aria-hidden="true"
                        className={cn(
                          "mt-0.5 flex size-4 shrink-0 items-center justify-center rounded-sm border",
                          item.supported ? "border-primary bg-primary" : "border-border-strong",
                        )}
                      />
                      <span className="min-w-0">
                        <span className="block text-caption font-semibold text-text-primary">{item.title}</span>
                        <span className="block pt-0.5 text-[11px] leading-relaxed text-text-tertiary">
                          {item.body}
                        </span>
                      </span>
                      {!item.supported ? (
                        <span className="ml-auto shrink-0 rounded-md bg-background-alt px-2 py-0.5 text-[10px] font-semibold text-text-tertiary">
                          {t("web.outOfScope")}
                        </span>
                      ) : null}
                    </li>
                  ))}
                </ul>
              </div>
            </section>

            {/* Uỷ quyền phòng khám — không có backend */}
            <MockSection
              icon={Building2}
              iconTone="green"
              title={t("web.vetSharingTitle")}
              subtitle={t("web.vetSharingSubtitle")}
              rightSlot={
                <Link
                  to="/account/privacy"
                  className="flex shrink-0 items-center gap-2 rounded-xl bg-background-alt px-4 py-3 text-caption font-bold text-primary hover:bg-chip-bg"
                >
                  <KeyRound size={15} aria-hidden="true" />
                  {t("web.vetManageAccess")}
                </Link>
              }
            >
              <div className="rounded-xl bg-background-alt p-4">
                <div className="flex items-start gap-3">
                  <span className="flex size-12 shrink-0 items-center justify-center rounded-xl bg-surface text-primary-dark">
                    <Building2 size={20} aria-hidden="true" />
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <p className="text-body font-bold text-text-primary">{SETTINGS_VET_SHARING.clinicName}</p>
                      <span className="rounded-full bg-verified-bright px-3 py-1 text-[11px] font-bold text-verified-deep">
                        {SETTINGS_VET_SHARING.statusLabel}
                      </span>
                    </div>
                    <p className="pt-2 text-caption leading-relaxed text-text-secondary">
                      {SETTINGS_VET_SHARING.scope}
                    </p>
                    <p className="flex flex-wrap items-center gap-x-5 gap-y-1 pt-3 text-[11px] text-text-tertiary">
                      <span>
                        {t("web.vetDoctor")} <b className="text-text-secondary">{SETTINGS_VET_SHARING.doctorName}</b>
                      </span>
                      <span>
                        {t("web.vetUpdatedAt")}{" "}
                        <b className="text-text-secondary">{SETTINGS_VET_SHARING.updatedAtLabel}</b>
                      </span>
                      <span className="ml-auto flex items-center gap-1.5 rounded-lg bg-surface px-2.5 py-1 font-bold text-primary">
                        <KeyRound size={12} aria-hidden="true" />
                        {t("web.vetToken", { token: SETTINGS_VET_SHARING.token })}
                      </span>
                    </p>
                  </div>
                </div>
              </div>
            </MockSection>

            {/* Subscription — không có backend */}
            <MockSection
              icon={Truck}
              iconTone="amberStrong"
              title={t("web.subscriptionTitle")}
              subtitle={t("web.subscriptionSubtitle")}
              rightSlot={
                <span className="shrink-0 whitespace-nowrap rounded-full bg-secondary-light px-3 py-1 text-[11px] font-bold text-secondary-text-on">
                  {SETTINGS_SUBSCRIPTION.savingBadge}
                </span>
              }
            >
              <dl className="grid grid-cols-3 gap-4">
                <InfoCell
                  label={t("web.subCurrentPlan")}
                  value={SETTINGS_SUBSCRIPTION.planName}
                  note={SETTINGS_SUBSCRIPTION.planNote}
                  accent
                />
                <InfoCell
                  label={t("web.subNextDelivery")}
                  value={SETTINGS_SUBSCRIPTION.nextDeliveryDate}
                  note={SETTINGS_SUBSCRIPTION.nextDeliveryNote}
                  noteTone="success"
                />
                <InfoCell label={t("web.subAddress")} value={SETTINGS_SUBSCRIPTION.address} />
              </dl>
            </MockSection>
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
                  <CatTile cat={cat} bands={phBands} />
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

        <MobileGroup icon={Radio} title={t("mobile.groupMonitoring")}>
          <MobileRow icon={Timer} to="/reminders" title={t("mobile.rows.reminders.title")} note={t("mobile.rows.reminders.note")} />
          <MobileRow icon={Bell} to="/settings/notifications" title={t("mobile.rows.notifications.title")} note={t("mobile.rows.notifications.note")} />
          {/* Khối phần cứng của thiết kế — chưa có backend, dùng `SETTINGS_DEVICE` và gắn nhãn. */}
          <MobileRow
            icon={ScanLine}
            title={t("mobile.rows.device.title")}
            note={`${SETTINGS_DEVICE.name} (${SETTINGS_DEVICE.statusLabel})`}
            dot
            mock
          />
        </MobileGroup>

        <MobileGroup icon={FileText} title={t("mobile.groupData")}>
          <MobileRow
            icon={Download}
            to="/export"
            title={t("mobile.rows.export.title")}
            note={t("mobile.rows.export.note")}
            action={t("mobile.exportAction")}
            actionTone="brand"
          />
          {/* Phòng khám liên kết — chưa có backend, dùng `SETTINGS_VET_SHARING` và gắn nhãn. */}
          <MobileRow
            icon={Building2}
            title={t("mobile.rows.clinic.title")}
            note={`${SETTINGS_VET_SHARING.clinicName} (${SETTINGS_VET_SHARING.statusLabel})`}
            dot
            mock
          />
          <MobileRow icon={KeyRound} to="/settings/security" title={t("mobile.rows.security.title")} note={t("mobile.rows.security.note")} />
          <MobileRow icon={ShieldCheck} to="/account/privacy" title={t("mobile.rows.privacy.title")} note={t("mobile.rows.privacy.note")} />
          <MobileRow icon={Languages} to="/settings/language" title={t("mobile.rows.language.title")} note={t("mobile.rows.language.note")} />
        </MobileGroup>

        <MobileGroup icon={Headphones} title={t("mobile.groupSupport")}>
          <MobileRow icon={BookOpen} to="/community" title={t("mobile.rows.guides.title")} note={t("mobile.rows.guides.note")} />
          <MobileRow
            icon={Phone}
            href={`tel:${SETTINGS_SUPPORT_HOTLINE.number.replace(/-/g, "")}`}
            title={t("mobile.rows.hotline.title")}
            note={`${SETTINGS_SUPPORT_HOTLINE.number} (${SETTINGS_SUPPORT_HOTLINE.hours})`}
            action={t("mobile.call")}
          />
          <MobileRow icon={Mail} to="/legal/contact" title={t("mobile.rows.contact.title")} note={t("mobile.rows.contact.note")} />
        </MobileGroup>

        <section className="flex gap-2.5 rounded-2xl bg-chip-bg p-4">
          <Info size={17} className="mt-0.5 shrink-0 text-warning" aria-hidden="true" />
          <div>
            <p className="text-caption font-bold uppercase tracking-wide text-text-primary">
              {t("mobile.disclaimerTitle")}
            </p>
            <p className="pt-1 text-caption leading-relaxed text-text-secondary">{t("mobile.disclaimerBody")}</p>
          </div>
        </section>

        <nav className="flex flex-wrap justify-center gap-x-3 gap-y-1 text-caption font-bold text-text-primary">
          <Link to="/legal/terms" className="hover:underline">{t("mobile.legalTerms")}</Link>
          <span aria-hidden="true" className="text-text-tertiary">•</span>
          <Link to="/legal/privacy" className="hover:underline">{t("mobile.legalPrivacy")}</Link>
          <span aria-hidden="true" className="text-text-tertiary">•</span>
          <Link to="/legal/medical-disclaimer" className="hover:underline">{t("mobile.legalSafety")}</Link>
        </nav>
        <p className="text-center text-[11px] leading-relaxed text-text-tertiary">
          {t("mobile.versionLine", { version: SETTINGS_APP_VERSION })}
        </p>

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

      {/* Đăng xuất bản desktop nằm cuối trang, tách khỏi khối nội dung */}
      <div className="hidden justify-end lg:flex">
        <button
          type="button"
          onClick={() => {
            onLogout();
            void navigate("/auth/login");
          }}
          disabled={logout.isPending}
          className="flex items-center gap-2 rounded-xl border border-border px-4 py-2.5 text-caption font-semibold text-danger hover:bg-danger-bg disabled:opacity-50"
        >
          <LogOut size={15} aria-hidden="true" />
          {t("mobile.logout")}
        </button>
      </div>
    </div>
  );
}

/* ---------------- Thành phần phụ dùng trong trang ---------------- */

/**
 * Ô ảnh một bé mèo ở bản mobile (Figma: ảnh bo góc + pill trạng thái + mũi tên bên phải).
 * Ảnh lấy từ `cat.avatarUrl` — mèo chưa có ảnh thì hiện khung giữ chỗ, KHÔNG chèn ảnh mẫu.
 * Pill trạng thái dùng `PhBadge` với dải tra từ `GET /reference/ph-bands`, không tự đặt nhãn.
 */
function CatTile({ cat, bands }: { cat: Cat; bands: PhBand[] | undefined }) {
  const { t } = useTranslation("settings");
  const band = cat.lastClassification
    ? bands?.find((item) => item.code === cat.lastClassification)
    : undefined;

  return (
    <Link to={`/cats/${cat.id}`} className="block rounded-xl bg-background-alt p-3">
      <div className="relative h-40 overflow-hidden rounded-xl bg-deco-backdrop">
        {cat.avatarUrl ? (
          <img src={cat.avatarUrl} alt={cat.name} className="size-full object-cover" />
        ) : (
          <span className="flex size-full items-center justify-center text-primary opacity-40">
            <PawPrint size={44} aria-hidden="true" />
          </span>
        )}
        {band ? (
          <PhBadge band={band} className="absolute right-2 top-2 shadow-xs" />
        ) : (
          <span className="absolute right-2 top-2 rounded-full bg-surface px-3 py-1 text-caption font-semibold text-text-secondary shadow-xs">
            {t("mobile.catNoScan")}
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
      <p className="truncate text-caption text-text-secondary">
        {cat.breedName ?? cat.breedOther ?? cat.publicCode}
      </p>
    </Link>
  );
}

function InfoCell({
  label,
  value,
  valueTone = "strong",
  note,
  noteTone = "neutral",
  noteIcon: NoteIcon,
  chip,
  chipIcon: ChipIcon,
  accent = false,
  mock = false,
}: {
  label: string;
  value: string;
  valueTone?: "strong" | "muted";
  note?: string;
  noteTone?: "neutral" | "success" | "warning";
  noteIcon?: LucideIcon;
  chip?: string;
  chipIcon?: LucideIcon;
  accent?: boolean;
  mock?: boolean;
}) {
  const { t } = useTranslation("settings");
  return (
    <div className="rounded-xl bg-background-alt p-3.5">
      <dt className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-wide text-text-tertiary">
        {label}
        {mock ? (
          <span className="rounded bg-surface px-1.5 py-0.5 text-[9px] font-bold normal-case text-text-tertiary">
            {t("web.mockBadge")}
          </span>
        ) : null}
      </dt>
      {chip ? (
        <dd className="pt-1.5">
          <span className="inline-flex items-center gap-1.5 rounded-full bg-secondary px-2.5 py-1 text-[11px] font-bold text-secondary-text-on">
            {ChipIcon ? <ChipIcon size={13} aria-hidden="true" /> : null}
            {chip}
          </span>
        </dd>
      ) : null}
      <dd
        className={cn(
          "break-words pt-1",
          chip || valueTone === "muted"
            ? "text-caption font-semibold text-text-tertiary"
            : accent
              ? "text-body font-bold text-primary-dark"
              : "text-body font-bold text-text-primary",
        )}
      >
        {value}
      </dd>
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

function MockSection({
  icon: Icon,
  iconTone = "brand",
  title,
  subtitle,
  rightSlot,
  children,
}: {
  icon: LucideIcon;
  iconTone?: SectionIconTone;
  title: string;
  subtitle: string;
  rightSlot?: React.ReactNode;
  children: React.ReactNode;
}) {
  const { t } = useTranslation("settings");
  return (
    <section className="rounded-2xl bg-surface p-5 shadow-brand-md">
      <div className="flex items-start justify-between gap-4">
        <div className="flex items-center gap-3">
          <span className={cn("flex size-10 shrink-0 items-center justify-center rounded-xl", SECTION_ICON_TONES[iconTone])}>
            <Icon size={19} aria-hidden="true" />
          </span>
          <div>
            <h2 className="flex flex-wrap items-center gap-2 text-[17px] font-bold text-text-primary">
              {title}
              <span className="rounded bg-background-alt px-1.5 py-0.5 text-[10px] font-bold text-text-tertiary">
                {t("web.mockBadge")}
              </span>
            </h2>
            <p className="text-caption text-text-secondary">{subtitle}</p>
          </div>
        </div>
        {rightSlot}
      </div>
      <div className="pt-4">{children}</div>
    </section>
  );
}

function MobileGroup({
  icon: Icon,
  title,
  children,
}: {
  icon: LucideIcon;
  title: string;
  children: React.ReactNode;
}) {
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

function MobileRow({
  icon: Icon,
  to,
  href,
  title,
  note,
  action,
  actionTone = "secondary",
  dot = false,
  mock = false,
}: {
  icon: LucideIcon;
  to?: string;
  href?: string;
  title: string;
  note: string;
  action?: string;
  actionTone?: "secondary" | "brand";
  dot?: boolean;
  mock?: boolean;
}) {
  const { t } = useTranslation("settings");
  const inner = (
    <>
      <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-deco-backdrop text-primary-dark">
        <Icon size={17} aria-hidden="true" />
      </span>
      <span className="min-w-0 flex-1">
        <span className="flex items-center gap-1.5">
          <span className="truncate text-body font-bold text-text-primary">{title}</span>
          {mock ? (
            <span className="shrink-0 rounded bg-background-alt px-1.5 py-0.5 text-[9px] font-bold text-text-tertiary">
              {t("web.mockBadge")}
            </span>
          ) : null}
        </span>
        <span className="block truncate text-caption text-text-secondary">{note}</span>
      </span>
      {action ? (
        <span
          className={cn(
            "shrink-0 rounded-full px-3 py-1 text-caption font-bold",
            actionTone === "brand" ? "bg-chip-bg text-primary-dark" : "bg-secondary text-secondary-text-on",
          )}
        >
          {action}
        </span>
      ) : dot ? (
        <span aria-hidden="true" className="size-2.5 shrink-0 rounded-full bg-success" />
      ) : (
        <ChevronRight size={17} className="shrink-0 text-text-tertiary" aria-hidden="true" />
      )}
    </>
  );
  const classes = "flex min-h-[var(--touch-target-min)] items-center gap-3 py-3";
  if (href) {
    return (
      <li>
        <a href={href} className={classes}>
          {inner}
        </a>
      </li>
    );
  }
  if (!to) {
    // Hàng chỉ có trong thiết kế (chưa có route/backend) — không bọc link để không dẫn đi đâu cả.
    return (
      <li>
        <div className={classes} title={t("web.designOnly")}>
          {inner}
        </div>
      </li>
    );
  }
  return (
    <li>
      <Link to={to} className={classes}>
        {inner}
      </Link>
    </li>
  );
}
