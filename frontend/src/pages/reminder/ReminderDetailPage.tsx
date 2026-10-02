import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslation } from "react-i18next";
import { Link, useNavigate, useParams } from "react-router";
import { toast } from "sonner";
import { CalendarClock, Trash2 } from "lucide-react";
import { Button, EmptyState, ErrorState, SkeletonLoader, Switch } from "@/shared/ui";
import { isApiError } from "@/shared/api";
import { useCatList } from "@/features/cat";
import {
  REMINDER_ERROR,
  isFeatureLocked,
  reminderCalendarUrl,
  reminderErrorCode,
  useDeleteReminder,
  useReminder,
  useUpdateReminder,
} from "@/features/reminder";
import {
  AddToCalendarLink,
  ChannelPicker,
  DueChip,
  FeatureLockedCard,
  FrequencyPicker,
  GenericSaveError,
  PageHeader,
  ScheduleInvalidNotice,
  StatusChip,
  TimeWindowPicker,
  dueState,
  formatDateTime,
  buildReminderPatch,
  reminderFormSchema,
  toReminderForm,
  useScheduleLabel,
  type ReminderFormValues,
} from "./reminderUi";

/**
 * `/reminders/:reminderId` — chi tiết + sửa một lịch nhắc (I3 đọc, I4 sửa, I5 xoá mềm, I6 .ics).
 *
 * Dùng lại nguyên bộ field của trang tạo mới (frame `09`), thêm cột tóm tắt bên phải ở desktop
 * theo khuôn `SettingsSecurityPage` (cột nội dung + panel 360px). Trang nằm trong `TaskLayout`
 * nên không tự thêm padding ngang.
 */

export function ReminderDetailPage() {
  const { t } = useTranslation(["reminder", "common"]);
  const { reminderId } = useParams<{ reminderId: string }>();
  const navigate = useNavigate();

  const reminderQuery = useReminder(reminderId);
  const catsQuery = useCatList("ACTIVE");
  const updateReminder = useUpdateReminder();
  const deleteReminder = useDeleteReminder();
  const scheduleLabel = useScheduleLabel();

  const [confirmingDelete, setConfirmingDelete] = useState(false);

  const {
    handleSubmit,
    watch,
    setValue,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<ReminderFormValues>({
    resolver: zodResolver(reminderFormSchema),
    defaultValues: {
      catId: "",
      scheduleMode: "INTERVAL",
      intervalDays: "",
      rrule: "",
      preferredTimeStart: "",
      preferredTimeEnd: "",
      channels: [],
      active: true,
      // Mèo đã gắn lúc tạo và server không cho đổi — không bắt buộc chọn lại ở trang này.
      requireCat: false,
    },
  });

  const reminder = reminderQuery.data;

  useEffect(() => {
    if (reminder === undefined) return;
    reset(toReminderForm(reminder));
  }, [reminder, reset]);

  const values = watch();
  const errorCode = reminderErrorCode(reminderQuery.error);

  // ── Trạng thái tải ───────────────────────────────────────────────────────────────────
  if (isFeatureLocked(reminderQuery.error)) {
    return (
      <div className="flex flex-col gap-4">
        <PageHeader title={t("pages.detail.title")} />
        <FeatureLockedCard />
      </div>
    );
  }

  if (
    errorCode === REMINDER_ERROR.NOT_FOUND ||
    (isApiError(reminderQuery.error) && reminderQuery.error.status === 404)
  ) {
    return (
      <EmptyState
        icon={<CalendarClock size={22} aria-hidden="true" />}
        title={t("errors.notFoundTitle")}
        description={t("errors.notFoundBody")}
        action={
          <Link
            to="/reminders"
            className="inline-flex items-center justify-center rounded-xl bg-primary-dark px-4 py-2.5 text-caption font-bold text-white shadow-sm"
          >
            {t("errors.notFoundCta")}
          </Link>
        }
      />
    );
  }

  if (reminderQuery.isPending) {
    return (
      <div className="flex flex-col gap-3">
        <SkeletonLoader shape="card" className="h-24" />
        <SkeletonLoader shape="card" className="h-56" />
      </div>
    );
  }

  if (reminderQuery.isError || reminder === undefined) {
    return (
      <ErrorState
        title={t("list.loadError")}
        onRetry={() => {
          void reminderQuery.refetch();
        }}
        retryLabel={t("actions.retry", { ns: "common" })}
      />
    );
  }

  // ── Dữ liệu dẫn xuất ─────────────────────────────────────────────────────────────────
  const cats = catsQuery.data?.items ?? [];
  const catName =
    reminder.catId === undefined
      ? t("list.accountWide")
      : (cats.find((cat) => cat.id === reminder.catId)?.name ?? t("list.unknownCat"));
  const due = dueState(reminder.nextRunAt);
  const saveErrorCode = reminderErrorCode(updateReminder.error);
  const isSaving = isSubmitting || updateReminder.isPending;

  const onSubmit = (formValues: ReminderFormValues) => {
    const patch = buildReminderPatch(formValues, reminder);
    if (Object.keys(patch).length === 0) {
      toast.success(t("form.saved"));
      return;
    }
    updateReminder.mutate(
      { reminderId: reminder.id, patch },
      {
        onSuccess: () => {
          toast.success(t("form.saved"));
        },
      },
    );
  };

  return (
    <form
      onSubmit={(event) => {
        void handleSubmit(onSubmit)(event);
      }}
      className="flex flex-col gap-5"
    >
      <PageHeader title={t("pages.detail.title")} subtitle={catName} />

      <div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:gap-6">
        <div className="flex min-w-0 flex-1 flex-col gap-4">
          <FrequencyPicker
            scheduleMode={values.scheduleMode}
            intervalDays={values.intervalDays}
            rrule={values.rrule}
            errorKeyInterval={errors.intervalDays?.message}
            errorKeyRrule={errors.rrule?.message}
            onModeChange={(mode) => {
              setValue("scheduleMode", mode, { shouldValidate: true });
            }}
            onIntervalChange={(value) => {
              setValue("intervalDays", value, { shouldValidate: true });
            }}
            onRruleChange={(value) => {
              setValue("rrule", value, { shouldValidate: true });
            }}
          />

          <TimeWindowPicker
            start={values.preferredTimeStart}
            end={values.preferredTimeEnd}
            timezone={reminder.timezone}
            errorKey={errors.preferredTimeEnd?.message}
            onStartChange={(value) => {
              setValue("preferredTimeStart", value, { shouldValidate: true });
            }}
            onEndChange={(value) => {
              setValue("preferredTimeEnd", value, { shouldValidate: true });
            }}
          />

          <ChannelPicker
            value={values.channels}
            errorKey={errors.channels?.message}
            onChange={(channels) => {
              setValue("channels", channels, { shouldValidate: true });
            }}
          />

          {saveErrorCode === REMINDER_ERROR.SCHEDULE_INVALID ? (
            <ScheduleInvalidNotice />
          ) : updateReminder.isError ? (
            <GenericSaveError />
          ) : null}

          <Button type="submit" size="lg" loading={isSaving}>
            {t("form.submitSave")}
          </Button>
        </div>

        {/* Panel tóm tắt 360px ở desktop — cùng khuôn `SettingsSecurityPage`. */}
        <aside className="flex w-full flex-col gap-3 rounded-2xl bg-surface p-5 shadow-brand-md lg:w-[360px] lg:shrink-0">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h2 className="text-h3 font-bold text-text-primary">{t(`type.${reminder.type}`)}</h2>
            <StatusChip reminder={reminder} due={due} />
          </div>

          <div className="flex min-h-11 items-center justify-between gap-3">
            <span>
              <span className="block text-caption font-semibold text-text-primary">
                {t("form.activeLabel")}
              </span>
              <span className="block text-small text-text-tertiary">{t("form.activeHint")}</span>
            </span>
            <Switch
              checked={values.active}
              aria-label={t("form.activeLabel")}
              onCheckedChange={(checked) => {
                setValue("active", checked, { shouldValidate: true });
              }}
            />
          </div>

          <dl className="flex flex-col gap-2 rounded-xl bg-background-alt p-3">
            <div className="flex items-center justify-between gap-3">
              <dt className="text-small text-text-secondary">{t("list.nextRunLabel")}</dt>
              <dd className="text-caption font-bold text-text-primary">
                {reminder.nextRunAt === undefined
                  ? t("list.nextRunUnknown")
                  : formatDateTime(reminder.nextRunAt)}
              </dd>
            </div>
            <div className="flex items-center justify-between gap-3">
              <dt className="text-small text-text-secondary">{t("list.lastRunLabel")}</dt>
              <dd className="text-caption text-text-primary">
                {reminder.lastRunAt === undefined
                  ? t("list.nextRunUnknown")
                  : formatDateTime(reminder.lastRunAt)}
              </dd>
            </div>
            <div className="flex items-center justify-between gap-3">
              <dt className="text-small text-text-secondary">{t("list.lastSatisfiedLabel")}</dt>
              <dd className="text-caption text-text-primary">
                {reminder.lastSatisfiedAt === undefined
                  ? t("list.nextRunUnknown")
                  : formatDateTime(reminder.lastSatisfiedAt)}
              </dd>
            </div>
            <div className="flex items-center justify-between gap-3">
              <dt className="text-small text-text-secondary">{t("form.frequencyTitle")}</dt>
              <dd className="text-caption text-text-primary">{scheduleLabel(reminder)}</dd>
            </div>
            <div className="flex items-center justify-between gap-3">
              <dt className="text-small text-text-secondary">{t("source.USER")}</dt>
              <dd className="text-caption text-text-primary">{t(`source.${reminder.source}`)}</dd>
            </div>
          </dl>

          <DueChip due={due} />

          <AddToCalendarLink href={reminderCalendarUrl(reminder.id)} className="w-full" />

          {confirmingDelete ? (
            <div className="flex flex-col gap-2 rounded-xl bg-danger-bg p-3">
              <p className="text-caption text-danger-text">{t("form.deleteConfirm")}</p>
              <div className="flex gap-2">
                <Button
                  type="button"
                  size="sm"
                  loading={deleteReminder.isPending}
                  onClick={() => {
                    deleteReminder.mutate(reminder.id, {
                      onSuccess: () => {
                        void navigate("/reminders", { replace: true });
                      },
                      onError: () => {
                        toast.error(t("errors.deleteFailed"));
                      },
                    });
                  }}
                >
                  {t("form.deleteCta")}
                </Button>
                <Button
                  type="button"
                  size="sm"
                  variant="tertiary"
                  onClick={() => {
                    setConfirmingDelete(false);
                  }}
                >
                  {t("actions.cancel", { ns: "common" })}
                </Button>
              </div>
            </div>
          ) : (
            <button
              type="button"
              onClick={() => {
                setConfirmingDelete(true);
              }}
              className="inline-flex items-center justify-center gap-2 rounded-xl px-4 py-2.5 text-caption font-semibold text-danger-text hover:bg-danger-bg"
            >
              <Trash2 size={15} aria-hidden="true" />
              {t("form.deleteCta")}
            </button>
          )}
        </aside>
      </div>
    </form>
  );
}
