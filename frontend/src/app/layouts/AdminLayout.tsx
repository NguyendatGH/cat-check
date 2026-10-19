import { useCallback, useMemo, useState } from "react";
import { Link, Outlet, useLocation } from "react-router";
import { useTranslation } from "react-i18next";
import { Avatar, ConfigProvider } from "antd";
import {
  Archive,
  ChevronDown,
  ChevronRight,
  BellRing,
  BookOpenText,
  CalendarClock,
  ClipboardList,
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
import { StaffLogoutButton } from "./shell/StaffLogoutButton";
import { ROUTE_PATTERNS } from "../router/routes";
import { ROLES, hasAnyRole } from "@/shared/auth/roles";
import { adminTheme } from "./adminTheme";
import "./adminLayout.css";

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
        id: "orders",
        to: ROUTE_PATTERNS.adminOrders,
        labelKey: "nav.orders",
        icon: ClipboardList,
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

// Vai trò ưu tiên hiển thị dưới tên người dùng; dùng nhãn nghiệp vụ thay cho enum thô (p17 A4).
const ROLE_PRIORITY = ["ADMIN_SUPER", "DPO", "ADMIN_SUPPORT", "ADMIN_CATALOG", "MODERATOR"] as const;

function primaryRoleLabel(roles: readonly string[] | undefined, t: (key: string) => string): string {
  const role = ROLE_PRIORITY.find((item) => roles?.includes(item));
  return role === undefined ? t("nav.label") : t(`roleLabels.${role}`);
}

function hrefOf(item: AdminNavItem): string {
  return item.tab === undefined ? item.to : `${item.to}?tab=${item.tab}`;
}

interface AdminNavListProps {
  groups: readonly AdminNavGroup<AdminNavItem>[];
  location: { pathname: string; search: string };
}

function filterNavByQuery(
  groups: readonly AdminNavGroup<AdminNavItem>[],
  query: string,
  label: (item: AdminNavItem) => string,
): AdminNavGroup<AdminNavItem>[] {
  return groups
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => label(item).toLowerCase().includes(query)),
    }))
    .filter((group) => group.items.length > 0);
}

function NavSearchInput({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const { t } = useTranslation("admin");
  return (
    <div className="relative">
      <Search
        size={14}
        className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
        aria-hidden="true"
      />
      <input
        type="search"
        value={value}
        onChange={(event) => {
          onChange(event.target.value);
        }}
        aria-label={t("actions.search")}
        placeholder={t("actions.search")}
        className="h-10 w-full rounded-md border border-white/15 bg-white/5 pl-8 pr-10 text-[13px] text-white transition-colors placeholder:text-slate-400 focus:border-transparent focus:outline-none focus:ring-2 focus:ring-primary"
      />
      {value ? (
        <button
          type="button"
          onClick={() => {
            onChange("");
          }}
          className="absolute right-0 top-1/2 flex size-10 -translate-y-1/2 items-center justify-center text-slate-400 hover:text-white"
          aria-label={t("actions.clearSearch")}
        >
          <X size={13} aria-hidden="true" />
        </button>
      ) : null}
    </div>
  );
}

function NavSearchEmpty({ onClear }: { onClear: () => void }) {
  const { t } = useTranslation("admin");
  return (
    <div className="py-8 text-center text-[12px] text-slate-400">
      <p>{t("nav.searchEmpty")}</p>
      <button
        type="button"
        onClick={onClear}
        className="mt-2 min-h-11 cursor-pointer font-medium text-primary-light underline"
      >
        {t("nav.clearFilter")}
      </button>
    </div>
  );
}

function AdminNavList({ groups, location }: AdminNavListProps) {
  const { t } = useTranslation("admin");
  const [query, setQuery] = useState("");
  const normalizedQuery = query.trim().toLowerCase();
  const activeItem = findActiveNavItem(groups, location);
  const activeGroupId = groups.find((group) => group.items.some((item) => item.id === activeItem?.id))?.id;
  const [openGroups, setOpenGroups] = useState<ReadonlySet<string>>(
    () => new Set(activeGroupId ? [activeGroupId] : []),
  );
  const [trackedActive, setTrackedActive] = useState(activeGroupId);
  if (activeGroupId !== trackedActive) {
    setTrackedActive(activeGroupId);
    if (activeGroupId && !openGroups.has(activeGroupId)) setOpenGroups(new Set(openGroups).add(activeGroupId));
  }

  const toggle = (groupId: string) => {
    setOpenGroups((prev) => {
      const next = new Set(prev);
      if (next.has(groupId)) next.delete(groupId);
      else next.add(groupId);
      return next;
    });
  };

  const navLink = (item: AdminNavItem, nested: boolean) => {
    const Icon = item.icon;
    const active = activeItem?.id === item.id;
    return (
      <li key={item.id}>
        <Link
          to={hrefOf(item)}
          aria-current={active ? "page" : undefined}
          className={cn("admin-nav-item", nested && "admin-nav-item--nested", active && "is-active")}
        >
          <Icon size={17} aria-hidden="true" className="shrink-0" />
          <span className="min-w-0 truncate">{t(item.labelKey)}</span>
        </Link>
      </li>
    );
  };

  if (normalizedQuery) {
    const matches = filterNavByQuery(groups, normalizedQuery, (item) => t(item.labelKey));
    return (
      <div className="flex flex-col gap-3">
        <NavSearchInput value={query} onChange={setQuery} />
        {matches.length === 0 ? (
          <NavSearchEmpty
            onClear={() => {
              setQuery("");
            }}
          />
        ) : (
          <ul className="flex flex-col gap-0.5">
            {matches.flatMap((group) => group.items.map((item) => navLink(item, false)))}
          </ul>
        )}
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-3">
      <NavSearchInput value={query} onChange={setQuery} />
      <ul className="flex flex-col gap-1">
        {groups.map((group) => {
          if (group.id === "overview" && group.items.length === 1) return navLink(group.items[0], false);
          const isOpen = openGroups.has(group.id);
          const panelId = `admin-nav-${group.id}`;
          return (
            <li key={group.id}>
              <button
                type="button"
                aria-expanded={isOpen}
                aria-controls={panelId}
                onClick={() => {
                  toggle(group.id);
                }}
                className={cn("admin-nav-group", group.id === activeGroupId && "has-active")}
              >
                <span className="min-w-0 flex-1 truncate text-left">{t(group.labelKey)}</span>
                <ChevronDown size={16} aria-hidden="true" className="admin-nav-chevron shrink-0" />
              </button>
              <div id={panelId} className="nav-collapse" data-open={isOpen} inert={!isOpen}>
                <div>
                  <ul className="flex flex-col gap-0.5 pb-2 pt-1">{group.items.map((item) => navLink(item, true))}</ul>
                </div>
              </div>
            </li>
          );
        })}
      </ul>
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

function AdminMobileDrawer({ open, onOpenChange, groups, location, user, onNavigate }: AdminMobileDrawerProps) {
  const { t } = useTranslation("admin");
  const [searchQuery, setSearchQuery] = useState("");
  const activeItem = findActiveNavItem(groups, location);
  const [collapsedGroups, setCollapsedGroups] = useState<Record<string, boolean>>({});

  const toggleGroup = (groupId: string) => {
    setCollapsedGroups((prev) => ({ ...prev, [groupId]: !prev[groupId] }));
  };

  const normalizedQuery = searchQuery.trim().toLowerCase();

  const filteredGroups = useMemo(() => {
    if (!normalizedQuery) return groups;
    return filterNavByQuery(groups, normalizedQuery, (item) => t(item.labelKey));
  }, [groups, normalizedQuery, t]);

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side="left"
        aria-describedby={undefined}
        className="admin-mobile-drawer !p-0 !max-w-[340px] w-[86vw] text-white border-r border-white/10 flex flex-col h-dvh overflow-hidden shadow-lg"
      >
        <SheetTitle className="sr-only">{t("nav.label")}</SheetTitle>

        <div className="admin-drawer-header border-b border-white/10 px-4 pt-5 pb-3 shrink-0">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2.5">
              <span className="flex size-9 items-center justify-center rounded-lg bg-primary text-white">
                <ShieldCheck size={19} aria-hidden="true" />
              </span>
              <div className="min-w-0">
                <p className="truncate text-[15px] font-extrabold tracking-tight text-white leading-tight">
                  {t("nav.brand")}
                </p>
                <p className="truncate text-[11px] font-medium text-slate-300">{t("nav.label")}</p>
              </div>
            </div>
            <SheetClose asChild>
              <button
                type="button"
                aria-label={t("actions.close")}
                className="flex size-11 items-center justify-center rounded-md border border-white/10 bg-white/5 text-slate-300 hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
              >
                <X size={17} aria-hidden="true" />
              </button>
            </SheetClose>
          </div>

          <div className="mt-3 flex items-center gap-2.5 rounded-lg bg-white/[0.06] p-2 border border-white/10">
            <Avatar size={32} className="!rounded-md !bg-primary" icon={<ShieldCheck size={16} aria-hidden="true" />} />
            <div className="min-w-0 flex-1">
              <p className="truncate text-[12px] font-bold text-white">{user?.displayName}</p>
              <p className="truncate text-[10px] text-slate-400">{primaryRoleLabel(user?.roles, t)}</p>
            </div>
            <Link
              to={ROUTE_PATTERNS.adminMeSecurity}
              onClick={onNavigate}
              aria-label={t("nav.settings")}
              className="flex size-11 items-center justify-center rounded-md bg-white/10 text-slate-200 hover:text-white hover:bg-white/20 transition-colors cursor-pointer"
            >
              <Settings size={16} aria-hidden="true" />
            </Link>
            <StaffLogoutButton variant="icon" className="bg-white/10 text-slate-200 hover:bg-danger hover:text-white" />
          </div>
        </div>

        <div className="px-3.5 pt-3 pb-1 shrink-0">
          <NavSearchInput value={searchQuery} onChange={setSearchQuery} />
        </div>

        <nav aria-label={t("nav.label")} className="admin-mobile-scroll flex-1 overflow-y-auto px-3 py-2 space-y-3">
          {filteredGroups.length === 0 ? (
            <NavSearchEmpty
              onClear={() => {
                setSearchQuery("");
              }}
            />
          ) : (
            filteredGroups.map((group) => {
              const isCollapsed = !normalizedQuery && collapsedGroups[group.id];
              return (
                <div key={group.id} className="space-y-1">
                  <button
                    type="button"
                    onClick={() => {
                      toggleGroup(group.id);
                    }}
                    aria-expanded={!isCollapsed}
                    className="flex min-h-11 w-full items-center justify-between px-2 text-[11px] font-bold tracking-wider text-slate-400 uppercase hover:text-slate-200 transition-colors cursor-pointer"
                  >
                    <span>{t(group.labelKey)}</span>
                    <span className="flex items-center gap-1.5 text-[10px] text-slate-400 bg-white/5 px-1.5 py-0.5 rounded-md font-mono">
                      {group.items.length}
                    </span>
                  </button>

                  <div className="nav-collapse" data-open={!isCollapsed} inert={isCollapsed}>
                    <div>
                      <div className="space-y-1 pb-1">
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
                                  : "text-slate-300 hover:bg-white/8 hover:text-white active:bg-white/10",
                              )}
                            >
                              <span
                                className={cn(
                                  "flex size-8 shrink-0 items-center justify-center rounded-md transition-colors",
                                  isActive
                                    ? "bg-white/20 text-white"
                                    : "bg-white/5 text-slate-300 group-hover:bg-white/10 group-hover:text-white",
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
                                  isActive ? "text-white opacity-90" : "text-white/20 group-hover:text-white/50",
                                )}
                              />
                            </Link>
                          );
                        })}
                      </div>
                    </div>
                  </div>
                </div>
              );
            })
          )}
        </nav>
      </SheetContent>
    </Sheet>
  );
}

export function AdminLayout() {
  const { t } = useTranslation("admin");
  const location = useLocation();
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
            <AdminNavList groups={groups} location={location} />
          </nav>
          <div className="border-t border-white/10 px-4 py-4">
            <div className="flex min-w-0 items-center gap-2.5">
              <Avatar
                size={36}
                className="!rounded-xl !bg-primary"
                icon={<ShieldCheck size={17} aria-hidden="true" />}
              />
              <div className="min-w-0">
                <p className="truncate text-caption font-semibold text-white">{user?.displayName}</p>
                <p className="truncate text-small text-slate-300">{primaryRoleLabel(user?.roles, t)}</p>
              </div>
            </div>
            <StaffLogoutButton className="mt-3 w-full border border-white/10 bg-white/5 text-caption text-slate-200 hover:bg-danger hover:text-white" />
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
                  onClick={() => {
                    setMenuOpen(true);
                  }}
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
                  onNavigate={() => {
                    setMenuOpen(false);
                  }}
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
                <p className="truncate text-[15px] sm:text-body font-extrabold tracking-tight text-text-primary">
                  {activeItem !== undefined ? t(activeItem.labelKey) : t("nav.label")}
                </p>
              </div>
            </div>

            <div className="flex items-center gap-2">
              <Link
                to={ROUTE_PATTERNS.adminMeSecurity}
                className="flex min-h-11 items-center gap-2 rounded-md bg-background-alt border border-border/60 p-1.5 sm:px-3 sm:py-1.5 text-small font-semibold text-primary-dark hover:bg-background"
                title={t("nav.settings")}
                aria-label={t("nav.settings")}
              >
                <span className="relative flex size-7 items-center justify-center rounded-lg bg-primary text-white font-bold text-[11px]">
                  {user?.displayName ? user.displayName.slice(0, 1).toUpperCase() : <ShieldCheck size={14} />}
                </span>
                <span className="hidden md:inline truncate max-w-[140px]">{user?.displayName}</span>
              </Link>
              <StaffLogoutButton
                variant="icon"
                className="border border-border/60 bg-background-alt text-text-secondary hover:border-danger hover:bg-danger-bg hover:text-danger"
              />
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
