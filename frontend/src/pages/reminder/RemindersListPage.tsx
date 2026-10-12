import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { ArrowRight, Bell, CalendarPlus, CalendarX2, ChevronRight } from "lucide-react";
import { EmptyState, ErrorState, SkeletonLoader, Switch } from "@/shared/ui";
import { CatAvatar } from "@/entities/cat";
import { useCatList } from "@/features/cat";
import { isFeatureLocked, useReminders, useUpdateReminder, type Reminder } from "@/features/reminder";
import {
  BreedChip,
  DueChip,
  EducationCard,
  FeatureLockedCard,
  NeedScanChip,
  PageHeader,
  ReminderStatusLine,
  dueState,
  formatDateTime,
  reminderAction,
  useNextRunLabel,
  useScheduleLabel,
} from "./reminderUi";
import { ReminderQuickPlan } from "./ReminderQuickPlan";
import { WebRemindersScreen, type ReminderTab } from "./webReminders";

export function RemindersListPage() {
  const { t } = useTranslation(["reminder", "common"]);
  const [tab, setTab] = useState<ReminderTab>("ALL");
  const [togglingId, setTogglingId] = useState<string | null>(null);

  const remindersQuery = useReminders();
  const catsQuery = useCatList("ACTIVE");
  const updateReminder = useUpdateReminder();
  const scheduleLabel = useScheduleLabel();
  const nextRunLabel = useNextRunLabel();

  const locked = isFeatureLocked(remindersQuery.error);
  const reminders = remindersQuery.data?.items ?? [];

  const catItems = catsQuery.data?.items;
  const catById = useMemo(() => new Map((catItems ?? []).map((cat) => [cat.id, cat] as const)), [catItems]);
  const catNameById = useMemo(() => new Map((catItems ?? []).map((cat) => [cat.id, cat.name] as const)), [catItems]);

  const handleToggle = (reminder: Reminder) => {
    setTogglingId(reminder.id);
    updateReminder.mutate(
      { reminderId: reminder.id, patch: { active: !reminder.active } },
      {
        onSettled: () => {
          setTogglingId(null);
        },
      },
    );
  };

  const newLink = (
    <Link
      to="/reminders/new"
      className="inline-flex shrink-0 items-center gap-1.5 text-caption font-bold text-primary-dark"
    >
      <CalendarPlus size={15} aria-hidden="true" />
      {t("list.addCta")}
    </Link>
  );

  let mobileContent;
  if (locked) {
    mobileContent = <FeatureLockedCard />;
  } else if (remindersQuery.isPending) {
    mobileContent = (
      <div className="flex flex-col gap-3">
        <SkeletonLoader shape="card" className="h-36" />
        <SkeletonLoader shape="card" className="h-36" />
      </div>
    );
  } else if (remindersQuery.isError) {
    mobileContent = (
      <ErrorState
        title={t("list.loadError")}
        onRetry={() => {
          void remindersQuery.refetch();
        }}
        retryLabel={t("actions.retry", { ns: "common" })}
      />
    );
  } else if (reminders.length === 0) {
    mobileContent = (
      <EmptyState
        icon={<Bell size={22} aria-hidden="true" />}
        title={t("list.emptyTitle")}
        description={t("list.emptyDescription")}
        action={
          <Link
            to="/reminders/new"
            className="inline-flex items-center justify-center rounded-xl bg-primary-dark px-4 py-2.5 text-caption font-bold text-white shadow-sm"
          >
            {t("list.emptyCta")}
          </Link>
        }
      />
    );
  } else {
    mobileContent = (
      <ul className="flex flex-col gap-3">
        {reminders.map((reminder) => {
          const due = dueState(reminder.nextRunAt);
          const cat = reminder.catId === undefined ? undefined : catById.get(reminder.catId);
          const displayName =
            cat?.name ?? (reminder.catId === undefined ? t("list.accountWide") : t("list.unknownCat"));

          const chipLabel = cat?.breedName ?? cat?.breedOther ?? t(`type.${reminder.type}`);
          const overdue = due.kind === "overdue";

          return (
            <li key={reminder.id}>
              <article className="flex flex-col gap-3 rounded-2xl bg-surface p-4 shadow-brand-md">
                <div className="flex items-start gap-3">
                  <CatAvatar name={displayName} src={cat?.avatarUrl} size="md" />
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="truncate text-h3 font-bold text-text-primary">{displayName}</p>
                      {overdue && reminder.type === "SCAN_ROUTINE" ? <NeedScanChip /> : <BreedChip label={chipLabel} />}
                    </div>
                    <ReminderStatusLine reminder={reminder} due={due} schedule={scheduleLabel(reminder)} />
                  </div>
                  <Switch
                    checked={reminder.active}
                    disabled={togglingId === reminder.id}
                    aria-label={t("list.toggleLabel")}
                    onCheckedChange={() => {
                      handleToggle(reminder);
                    }}
                  />
                </div>

                {reminder.nextRunAt === undefined ? null : (
                  <div className="flex items-center gap-3 rounded-xl bg-background-alt p-3">
                    <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-surface text-primary-dark">
                      <Bell size={16} aria-hidden="true" />
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block text-small text-text-secondary">{nextRunLabel(reminder)}</span>
                      <span className="block truncate text-body font-bold text-text-primary">
                        {formatDateTime(reminder.nextRunAt)}
                      </span>
                    </span>
                    {overdue ? null : <DueChip due={due} />}
                  </div>
                )}

                {overdue ? (
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <span className="flex items-center gap-1.5 text-caption font-bold text-danger-text">
                      <CalendarX2 size={14} className="shrink-0" aria-hidden="true" />
                      {due.days === 0 ? t("list.overdueToday") : t("list.overdueDays", { days: due.days })}
                    </span>
                    <Link
                      to={reminderAction(reminder).to}
                      className="inline-flex items-center gap-1.5 text-caption font-bold text-primary-dark"
                    >
                      {reminder.type === "SCAN_ROUTINE" ? t("list.quickScanCta") : t(reminderAction(reminder).labelKey)}
                      <ArrowRight size={14} aria-hidden="true" />
                    </Link>
                  </div>
                ) : null}

                <Link
                  to={`/reminders/${reminder.id}`}
                  className="inline-flex items-center gap-1 self-end text-caption font-semibold text-primary-dark"
                >
                  {t("list.openDetail")}
                  <ChevronRight size={14} aria-hidden="true" />
                </Link>
              </article>
            </li>
          );
        })}
      </ul>
    );
  }

  return (
    <>
      <div className="hidden lg:block">
        <WebRemindersScreen
          reminders={reminders}
          catNameById={catNameById}
          isPending={remindersQuery.isPending}
          locked={locked}
          isError={remindersQuery.isError && !locked}
          tab={tab}
          onTabChange={setTab}
          onToggle={handleToggle}
          togglingId={togglingId}
          onRetry={() => {
            void remindersQuery.refetch();
          }}
        />
      </div>

      <div className="flex flex-col gap-4 lg:hidden">
        <PageHeader title={t("pages.list.title")} subtitle={t("list.subtitle")} action={newLink} />
        <h2 className="text-body font-bold text-text-primary">{t("list.sectionCats")}</h2>
        {mobileContent}
        {locked ? null : <ReminderQuickPlan reminders={reminders} catNameById={catNameById} />}
        {locked ? null : <EducationCard />}
      </div>
    </>
  );
}
