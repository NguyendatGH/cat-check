import { useCallback, useMemo, useRef, useState } from "react";
import { Link, Outlet, useLocation, useNavigate } from "react-router";
import { useTranslation } from "react-i18next";
import { useGSAP } from "@gsap/react";
import { gsap } from "gsap";
import { Avatar, Button as AntButton, ConfigProvider, Menu as AntMenu, type MenuProps } from "antd";
import {
  Archive,
  ArrowLeft,
  ChevronRight,
  BellRing,
  BookOpenText,
  CalendarClock,
  FileCheck2,
  Flag,
  History,
  Inbox,
  KeyRound,
  LayoutDashboard,
  type LucideIcon,
  Megaphone,
  Menu,
  Package,
  Palette,
  ScanLine,
  ScrollText,
  Send,
  Settings,
  ShieldAlert,
  ShieldCheck,
  ShoppingBag,
  UserCog,
  Users,
  LogOut,
  Search,
  X,
} from "lucide-react";
import { Button, Sheet, SheetClose, SheetContent, SheetTitle } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import { useSessionStore } from "@/entities/user";
import {
  AdminAccessProvider,
  computeCapabilities,
  filterNavGroups,
  findActiveNavItem,
  type AdminCapabilities,
  type AdminNavGroup,
  type AdminNavItemBase,
  type RoleCheck,
} from "@/features/admin";
import { ForbiddenPage } from "@/pages/system/ForbiddenPage";
import { ROUTE_PATTERNS } from "../router/routes";
import { ROLES, hasAnyRole } from "@/shared/auth/roles";
import { adminTheme } from "./adminTheme";
import "./adminLayout.css";

gsap.registerPlugin(useGSAP);

interface AdminNavItem extends AdminNavItemBase {
  labelKey: string;
  icon: LucideIcon;
}

const ADMIN_NAV: readonly AdminNavGroup<AdminNavItem>[] = [
  {
    id: "overview",
    labelKey: "nav.groups.overview",
    items: [
      {
        id: "dashboard",
        to: ROUTE_PATTERNS.adminDashboard,
        end: true,
        labelKey: "nav.dashboard",
        icon: LayoutDashboard,
        roles: ROLES.dashboard,
      },
    ],
  },
  {
    id: "people",
    labelKey: "nav.groups.people",
    items: [
      { id: "users", to: ROUTE_PATTERNS.adminUsers, labelKey: "nav.users", icon: Users, roles: ROLES.users },
      { id: "staff", to: ROUTE_PATTERNS.adminStaff, labelKey: "nav.staff", icon: UserCog, roles: ROLES.staff },
      { id: "scans", to: ROUTE_PATTERNS.adminScans, labelKey: "nav.scans", icon: ScanLine, roles: ROLES.scans },
      {
        id: "consents",
        to: ROUTE_PATTERNS.adminPrivacyConsents,
        labelKey: "nav.consents",
        icon: FileCheck2,
        roles: ROLES.consentsRead,
      },
    ],
  },
  {
    id: "billing",
    labelKey: "nav.groups.billing",
    items: [
      {
        id: "packages",
        to: ROUTE_PATTERNS.adminPackages,
        labelKey: "nav.packages",
        icon: Package,
        roles: ROLES.packagesRead,
      },
      {
        id: "activationCodes",
        to: ROUTE_PATTERNS.adminActivationCodes,
        labelKey: "nav.activationCodes",
        icon: KeyRound,
        roles: ROLES.activationCodes,
      },
    ],
  },
  {
    id: "catalog",
    labelKey: "nav.groups.catalog",
    items: [
      {
        id: "products",
        to: ROUTE_PATTERNS.adminProducts,
        labelKey: "nav.products",
        icon: ShoppingBag,
        roles: ROLES.products,
      },
      {
        id: "phColorChart",
        to: ROUTE_PATTERNS.adminPhColorChart,
        labelKey: "nav.phColorChart",
        icon: Palette,
        roles: ROLES.colorChart,
      },
      {
        id: "content",
        to: ROUTE_PATTERNS.adminContent,
        labelKey: "nav.content",
        icon: BookOpenText,
        roles: ROLES.careTipsWrite,
      },
      {
        id: "monitoringRules",
        to: ROUTE_PATTERNS.adminMonitoringRules,
        labelKey: "nav.monitoringRules",
        icon: BellRing,
        roles: ROLES.monitoringRulesRead,
      },
    ],
  },
  {
    id: "community",
    labelKey: "nav.groups.community",
    items: [
      {
        id: "communityReports",
        to: ROUTE_PATTERNS.adminCommunityReports,
        labelKey: "nav.communityReports",
        icon: Flag,
        roles: ROLES.communityModeration,
      },
    ],
  },
  {
    id: "privacy",
    labelKey: "nav.groups.privacy",
    items: [
      {
        id: "privacyRequests",
        to: ROUTE_PATTERNS.adminPrivacyRequests,
        labelKey: "nav.privacyRequests",
        icon: Inbox,
        roles: ROLES.privacyRequests,
      },
      {
        id: "retention",
        to: ROUTE_PATTERNS.adminPrivacyRetention,
        labelKey: "nav.retention",
        icon: Archive,
        roles: ROLES.retention,
      },
      {
        id: "policyVersions",
        to: ROUTE_PATTERNS.adminPolicyVersions,
        labelKey: "nav.policyVersions",
        icon: ScrollText,
        roles: ROLES.policyVersionsRead,
      },
      {
        id: "incidents",
        to: ROUTE_PATTERNS.adminSecurity,
        tab: "incidents",
        defaultTab: true,
        labelKey: "nav.incidents",
        icon: ShieldAlert,
        roles: ROLES.securityIncidentsRead,
      },
      {
        id: "sessions",
        to: ROUTE_PATTERNS.adminSecurity,
        tab: "sessions",
        labelKey: "nav.sessions",
        icon: LogOut,
        roles: ROLES.securitySessionsPurge,
      },
    ],
  },
  {
    id: "ops",
    labelKey: "nav.groups.ops",
    items: [
      { id: "jobs", to: ROUTE_PATTERNS.adminJobs, labelKey: "nav.jobs", icon: CalendarClock, roles: ROLES.jobs },
      {
        id: "outbox",
        to: ROUTE_PATTERNS.adminNotificationsOutbox,
        labelKey: "nav.outbox",
        icon: Send,
        roles: ROLES.outbox,
      },
      {
        id: "broadcast",
        to: ROUTE_PATTERNS.adminBroadcast,
        labelKey: "nav.broadcast",
        icon: Megaphone,
        roles: ROLES.broadcast,
      },
      {
        id: "auditLog",
        to: ROUTE_PATTERNS.adminAuditLog,
        labelKey: "nav.auditLog",
        icon: History,
        roles: ROLES.auditLog,
      },
      {
        id: "settings",
        to: ROUTE_PATTERNS.adminMeSecurity,
        labelKey: "nav.settings",
        icon: Settings,
        roles: ROLES.adminPortal,
      },
    ],
  },
];

function hrefOf(item: AdminNavItem): string {
  return item.tab === undefined ? item.to : `${item.to}?tab=${item.tab}`;
}

interface AdminNavListProps {
  groups: readonly AdminNavGroup<AdminNavItem>[];
  location: { pathname: string; search: string };
  touch?: boolean;
  onNavigate?: () => void;
}

function AdminNavList({ groups, location, touch = false, onNavigate }: AdminNavListProps) {
  const { t } = useTranslation("admin");
  const activeItem = findActiveNavItem(groups, location);
  const activeGroup = groups.find((group) => group.items.some((item) => item.id === activeItem?.id));
  const [openGroup, setOpenGroup] = useState<string | null>(
    activeGroup?.id === "overview" ? null : (activeGroup?.id ?? null),
  );
  const navRef = useRef<HTMLDivElement>(null);

  useGSAP(
    () => {
      if (!openGroup || !navRef.current || window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
      const rows = navRef.current.querySelectorAll<HTMLElement>(
        ".ant-menu-submenu-open .ant-menu-sub > .ant-menu-item",
      );
      if (rows.length === 0) return;
      gsap.fromTo(
        rows,
        { autoAlpha: 0, y: -20 },
        {
          autoAlpha: 1,
          y: 0,
          duration: 0.2,
          stagger: 0.025,
          ease: "ease-out",
          clearProps: "opacity,visibility,transform",
        },
      );
    },
    { scope: navRef, dependencies: [openGroup], revertOnUpdate: true },
  );

  const navItem = (item: AdminNavItem) => {
    const Icon = item.icon;
    return {
      key: item.id,
      icon: <Icon size={17} aria-hidden="true" />,
      label: (
        <Link to={hrefOf(item)} onClick={onNavigate} aria-current={activeItem?.id === item.id ? "page" : undefined}>
          {t(item.labelKey)}
        </Link>
      ),
    };
  };
  const items: MenuProps["items"] = groups.map((group) =>
    group.id === "overview" && group.items.length === 1
      ? navItem(group.items[0])
      : {
          key: group.id,
          label: t(group.labelKey),
          children: group.items.map(navItem),
        },
  );
  return (
    <div ref={navRef}>
      <AntMenu
        aria-label={t("nav.label")}
        className="admin-nav-menu"
        theme={touch ? "light" : "dark"}
        mode="inline"
        selectedKeys={activeItem ? [activeItem.id] : []}
        openKeys={openGroup ? [openGroup] : []}
        onOpenChange={(keys) => setOpenGroup(keys.at(-1) ?? null)}
        items={items}
        inlineIndent={touch ? 16 : 20}
        style={{ borderInlineEnd: 0, background: "transparent" }}
      />
    </div>
  );
}

interface AdminMobileDrawerProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  groups: readonly AdminNavGroup<AdminNavItem>[];
  location: { pathname: string; search: string };
  user: ReturnType<typeof useSessionStore.getState>["user"];
  onNavigate: () => void;
}

function AdminMobileDrawer({
  open,
  onOpenChange,
  groups,
  location,
  user,
  onNavigate,
}: AdminMobileDrawerProps) {
  const { t } = useTranslation("admin");
  const navigate = useNavigate();
  const [searchQuery, setSearchQuery] = useState("");
  const activeItem = findActiveNavItem(groups, location);
  const [collapsedGroups, setCollapsedGroups] = useState<Record<string, boolean>>({});

  const toggleGroup = (groupId: string) => {
    setCollapsedGroups((prev) => ({ ...prev, [groupId]: !prev[groupId] }));
  };

  const normalizedQuery = searchQuery.trim().toLowerCase();

  const filteredGroups = useMemo(() => {
    if (!normalizedQuery) return groups;
    return groups
      .map((group) => {
        const matchingItems = group.items.filter((item) => {
          const label = t(item.labelKey).toLowerCase();
          return label.includes(normalizedQuery) || item.id.toLowerCase().includes(normalizedQuery);
        });
        return { ...group, items: matchingItems };
      })
      .filter((group) => group.items.length > 0);
  }, [groups, normalizedQuery, t]);

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side="left"
        aria-describedby={undefined}
        className="admin-mobile-drawer !p-0 !max-w-[340px] w-[86vw] bg-[#14213d] text-white border-r border-white/10 flex flex-col h-dvh overflow-hidden shadow-lg"
      >
        <SheetTitle className="sr-only">{t("nav.label")}</SheetTitle>

        <div className="border-b border-white/10 bg-[#0f1a30] px-4 pt-5 pb-3 shrink-0">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2.5">
              <span className="flex size-9 items-center justify-center rounded-lg bg-primary text-white">
                <ShieldCheck size={19} aria-hidden="true" />
              </span>
              <div className="min-w-0">
                <p className="truncate text-[15px] font-extrabold tracking-tight text-white leading-tight">
                  {t("nav.brand")}
                </p>
                <p className="truncate text-[11px] font-medium text-slate-300">
                  {t("nav.label")}
                </p>
              </div>
            </div>
            <SheetClose asChild>
              <button
                type="button"
                aria-label={t("actions.close", "Đóng")}
                className="flex size-11 items-center justify-center rounded-md border border-white/10 bg-white/5 text-slate-300 hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
              >
                <X size={17} aria-hidden="true" />
              </button>
            </SheetClose>
          </div>

          <div className="mt-3 flex items-center justify-between">
            <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-500/15 px-2.5 py-0.5 text-[11px] font-semibold text-emerald-300 border border-emerald-500/30">
              <span className="size-1.5 rounded-full bg-emerald-400 animate-pulse" />
              Online Console
            </span>
            <span className="truncate max-w-[120px] rounded-md bg-white/10 px-2 py-0.5 text-[10px] font-mono font-medium text-slate-300 border border-white/10">
              {user?.roles?.[0] ?? "ADMIN"}
            </span>
          </div>

          <div className="mt-2.5 flex items-center gap-2.5 rounded-lg bg-white/[0.06] p-2 border border-white/10">
            <Avatar size={32} className="!rounded-md !bg-primary" icon={<ShieldCheck size={16} aria-hidden="true" />} />
            <div className="min-w-0 flex-1">
              <p className="truncate text-[12px] font-bold text-white">{user?.displayName ?? "Admin"}</p>
              <p className="truncate text-[10px] text-slate-400">Quản trị viên</p>
            </div>
            <Link
              to={ROUTE_PATTERNS.adminMeSecurity}
              onClick={onNavigate}
              aria-label={t("nav.settings")}
              className="flex size-11 items-center justify-center rounded-md bg-white/10 text-slate-200 hover:text-white hover:bg-white/20 transition-colors cursor-pointer"
            >
              <Settings size={13} aria-hidden="true" />
            </Link>
          </div>
        </div>

        <div className="px-3.5 pt-3 pb-1 shrink-0">
          <div className="relative">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none" aria-hidden="true" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder={t("actions.search", "Tìm tính năng...")}
              className="h-10 w-full rounded-md bg-white/5 border border-white/15 pl-8 pr-8 text-[13px] text-white placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent transition-colors"
            />
            {searchQuery ? (
              <button
                type="button"
                onClick={() => setSearchQuery("")}
                className="absolute right-1 top-1/2 flex size-10 -translate-y-1/2 items-center justify-center text-slate-400 hover:text-white"
                aria-label="Xoá tìm kiếm"
              >
                <X size={13} aria-hidden="true" />
              </button>
            ) : null}
          </div>
        </div>

        <nav aria-label={t("nav.label")} className="admin-mobile-scroll flex-1 overflow-y-auto px-3 py-2 space-y-3">
          {filteredGroups.length === 0 ? (
            <div className="py-8 text-center text-slate-400 text-[12px]">
              <p>Không tìm thấy tính năng phù hợp</p>
              <button
                type="button"
                onClick={() => setSearchQuery("")}
                className="mt-2 text-primary-light underline font-medium cursor-pointer"
              >
                Xoá bộ lọc
              </button>
            </div>
          ) : (
            filteredGroups.map((group) => {
              const isCollapsed = !normalizedQuery && collapsedGroups[group.id];
              return (
                <div key={group.id} className="space-y-1">
                  <button
                    type="button"
                    onClick={() => toggleGroup(group.id)}
                    className="flex min-h-11 w-full items-center justify-between px-2 text-[11px] font-bold tracking-wider text-slate-400 uppercase hover:text-slate-200 transition-colors cursor-pointer"
                  >
                    <span>{t(group.labelKey)}</span>
                    <span className="flex items-center gap-1.5 text-[10px] text-slate-400 bg-white/5 px-1.5 py-0.5 rounded-md font-mono">
                      {group.items.length}
                    </span>
                  </button>

                  {!isCollapsed ? (
                    <div className="space-y-1">
                      {group.items.map((item) => {
                        const isActive = activeItem?.id === item.id;
                        const Icon = item.icon;
                        return (
                          <Link
                            key={item.id}
                            to={hrefOf(item)}
                            onClick={onNavigate}
                            aria-current={isActive ? "page" : undefined}
                            className={cn(
                              "group flex min-h-12 items-center gap-3 rounded-md px-2.5 py-2 text-[13px] font-semibold transition-colors",
                              isActive
                                ? "bg-primary text-white"
                                : "text-slate-300 hover:bg-white/8 hover:text-white active:bg-white/10"
                            )}
                          >
                            <span
                              className={cn(
                                "flex size-8 shrink-0 items-center justify-center rounded-md transition-colors",
                                isActive
                                  ? "bg-white/20 text-white"
                                  : "bg-white/5 text-slate-300 group-hover:bg-white/10 group-hover:text-white"
                              )}
                              aria-hidden="true"
                            >
                              <Icon size={16} />
                            </span>
                            <span className="min-w-0 flex-1 truncate">{t(item.labelKey)}</span>
                            <ChevronRight
                              size={14}
                              aria-hidden="true"
                              className={cn(
                                "shrink-0 transition-transform",
                                isActive ? "text-white opacity-90" : "text-white/20 group-hover:text-white/50"
                              )}
                            />
                          </Link>
                        );
                      })}
                    </div>
                  ) : null}
                </div>
              );
            })
          )}
        </nav>

        <div className="border-t border-white/10 bg-[#0f1a30] p-3 shrink-0 flex flex-col gap-2">
          <button
            type="button"
            onClick={() => {
              onNavigate();
              void navigate(ROUTE_PATTERNS.dashboard);
            }}
            className="flex min-h-[44px] w-full items-center justify-center gap-2 rounded-xl bg-white/10 hover:bg-white/15 active:scale-[0.98] px-3 py-2 text-[13px] font-bold text-white border border-white/10 transition-all cursor-pointer"
          >
            <ArrowLeft size={16} aria-hidden="true" />
            <span>{t("nav.backToApp")}</span>
          </button>
        </div>
      </SheetContent>
    </Sheet>
  );
}

export function AdminLayout() {
  const { t } = useTranslation("admin");
  const location = useLocation();
  const navigate = useNavigate();
  const user = useSessionStore((state) => state.user);
  const roles = user?.roles;
  const [menuOpen, setMenuOpen] = useState(false);

  const allows = useCallback<RoleCheck>((allowed) => hasAnyRole(roles, allowed), [roles]);
  const capabilities: AdminCapabilities = useMemo(() => computeCapabilities(ROLES, allows), [allows]);
  const groups = useMemo(() => filterNavGroups(ADMIN_NAV, allows), [allows]);
  const activeItem = findActiveNavItem(groups, location);
  const activeGroup = groups.find((group) => group.items.some((item) => item.id === activeItem?.id));
  if (!allows(ROLES.adminPortal)) {
    return (
      <main className="min-h-dvh bg-background p-6">
        <ForbiddenPage />
      </main>
    );
  }

  return (
    <ConfigProvider theme={adminTheme}>
      <div className="admin-shell flex min-h-dvh">
        <a
          href="#admin-main"
          className="sr-only z-[var(--z-toast)] rounded-md bg-surface px-3 py-2 text-caption font-semibold text-primary-dark focus:not-sr-only focus:fixed focus:left-3 focus:top-3"
        >
          {t("nav.skipToContent")}
        </a>

        <aside className="admin-sidebar sticky top-0 hidden h-dvh w-72 shrink-0 flex-col lg:flex">
          <div className="flex items-center gap-3 border-b border-white/10 px-5 py-5">
            <span
              className="flex size-11 items-center justify-center rounded-xl bg-primary text-white"
              aria-hidden="true"
            >
              <ShieldCheck size={20} />
            </span>
            <div className="min-w-0">
              <p className="truncate text-body font-extrabold tracking-tight text-white">{t("nav.brand")}</p>
              <p className="truncate text-small text-slate-300">{t("nav.label")}</p>
            </div>
          </div>
          <nav aria-label={t("nav.label")} className="admin-nav-scroll flex-1 overflow-y-auto px-3 py-5">
            <AdminNavList key={activeGroup?.id ?? "none"} groups={groups} location={location} />
          </nav>
          <div className="border-t border-white/10 px-4 py-4">
            <div className="mb-3 flex min-w-0 items-center gap-2.5">
              <Avatar
                size={36}
                className="!rounded-xl !bg-primary"
                icon={<ShieldCheck size={17} aria-hidden="true" />}
              />
              <span className="min-w-0 truncate text-caption font-semibold text-white">{user?.displayName}</span>
            </div>
            <AntButton
              type="text"
              block
              icon={<ArrowLeft size={16} aria-hidden="true" />}
              className="admin-back-link !flex !min-h-11 !items-center !justify-start"
              onClick={() => {
                void navigate(ROUTE_PATTERNS.dashboard);
              }}
            >
              {t("nav.backToApp")}
            </AntButton>
          </div>
        </aside>

        <div className="flex min-w-0 flex-1 flex-col">
          <header className="admin-topbar sticky top-0 z-[var(--z-sticky-header)] flex min-h-16 items-center justify-between gap-2.5 border-b border-border/60 bg-surface px-3.5 sm:px-6 lg:px-8">
            <div className="flex min-w-0 items-center gap-2.5">
              <div className="lg:hidden">
                <Button
                  type="button"
                  variant="tertiary"
                  size="sm"
                  aria-label={t("nav.openMenu")}
                  onClick={() => setMenuOpen(true)}
                  className="!size-11 !rounded-md !p-0 border border-border/80 bg-background text-text-primary shadow-xs hover:bg-background-alt"
                >
                  <Menu size={20} aria-hidden="true" />
                </Button>
                <AdminMobileDrawer
                  open={menuOpen}
                  onOpenChange={setMenuOpen}
                  groups={groups}
                  location={location}
                  user={user}
                  onNavigate={() => setMenuOpen(false)}
                />
              </div>

              <div className="min-w-0">
                <p className="flex items-center gap-1 text-[11px] font-semibold text-text-tertiary">
                  <span className="hidden lg:inline">{t("nav.label")}</span>
                  {activeGroup ? (
                    <>
                      <ChevronRight size={11} aria-hidden="true" className="hidden lg:inline" />
                      <span className="truncate">{t(activeGroup.labelKey)}</span>
                    </>
                  ) : null}
                </p>
                <h1 className="truncate text-[15px] sm:text-body font-extrabold tracking-tight text-text-primary">
                  {activeItem !== undefined ? t(activeItem.labelKey) : t("nav.label")}
                </h1>
              </div>
            </div>

            <div className="flex items-center gap-2">
              <Link
                to={ROUTE_PATTERNS.dashboard}
                aria-label={t("nav.backToApp")}
                title={t("nav.backToApp")}
                className="flex size-11 items-center justify-center rounded-md border border-border/80 bg-background text-text-secondary hover:text-text-primary hover:bg-background-alt shadow-xs lg:hidden cursor-pointer"
              >
                <ArrowLeft size={17} aria-hidden="true" />
              </Link>
              <Link
                to={ROUTE_PATTERNS.adminMeSecurity}
                className="flex min-h-11 items-center gap-2 rounded-md bg-background-alt border border-border/60 p-1.5 sm:px-3 sm:py-1.5 text-small font-semibold text-primary-dark hover:bg-background"
                title={user?.displayName ?? "Admin"}
              >
                <span className="relative flex size-7 items-center justify-center rounded-lg bg-primary text-white font-bold text-[11px]">
                  {user?.displayName ? user.displayName.slice(0, 1).toUpperCase() : <ShieldCheck size={14} />}
                  <span className="absolute -bottom-0.5 -right-0.5 size-2 rounded-full bg-emerald-500 ring-2 ring-surface" />
                </span>
                <span className="hidden md:inline truncate max-w-[140px]">{user?.displayName}</span>
              </Link>
            </div>
          </header>

          <main id="admin-main" tabIndex={-1} className="flex-1 px-3.5 py-4 sm:px-6 lg:px-8 lg:py-7">
            <AdminAccessProvider capabilities={capabilities}>
              <Outlet />
            </AdminAccessProvider>
          </main>
        </div>
      </div>
    </ConfigProvider>
  );
}
